package com.nexusbudget.core.engine

import com.nexusbudget.core.model.BudgetBucket
import com.nexusbudget.core.model.Money
import kotlin.math.abs
import kotlin.math.roundToInt

enum class InsightTone { INFO, GOOD, WARNING }

data class BudgetInsight(val tone: InsightTone, val title: String, val detail: String)

/** Why a category's budget is what it is, in plain language. */
data class CategoryReason(
    /** One short line for the budget list. */
    val summary: String,
    /** A few sentences for the category's detail view. */
    val details: List<String>,
)

/**
 * Explains the numbers on the budget screen: where expected income comes from, how the budget splits
 * between needs, wants and savings, whether spending is on pace, and the reasoning behind each
 * category's amount.
 */
object BudgetInsights {

    private fun fmt(cents: Long) = Money.format(cents, showCents = false)
    private fun pct(share: Double) = "${(share * 100).roundToInt()}%"

    fun overview(
        summary: MonthBudgetSummary,
        stats: CashFlowStats,
        recurring: List<RecurringSeries>,
        fixedCategoryIds: Set<String>,
        isCurrentMonth: Boolean,
    ): List<BudgetInsight> = listOfNotNull(
        incomeBasis(summary, stats, recurring, isCurrentMonth),
        split(summary),
        pace(summary, fixedCategoryIds, isCurrentMonth),
        overBudget(summary),
    )

    private fun incomeBasis(summary: MonthBudgetSummary, stats: CashFlowStats, recurring: List<RecurringSeries>, isCurrentMonth: Boolean): BudgetInsight? {
        if (summary.expectedIncome <= 0) return null
        val paychecks = recurring.filter { it.isIncome }
        val paycheckTotal = paychecks.sumOf { it.monthlyAmount }
        val usual = if (paycheckTotal > 0) paycheckTotal else stats.avgMonthlyIncome
        val detail = when {
            summary.income >= summary.expectedIncome && summary.income > usual ->
                "You've received ${fmt(summary.income)} ${if (isCurrentMonth) "this month" else "that month"}, more than the usual " +
                    "${fmt(usual)}, so that's what the budget works from."
            paycheckTotal > 0 -> {
                val sources = paychecks.sortedByDescending { it.monthlyAmount }.take(2).joinToString(" and ") { it.merchant }
                val averaged = paychecks.any { it.frequency == Frequency.WEEKLY || it.frequency == Frequency.BIWEEKLY }
                "Your recurring paychecks from $sources add up to about ${fmt(paycheckTotal)} a month" +
                    if (averaged) ", averaged because some months have an extra paycheck." else "."
            }
            stats.hasHistory -> "That's your average monthly income over the last ${stats.months.size} months."
            else -> "That's the income recorded so far. It gets more accurate after a full month of history."
        }
        return BudgetInsight(InsightTone.INFO, "Expected income: ${fmt(summary.expectedIncome)}", detail)
    }

    private fun split(summary: MonthBudgetSummary): BudgetInsight? {
        val income = summary.expectedIncome
        if (!summary.hasBudgets || income <= 0) return null
        val byBucket = summary.categories.groupBy { it.category.bucket }.mapValues { (_, list) -> list.sumOf { it.budgeted } }
        val needs = byBucket[BudgetBucket.NEEDS] ?: 0
        val wants = byBucket[BudgetBucket.WANTS] ?: 0
        // Money budgeted to savings plus anything not assigned is what's left to save or pay debt down.
        val saving = income - needs - wants
        val needsShare = needs.toDouble() / income
        val wantsShare = wants.toDouble() / income
        val savingShare = saving.toDouble() / income
        val title = "Needs ${pct(needsShare)} · Wants ${pct(wantsShare)} · Savings & debt ${pct(savingShare)}"
        val wantsCeiling = (income * BudgetBucket.WANTS.targetShare).toLong()
        val advice = when {
            saving < 0 -> "Your budgets add up to ${fmt(-saving)} more than you expect to earn, so something has to give."
            savingShare >= BudgetBucket.SAVINGS.targetShare ->
                "At least 20% is left for savings and extra debt payments, which meets the 50/30/20 guideline."
            wants > wantsCeiling ->
                "Wants are above the guideline's 30%. Trimming them by ${fmt(Money.roundUp(wants - wantsCeiling, 10_00))} " +
                    "would free that much for savings and debt."
            else -> "Less than 20% is left for savings and debt. Needs take ${pct(needsShare)} of income, so the room has to come from wants."
        }
        val tone = if (saving >= 0 && savingShare >= BudgetBucket.SAVINGS.targetShare) InsightTone.GOOD else InsightTone.WARNING
        return BudgetInsight(
            tone,
            title,
            "A common guideline (50/30/20) is about half of income for needs, 30% for wants and at least 20% for savings and debt payoff. $advice",
        )
    }

