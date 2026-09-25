package com.nexusbudget.assistant

import com.nexusbudget.core.engine.BudgetEngine
import com.nexusbudget.core.engine.CashFlowAnalyzer
import com.nexusbudget.core.engine.CashFlowForecaster
import com.nexusbudget.core.engine.DebtPayoffEngine
import com.nexusbudget.core.engine.FinancialPicture
import com.nexusbudget.core.engine.GoalEngine
import com.nexusbudget.core.engine.PayoffStrategy
import com.nexusbudget.core.model.BudgetTarget
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.CategoryKind
import com.nexusbudget.core.model.Goal
import com.nexusbudget.core.model.GoalType
import com.nexusbudget.core.model.Money
import com.nexusbudget.core.model.Transaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID

/** Everything the assistant's tools can read. Built fresh by the app for each question. */
data class AssistantContext(
    val picture: FinancialPicture,
    val transactions: List<Transaction>,
    val categories: CategoryIndex,
    val budgets: List<BudgetTarget>,
    val shareTransactionDetails: Boolean,
) {
    val today: LocalDate get() = picture.today
}

data class ToolSpec(
    val name: String,
    val description: String,
    /** Human-readable status shown in the chat while the tool runs. */
    val statusLabel: String,
    val properties: Map<String, Map<String, Any>> = emptyMap(),
    val required: List<String> = emptyList(),
    val needsTransactionDetails: Boolean = false,
)

data class ToolOutcome(val content: String, val isError: Boolean = false, val proposal: Proposal? = null)

/**
 * Tools that let Claude look up the user's finances on demand. They run locally on the phone
 * and return only the numbers the model asked for; account numbers are never included.
 */
object FinanceTools {

