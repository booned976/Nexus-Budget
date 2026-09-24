package com.nexusbudget.core

import com.nexusbudget.core.engine.BudgetEngine
import com.nexusbudget.core.engine.BudgetHealth
import com.nexusbudget.core.engine.Frequency
import com.nexusbudget.core.engine.RecurringDetector
import com.nexusbudget.core.engine.SafeToSpendCalculator
import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.BudgetTarget
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.Transaction
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BudgetAndRecurringTest {

    private val categories = CategoryIndex(Categories.defaults)
    private var n = 0
    private fun txn(date: LocalDate, amount: Long, description: String, category: String?, account: String = "chk") =
        Transaction("t${n++}", account, date, amount, description, categoryId = category)

    @Test
    fun `budget tracks spending, refunds and pace`() {
        val today = LocalDate.of(2026, 4, 10)
        val txns = listOf(
            txn(LocalDate.of(2026, 4, 2), -150_00, "GREENLEAF GROCERY", Categories.GROCERIES),
            txn(LocalDate.of(2026, 4, 6), -150_00, "GREENLEAF GROCERY", Categories.GROCERIES),
            txn(LocalDate.of(2026, 4, 7), 20_00, "GREENLEAF GROCERY REFUND", Categories.GROCERIES),
            txn(LocalDate.of(2026, 4, 3), -300_00, "CORNER BISTRO", Categories.RESTAURANTS),
            txn(LocalDate.of(2026, 4, 1), 2_000_00, "PAYROLL", Categories.PAYCHECK),
            txn(LocalDate.of(2026, 3, 20), -999_00, "OLD", Categories.GROCERIES),
        )
        val targets = listOf(
            BudgetTarget(Categories.GROCERIES, 500_00, startMonth = YearMonth.of(2026, 1)),
            BudgetTarget(Categories.RESTAURANTS, 200_00, startMonth = YearMonth.of(2026, 1)),
        )
        val summary = BudgetEngine.summarize(YearMonth.from(today), today, txns, categories, targets, 4_000_00)
        val groceries = summary.categories.first { it.category.id == Categories.GROCERIES }
        assertEquals(280_00, groceries.spent)
        assertEquals(220_00, groceries.available)
        assertEquals(BudgetHealth.WATCH, groceries.health)
        val restaurants = summary.categories.first { it.category.id == Categories.RESTAURANTS }
        assertEquals(BudgetHealth.OVER, restaurants.health)
        assertEquals(2_000_00, summary.income)
        assertEquals(4_000_00 - 700_00, summary.leftToBudget)
        assertEquals(580_00, summary.totalSpent)
    }

    @Test
    fun `rollover carries unspent money forward`() {
        val today = LocalDate.of(2026, 3, 5)
        val txns = listOf(
            txn(LocalDate.of(2026, 1, 10), -60_00, "SHOP", Categories.SHOPPING),
            txn(LocalDate.of(2026, 2, 10), -140_00, "SHOP", Categories.SHOPPING),
        )
        val targets = listOf(BudgetTarget(Categories.SHOPPING, 100_00, rollover = true, startMonth = YearMonth.of(2026, 1)))
        val summary = BudgetEngine.summarize(YearMonth.of(2026, 3), today, txns, categories, targets, 0)
        // +40 from January, -40 from February.
        assertEquals(0, summary.categories.single().rolloverIn)
        assertEquals(100_00, summary.categories.single().available)
    }

    @Test
    fun `monthly subscription and biweekly paycheck are detected`() {
        val today = LocalDate.of(2026, 5, 20)
        val txns = mutableListOf<Transaction>()
        for (m in 1..4) txns += txn(LocalDate.of(2026, m, 5), -15_49, "STREAMFLIX SUBSCRIPTION", Categories.SUBSCRIPTIONS, "card")
        var payday = LocalDate.of(2026, 1, 2)
        while (!payday.isAfter(today)) {
            txns += txn(payday, 2_150_00, "ACME PAYROLL", Categories.PAYCHECK)
            payday = payday.plusWeeks(2)
        }
        // Irregular spending shouldn't be detected.
        listOf(3, 9, 11, 20, 28, 40, 43, 60).forEach { txns += txn(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), -(30_00L + it * 97), "GREENLEAF GROCERY", Categories.GROCERIES) }

        val series = RecurringDetector.detect(txns, categories, today)
        val subscription = series.first { it.merchant.contains("Streamflix") }
        assertEquals(Frequency.MONTHLY, subscription.frequency)
        assertEquals(LocalDate.of(2026, 6, 5), subscription.nextDate)
        assertTrue(subscription.isSubscription)
        val paycheck = series.first { it.isIncome }
        assertEquals(Frequency.BIWEEKLY, paycheck.frequency)
        assertTrue(series.none { it.merchant.contains("Greenleaf") })
    }

    @Test
    fun `price increases are flagged`() {
        val today = LocalDate.of(2026, 5, 10)
        val txns = listOf(
            txn(LocalDate.of(2026, 2, 5), -13_99, "STREAMFLIX", Categories.SUBSCRIPTIONS),
            txn(LocalDate.of(2026, 3, 5), -13_99, "STREAMFLIX", Categories.SUBSCRIPTIONS),
            txn(LocalDate.of(2026, 4, 5), -13_99, "STREAMFLIX", Categories.SUBSCRIPTIONS),
            txn(LocalDate.of(2026, 5, 5), -15_49, "STREAMFLIX", Categories.SUBSCRIPTIONS),
        )
        val series = RecurringDetector.detect(txns, categories, today).single()
        assertEquals(1_50, series.priceChange)
    }

    @Test
    fun `safe to spend subtracts bills before payday and goal savings`() {
        val today = LocalDate.of(2026, 5, 10)
        val txns = mutableListOf<Transaction>()
        for (m in 1..4) txns += txn(LocalDate.of(2026, m, 14), -65_00, "SKYLINE INTERNET", Categories.INTERNET_PHONE)
        for (m in 1..5) txns += txn(LocalDate.of(2026, m, 1), 3_000_00, "ACME PAYROLL", Categories.PAYCHECK)
        val recurring = RecurringDetector.detect(txns, categories, today)
        val accounts = listOf(
            Account("chk", "Checking", AccountType.CHECKING, 1_000_00),
            Account("card", "Card", AccountType.CREDIT_CARD, 500_00, minimumPayment = 40_00, paymentDueDay = 20),
        )
        val safe = SafeToSpendCalculator.calculate(accounts, recurring, emptyList(), today)
        assertEquals(LocalDate.of(2026, 5, 31), safe.until)
        assertTrue(safe.untilPayday)
        // Internet bill on the 14th and the card minimum on the 20th.
        assertEquals(105_00, safe.billsTotal)
        assertEquals(1_000_00 - 105_00, safe.amount)
        assertNotNull(safe.upcomingBills.firstOrNull { it.accountId == "card" })
    }
}
