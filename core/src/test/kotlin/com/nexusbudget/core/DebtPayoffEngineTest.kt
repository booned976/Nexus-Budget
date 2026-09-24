package com.nexusbudget.core

import com.nexusbudget.core.engine.Debt
import com.nexusbudget.core.engine.DebtPayoffEngine
import com.nexusbudget.core.engine.PayoffStrategy
import com.nexusbudget.core.model.AccountType
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebtPayoffEngineTest {

    private val start = YearMonth.of(2026, 1)

    @Test
    fun `single loan matches standard amortization`() {
        // $1,000 at 12% APR paying $100/month takes 11 payments and about $59 of interest.
        val plan = DebtPayoffEngine.simulate(listOf(Debt("a", "Card", 1_000_00, 12.0, 100_00)), PayoffStrategy.AVALANCHE, 0, start)
        assertTrue(plan.feasible)
        assertEquals(11, plan.months)
        assertTrue(plan.totalInterest in 55_00..62_00, "interest was ${plan.totalInterest}")
        assertEquals(YearMonth.of(2026, 11), plan.debtFreeMonth)
        assertEquals(0, plan.balanceTimeline.last())
    }

    @Test
    fun `zero interest loan pays off exactly`() {
        val plan = DebtPayoffEngine.simulate(listOf(Debt("a", "Loan", 1_000_00, 0.0, 100_00)), PayoffStrategy.SNOWBALL, 0, start)
        assertEquals(10, plan.months)
        assertEquals(0, plan.totalInterest)
        assertEquals(1_000_00, plan.totalPaid)
    }

    @Test
    fun `avalanche saves interest and snowball clears the smallest debt first`() {
        val debts = listOf(
            Debt("big", "Card", 5_000_00, 20.0, 100_00, AccountType.CREDIT_CARD),
            Debt("small", "Loan", 1_000_00, 5.0, 50_00, AccountType.PERSONAL_LOAN),
        )
        val comparison = DebtPayoffEngine.compare(debts, 200_00, start)
        assertTrue(comparison.avalanche.totalInterest < comparison.snowball.totalInterest)
        assertEquals("small", comparison.snowball.debts.first().debt.id)
        assertEquals("big", comparison.avalanche.debts.first().debt.id)
        // Rollover keeps the total monthly payment constant.
        assertEquals(350_00, comparison.snowball.monthlyPayment)
        val saved = comparison.interestSaved(comparison.avalanche)
        assertNotNull(saved)
        assertTrue(saved > 0)
        assertTrue(comparison.avalanche.months < comparison.minimumOnly.months)
    }

    @Test
    fun `first month payments send the extra to the priority debt`() {
        val debts = listOf(
            Debt("big", "Card", 5_000_00, 20.0, 100_00),
            Debt("small", "Loan", 1_000_00, 5.0, 50_00),
        )
        val plan = DebtPayoffEngine.simulate(debts, PayoffStrategy.AVALANCHE, 200_00, start)
        val byId = plan.firstMonthPayments.associateBy { it.debtId }
        assertEquals(300_00, byId.getValue("big").amount)
        assertTrue(byId.getValue("big").isExtra)
        assertEquals(50_00, byId.getValue("small").amount)
    }

    @Test
    fun `payments below interest are reported as infeasible`() {
        val plan = DebtPayoffEngine.simulate(listOf(Debt("a", "Card", 10_000_00, 30.0, 100_00)), PayoffStrategy.MINIMUM_ONLY, 0, start)
        assertFalse(plan.feasible)
        assertEquals(null, plan.debtFreeMonth)
    }

    @Test
    fun `custom order is respected`() {
        val debts = listOf(
            Debt("a", "A", 3_000_00, 22.0, 90_00),
            Debt("b", "B", 2_000_00, 8.0, 60_00),
            Debt("c", "C", 500_00, 15.0, 25_00),
        )
        val plan = DebtPayoffEngine.simulate(debts, PayoffStrategy.CUSTOM, 100_00, start, customOrder = listOf("b", "a"))
        assertEquals(listOf("b", "a", "c"), plan.debts.map { it.debt.id })
    }

    @Test
    fun `lump sum is applied before the first month`() {
        val plan = DebtPayoffEngine.simulate(listOf(Debt("a", "Card", 1_000_00, 12.0, 100_00)), PayoffStrategy.AVALANCHE, 0, start, lumpSum = 500_00)
        assertTrue(plan.months <= 6)
    }

    @Test
    fun `extra needed to hit a date is the smallest that works`() {
        val debts = listOf(
            Debt("big", "Card", 5_000_00, 20.0, 100_00),
            Debt("small", "Loan", 1_000_00, 5.0, 50_00),
        )
        val extra = DebtPayoffEngine.extraNeededFor(debts, 24, PayoffStrategy.AVALANCHE, start)
        assertNotNull(extra)
        assertTrue(DebtPayoffEngine.simulate(debts, PayoffStrategy.AVALANCHE, extra, start).months <= 24)
        assertTrue(DebtPayoffEngine.simulate(debts, PayoffStrategy.AVALANCHE, extra - 10_00, start).months > 24)
    }

    @Test
    fun `missing minimum payments are estimated`() {
        val card = Debt.estimateMinimum(2_000_00, 24.0, AccountType.CREDIT_CARD)
        // Interest (40) + 1% of balance (20) = $60.
        assertEquals(60_00, card)
        assertEquals(25_00, Debt.estimateMinimum(500_00, 0.0, AccountType.CREDIT_CARD))
        val loan = Debt.estimateMinimum(12_000_00, 6.0, AccountType.STUDENT_LOAN)
        assertTrue(loan in 132_00..134_00, "10-year payment was $loan")
    }
}
