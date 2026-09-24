package com.nexusbudget.core

import com.nexusbudget.core.demo.DemoData
import com.nexusbudget.core.engine.FinancialPicture
import com.nexusbudget.core.engine.GoalEngine
import com.nexusbudget.core.engine.GoalStatus
import com.nexusbudget.core.importer.CsvImporter
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.Goal
import com.nexusbudget.core.model.GoalType
import com.nexusbudget.core.model.Money
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiscEngineTest {

    @Test
    fun `money parsing handles bank formats`() {
        assertEquals(123_456, Money.parse("1,234.56"))
        assertEquals(-19_99, Money.parse("(19.99)"))
        assertEquals(-5_00, Money.parse("-$5"))
        assertEquals(10, Money.parse("0.1"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
        assertEquals("1234.56", Money.toPlainString(123_456))
        assertEquals(1_000_00, Money.roundUp(993_00, 10_00))
    }

    @Test
    fun `goal projections`() {
        val today = LocalDate.of(2026, 1, 15)
        val goal = Goal("g", "Trip", GoalType.TRAVEL, 2_400_00, 400_00, 200_00, targetDate = LocalDate.of(2026, 11, 1))
        val projection = GoalEngine.project(goal, emptyList(), today)
        assertEquals(2_000_00, projection.remaining)
        assertEquals(LocalDate.of(2026, 11, 15), projection.projectedDate)
        assertEquals(200_00, projection.requiredMonthly)
        assertEquals(GoalStatus.ON_TRACK, projection.status)
        val behind = GoalEngine.project(goal.copy(monthlyContribution = 100_00), emptyList(), today)
        assertEquals(GoalStatus.BEHIND, behind.status)
    }

    @Test
    fun `csv import detects columns and signs`() {
        val csv = """
            "Posted Date","Description","Debit","Credit"
            03/01/2026,"GREENLEAF GROCERY, #12","45.10",
            03/02/2026,"ACME PAYROLL",,"2,150.00"
            bad row,,,
        """.trimIndent()
        val result = CsvImporter.parse(csv)
        assertEquals(2, result.rows.size)
        assertEquals(-45_10, result.rows[0].amount)
        assertEquals("GREENLEAF GROCERY, #12", result.rows[0].description)
        assertEquals(2_150_00, result.rows[1].amount)
        assertEquals(LocalDate.of(2026, 3, 2), result.rows[1].date)
        assertEquals(1, result.skipped)

        val single = CsvImporter.parse("Date,Amount,Payee\n2026-03-05,-12.50,Coffee\n", invertAmounts = true)
        assertEquals(12_50, single.rows.single().amount)
    }

    @Test
    fun `demo data produces a complete financial picture`() {
        val today = LocalDate.of(2026, 9, 18)
        val demo = DemoData.generate(today)
        val picture = FinancialPicture.compute(
            today, demo.accounts, demo.transactions, CategoryIndex(Categories.defaults), demo.budgets, demo.goals, demo.snapshots,
            extraDebtPayment = 200_00,
        )
        assertTrue(picture.stats.avgMonthlyIncome > 4_000_00, "income ${picture.stats.avgMonthlyIncome}")
        assertTrue(picture.recurring.any { it.isSubscription })
        assertTrue(picture.recurring.any { it.isIncome })
        assertTrue(picture.budget.hasBudgets)
        assertTrue(picture.recommendations.isNotEmpty())
        assertTrue(picture.payoff!!.avalanche.feasible)
        val uncategorized = demo.transactions.count { it.categoryId == Categories.UNCATEGORIZED }
        assertEquals(0, uncategorized, demo.transactions.filter { it.categoryId == Categories.UNCATEGORIZED }.joinToString { it.description })

        println("Net worth ${Money.format(picture.netWorth.total)}; safe to spend ${Money.format(picture.safeToSpend.amount)} until ${picture.safeToSpend.until}")
        println("Income ${Money.format(picture.stats.avgMonthlyIncome)} spending ${Money.format(picture.stats.avgMonthlySpending)}")
        picture.recurring.forEach { println("  recurring ${it.merchant} ${it.frequency} ${Money.format(it.amount)} next ${it.nextDate} sub=${it.isSubscription}") }
        picture.budget.categories.forEach { println("  budget ${it.category.name} ${Money.format(it.spent)} / ${Money.format(it.limit)} ${it.health}") }
        picture.recommendations.forEach { println("  [${it.severity}] ${it.title} — ${it.message}") }
        println("Debt free ${picture.payoff.avalanche.debtFreeMonth} interest ${Money.format(picture.payoff.avalanche.totalInterest)}")
        println("Forecast lowest ${picture.forecast.lowest}")
    }
}