    private fun pace(summary: MonthBudgetSummary, fixedCategoryIds: Set<String>, isCurrentMonth: Boolean): BudgetInsight? {
        if (!isCurrentMonth || !summary.hasBudgets) return null
        val flexible = summary.categories.filter { it.category.id !in fixedCategoryIds && it.limit > 0 }
        if (flexible.isEmpty()) return null
        val limit = flexible.sumOf { it.limit }
        val spent = flexible.sumOf { it.spent }
        val used = spent.toDouble() / limit
        val elapsed = summary.monthProgress.toDouble()
        val projectedOver = flexible.sumOf { (it.projected - it.limit).coerceAtLeast(0) }
        val note = "Fixed bills like rent are left out, since they're paid in one go."
        return when {
            used > elapsed + 0.1 && projectedOver > 0 -> BudgetInsight(
                InsightTone.WARNING,
                "Flexible spending is running ahead",
                "You've used ${pct(used)} of your flexible budgets with ${pct(elapsed)} of the month gone. " +
                    "At this pace they'd end about ${fmt(projectedOver)} over. $note",
            )
            used <= elapsed -> BudgetInsight(
                InsightTone.GOOD,
                "Flexible spending is on pace",
                "You've used ${pct(used)} of your flexible budgets with ${pct(elapsed)} of the month gone, " +
                    "leaving ${fmt((limit - spent).coerceAtLeast(0))} for the rest of the month. $note",
            )
            else -> BudgetInsight(
                InsightTone.INFO,
                "Flexible spending is close to pace",
                "You've used ${pct(used)} of your flexible budgets with ${pct(elapsed)} of the month gone. $note",
            )
        }
    }

    private fun overBudget(summary: MonthBudgetSummary): BudgetInsight? {
        val over = summary.overspentCategories.sortedByDescending { -it.available }
        if (over.isEmpty()) return null
        val names = over.take(3).joinToString(", ") { it.category.name }
        val total = over.sumOf { -it.available }
        return BudgetInsight(
            InsightTone.WARNING,
            if (over.size == 1) "${over.first().category.name} is over budget" else "${over.size} categories are over budget",
            "$names ${if (over.size == 1) "is" else "are"} ${fmt(total)} over in total. " +
                "Cover it from a category with money left, or plan to spend less for the rest of the month.",
        )
    }

    /** The reasoning behind one category's budget. */
    fun reasonFor(
        status: CategoryBudgetStatus,
        stats: CashFlowStats,
        recurring: List<RecurringSeries>,
        fixedCategoryIds: Set<String>,
        isCurrentMonth: Boolean,
    ): CategoryReason {
        val id = status.category.id
        val budget = status.budgeted
        val average = stats.avgSpendingByCategory[id] ?: 0
        val months = stats.months.size
        val period = if (months > 0) "$months-month" else "recent"
        val bills = recurring.filter { it.isBill && it.categoryId == id }
        val billTotal = bills.sumOf { -it.monthlyAmount }
        val fixed = id in fixedCategoryIds

        val coversBills = billTotal > 0 && budget >= billTotal && budget - billTotal <= maxOf(billTotal / 10, 10_00)
        val summary = when {
            coversBills && bills.size == 1 -> "Covers your ${bills.first().merchant} bill (${fmt(billTotal)}/month)"
            coversBills -> "Covers ${bills.size} recurring bills (${fmt(billTotal)}/month)"
            average <= 0 -> "No spending here in your history yet"
            abs(budget - average) <= average / 10 -> "In line with your $period average of ${fmt(average)}"
            budget < average -> "${fmt(average - budget)} below your ${fmt(average)} average, a stretch target"
            else -> "${fmt(budget - average)} above your ${fmt(average)} average, room to spare"
        }

        val details = buildList {
            if (average > 0) {
                add("Over the last ${if (months > 0) "$months months" else "few weeks"} you spent an average of ${fmt(average)} a month here.")
            }
            if (bills.isNotEmpty()) {
                val list = bills.sortedByDescending { -it.monthlyAmount }.take(3)
                    .joinToString(", ") { "${it.merchant} (${fmt(-it.amount)} ${it.frequency.label.lowercase()})" }
                add("Recurring bills in this category: $list.")
            }
            when {
                coversBills -> add("The budget is set to cover those bills, so it only needs changing if a price changes.")
                average > 0 && budget < average ->
                    add("The budget is ${pct((average - budget).toDouble() / average)} below what you usually spend, so it asks you to cut back here.")
                average > 0 && budget > average + average / 10 ->
                    add("The budget is higher than you usually spend. Lowering it would free ${fmt(budget - average)} a month for savings or debt.")
            }
            if (status.rolloverIn > 0) add("It includes ${fmt(status.rolloverIn)} left over from last month.")
            if (status.rolloverIn < 0) add("Last month's overspending of ${fmt(-status.rolloverIn)} is taken out of this month.")
            if (fixed) {
                add("This is treated as a fixed bill, so it's only flagged if it actually goes over, not for spending early in the month.")
            } else if (isCurrentMonth && status.spent > 0 && status.limit > 0) {
                val difference = status.limit - status.projected
                add(
                    "At your current pace you'll spend about ${fmt(status.projected)} this month, " +
                        if (difference >= 0) "${fmt(difference)} under budget." else "${fmt(-difference)} over budget.",
                )
            }
        }
        return CategoryReason(summary, details)
    }
}
