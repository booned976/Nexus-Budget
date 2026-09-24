package com.nexusbudget.core.engine

import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.CategoryKind
import com.nexusbudget.core.model.Transaction
import java.time.LocalDate
import java.time.YearMonth

data class MonthTotals(
    val month: YearMonth,
    val income: Long,
    val spending: Long,
    val essentialSpending: Long,
    val spendingByCategory: Map<String, Long>,
) {
    val net: Long get() = income - spending
}

data class CashFlowStats(
    val months: List<MonthTotals>,
    val avgMonthlyIncome: Long,
    val avgMonthlySpending: Long,
    val avgEssentialSpending: Long,
    val avgSpendingByCategory: Map<String, Long>,
    val currentMonth: MonthTotals,
) {
    val avgMonthlyNet: Long get() = avgMonthlyIncome - avgMonthlySpending

    /** Share of income not spent, 0.0-1.0 (can be negative). Null when there is no income. */
    val savingsRate: Double? get() = if (avgMonthlyIncome > 0) avgMonthlyNet.toDouble() / avgMonthlyIncome else null

    val hasHistory: Boolean get() = months.isNotEmpty()
}

object CashFlowAnalyzer {

    /** Money actually spent in a category for a transaction (refunds reduce it). */
    fun spendingOf(txn: Transaction, categories: CategoryIndex): Long =
        if (txn.excluded || categories.kindOf(txn) != CategoryKind.EXPENSE) 0 else -txn.amount

    fun incomeOf(txn: Transaction, categories: CategoryIndex): Long =
        if (txn.excluded || categories.kindOf(txn) != CategoryKind.INCOME) 0 else txn.amount

    fun totalsFor(month: YearMonth, transactions: List<Transaction>, categories: CategoryIndex): MonthTotals {
        var income = 0L
        var spending = 0L
        var essential = 0L
        val byCategory = mutableMapOf<String, Long>()
        for (txn in transactions) {
            if (YearMonth.from(txn.date) != month) continue
            income += incomeOf(txn, categories)
            val spent = spendingOf(txn, categories)
            if (spent != 0L) {
                spending += spent
                val category = categories[txn.categoryId]
                if (category.isEssential) essential += spent
                byCategory.merge(category.id, spent, Long::plus)
            }
        }
        return MonthTotals(month, income, spending, essential, byCategory)
    }

    /**
     * Averages over the last [lookbackMonths] complete months that have any activity.
     * Falls back to the current partial month (scaled to a full month) when nothing else exists.
     */
    fun analyze(
        transactions: List<Transaction>,
        categories: CategoryIndex,
        today: LocalDate,
        lookbackMonths: Int = 3,
    ): CashFlowStats {
        val current = YearMonth.from(today)
        val earliest = transactions.minOfOrNull { it.date }?.let { YearMonth.from(it) }
        val months = (1..lookbackMonths)
            .map { current.minusMonths(it.toLong()) }
            .filter { earliest != null && !it.isBefore(earliest) }
            .map { totalsFor(it, transactions, categories) }
            .filter { it.income != 0L || it.spending != 0L }
            .sortedBy { it.month }
        val currentTotals = totalsFor(current, transactions, categories)

        if (months.isEmpty()) {
            val scale = current.lengthOfMonth().toDouble() / today.dayOfMonth
            return CashFlowStats(
                months = emptyList(),
                avgMonthlyIncome = currentTotals.income,
                avgMonthlySpending = (currentTotals.spending * scale).toLong(),
                avgEssentialSpending = (currentTotals.essentialSpending * scale).toLong(),
                avgSpendingByCategory = currentTotals.spendingByCategory.mapValues { (it.value * scale).toLong() },
                currentMonth = currentTotals,
            )
        }

        val n = months.size
        val categoryTotals = mutableMapOf<String, Long>()
        months.forEach { m -> m.spendingByCategory.forEach { (id, v) -> categoryTotals.merge(id, v, Long::plus) } }
        return CashFlowStats(
            months = months,
            avgMonthlyIncome = months.sumOf { it.income } / n,
            avgMonthlySpending = months.sumOf { it.spending } / n,
            avgEssentialSpending = months.sumOf { it.essentialSpending } / n,
            avgSpendingByCategory = categoryTotals.mapValues { it.value / n },
            currentMonth = currentTotals,
        )
    }

    /** Income and spending for each of the last [count] months including the current one. */
    fun trend(transactions: List<Transaction>, categories: CategoryIndex, today: LocalDate, count: Int = 6): List<MonthTotals> {
        val current = YearMonth.from(today)
        return (count - 1 downTo 0).map { totalsFor(current.minusMonths(it.toLong()), transactions, categories) }
    }
}
