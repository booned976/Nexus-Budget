package com.nexusbudget.core.engine

import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.Transaction

/**
 * Categorizes freshly imported transactions and links transfers between the user's own accounts.
 * Transactions the user already categorized are never changed.
 */
object TransactionPipeline {

    data class Result(
        /** The incoming transactions with categories applied. */
        val transactions: List<Transaction>,
        /** Category changes for already-stored transactions that turned out to be one side of a transfer. */
        val existingUpdates: Map<String, String>,
    )

    private val transferish = setOf(
        Categories.UNCATEGORIZED, Categories.OTHER_INCOME, Categories.TRANSFER,
        Categories.CREDIT_CARD_PAYMENT, Categories.LOAN_PAYMENT, Categories.SAVINGS_TRANSFER,
    )

    /**
     * @param incoming new or updated transactions (their categories may be empty)
     * @param recent recent stored transactions, used to find the other side of a transfer
     * @param hints optional category suggestions from the data provider, keyed by transaction id
     */
    fun process(
        incoming: List<Transaction>,
        recent: List<Transaction>,
        accounts: List<Account>,
        categorizer: Categorizer,
        hints: Map<String, String> = emptyMap(),
    ): Result {
        val categorized = incoming.map { txn ->
            if (txn.userCategorized) {
                txn
            } else {
                val renamed = categorizer.rename(txn)
                val base = if (renamed != null) txn.copy(merchant = renamed) else txn
                base.copy(categoryId = categorizer.categorize(base, hints[txn.id]))
            }
        }

        val accountTypes = accounts.associate { it.id to it.type }
        val incomingIds = categorized.map { it.id }.toSet()
        val pool = categorized + recent.filter { it.id !in incomingIds }
        val updates = mutableMapOf<String, String>()
        for ((out, inflow) in Categorizer.findTransferPairs(pool)) {
            if (out.id !in incomingIds && inflow.id !in incomingIds) continue
            val outCategory = out.categoryId ?: Categories.UNCATEGORIZED
            val inCategory = inflow.categoryId ?: Categories.UNCATEGORIZED
            if (outCategory !in transferish || inCategory !in transferish) continue
            val destination = accountTypes[inflow.accountId] ?: continue
            val (outCat, inCat) = Categorizer.transferCategories(destination)
            updates[out.id] = outCat
            updates[inflow.id] = inCat
        }
        val result = categorized.map { txn -> updates[txn.id]?.let { txn.copy(categoryId = it) } ?: txn }
        val existing = updates.filterKeys { it !in incomingIds }
        return Result(result, existing)
    }
}