    private val dateSchema = mapOf("type" to "string", "description" to "Date in YYYY-MM-DD format")

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            "get_financial_overview",
            "Snapshot of the user's finances: net worth, cash, debt, average monthly income and spending, savings rate, " +
                "emergency fund coverage, safe-to-spend amount, and this month's budget totals. Call this first for most questions.",
            "Reviewing your finances",
        ),
        ToolSpec(
            "list_accounts",
            "All accounts with type, institution, balance, and for debts the APR, minimum payment, due day and credit limit. " +
                "Debt balances are positive amounts owed.",
            "Checking your accounts",
        ),
        ToolSpec("list_categories", "All spending and income categories with their ids, groups and 50/30/20 bucket.", "Loading categories"),
        ToolSpec(
            "get_spending_by_category",
            "Spending per category for one month, compared with the budget and the user's 3-month average.",
            "Adding up spending",
            mapOf("month" to mapOf("type" to "string", "description" to "Month as YYYY-MM. Defaults to the current month.")),
        ),
        ToolSpec(
            "get_monthly_trend",
            "Income, spending and net for each recent month, oldest first.",
            "Looking at monthly trends",
            mapOf("months" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 12, "description" to "How many months, including the current one. Default 6.")),
        ),
        ToolSpec(
            "search_transactions",
            "Find individual transactions. All filters are optional. Amounts are negative for money spent and positive for money received.",
            "Searching transactions",
            mapOf(
                "start_date" to dateSchema,
                "end_date" to dateSchema,
                "category_id" to mapOf("type" to "string", "description" to "Category id from list_categories"),
                "merchant" to mapOf("type" to "string", "description" to "Case-insensitive text to match in the merchant or description"),
                "min_amount" to mapOf("type" to "number", "description" to "Minimum absolute amount in dollars"),
                "max_amount" to mapOf("type" to "number", "description" to "Maximum absolute amount in dollars"),
                "limit" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 100, "description" to "Maximum results, newest first. Default 25."),
            ),
            needsTransactionDetails = true,
        ),
        ToolSpec("get_budget_status", "This month's budget for each category: budgeted, spent, remaining, pace and status.", "Checking your budget"),
        ToolSpec(
            "get_recurring_charges",
            "Detected recurring bills, subscriptions and paychecks with amount, frequency and next expected date.",
            "Finding bills and subscriptions",
        ),
        ToolSpec("get_goals", "Savings goals with progress, monthly contribution, target date and projected completion.", "Checking your goals"),
        ToolSpec(
            "simulate_debt_payoff",
            "Simulate paying off all debts with a strategy and an extra monthly payment. Returns the debt-free date, total interest, " +
                "the payoff order with dates, what to pay each debt this month, and savings versus paying only minimums.",
            "Running a payoff plan",
            mapOf(
                "strategy" to mapOf("type" to "string", "enum" to listOf("avalanche", "snowball"), "description" to "avalanche = highest APR first; snowball = smallest balance first"),
                "extra_monthly" to mapOf("type" to "number", "minimum" to 0, "description" to "Extra dollars per month on top of all minimum payments"),
                "lump_sum" to mapOf("type" to "number", "minimum" to 0, "description" to "Optional one-time payment in dollars applied now"),
            ),
            listOf("strategy", "extra_monthly"),
        ),
        ToolSpec(
            "debt_extra_needed",
            "How much extra per month (on top of minimums) is needed to be debt-free within a number of months.",
            "Calculating what it takes",
            mapOf(
                "target_months" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 600),
                "strategy" to mapOf("type" to "string", "enum" to listOf("avalanche", "snowball")),
            ),
            listOf("target_months"),
        ),
        ToolSpec(
            "project_goal",
            "What-if calculator for a savings goal: when it would be reached at a monthly amount, or what's needed for a target date.",
            "Projecting a goal",
            mapOf(
                "target_amount" to mapOf("type" to "number", "minimum" to 0),
                "monthly_contribution" to mapOf("type" to "number", "minimum" to 0),
                "already_saved" to mapOf("type" to "number", "minimum" to 0),
                "target_date" to dateSchema,
            ),
            listOf("target_amount", "monthly_contribution"),
        ),
        ToolSpec(
            "get_cash_flow_forecast",
            "Projected checking balance day by day using upcoming bills, paychecks and typical everyday spending. Returns the lowest point and upcoming events.",
            "Forecasting cash flow",
            mapOf("days" to mapOf("type" to "integer", "minimum" to 7, "maximum" to 60, "description" to "Days ahead. Default 30.")),
        ),
        ToolSpec(
            "propose_budget_changes",
            "Suggest new monthly budget amounts. The user sees the proposal with an Apply button; nothing changes unless they approve. " +
                "Use category ids from list_categories.",
            "Preparing a budget proposal",
            mapOf(
                "changes" to mapOf(
                    "type" to "array",
                    "minItems" to 1,
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "category_id" to mapOf("type" to "string"),
                            "monthly_amount" to mapOf("type" to "number", "minimum" to 0),
                        ),
                        "required" to listOf("category_id", "monthly_amount"),
                        "additionalProperties" to false,
                    ),
                ),
                "rationale" to mapOf("type" to "string", "description" to "One or two sentences explaining the change"),
            ),
            listOf("changes", "rationale"),
        ),
        ToolSpec(
            "propose_goal",
            "Suggest a new savings goal. The user sees it with an Apply button; nothing is created unless they approve.",
            "Preparing a goal",
            mapOf(
                "name" to mapOf("type" to "string"),
                "type" to mapOf("type" to "string", "enum" to GoalType.entries.map { it.name.lowercase(Locale.US) }),
                "target_amount" to mapOf("type" to "number", "minimum" to 1),
                "monthly_contribution" to mapOf("type" to "number", "minimum" to 0),
                "target_date" to dateSchema,
                "rationale" to mapOf("type" to "string"),
            ),
            listOf("name", "type", "target_amount", "monthly_contribution", "rationale"),
        ),
    )

    private val byName = specs.associateBy { it.name }

    fun spec(name: String): ToolSpec? = byName[name]

    private val json = Json { prettyPrint = false }

    /** Runs a tool. Never throws: problems are returned as error results the model can react to. */
    fun execute(name: String, rawInput: String, context: AssistantContext): ToolOutcome {
        val spec = byName[name] ?: return ToolOutcome("Unknown tool: $name", isError = true)
        if (spec.needsTransactionDetails && !context.shareTransactionDetails) {
            return ToolOutcome(
                "The user turned off sharing individual transactions with the assistant. Use category totals instead " +
                    "(get_spending_by_category, get_monthly_trend).",
                isError = true,
            )
        }
        val input = try {
            json.parseToJsonElement(rawInput.ifBlank { "{}" }) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return ToolOutcome("Invalid JSON input. Send a JSON object matching the tool's schema.", isError = true)

        spec.required.firstOrNull { input[it] == null || input[it] is JsonNull }?.let {
            return ToolOutcome("Missing required field '$it'.", isError = true)
        }
        return try {
            when (name) {
                "get_financial_overview" -> ok(overview(context))
                "list_accounts" -> ok(accounts(context))
                "list_categories" -> ok(categories(context))
                "get_spending_by_category" -> ok(spendingByCategory(input, context))
                "get_monthly_trend" -> ok(trend(input, context))
                "search_transactions" -> ok(searchTransactions(input, context))
                "get_budget_status" -> ok(budgetStatus(context))
                "get_recurring_charges" -> ok(recurring(context))
                "get_goals" -> ok(goals(context))
                "simulate_debt_payoff" -> ok(simulateDebt(input, context))
                "debt_extra_needed" -> ok(debtExtraNeeded(input, context))
                "project_goal" -> ok(projectGoal(input, context))
                "get_cash_flow_forecast" -> ok(forecast(input, context))
                "propose_budget_changes" -> proposeBudget(input, context)
                "propose_goal" -> proposeGoal(input)
                else -> ToolOutcome("Unknown tool: $name", isError = true)
            }
        } catch (e: InvalidToolInput) {
            ToolOutcome(e.message ?: "Invalid input.", isError = true)
        }
    }

    private class InvalidToolInput(message: String) : Exception(message)

    private fun ok(element: JsonElement) = ToolOutcome(element.toString())

    private fun dollars(cents: Long): JsonPrimitive = JsonPrimitive(BigDecimal.valueOf(cents, 2))

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Double? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.doubleOrNull ?: throw InvalidToolInput("'$key' must be a number.")
    }

    private fun JsonObject.integer(key: String): Int? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.intOrNull ?: (value as? JsonPrimitive)?.doubleOrNull?.toInt()
            ?: throw InvalidToolInput("'$key' must be an integer.")
    }

    private fun JsonObject.date(key: String): LocalDate? = string(key)?.let {
        try {
            LocalDate.parse(it)
        } catch (e: DateTimeParseException) {
            throw InvalidToolInput("'$key' must be a date in YYYY-MM-DD format.")
        }
    }

    private fun cents(dollars: Double): Long = Money.fromDollars(dollars)

    private fun overview(ctx: AssistantContext): JsonObject {
        val p = ctx.picture
        return buildJsonObject {
            put("today", ctx.today.toString())
            put("net_worth", dollars(p.netWorth.total))
            put("assets", dollars(p.netWorth.assets))
            put("liabilities", dollars(p.netWorth.liabilities))
            p.netWorthChange30d?.let { put("net_worth_change_30_days", dollars(it)) }
            put("cash_in_checking_and_cash", dollars(p.safeToSpend.spendableCash))
            put("savings_balance", dollars(p.accounts.filter { it.type == com.nexusbudget.core.model.AccountType.SAVINGS }.sumOf { it.balance }))
            put("total_debt", dollars(p.totalDebt))
            putJsonObject("monthly_averages_last_3_months") {
                put("income", dollars(p.stats.avgMonthlyIncome))
                put("spending", dollars(p.stats.avgMonthlySpending))
                put("essential_spending", dollars(p.stats.avgEssentialSpending))
                put("net", dollars(p.stats.avgMonthlyNet))
                p.stats.savingsRate?.let { put("savings_rate_percent", Math.round(it * 1000) / 10.0) }
                put("months_of_history", p.stats.months.size)
            }
            p.emergencyFundMonths?.let { put("emergency_fund_months_of_essentials", Math.round(it * 10) / 10.0) }
            putJsonObject("safe_to_spend") {
                put("amount", dollars(p.safeToSpend.amount))
                put("per_day", dollars(p.safeToSpend.perDay))
                put("until", p.safeToSpend.until.toString())
                put("until_next_paycheck", p.safeToSpend.untilPayday)
                put("bills_reserved", dollars(p.safeToSpend.billsTotal))
                put("goal_savings_reserved", dollars(p.safeToSpend.goalSetAside))
            }
            putJsonObject("this_month") {
                put("month", p.budget.month.toString())
                put("income_so_far", dollars(p.budget.income))
                put("expected_income", dollars(p.budget.expectedIncome))
                put("spent_so_far", dollars(p.budget.totalSpent))
                put("total_budgeted", dollars(p.budget.totalBudgeted))
                put("left_to_budget", dollars(p.budget.leftToBudget))
                put("days_left", p.budget.daysLeft)
                put("categories_over_budget", p.budget.overspentCategories.size)
            }
            putJsonArray("top_recommendations") {
                p.recommendations.take(5).forEach { r ->
                    addJsonObject {
                        put("priority", r.severity.name.lowercase(Locale.US))
                        put("title", r.title)
                        put("detail", r.message)
                    }
                }
            }
        }
    }

    private fun accounts(ctx: AssistantContext): JsonArray = buildJsonArray {
        ctx.picture.accounts.sortedWith(compareBy({ it.type.group.ordinal }, { -it.balance })).forEach { a ->
            addJsonObject {
                put("name", a.name)
                put("type", a.type.label)
                put("group", a.type.group.label)
                a.institution?.let { put("institution", it) }
                put(if (a.type.isLiability) "amount_owed" else "balance", dollars(a.balance))
                a.available?.let { put("available", dollars(it)) }
                a.apr?.let { put("apr_percent", it) }
                a.minimumPayment?.let { put("minimum_payment", dollars(it)) }
                a.paymentDueDay?.let { put("payment_due_day_of_month", it) }
                a.creditLimit?.let { put("credit_limit", dollars(it)) }
                a.utilization?.let { put("utilization_percent", Math.round(it * 1000) / 10.0) }
                put("tracked", if (a.isManual) "manually" else "synced from bank")
            }
        }
    }

    private fun categories(ctx: AssistantContext): JsonArray = buildJsonArray {
        ctx.categories.all.sortedWith(compareBy({ it.kind.ordinal }, { it.group }, { it.name })).forEach { c ->
            addJsonObject {
                put("id", c.id)
                put("name", c.name)
                put("group", c.group)
                put("kind", c.kind.name.lowercase(Locale.US))
                if (c.kind == CategoryKind.EXPENSE) put("bucket", c.bucket.label)
            }
        }
    }

    private fun spendingByCategory(input: JsonObject, ctx: AssistantContext): JsonObject {
        val month = input.string("month")?.let {
            try {
                YearMonth.parse(it)
            } catch (e: DateTimeParseException) {
                throw InvalidToolInput("'month' must be YYYY-MM.")
            }
        } ?: YearMonth.from(ctx.today)
        val spent = BudgetEngine.spendingByCategory(month, ctx.transactions, ctx.categories)
        val budgets = ctx.budgets.associateBy { it.categoryId }
        val avg = ctx.picture.stats.avgSpendingByCategory
        val ids = (spent.keys + budgets.keys).filter { ctx.categories[it].kind == CategoryKind.EXPENSE }
        return buildJsonObject {
            put("month", month.toString())
            put("total_spent", dollars(spent.values.sum()))
            putJsonArray("categories") {
                ids.sortedByDescending { spent[it] ?: 0 }.forEach { id ->
                    val category = ctx.categories[id]
                    addJsonObject {
                        put("category_id", id)
                        put("name", category.name)
                        put("spent", dollars(spent[id] ?: 0))
                        budgets[id]?.let { put("budget", dollars(it.monthlyAmount)) }
                        avg[id]?.let { put("three_month_average", dollars(it)) }
                    }
                }
            }
        }
    }

    private fun trend(input: JsonObject, ctx: AssistantContext): JsonArray {
        val months = (input.integer("months") ?: 6).coerceIn(1, 12)
        return buildJsonArray {
            CashFlowAnalyzer.trend(ctx.transactions, ctx.categories, ctx.today, months).forEach { m ->
                addJsonObject {
                    put("month", m.month.toString())
                    put("income", dollars(m.income))
                    put("spending", dollars(m.spending))
                    put("net", dollars(m.net))
                    if (m.month == YearMonth.from(ctx.today)) put("partial_month", true)
                }
            }
        }
    }

    private fun searchTransactions(input: JsonObject, ctx: AssistantContext): JsonObject {
        val start = input.date("start_date")
        val end = input.date("end_date")
        val categoryId = input.string("category_id")
        val merchant = input.string("merchant")?.lowercase(Locale.US)
        val min = input.number("min_amount")?.let(::cents)
        val max = input.number("max_amount")?.let(::cents)
        val limit = (input.integer("limit") ?: 25).coerceIn(1, 100)
        val accounts = ctx.picture.accounts.associateBy { it.id }
        val matches = ctx.transactions.asSequence()
            .filter { start == null || !it.date.isBefore(start) }
            .filter { end == null || !it.date.isAfter(end) }
            .filter { categoryId == null || ctx.categories[it.categoryId].id == categoryId }
            .filter { merchant == null || it.merchant.lowercase(Locale.US).contains(merchant) || it.description.lowercase(Locale.US).contains(merchant) }
            .filter { min == null || kotlin.math.abs(it.amount) >= min }
            .filter { max == null || kotlin.math.abs(it.amount) <= max }
            .sortedByDescending { it.date }
            .toList()
        return buildJsonObject {
            put("total_matches", matches.size)
            put("sum_of_matches", dollars(matches.sumOf { it.amount }))
            putJsonArray("transactions") {
                matches.take(limit).forEach { t ->
                    addJsonObject {
                        put("date", t.date.toString())
                        put("merchant", t.merchant)
                        put("amount", dollars(t.amount))
                        put("category", ctx.categories[t.categoryId].name)
                        accounts[t.accountId]?.let { put("account", it.name) }
                        if (t.pending) put("pending", true)
                        if (t.excluded) put("excluded_from_budget", true)
                    }
                }
            }
        }
    }

    private fun budgetStatus(ctx: AssistantContext): JsonObject {
        val b = ctx.picture.budget
        return buildJsonObject {
            put("month", b.month.toString())
            put("percent_of_month_elapsed", Math.round(b.monthProgress * 100))
            put("total_budgeted", dollars(b.totalBudgeted))
            put("spent_in_budgeted_categories", dollars(b.budgetedSpent))
            put("expected_income", dollars(b.expectedIncome))
            put("left_to_budget", dollars(b.leftToBudget))
            putJsonArray("categories") {
                b.categories.forEach { s ->
                    addJsonObject {
                        put("category_id", s.category.id)
                        put("name", s.category.name)
                        put("budgeted", dollars(s.budgeted))
                        if (s.rolloverIn != 0L) put("rollover_from_previous_months", dollars(s.rolloverIn))
                        put("spent", dollars(s.spent))
                        put("remaining", dollars(s.available))
                        put("projected_month_end", dollars(s.projected))
                        put("status", s.health.name.lowercase(Locale.US))
                    }
                }
            }
            putJsonArray("unbudgeted_spending") {
                b.unbudgeted.take(10).forEach { s ->
                    addJsonObject {
                        put("category_id", s.category.id)
                        put("name", s.category.name)
                        put("spent", dollars(s.spent))
                    }
                }
            }
        }
    }

    private fun recurring(ctx: AssistantContext): JsonArray = buildJsonArray {
        ctx.picture.recurring.forEach { r ->
            addJsonObject {
                put("name", if (ctx.shareTransactionDetails) r.merchant else ctx.categories[r.categoryId].name)
                put("kind", when {
                    r.isIncome -> "income"
                    r.isSubscription -> "subscription"
                    r.kind == CategoryKind.TRANSFER -> "transfer"
                    else -> "bill"
                })
                put("amount", dollars(r.amount))
                put("frequency", r.frequency.label)
                put("monthly_equivalent", dollars(r.monthlyAmount))
                put("next_date", r.nextDate.toString())
                put("category", ctx.categories[r.categoryId].name)
                if (r.amountVaries) put("amount_varies", true)
                r.priceChange?.let { put("latest_price_change", dollars(it)) }
            }
        }
    }

    private fun goals(ctx: AssistantContext): JsonArray = buildJsonArray {
        ctx.picture.goals.forEach { g ->
            addJsonObject {
                put("name", g.goal.name)
                put("type", g.goal.type.label)
                put("target", dollars(g.goal.target))
                put("saved", dollars(g.saved))
                put("progress_percent", Math.round(g.progress * 100))
                put("monthly_contribution", dollars(g.goal.monthlyContribution))
                g.goal.targetDate?.let { put("target_date", it.toString()) }
                g.projectedDate?.let { put("projected_completion", it.toString()) }
                g.requiredMonthly?.let { put("monthly_needed_for_target_date", dollars(it)) }
                put("status", g.status.label)
            }
        }
    }

    private fun strategyOf(value: String?): PayoffStrategy = when (value?.lowercase(Locale.US)) {
        null, "avalanche" -> PayoffStrategy.AVALANCHE
        "snowball" -> PayoffStrategy.SNOWBALL
        else -> throw InvalidToolInput("'strategy' must be 'avalanche' or 'snowball'.")
    }

    private fun simulateDebt(input: JsonObject, ctx: AssistantContext): JsonObject {
        val debts = ctx.picture.debts
        if (debts.isEmpty()) return buildJsonObject { put("message", "The user has no debts with a balance.") }
        val strategy = strategyOf(input.string("strategy"))
        val extra = cents(input.number("extra_monthly") ?: 0.0).coerceAtLeast(0)
        val lump = cents(input.number("lump_sum") ?: 0.0).coerceAtLeast(0)
        val start = YearMonth.from(ctx.today)
        val plan = DebtPayoffEngine.simulate(debts, strategy, extra, start, lumpSum = lump)
        val baseline = DebtPayoffEngine.simulate(debts, PayoffStrategy.MINIMUM_ONLY, 0, start)
        return buildJsonObject {
            put("strategy", strategy.label)
            put("total_monthly_payment", dollars(plan.monthlyPayment))
            put("feasible", plan.feasible)
            plan.debtFreeMonth?.let { put("debt_free_month", it.toString()) }
            put("months_to_debt_free", plan.months)
            put("total_interest", dollars(plan.totalInterest))
            if (baseline.feasible && plan.feasible) {
                put("interest_saved_vs_minimums_only", dollars(baseline.totalInterest - plan.totalInterest))
                put("months_saved_vs_minimums_only", baseline.months - plan.months)
            } else if (!baseline.feasible) {
                put("minimums_only_note", "Paying only minimums never pays off at least one debt.")
            }
            putJsonArray("payoff_order") {
                plan.debts.forEach { d ->
                    addJsonObject {
                        put("order", d.order)
                        put("name", d.debt.name)
                        put("balance", dollars(d.debt.balance))
                        put("apr_percent", d.debt.apr)
                        put("minimum_payment", dollars(d.debt.minimumPayment))
                        if (d.debt.minimumEstimated) put("minimum_is_estimated", true)
                        d.payoffMonth?.let { put("paid_off", it.toString()) }
                        put("interest_paid", dollars(d.interestPaid))
                    }
                }
            }
            putJsonArray("pay_this_month") {
                plan.firstMonthPayments.forEach { p ->
                    addJsonObject {
                        put("debt", p.debtName)
                        put("amount", dollars(p.amount))
                        put("includes_extra", p.isExtra)
                    }
                }
            }
            put("assumptions", "Interest compounds monthly at the listed APR; minimum payments stay fixed; paid-off minimums roll into the next debt.")
        }
    }

    private fun debtExtraNeeded(input: JsonObject, ctx: AssistantContext): JsonObject {
        val months = input.integer("target_months") ?: throw InvalidToolInput("'target_months' is required.")
        val strategy = strategyOf(input.string("strategy"))
        val extra = DebtPayoffEngine.extraNeededFor(ctx.picture.debts, months, strategy, YearMonth.from(ctx.today))
        return buildJsonObject {
            put("target_months", months)
            put("strategy", strategy.label)
            if (extra == null) {
                put("result", "Not reachable within that time, or there are no debts.")
            } else {
                put("extra_monthly_needed", dollars(extra))
                put("total_monthly_payment", dollars(ctx.picture.debts.sumOf { it.minimumPayment } + extra))
            }
        }
    }

    private fun projectGoal(input: JsonObject, ctx: AssistantContext): JsonObject {
        val goal = Goal(
            id = "what-if",
            name = "What-if",
            type = GoalType.SAVINGS,
            target = cents(input.number("target_amount") ?: 0.0),
            saved = cents(input.number("already_saved") ?: 0.0),
            monthlyContribution = cents(input.number("monthly_contribution") ?: 0.0),
            targetDate = input.date("target_date"),
        )
        val projection = GoalEngine.project(goal, emptyList(), ctx.today)
        return buildJsonObject {
            put("remaining", dollars(projection.remaining))
            projection.projectedDate?.let { put("reached_on", it.toString()) }
            projection.requiredMonthly?.let { put("monthly_needed_for_target_date", dollars(it)) }
            put("status", projection.status.label)
        }
    }

    private fun forecast(input: JsonObject, ctx: AssistantContext): JsonObject {
        val days = (input.integer("days") ?: 30).coerceIn(7, 60)
        val p = ctx.picture
        val result = if (days == 30) {
            p.forecast
        } else {
            CashFlowForecaster.forecast(p.safeToSpend.spendableCash, p.recurring, p.forecast.dailySpending, ctx.today, days,
                p.accounts.filter { it.type.isSpendable || it.type.isRevolving }.map { it.id }.toSet())
        }
        return buildJsonObject {
            put("starting_cash", dollars(p.safeToSpend.spendableCash))
            put("typical_daily_spending", dollars(result.dailySpending))
            result.lowest?.let {
                put("lowest_balance", dollars(it.balance))
                put("lowest_on", it.date.toString())
            }
            put("ending_balance", dollars(result.ending))
            putJsonArray("upcoming_events") {
                result.events.take(40).forEach { e ->
                    addJsonObject {
                        put("date", e.date.toString())
                        put("name", if (ctx.shareTransactionDetails) e.name else if (e.amount > 0) "Income" else "Bill")
                        put("amount", dollars(e.amount))
                    }
                }
            }
        }
    }

    private fun proposeBudget(input: JsonObject, ctx: AssistantContext): ToolOutcome {
        val items = try {
            input["changes"]!!.jsonArray
        } catch (e: IllegalArgumentException) {
            throw InvalidToolInput("'changes' must be an array.")
        }
        val existing = ctx.budgets.associateBy { it.categoryId }
        val changes = items.map { item ->
            val obj = item as? JsonObject ?: throw InvalidToolInput("Each change must be an object.")
            val id = obj.string("category_id") ?: throw InvalidToolInput("Each change needs a category_id.")
            val category = ctx.categories.find(id) ?: throw InvalidToolInput("Unknown category_id '$id'. Call list_categories for valid ids.")
            if (category.kind != CategoryKind.EXPENSE) throw InvalidToolInput("'$id' is not a spending category.")
            val amount = obj.number("monthly_amount") ?: throw InvalidToolInput("Each change needs a monthly_amount.")
            if (amount < 0) throw InvalidToolInput("monthly_amount can't be negative.")
            BudgetChange(id, category.name, cents(amount), existing[id]?.monthlyAmount)
        }
        val proposal = Proposal.BudgetChanges(UUID.randomUUID().toString(), changes, input.string("rationale").orEmpty())
        return ToolOutcome(
            "The budget proposal is now shown to the user with an Apply button. Don't repeat every number; briefly explain the reasoning.",
            proposal = proposal,
        )
    }

    private fun proposeGoal(input: JsonObject): ToolOutcome {
        val typeName = input.string("type")?.uppercase(Locale.US)
        val type = GoalType.entries.firstOrNull { it.name == typeName } ?: throw InvalidToolInput("Unknown goal type.")
        val target = input.number("target_amount") ?: throw InvalidToolInput("'target_amount' is required.")
        if (target <= 0) throw InvalidToolInput("'target_amount' must be positive.")
        val proposal = Proposal.NewGoal(
            id = UUID.randomUUID().toString(),
            name = input.string("name")?.take(60) ?: throw InvalidToolInput("'name' is required."),
            type = type,
            target = cents(target),
            monthlyContribution = cents(input.number("monthly_contribution") ?: 0.0).coerceAtLeast(0),
            targetDate = input.date("target_date"),
            rationale = input.string("rationale").orEmpty(),
        )
        return ToolOutcome("The goal is now shown to the user with an Apply button. Briefly explain why it helps.", proposal = proposal)
    }
}
