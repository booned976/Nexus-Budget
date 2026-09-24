package com.nexusbudget.core.engine

import com.nexusbudget.core.model.BudgetBucket
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.CategoryKind
import com.nexusbudget.core.model.Money

data class SuggestedBudget(val categoryId: String, val amount: Long, val averageSpent: Long, val reason: String)

data class BudgetPlan(
    val income: Long,
    val suggestions: List<SuggestedBudget>,
    val needs: Long,
    val wants: Long,
    /** Income left for savings and extra debt payments. */
    val savings: Long,
) {
    val total: Long get() = suggestions.sumOf { it.amount }
}

/**
 * Builds a starting budget from real spending history, then trims "wants" so that at least
 * 20% of income is left for savings and debt payoff (the 50/30/20 guideline).
 */
object BudgetPlanner {

    fun suggest(stats: CashFlowStats, categories: CategoryIndex, recurring: List<RecurringSeries> = emptyList()): BudgetPlan {
        val income = stats.avgMonthlyIncome
        val fixedByCategory = recurring
            .filter { it.isBill && it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, list) -> list.sumOf { -it.monthlyAmount } }

        val base = stats.avgSpendingByCategory
            .filter { (id, amount) -> amount > 0 && id != Categories.UNCATEGORIZED && categories[id].kind == CategoryKind.EXPENSE }
            .map { (id, avg) ->
                val fixed = fixedByCategory[id]
                val amount = if (fixed != null && fixed >= avg * 0.8) Money.roundUp(fixed, 1_00) else Money.roundUp(avg, 10_00)
                SuggestedBudget(id, amount, avg, if (fixed != null) "Based on your recurring bills" else "Your 3-month average")
            }

        val needs = base.filter { categories[it.categoryId].bucket != BudgetBucket.WANTS }.sumOf { it.amount }
        var wants = base.filter { categories[it.categoryId].bucket == BudgetBucket.WANTS }.sumOf { it.amount }
        var suggestions = base

        if (income > 0) {
            val wantsCeiling = (income * (1 - BudgetBucket.SAVINGS.targetShare)).toLong() - needs
            if (wants > wantsCeiling && wants > 0) {
                // Trim wants proportionally, but never below half of what's usually spent.
                val ratio = (wantsCeiling.toDouble() / wants).coerceIn(0.5, 1.0)
                suggestions = base.map { s ->
                    if (categories[s.categoryId].bucket == BudgetBucket.WANTS) {
                        s.copy(amount = Money.roundUp((s.amount * ratio).toLong(), 5_00), reason = "Trimmed to leave room for savings")
                    } else {
                        s
                    }
                }
                wants = suggestions.filter { categories[it.categoryId].bucket == BudgetBucket.WANTS }.sumOf { it.amount }
            }
        }
        return BudgetPlan(
            income = income,
            suggestions = suggestions.sortedByDescending { it.amount },
            needs = needs,
            wants = wants,
            savings = income - needs - wants,
        )
    }
}
