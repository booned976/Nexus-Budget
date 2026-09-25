package com.nexusbudget.app.data

import androidx.room.withTransaction
import com.nexusbudget.app.data.db.NexusDatabase
import com.nexusbudget.app.data.db.RuleEntity
import com.nexusbudget.core.demo.DemoData
import com.nexusbudget.core.engine.Categorizer
import com.nexusbudget.core.engine.FinancialPicture
import com.nexusbudget.core.engine.MerchantNormalizer
import com.nexusbudget.core.engine.NetWorthCalculator
import com.nexusbudget.core.engine.TransactionPipeline
import com.nexusbudget.core.importer.ImportedRow
import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.BudgetTarget
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.Category
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.CategoryRule
import com.nexusbudget.core.model.Goal
import com.nexusbudget.core.model.Holding
import com.nexusbudget.core.model.NetWorthSnapshot
import com.nexusbudget.core.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Raw data plus the computed financial picture, emitted together so screens never see them out of sync. */
data class FinanceState(
    val picture: FinancialPicture,
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val categories: CategoryIndex,
    val budgets: List<BudgetTarget>,
    val goals: List<Goal>,
    val snapshots: List<NetWorthSnapshot>,
    val settings: AppSettings,
)

class FinanceRepository(
    private val db: NexusDatabase,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    init {
        scope.launch { ensureDefaultCategories() }
    }

    val accounts: Flow<List<Account>> = db.accounts().observeAll().map { list -> list.map { it.toDomain() } }

    /** Two years is plenty for budgets, trends and recurring detection. */
    val transactions: Flow<List<Transaction>> =
        db.transactions().observeSince(LocalDate.now().minusYears(2).toEpochDay()).map { list -> list.map { it.toDomain() } }

    val categories: Flow<CategoryIndex> = db.categories().observeAll().map { list ->
        CategoryIndex(if (list.isEmpty()) Categories.defaults else list.map { it.toDomain() })
    }

    val budgets: Flow<List<BudgetTarget>> = db.budgets().observeAll().map { list -> list.map { it.toDomain() } }
    val goals: Flow<List<Goal>> = db.goals().observeAll().map { list -> list.map { it.toDomain() } }
    val snapshots: Flow<List<NetWorthSnapshot>> = db.snapshots().observeAll().map { list -> list.map { it.toDomain() } }
    val rules: Flow<List<CategoryRule>> = db.rules().observeAll().map { list -> list.map { it.toDomain() } }

    fun holdings(accountId: String): Flow<List<Holding>> = db.holdings().observeForAccount(accountId).map { list -> list.map { it.toDomain() } }

    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(15 * 60 * 1000L)
        }
    }.distinctUntilChanged()

    private data class Raw(val accounts: List<Account>, val transactions: List<Transaction>, val categories: CategoryIndex)
    private data class Plans(val budgets: List<BudgetTarget>, val goals: List<Goal>, val snapshots: List<NetWorthSnapshot>)

    /** The single source of truth for every screen. Null until the first computation finishes. */
    val state: StateFlow<FinanceState?> = combine(
        combine(accounts, transactions, categories) { a, t, c -> Raw(a, t, c) },
        combine(budgets, goals, snapshots) { b, g, s -> Plans(b, g, s) },
        settingsRepository.settings,
        today,
    ) { raw, plans, settings, date ->
        val picture = FinancialPicture.compute(
            today = date,
            accounts = raw.accounts,
            transactions = raw.transactions,
            categories = raw.categories,
            budgets = plans.budgets,
            goals = plans.goals,
            snapshots = plans.snapshots,
            extraDebtPayment = settings.extraDebtPayment,
            dismissedRecurring = settings.dismissedRecurring,
            dismissedRecommendations = settings.dismissedRecommendations,
        )
        FinanceState(picture, raw.accounts, raw.transactions, raw.categories, plans.budgets, plans.goals, plans.snapshots, settings)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private suspend fun ensureDefaultCategories() {
        db.categories().insertAll(Categories.defaults.map { it.toEntity() })
    }

    suspend fun categorizer(): Categorizer {
        val rules = db.rules().all().map { it.toDomain() }
        val history = db.transactions().userCategorized().map { it.toDomain() }
        return Categorizer(rules, history)
    }

    // Accounts

    suspend fun account(id: String): Account? = db.accounts().get(id)?.toDomain()

    suspend fun saveManualAccount(account: Account) {
        val existing = db.accounts().get(account.id)
        db.accounts().upsert(
            account.copy(lastUpdated = Instant.now()).toEntity(
                externalId = existing?.externalId,
                userEditedType = existing?.userEditedType ?: false,
                userEditedDebtDetails = existing?.userEditedDebtDetails ?: false,
            ),
        )
        recordSnapshot()
    }

    /** Edits made on the account screen. For synced accounts, balances stay under the bank's control. */
    suspend fun updateAccountDetails(updated: Account) {
        val existing = db.accounts().get(updated.id) ?: return
        val current = existing.toDomain()
        val typeChanged = current.type != updated.type
        val debtChanged = current.apr != updated.apr || current.minimumPayment != updated.minimumPayment ||
            current.paymentDueDay != updated.paymentDueDay || current.creditLimit != updated.creditLimit
        val merged = if (current.isManual) updated else updated.copy(balance = current.balance, available = current.available)
        db.accounts().upsert(
            merged.toEntity(
                externalId = existing.externalId,
                userEditedType = existing.userEditedType || typeChanged,
                userEditedDebtDetails = existing.userEditedDebtDetails || debtChanged,
            ),
        )
        recordSnapshot()
    }

    suspend fun deleteAccount(id: String) {
        db.withTransaction {
            db.transactions().deleteForAccount(id)
            db.holdings().deleteForAccounts(listOf(id))
            db.accounts().delete(id)
        }
        recordSnapshot()
    }

    // Transactions

    suspend fun transaction(id: String): Transaction? = db.transactions().get(id)?.toDomain()

    suspend fun addManualTransaction(accountId: String, date: LocalDate, amount: Long, description: String, categoryId: String?) {
        val base = Transaction(
            id = "manual:${UUID.randomUUID()}",
            accountId = accountId,
            date = date,
            amount = amount,
            description = description,
        )
        val txn = if (categoryId != null) base.copy(categoryId = categoryId, userCategorized = true) else base
        insertProcessed(listOf(txn))
        // Keep a manual account's balance in step with what the user records.
        db.accounts().get(accountId)?.let { account ->
            if (account.connectionId == null) {
                val domain = account.toDomain()
                val delta = if (domain.type.isLiability) -amount else amount
                db.accounts().upsert(account.copy(balance = account.balance + delta, lastUpdated = System.currentTimeMillis()))
            }
        }
    }

    /**
     * Saves a user's edit. When [rememberForMerchant] is set, a rule is created so future
     * transactions from the same merchant get the same category automatically.
     */
    suspend fun updateTransaction(updated: Transaction, rememberForMerchant: Boolean = false) {
        val existing = db.transactions().get(updated.id)?.toDomain() ?: return
        val categoryChanged = existing.categoryId != updated.categoryId
        val saved = if (categoryChanged) updated.copy(userCategorized = true) else updated
        db.transactions().upsert(saved.toEntity())
        if (rememberForMerchant && saved.categoryId != null) {
            addRule(saved.merchant, saved.categoryId!!)
            // Apply to other uncategorized-by-user transactions from this merchant right away.
            val key = MerchantNormalizer.key(saved.merchant)
            val others = db.transactions().since(LocalDate.now().minusYears(2).toEpochDay())
                .filter { !it.userCategorized && it.id != saved.id && MerchantNormalizer.key(it.merchant) == key }
            others.forEach { db.transactions().setAutoCategory(it.id, saved.categoryId!!) }
        }
    }

    suspend fun applyCategories(assignments: Map<String, String>) {
        assignments.forEach { (id, category) -> db.transactions().setAutoCategory(id, category) }
    }

    suspend fun deleteTransaction(id: String) = db.transactions().deleteIds(listOf(id))

    /** Categorizes and stores new transactions, linking transfers with recent history. */
    suspend fun insertProcessed(incoming: List<Transaction>, hints: Map<String, String> = emptyMap()) {
        if (incoming.isEmpty()) return
        val accounts = db.accounts().all().map { it.toDomain() }
        val recent = db.transactions().since(LocalDate.now().minusDays(60).toEpochDay()).map { it.toDomain() }
        val result = TransactionPipeline.process(incoming, recent, accounts, categorizer(), hints)
        db.withTransaction {
            db.transactions().upsertAll(result.transactions.map { it.toEntity() })
            result.existingUpdates.forEach { (id, category) -> db.transactions().setAutoCategory(id, category) }
        }
    }

    suspend fun importCsv(accountId: String, rows: List<ImportedRow>): Int {
        val existing = db.transactions().since(0).filter { it.accountId == accountId }.map { it.id }.toSet()
        val seen = mutableMapOf<String, Int>()
        val txns = rows.mapNotNull { row ->
            val base = "csv:$accountId:${row.date}:${row.amount}:${row.description.trim().lowercase().hashCode()}"
            val n = seen.merge(base, 1, Int::plus)!!
            val id = "$base:$n"
            if (id in existing) null else Transaction(id, accountId, row.date, row.amount, row.description.trim())
        }
        insertProcessed(txns)
        return txns.size
    }

    // Categories and rules

    suspend fun addRule(pattern: String, categoryId: String, renameTo: String? = null) {
        val cleaned = pattern.trim()
        if (cleaned.isEmpty()) return
        val existing = db.rules().all().firstOrNull { it.pattern.equals(cleaned, ignoreCase = true) }
        db.rules().upsert(RuleEntity(existing?.id ?: UUID.randomUUID().toString(), cleaned, categoryId, renameTo))
    }

    suspend fun deleteRule(rule: CategoryRule) = db.rules().delete(RuleEntity(rule.id, rule.pattern, rule.categoryId, rule.renameTo))

    suspend fun saveCategory(category: Category) = db.categories().upsert(category.toEntity())

    // Budgets

    suspend fun setBudget(categoryId: String, monthlyAmount: Long, rollover: Boolean) {
        val existing = db.budgets().all().firstOrNull { it.categoryId == categoryId }
        val start = existing?.startMonth ?: YearMonth.now().toString()
        db.budgets().upsert(BudgetTarget(categoryId, monthlyAmount, rollover, YearMonth.parse(start)).toEntity())
    }

    suspend fun setBudgets(amounts: Map<String, Long>) {
        val existing = db.budgets().all().associateBy { it.categoryId }
        db.budgets().upsertAll(
            amounts.map { (id, amount) ->
                val prior = existing[id]
                BudgetTarget(id, amount, prior?.rollover ?: false, prior?.let { YearMonth.parse(it.startMonth) } ?: YearMonth.now()).toEntity()
            },
        )
    }

    suspend fun removeBudget(categoryId: String) = db.budgets().delete(categoryId)

    // Goals

    suspend fun goal(id: String): Goal? = db.goals().get(id)?.toDomain()

    suspend fun saveGoal(goal: Goal) = db.goals().upsert(goal.toEntity())

    suspend fun deleteGoal(id: String) = db.goals().delete(id)

    // Net worth history

    suspend fun recordSnapshot() {
        val accounts = db.accounts().all().map { it.toDomain() }.filter { !it.isHidden }
        if (accounts.isEmpty()) return
        db.snapshots().upsert(NetWorthCalculator.snapshot(accounts, LocalDate.now()).toEntity())
    }

    // Demo data and reset

    suspend fun loadDemo() {
        val demo = DemoData.generate(LocalDate.now())
        clearFinancialData()
        db.withTransaction {
            db.accounts().upsertAll(demo.accounts.map { it.copy(lastUpdated = Instant.now()).toEntity() })
            db.transactions().upsertAll(demo.transactions.map { it.toEntity() })
            db.budgets().upsertAll(demo.budgets.map { it.toEntity() })
            demo.goals.forEach { db.goals().upsert(it.toEntity()) }
            db.snapshots().upsertAll(demo.snapshots.map { it.toEntity() })
        }
        settingsRepository.setDemoMode(true)
    }

    suspend fun clearFinancialData() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        ensureDefaultCategories()
        settingsRepository.setDemoMode(false)
    }
}
