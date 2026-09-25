package com.nexusbudget.core.engine

import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.BudgetTarget
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.Goal
import com.nexusbudget.core.model.NetWorthSnapshot
import com.nexusbudget.core.model.Transaction
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the dashboard, plans screen and AI assistant need, computed in one pass from raw data.
 */
data class FinancialPicture(
    val today: LocalDate,
    val accounts: List<Account>,
    val netWorth: NetWorth,
    val netWorthChange30d: Long?,
    val stats: CashFlowStats,
    val budget: MonthBudgetSummary,
    val recurring: List<RecurringSeries>,
    val safeToSpend: SafeToSpend,
    val forecast: CashFlowForecast,
    val goals: List<GoalProjection>,
    val debts: List<Debt>,
    val payoff: PayoffComparison?,
    val recommendations: List<Recommendation>,
    val emergencyFundMonths: Double?,
) {
    /** Bills and debt payments due in the next two weeks, from any account. */
    val upcomingBills: List<UpcomingBill>
        get() = SafeToSpendCalculator.billsBetween(accounts, recurring, today, today.plusDays(14))

    val totalDebt: Long get() = debts.sumOf { it.balance }

    companion object {
        fun compute(
            today: LocalDate,
            accounts: List<Account>,
            transactions: List<Transaction>,
            categories: CategoryIndex,
            budgets: List<BudgetTarget>,
            goals: List<Goal>,
            snapshots: List<NetWorthSnapshot>,
            extraDebtPayment: Long = 0,
            dismissedRecurring: Set<String> = emptySet(),
            dismissedRecommendations: Set<String> = emptySet(),
        ): FinancialPicture {
            val visibleAccounts = accounts.filter { !it.isHidden }
            val netWorth = NetWorthCalculator.calculate(visibleAccounts)
            val stats = CashFlowAnalyzer.analyze(transactions, categories, today)
            val recurring = RecurringDetector.detect(transactions, categories, today, dismissedRecurring)
            val expectedIncome = recurring.filter { it.isIncome }.sumOf { it.monthlyAmount }.takeIf { it > 0 } ?: stats.avgMonthlyIncome
            val fixedCategories = BudgetEngine.fixedBillCategories +
                recurring.filter { it.isBill && !it.amountVaries && it.frequency == Frequency.MONTHLY }.mapNotNull { it.categoryId }
                    .filter { id -> stats.avgSpendingByCategory[id]?.let { avg -> recurring.filter { it.categoryId == id }.sumOf { -it.monthlyAmount } >= avg * 0.75 } ?: false }
            val budget = BudgetEngine.summarize(YearMonth.from(today), today, transactions, categories, budgets, expectedIncome, fixedCategories)
            val safe = SafeToSpendCalculator.calculate(visibleAccounts, recurring, goals, today)
            // Card purchases are forecast as everyday spending (they reach checking via the card payment),
            // so card payments themselves are left out of the forecast to avoid counting them twice.
            val cashAndCards = visibleAccounts.filter { it.type.isSpendable || it.type.isRevolving }.map { it.id }.toSet()
            val daily = CashFlowForecaster.everydaySpending(transactions.filter { it.accountId in cashAndCards }, recurring, categories, today)
            val forecast = CashFlowForecaster.forecast(
                startingBalance = safe.spendableCash,
                recurring = recurring.filter { it.categoryId != Categories.CREDIT_CARD_PAYMENT },
                dailySpending = daily,
                today = today,
                days = 30,
                spendableAccountIds = cashAndCards,
            )
            val goalProjections = goals.filter { !it.archived }.map { GoalEngine.project(it, accounts, today) }
            val debts = Debt.fromAccounts(visibleAccounts)
            val payoff = if (debts.isNotEmpty()) DebtPayoffEngine.compare(debts, extraDebtPayment, YearMonth.from(today)) else null

            val recommendations = Advisor.recommend(
                AdvisorInput(today, visibleAccounts, transactions, categories, stats, budget, recurring, goalProjections, debts, safe, forecast),
                dismissedRecommendations,
            )
            return FinancialPicture(
                today = today,
                accounts = visibleAccounts,
                netWorth = netWorth,
                netWorthChange30d = NetWorthCalculator.change(snapshots, netWorth.total, today),
                stats = stats,
                budget = budget,
                recurring = recurring,
                safeToSpend = safe,
                forecast = forecast,
                goals = goalProjections,
                debts = debts,
                payoff = payoff,
                recommendations = recommendations,
                emergencyFundMonths = Advisor.emergencyMonths(visibleAccounts, stats.avgEssentialSpending),
            )
        }
    }
}
