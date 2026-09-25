package com.nexusbudget.core.engine

import com.nexusbudget.core.model.BudgetTarget
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.Category
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.CategoryKind
import com.nexusbudget.core.model.Transaction
import java.time.LocalDate
import java.time.YearMonth

enum class BudgetHealth { NO_BUDGET, ON_TRACK, WATCH, OVER }

data class CategoryBudgetStatus(
    val category: Category,
    val budgeted: Long,
    val rolloverIn: Long,
    val spent: Long,
    val health: BudgetHealth,
    /** Straight-line estimate of month-end spending at the current pace. */
    val projected: Long,
) {
    /** What's left to spend this month, including rollover. Negative when overspent. */
    val available: Long get() = budgeted + rolloverIn - spent
    val limit: Long get() = budgeted + rolloverIn
    val progress: Float get() = if (limit > 0) (spent.toFloat() / limit).coerceAtLeast(0f) else if (spent > 0) 1f else 0f
}

data class MonthBudgetSummary(
    val month: YearMonth,
    val income: Long,
    val expectedIncome: Long,
    val totalBudgeted: Long,
    val totalSpent: Long,
    /** Spending in budgeted categories only. */
    val budgetedSpent: Long,
    val categories: List<CategoryBudgetStatus>,
    val unbudgeted: List<CategoryBudgetStatus>,
    /** Fraction of the month that has elapsed, 0.0-1.0. */
    val monthProgress: Float,
    val daysLeft: Int,
) {
    /** Income not yet assigned to a budget ("give every dollar a job"). */
    val leftToBudget: Long get() = expectedIncome - totalBudgeted
    val remaining: Long get() = categories.sumOf { it.available.coerceAtLeast(0) }
    val overspentCategories: List<CategoryBudgetStatus> get() = categories.filter { it.health == BudgetHealth.OVER }
    val hasBudgets: Boolean get() = categories.isNotEmpty()
}

object BudgetEngine {

    /** Categories usually paid as one fixed bill a month, where spending "pace" means nothing. */
    val fixedBillCategories: Set<String> = setOf(
        Categories.RENT, Categories.UTILITIES, Categories.INTERNET_PHONE, Categories.INSURANCE,
        Categories.LOAN_PAYMENT, Categories.SUBSCRIPTIONS, Categories.FITNESS, Categories.TAXES,
        Categories.EDUCATION, Categories.KIDS,
    )

    /**
     * @param fixedCategoryIds categories paid as fixed bills; they're only flagged once actually over budget
     */
    fun summarize(
        month: YearMonth,
        today: LocalDate,
        transactions: List<Transaction>,
        categories: CategoryIndex,
        targets: List<BudgetTarget>,
        expectedIncome: Long,
        fixedCategoryIds: Set<String> = fixedBillCategories,
    ): MonthBudgetSummary {
        val spentByCategory = spendingByCategory(month, transactions, categories)
        val income = transactions.filter { YearMonth.from(it.date) == month }.sumOf { CashFlowAnalyzer.incomeOf(it, categories) }

        val current = YearMonth.from(today)
        val (monthProgress, daysLeft) = when {
            month.isBefore(current) -> 1f to 0
            month.isAfter(current) -> 0f to month.lengthOfMonth()
            else -> today.dayOfMonth.toFloat() / month.lengthOfMonth() to (month.lengthOfMonth() - today.dayOfMonth)
        }

        val targetsById = targets.filter { !month.isBefore(it.startMonth) }.associateBy { it.categoryId }
        val budgeted = targetsById.values.mapNotNull { target ->
            val category = categories.find(target.categoryId) ?: return@mapNotNull null
            if (category.kind != CategoryKind.EXPENSE) return@mapNotNull null
            val spent = spentByCategory[category.id] ?: 0
            val rollover = if (target.rollover) rolloverInto(month, target, transactions, categories) else 0
            status(category, target.monthlyAmount, rollover, spent, monthProgress, category.id in fixedCategoryIds)
        }.sortedWith(compareBy({ it.category.group }, { it.category.name }))

        val unbudgeted = spentByCategory
            .filterKeys { it !in targetsById }
            .filterValues { it > 0 }
            .map { (id, spent) -> status(categories[id], 0, 0, spent, monthProgress, id in fixedCategoryIds) }
            .sortedByDescending { it.spent }

        return MonthBudgetSummary(
            month = month,
            income = income,
            expectedIncome = maxOf(expectedIncome, income),
            totalBudgeted = budgeted.sumOf { it.budgeted },
            totalSpent = spentByCategory.values.sum(),
            budgetedSpent = budgeted.sumOf { it.spent },
            categories = budgeted,
            unbudgeted = unbudgeted,
            monthProgress = monthProgress,
            daysLeft = daysLeft,
        )
    }

    fun spendingByCategory(month: YearMonth, transactions: List<Transaction>, categories: CategoryIndex): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        for (txn in transactions) {
            if (YearMonth.from(txn.date) != month) continue
            val spent = CashFlowAnalyzer.spendingOf(txn, categories)
            if (spent != 0L) result.merge(categories[txn.categoryId].id, spent, Long::plus)
        }
        return result
    }

    private fun status(
        category: Category,
        budgeted: Long,
        rollover: Long,
        spent: Long,
        monthProgress: Float,
        fixed: Boolean,
    ): CategoryBudgetStatus {
        val limit = budgeted + rollover
        val projected = when {
            fixed -> maxOf(spent, if (monthProgress < 1f) limit.coerceAtLeast(0) else spent)
            monthProgress > 0.05f -> (spent / monthProgress).toLong()
            else -> spent
        }
        val health = when {
            budgeted == 0L && rollover == 0L -> BudgetHealth.NO_BUDGET
            spent > limit -> BudgetHealth.OVER
            fixed -> BudgetHealth.ON_TRACK
            limit > 0 && spent.toFloat() / limit > monthProgress + 0.15f && spent.toFloat() / limit > 0.5f -> BudgetHealth.WATCH
            else -> BudgetHealth.ON_TRACK
        }
        return CategoryBudgetStatus(category, budgeted, rollover, spent, health, projected)
    }

    /** Sum of (budget - spent) over prior months since the target started, capped at 12 months. */
    private fun rolloverInto(month: YearMonth, target: BudgetTarget, transactions: List<Transaction>, categories: CategoryIndex): Long {
        var carry = 0L
        var m = maxOf(target.startMonth, month.minusMonths(12))
        while (m.isBefore(month)) {
            val spent = spendingByCategory(m, transactions, categories)[target.categoryId] ?: 0
            carry += target.monthlyAmount - spent
            m = m.plusMonths(1)
        }
        return carry
    }
}
