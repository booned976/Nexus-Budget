package com.nexusbudget.core.engine

import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.CategoryKind
import com.nexusbudget.core.model.Transaction
import java.time.LocalDate

data class ForecastEvent(val date: LocalDate, val name: String, val amount: Long)

data class ForecastPoint(val date: LocalDate, val balance: Long)

data class CashFlowForecast(
    val points: List<ForecastPoint>,
    val events: List<ForecastEvent>,
    val dailySpending: Long,
) {
    val lowest: ForecastPoint? get() = points.minByOrNull { it.balance }
    val ending: Long get() = points.lastOrNull()?.balance ?: 0
}

/** Projects spendable cash forward using detected bills, paychecks and typical everyday spending. */
object CashFlowForecaster {

    fun forecast(
        startingBalance: Long,
        recurring: List<RecurringSeries>,
        dailySpending: Long,
        today: LocalDate,
        days: Int = 30,
        spendableAccountIds: Set<String>? = null,
    ): CashFlowForecast {
        val end = today.plusDays(days.toLong())
        val events = recurring
            .filter { spendableAccountIds == null || it.accountId in spendableAccountIds }
            .flatMap { series ->
                series.occurrencesBetween(today.plusDays(1), end).map { ForecastEvent(it, series.merchant, series.amount) }
            }
            .sortedBy { it.date }
        val byDate = events.groupBy { it.date }
        val points = mutableListOf(ForecastPoint(today, startingBalance))
        var balance = startingBalance
        for (offset in 1..days) {
            val date = today.plusDays(offset.toLong())
            balance -= dailySpending
            balance += byDate[date]?.sumOf { it.amount } ?: 0
            points += ForecastPoint(date, balance)
        }
        return CashFlowForecast(points, events, dailySpending)
    }

    /**
     * Average daily everyday spending over the last [days] days, excluding anything that belongs
     * to a recurring series (those are forecast individually).
     */
    fun everydaySpending(
        transactions: List<Transaction>,
        recurring: List<RecurringSeries>,
        categories: CategoryIndex,
        today: LocalDate,
        days: Int = 90,
    ): Long {
        val recurringKeys = recurring.filter { !it.isIncome }.map { it.key.substringBefore('|') }.toSet()
        val since = today.minusDays(days.toLong())
        val earliest = transactions.minOfOrNull { it.date } ?: return 0
        val span = minOf(days.toLong(), today.toEpochDay() - maxOf(since, earliest).toEpochDay()).coerceAtLeast(1)
        val total = transactions
            .filter { it.date.isAfter(since) && !it.date.isAfter(today) }
            .filter { categories.kindOf(it) == CategoryKind.EXPENSE && it.merchantKey !in recurringKeys }
            .sumOf { CashFlowAnalyzer.spendingOf(it, categories) }
        return (total / span).coerceAtLeast(0)
    }
}
