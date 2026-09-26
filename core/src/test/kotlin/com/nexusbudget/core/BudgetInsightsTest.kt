package com.nexusbudget.core

import com.nexusbudget.core.demo.DemoData
import com.nexusbudget.core.engine.BudgetHealth
import com.nexusbudget.core.engine.BudgetInsights
import com.nexusbudget.core.engine.CashFlowStats
import com.nexusbudget.core.engine.CategoryBudgetStatus
import com.nexusbudget.core.engine.FinancialPicture
import com.nexusbudget.core.engine.InsightTone
import com.nexusbudget.core.engine.MonthBudgetSummary
import com.nexusbudget.core.engine.MonthTotals
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetInsightsTest {

    private val categories = CategoryIndex(Categories.defaults)
    private val month = YearMonth.of(2026, 9)

    private fun status(id: String, budgeted: Long, spent: Long, projected: Long = spent, rollover: Long = 0) =
        CategoryBudgetStatus(categories[id], budgeted, rollover, spent, BudgetHealth.ON_TRACK, projected)

    private fun stats(averages: Map<String, Long>, income: Long = 5_000_00) = CashFlowStats(
        months = listOf(MonthTotals(month.minusMonths(1), income, 3_000_00, 1_500_00, averages)),
        avgMonthlyIncome = income,
        avgMonthlySpending = 3_000_00,
        avgEssentialSpending = 1_500_00,
        avgSpendingByCategory = averages,
        currentMonth = MonthTotals(month, 0, 0, 0, emptyMap()),
    )

    private fun summary(statuses: List<CategoryBudgetStatus>, income: Long = 5_000_00, progress: Float = 0.5f) = MonthBudgetSummary(
        month = month,
        income = 0,
        expectedIncome = income,
        totalBudgeted = statuses.sumOf { it.budgeted },
        totalSpent = statuses.sumOf { it.spent },
        budgetedSpent = statuses.sumOf { it.spent },
        categories = statuses,
        unbudgeted = emptyList(),
        monthProgress = progress,
        daysLeft = 15,
    )

    @Test
    fun `demo household gets an overview and a reason for every budget`() {
        val today = LocalDate.of(2026, 9, 18)
        val demo = DemoData.generate(today)
        val picture = FinancialPicture.compute(today, demo.accounts, demo.transactions, categories, demo.budgets, demo.goals, demo.snapshots)
        val overview = BudgetInsights.overview(picture.budget, picture.stats, picture.recurring, picture.fixedCategories, isCurrentMonth = true)
        assertTrue(overview.any { it.title.startsWith("Expected income") }, overview.toString())
        assertTrue(overview.any { it.title.startsWith("Needs ") && "Wants" in it.title }, overview.toString())

        val rent = picture.budget.categories.first { it.category.id == Categories.RENT }
        val reason = BudgetInsights.reasonFor(rent, picture.stats, picture.recurring, picture.fixedCategories, isCurrentMonth = true)
        assertTrue(reason.summary.startsWith("Covers"), reason.summary)
        assertTrue(reason.details.any { "fixed bill" in it }, reason.details.toString())

        picture.budget.categories.forEach { status ->
            val r = BudgetInsights.reasonFor(status, picture.stats, picture.recurring, picture.fixedCategories, isCurrentMonth = true)
            assertTrue(r.summary.isNotBlank() && r.details.isNotEmpty(), "${status.category.name}: $r")
        }
    }

    @Test
    fun `compares each budget with usual spending`() {
        val averages = mapOf(Categories.GROCERIES to 500_00L, Categories.RESTAURANTS to 300_00L, Categories.COFFEE to 40_00L)
        val stats = stats(averages)
        fun summaryFor(id: String, budget: Long) =
            BudgetInsights.reasonFor(status(id, budget, 100_00, projected = 200_00), stats, emptyList(), emptySet(), isCurrentMonth = true).summary

        assertEquals("In line with your 1-month average of \$500", summaryFor(Categories.GROCERIES, 520_00))
        assertEquals("\$100 below your \$300 average, a stretch target", summaryFor(Categories.RESTAURANTS, 200_00))
        assertEquals("\$20 above your \$40 average, room to spare", summaryFor(Categories.COFFEE, 60_00))
        assertEquals("No spending here in your history yet", summaryFor(Categories.GIFTS, 50_00))
    }

    @Test
    fun `split follows the 50-30-20 guideline and suggests how much to trim`() {
        val statuses = listOf(
            status(Categories.RENT, 2_000_00, 2_000_00),
            status(Categories.RESTAURANTS, 1_000_00, 400_00),
            status(Categories.SHOPPING, 1_000_00, 400_00),
        )
        val split = BudgetInsights.overview(summary(statuses), stats(emptyMap()), emptyList(), setOf(Categories.RENT), isCurrentMonth = true)
            .first { it.title.startsWith("Needs") }
        assertEquals("Needs 40% · Wants 40% · Savings & debt 20%", split.title)
        assertEquals(InsightTone.GOOD, split.tone)

        val heavy = listOf(status(Categories.RENT, 2_500_00, 2_500_00), status(Categories.SHOPPING, 2_000_00, 500_00))
        val warning = BudgetInsights.overview(summary(heavy), stats(emptyMap()), emptyList(), setOf(Categories.RENT), isCurrentMonth = true)
            .first { it.title.startsWith("Needs") }
        assertEquals(InsightTone.WARNING, warning.tone)
        assertTrue("\$500" in warning.detail, warning.detail)
    }

    @Test
    fun `pace ignores fixed bills and flags flexible spending running ahead`() {
        val statuses = listOf(
            status(Categories.RENT, 1_500_00, 1_500_00),
            status(Categories.RESTAURANTS, 300_00, 240_00, projected = 480_00),
        )
        val pace = BudgetInsights.overview(summary(statuses, progress = 0.5f), stats(emptyMap()), emptyList(), setOf(Categories.RENT), isCurrentMonth = true)
            .first { it.title.startsWith("Flexible") }
        assertEquals(InsightTone.WARNING, pace.tone)
        assertTrue("80%" in pace.detail && "\$180" in pace.detail, pace.detail)

        val calm = listOf(status(Categories.RESTAURANTS, 300_00, 100_00, projected = 200_00))
        val onPace = BudgetInsights.overview(summary(calm, progress = 0.5f), stats(emptyMap()), emptyList(), emptySet(), isCurrentMonth = true)
            .first { it.title.startsWith("Flexible") }
        assertEquals(InsightTone.GOOD, onPace.tone)
        assertTrue(BudgetInsights.overview(summary(calm), stats(emptyMap()), emptyList(), emptySet(), isCurrentMonth = false).none { it.title.startsWith("Flexible") })
    }

    @Test
    fun `explains rollover and over-budget categories`() {
        val over = CategoryBudgetStatus(categories[Categories.SHOPPING], 200_00, -50_00, 260_00, BudgetHealth.OVER, 300_00)
        val reason = BudgetInsights.reasonFor(over, stats(mapOf(Categories.SHOPPING to 220_00L)), emptyList(), emptySet(), isCurrentMonth = true)
        assertTrue(reason.details.any { "overspending of \$50" in it }, reason.details.toString())
        val insights = BudgetInsights.overview(summary(listOf(over)), stats(emptyMap()), emptyList(), emptySet(), isCurrentMonth = true)
        assertTrue(insights.any { it.title == "Shopping is over budget" && "\$110" in it.detail }, insights.toString())
    }
}
