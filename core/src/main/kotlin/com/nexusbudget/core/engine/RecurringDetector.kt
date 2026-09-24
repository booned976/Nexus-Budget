package com.nexusbudget.core.engine

import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.CategoryKind
import com.nexusbudget.core.model.Transaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class Frequency(val label: String, val days: Double, val minGap: Int, val maxGap: Int, val minOccurrences: Int) {
    WEEKLY("Weekly", 7.0, 5, 9, 4),
    BIWEEKLY("Every 2 weeks", 14.0, 12, 16, 3),
    MONTHLY("Monthly", 30.44, 26, 35, 2),
    QUARTERLY("Quarterly", 91.3, 80, 100, 2),
    SEMIANNUAL("Twice a year", 182.6, 170, 195, 2),
    ANNUAL("Yearly", 365.25, 350, 380, 2),
    ;

    fun next(from: LocalDate): LocalDate = when (this) {
        WEEKLY -> from.plusWeeks(1)
        BIWEEKLY -> from.plusWeeks(2)
        MONTHLY -> from.plusMonths(1)
        QUARTERLY -> from.plusMonths(3)
        SEMIANNUAL -> from.plusMonths(6)
        ANNUAL -> from.plusYears(1)
    }

    /** Converts a per-occurrence amount into an average monthly amount. */
    fun monthly(amount: Long): Long = Math.round(amount * (30.44 / days))
}

data class RecurringSeries(
    val key: String,
    val merchant: String,
    val accountId: String,
    val categoryId: String?,
    val frequency: Frequency,
    /** Typical signed amount per occurrence (negative for bills, positive for income). */
    val amount: Long,
    val lastAmount: Long,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    val occurrences: Int,
    val amountVaries: Boolean,
    val kind: CategoryKind,
    val isSubscription: Boolean,
    /** Change between the last two charges when a normally-fixed amount changed (positive = costs more). */
    val priceChange: Long?,
) {
    val isIncome: Boolean get() = kind == CategoryKind.INCOME
    val isBill: Boolean get() = kind == CategoryKind.EXPENSE
    val monthlyAmount: Long get() = frequency.monthly(amount)

    /** Occurrence dates within [from, to] inclusive. */
    fun occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var date = nextDate
        var guard = 0
        while (!date.isAfter(to) && guard++ < 400) {
            if (!date.isBefore(from)) dates += date
            date = frequency.next(date)
        }
        return dates
    }
}

object RecurringDetector {

    /** Bills that legitimately change from month to month. */
    private val variableBills = setOf(
        Categories.UTILITIES, Categories.INTERNET_PHONE, Categories.INSURANCE, Categories.RENT,
        Categories.LOAN_PAYMENT, Categories.CREDIT_CARD_PAYMENT, Categories.TAXES, Categories.SAVINGS_TRANSFER,
    )

    fun detect(
        transactions: List<Transaction>,
        categories: CategoryIndex,
        today: LocalDate,
        dismissedKeys: Set<String> = emptySet(),
    ): List<RecurringSeries> {
        val window = today.minusDays(400)
        return transactions.asSequence()
            .filter { !it.excluded && !it.pending && it.date.isAfter(window) && it.amount != 0L }
            .groupBy { "${it.merchantKey}|${if (it.amount < 0) "out" else "in"}" }
            .mapNotNull { (key, txns) -> if (key in dismissedKeys) null else analyze(key, txns, categories, today) }
            .sortedBy { it.nextDate }
    }

    private fun analyze(key: String, raw: List<Transaction>, categories: CategoryIndex, today: LocalDate): RecurringSeries? {
        // One charge per day at most (split tenders or authorization + capture).
        val txns = raw.sortedBy { it.date }.distinctBy { it.date }
        if (txns.size < 2) return null

        val gaps = txns.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
        val medianGap = gaps.sorted()[gaps.size / 2]
        val frequency = Frequency.entries.firstOrNull { medianGap in it.minGap..it.maxGap } ?: return null

        val regular = gaps.count { it in frequency.minGap..frequency.maxGap }
        if (regular.toDouble() / gaps.size < 0.7) return null

        val amounts = txns.map { abs(it.amount) }
        val recentAmounts = amounts.takeLast(3).sorted()
        val typical = recentAmounts[recentAmounts.size / 2]
        val spread = (amounts.takeLast(6).max() - amounts.takeLast(6).min()).toDouble() / maxOf(typical, 1)
        val amountVaries = spread > 0.15

        val minOccurrences = if (frequency == Frequency.MONTHLY && amountVaries) 3 else frequency.minOccurrences
        if (txns.size < minOccurrences) return null
        // Variable-amount "series" like weekly groceries are habits, not bills.
        if (amountVaries && spread > 0.6) return null
        val dominantCategory = txns.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        if (amountVaries && txns.last().amount < 0 && dominantCategory !in variableBills) return null

        val last = txns.last()
        // Ended series: nothing for well over one period.
        val staleAfter = (frequency.days * 1.5).toLong() + 7
        if (ChronoUnit.DAYS.between(last.date, today) > staleAfter) return null

        var next = frequency.next(last.date)
        while (next.isBefore(today.minusDays(2))) next = frequency.next(next)
        if (next.isBefore(today)) next = today

        val categoryId = dominantCategory
        val sign = if (last.amount < 0) -1 else 1
        val kind = when {
            categoryId != null && categoryId != Categories.UNCATEGORIZED -> categories[categoryId].kind
            sign > 0 -> CategoryKind.INCOME
            else -> CategoryKind.EXPENSE
        }
        val priceChange = if (!amountVaries && txns.size >= 2) {
            val previous = abs(txns[txns.size - 2].amount)
            val change = abs(last.amount) - previous
            if (abs(change) > previous / 100) change else null
        } else {
            null
        }
        val isSubscription = kind == CategoryKind.EXPENSE && !amountVaries &&
            frequency in setOf(Frequency.MONTHLY, Frequency.ANNUAL, Frequency.QUARTERLY) &&
            (categoryId in Categories.subscriptionLike || ((categoryId == null || categoryId == Categories.UNCATEGORIZED) && typical <= 50_00))

        return RecurringSeries(
            key = key,
            merchant = last.merchant,
            accountId = last.accountId,
            categoryId = categoryId,
            frequency = frequency,
            amount = sign * typical,
            lastAmount = last.amount,
            lastDate = last.date,
            nextDate = next,
            occurrences = txns.size,
            amountVaries = amountVaries,
            kind = kind,
            isSubscription = isSubscription,
            priceChange = priceChange,
        )
    }
}
