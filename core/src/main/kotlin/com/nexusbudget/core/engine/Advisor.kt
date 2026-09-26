package com.nexusbudget.core.engine

import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.GoalType
import com.nexusbudget.core.model.Money
import com.nexusbudget.core.model.Transaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class Severity { URGENT, IMPORTANT, SUGGESTION, POSITIVE }

/**
 * Where a recommendation's button leads. BUDGET opens the category in [Recommendation.categoryId] when
 * set; GOALS opens [Recommendation.goalId], or a new goal from [Recommendation.newGoalType], or the list.
 */
enum class RecommendationAction { DEBT_PLAN, BUDGET, BUILD_BUDGET, GOALS, RECURRING, TRANSACTIONS, ACCOUNTS, ASSISTANT, NONE }

data class Recommendation(
    /** Stable id so a dismissed recommendation stays dismissed (until its situation changes month). */
    val id: String,
    val severity: Severity,
    val title: String,
    val message: String,
    val action: RecommendationAction,
    val actionLabel: String,
    /** Estimated yearly dollars saved or gained, used for ranking. */
    val yearlyImpact: Long = 0,
    /** A question to hand to the AI assistant for a personalized walkthrough. */
    val assistantPrompt: String? = null,
    val categoryId: String? = null,
    /** An existing goal the button opens. */
    val goalId: String? = null,
    /** A new goal the button starts, with a suggested target. */
    val newGoalType: GoalType? = null,
    val newGoalTarget: Long? = null,
)

data class AdvisorInput(
    val today: LocalDate,
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val categories: CategoryIndex,
    val stats: CashFlowStats,
    val budget: MonthBudgetSummary,
    val recurring: List<RecurringSeries>,
    val goals: List<GoalProjection>,
    val debts: List<Debt>,
    val safeToSpend: SafeToSpend,
    val forecast: CashFlowForecast?,
)

/**
 * Rule-based recommendations that work offline and without AI. Each rule looks at one part of the
 * user's finances and, when it finds something worth acting on, explains it with real numbers.
 */
object Advisor {

    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun recommend(input: AdvisorInput, dismissed: Set<String> = emptySet()): List<Recommendation> {
        val list = mutableListOf<Recommendation>()
        cashFlow(input)?.let(list::add)
        lowBalance(input)?.let(list::add)
        emergencyFund(input)?.let(list::add)
        list += highInterestDebt(input)
        creditUtilization(input)?.let(list::add)
        list += overspending(input)
        budgetSetup(input)?.let(list::add)
        list += spendingSpikes(input)
        subscriptions(input)?.let(list::add)
        list += priceIncreases(input)
        list += goalsBehind(input)
        studentLoans(input)?.let(list::add)
        idleCash(input)?.let(list::add)
        reviewTransactions(input)?.let(list::add)
        savingsRate(input)?.let(list::add)

        return list
            .filter { it.id !in dismissed }
            .sortedWith(compareBy<Recommendation> { it.severity.ordinal }.thenByDescending { it.yearlyImpact })
    }

    private fun fmt(cents: Long) = Money.format(cents, showCents = false)
    private fun monthKey(input: AdvisorInput) = YearMonth.from(input.today).toString()

    private fun cashFlow(input: AdvisorInput): Recommendation? {
        val stats = input.stats
        if (!stats.hasHistory || stats.avgMonthlyIncome <= 0) return null
        val gap = stats.avgMonthlySpending - stats.avgMonthlyIncome
        if (gap <= 0) return null
        return Recommendation(
            id = "cashflow-${monthKey(input)}",
            severity = Severity.URGENT,
            title = "You're spending more than you earn",
            message = "Over the last ${stats.months.size} months you spent about ${fmt(gap)} more than you brought in each month. " +
                "Setting limits on your biggest flexible categories is the fastest way to close the gap.",
            action = RecommendationAction.BUDGET,
            actionLabel = "Review budget",
            yearlyImpact = gap * 12,
            assistantPrompt = "I'm spending about ${fmt(gap)} more than I earn each month. Look at my spending and give me a concrete plan to close the gap.",
        )
    }

    private fun lowBalance(input: AdvisorInput): Recommendation? {
        val lowest = input.forecast?.lowest ?: return null
        if (lowest.balance >= 0 || input.forecast.points.size < 2) return null
        return Recommendation(
            id = "lowbalance-${lowest.date}",
            severity = Severity.URGENT,
            title = "Your checking may run short",
            message = "Based on upcoming bills and your usual spending, checking could drop to ${Money.format(lowest.balance)} around " +
                "${lowest.date.format(DateTimeFormatter.ofPattern("MMM d"))}. Move money over or delay a flexible purchase to avoid overdraft fees.",
            action = RecommendationAction.RECURRING,
            actionLabel = "See upcoming bills",
            yearlyImpact = 35_00 * 12,
        )
    }

    private fun emergencyFund(input: AdvisorInput): Recommendation? {
        val essential = input.stats.avgEssentialSpending
        if (essential <= 0) return null
        val liquid = input.accounts.filter { it.type == AccountType.SAVINGS && !it.isHidden }.sumOf { it.balance }
        val months = liquid.toDouble() / essential
        val existingGoal = input.goals.firstOrNull { it.goal.type == GoalType.EMERGENCY_FUND }?.goal
        val hasGoal = existingGoal != null
        val target = GoalEngine.emergencyFundTarget(essential)
        return when {
            months < 1.0 -> Recommendation(
                id = "efund-start",
                severity = Severity.IMPORTANT,
                title = "Build a starter emergency fund",
                message = "Your savings cover about ${"%.1f".format(months)} months of essential expenses (${fmt(essential)}/month). " +
                    "Aim for one month first, then three (${fmt(target)}). A cushion keeps surprise bills off your credit cards.",
                action = RecommendationAction.GOALS,
                actionLabel = if (hasGoal) "View goal" else "Create goal",
                yearlyImpact = essential,
                assistantPrompt = "Help me build an emergency fund. How much should I save each month based on my budget?",
                goalId = existingGoal?.id,
                newGoalType = GoalType.EMERGENCY_FUND.takeIf { !hasGoal },
                newGoalTarget = target.takeIf { !hasGoal },
            )
            months < 3.0 && !hasGoal -> Recommendation(
                id = "efund-grow",
                severity = Severity.SUGGESTION,
                title = "Grow your emergency fund to 3 months",
                message = "You have about ${"%.1f".format(months)} months of essentials saved. Three months (${fmt(target)}) is a common target.",
                action = RecommendationAction.GOALS,
                actionLabel = "Create goal",
                yearlyImpact = essential / 2,
                newGoalType = GoalType.EMERGENCY_FUND,
                newGoalTarget = target,
            )
            else -> null
        }
    }

    private fun highInterestDebt(input: AdvisorInput): List<Recommendation> {
        val expensive = input.debts.filter { it.apr >= 15.0 }.sortedByDescending { it.apr }
        if (expensive.isEmpty()) return emptyList()
        val monthlyInterest = expensive.sumOf { it.monthlyInterest }
        val surplus = input.stats.avgMonthlyNet.coerceAtLeast(0)
        val extra = Money.roundUp(maxOf(surplus / 2, 50_00), 25_00)
        val comparison = DebtPayoffEngine.compare(input.debts, extra, YearMonth.from(input.today))
        val saved = comparison.interestSaved(comparison.avalanche)
        val top = expensive.first()
        val savedText = if (saved != null && saved > 0) {
            " Adding ${fmt(extra)}/month with the avalanche method saves about ${fmt(saved)} in interest" +
                (comparison.avalanche.debtFreeMonth?.let { " and makes you debt-free by ${it.format(monthFormat)}" } ?: "") + "."
        } else {
            ""
        }
        return listOf(
            Recommendation(
                id = "debt-high-${monthKey(input)}",
                severity = Severity.IMPORTANT,
                title = "High-interest debt is costing ${fmt(monthlyInterest)}/month",
                message = "${top.name} charges ${"%.2f".format(top.apr)}% APR. Every extra dollar here earns a guaranteed ${top.apr.roundToInt()}% return.$savedText",
                action = RecommendationAction.DEBT_PLAN,
                actionLabel = "Open payoff plan",
                yearlyImpact = saved?.coerceAtLeast(0)?.let { minOf(it, monthlyInterest * 12) } ?: (monthlyInterest * 12),
                assistantPrompt = "What's the fastest realistic way for me to pay off my high-interest debt?",
            ),
        )
    }

    private fun creditUtilization(input: AdvisorInput): Recommendation? {
        val cards = input.accounts.filter { it.type.isRevolving && (it.creditLimit ?: 0) > 0 && !it.isHidden }
        if (cards.isEmpty()) return null
        val used = cards.sumOf { it.balance }
        val limit = cards.sumOf { it.creditLimit!! }
        val overall = used.toDouble() / limit
        val worst = cards.maxByOrNull { it.utilization ?: 0.0 } ?: return null
        val worstUtil = worst.utilization ?: 0.0
        if (overall < 0.3 && worstUtil < 0.5) return null
        val toTarget = (used - (limit * 0.3).roundToLong()).coerceAtLeast(0)
        return Recommendation(
            id = "utilization-${monthKey(input)}",
            severity = if (overall >= 0.5) Severity.IMPORTANT else Severity.SUGGESTION,
            title = "Credit utilization is ${(overall * 100).roundToInt()}%",
            message = "Using more than 30% of your available credit can lower your credit score. " +
                (if (toTarget > 0) "Paying down ${fmt(toTarget)} would bring you under 30%. " else "") +
                "${worst.name} is at ${(worstUtil * 100).roundToInt()}% of its limit.",
            action = RecommendationAction.DEBT_PLAN,
            actionLabel = "See debts",
            yearlyImpact = toTarget / 10,
        )
    }

    private fun overspending(input: AdvisorInput): List<Recommendation> {
        val budget = input.budget
        return budget.categories
            .filter { it.health == BudgetHealth.OVER || (it.health == BudgetHealth.WATCH && it.projected > it.limit * 1.1) }
            .sortedByDescending { it.spent - it.limit }
            .take(2)
            .map { status ->
                val over = status.health == BudgetHealth.OVER
                Recommendation(
                    id = "over-${status.category.id}-${budget.month}",
                    severity = if (over) Severity.IMPORTANT else Severity.SUGGESTION,
                    title = if (over) "${status.category.name} is over budget" else "${status.category.name} is running hot",
                    message = if (over) {
                        "You've spent ${fmt(status.spent)} of ${fmt(status.limit)} this month (${fmt(status.spent - status.limit)} over). " +
                            "Cover it from a category with money left, or lower spending for the rest of the month."
                    } else {
                        "At this pace you'll spend about ${fmt(status.projected)} against a ${fmt(status.limit)} budget. " +
                            "You have ${fmt(status.available)} left for ${budget.daysLeft} days."
                    },
                    action = RecommendationAction.BUDGET,
                    actionLabel = "Adjust budget",
                    yearlyImpact = (status.projected - status.limit).coerceAtLeast(0) * 12,
                    categoryId = status.category.id,
                )
            }
    }

    private fun budgetSetup(input: AdvisorInput): Recommendation? {
        if (input.budget.hasBudgets || !input.stats.hasHistory) return null
        val plan = BudgetPlanner.suggest(input.stats, input.categories, input.recurring)
        if (plan.suggestions.isEmpty()) return null
        return Recommendation(
            id = "budget-setup",
            severity = Severity.IMPORTANT,
            title = "Create your first budget in one tap",
            message = "We can build a budget from your last few months of spending that leaves " +
                "${fmt(plan.savings.coerceAtLeast(0))}/month for savings and debt payoff. You can adjust any category afterwards.",
            action = RecommendationAction.BUILD_BUDGET,
            actionLabel = "Build my budget",
            yearlyImpact = plan.savings.coerceAtLeast(0) * 12,
        )
    }

    private fun spendingSpikes(input: AdvisorInput): List<Recommendation> {
        val stats = input.stats
        if (stats.months.size < 2) return emptyList()
        val progress = input.budget.monthProgress.coerceAtLeast(0.2f)
        return stats.currentMonth.spendingByCategory
            .mapNotNull { (id, spent) ->
                val avg = stats.avgSpendingByCategory[id] ?: return@mapNotNull null
                val projected = (spent / progress).toLong()
                if (avg < 50_00 || projected < avg * 1.5 || projected - avg < 75_00) return@mapNotNull null
                if (input.budget.categories.any { it.category.id == id }) return@mapNotNull null
                Triple(id, projected, avg)
            }
            .sortedByDescending { it.second - it.third }
            .take(1)
            .map { (id, projected, avg) ->
                val category = input.categories[id]
                Recommendation(
                    id = "spike-$id-${monthKey(input)}",
                    severity = Severity.SUGGESTION,
                    title = "${category.name} is higher than usual",
                    message = "You're on pace for about ${fmt(projected)} this month versus your usual ${fmt(avg)}. " +
                        "Setting a ${fmt(Money.roundUp(avg, 10_00))} budget can help keep it in check.",
                    action = RecommendationAction.BUDGET,
                    actionLabel = "Set a budget",
                    yearlyImpact = (projected - avg) * 12,
                    categoryId = id,
                )
            }
    }

    private fun subscriptions(input: AdvisorInput): Recommendation? {
        val subs = input.recurring.filter { it.isSubscription }
        if (subs.size < 3) return null
        val monthly = subs.sumOf { -it.monthlyAmount }
        return Recommendation(
            id = "subs-${monthKey(input)}",
            severity = Severity.SUGGESTION,
            title = "${subs.size} subscriptions cost ${fmt(monthly)}/month",
            message = "That's ${fmt(monthly * 12)} a year. Review them and cancel anything you haven't used in the last month.",
            action = RecommendationAction.RECURRING,
            actionLabel = "Review subscriptions",
            yearlyImpact = monthly * 12 / 4,
        )
    }

    private fun priceIncreases(input: AdvisorInput): List<Recommendation> =
        input.recurring
            .filter { it.isBill && (it.priceChange ?: 0) > 0 && input.today.toEpochDay() - it.lastDate.toEpochDay() <= 45 }
            .map { series ->
                val increase = series.priceChange!!
                Recommendation(
                    id = "price-${series.key}-${series.lastDate}",
                    severity = Severity.SUGGESTION,
                    title = "${series.merchant} raised its price",
                    message = "Your last charge was ${fmt(-series.lastAmount)}, up ${Money.format(increase)} from before. " +
                        "That adds ${fmt(series.frequency.monthly(increase) * 12)} a year.",
                    action = RecommendationAction.RECURRING,
                    actionLabel = "View recurring",
                    yearlyImpact = series.frequency.monthly(increase) * 12,
                )
            }

    private fun goalsBehind(input: AdvisorInput): List<Recommendation> =
        input.goals
            .filter { it.status == GoalStatus.BEHIND && it.requiredMonthly != null }
            .map { projection ->
                val needed = projection.requiredMonthly!! - projection.goal.monthlyContribution
                Recommendation(
                    id = "goal-${projection.goal.id}-${monthKey(input)}",
                    severity = Severity.SUGGESTION,
                    title = "${projection.goal.name} needs ${fmt(needed)} more a month",
                    message = "To reach ${fmt(projection.goal.target)} by " +
                        "${projection.goal.targetDate?.format(DateTimeFormatter.ofPattern("MMM yyyy"))}, save ${fmt(projection.requiredMonthly)}/month " +
                        "instead of ${fmt(projection.goal.monthlyContribution)}. Or move the date out.",
                    action = RecommendationAction.GOALS,
                    actionLabel = "Adjust goal",
                    yearlyImpact = needed * 12 / 2,
                    goalId = projection.goal.id,
                )
            }

    private fun studentLoans(input: AdvisorInput): Recommendation? {
        val loans = input.debts.filter { it.type == AccountType.STUDENT_LOAN }
        if (loans.isEmpty()) return null
        val total = loans.sumOf { it.balance }
        val highest = loans.maxBy { it.apr }
        val otherExpensive = input.debts.any { it.type != AccountType.STUDENT_LOAN && it.apr > highest.apr }
        val message = buildString {
            append("You owe ${fmt(total)} across ${loans.size} student loan${if (loans.size > 1) "s" else ""}. ")
            if (otherExpensive) {
                append("Your other debts charge more interest, so pay minimums here and send extra money there first. ")
            } else if (highest.apr >= 6.0) {
                append("${highest.name} is at ${"%.2f".format(highest.apr)}%, so extra payments there pay off. ")
            }
            append("Federal loans come with income-driven repayment and forgiveness options that are lost if you refinance into a private loan, so compare carefully before refinancing.")
        }
        return Recommendation(
            id = "student-loans",
            severity = Severity.SUGGESTION,
            title = "Make a plan for your student loans",
            message = message,
            action = RecommendationAction.DEBT_PLAN,
            actionLabel = "See payoff plan",
            yearlyImpact = loans.sumOf { it.monthlyInterest } * 12 / 10,
            assistantPrompt = "Walk me through my student loan options. Should I pay them off faster, and what should I know about repayment plans?",
        )
    }

    private fun idleCash(input: AdvisorInput): Recommendation? {
        val checking = input.accounts.filter { it.type == AccountType.CHECKING && !it.isHidden }.sumOf { it.balance }
        val spending = input.stats.avgMonthlySpending
        if (spending <= 0) return null
        val excess = checking - spending * 2
        if (excess < 1_000_00) return null
        if (input.debts.any { it.apr >= 15.0 }) return null
        val yearly = (excess * 0.04).roundToLong()
        return Recommendation(
            id = "idle-${monthKey(input)}",
            severity = Severity.SUGGESTION,
            title = "Put ${fmt(excess)} of idle cash to work",
            message = "Checking holds more than two months of spending. Moving the extra to a high-yield savings account or a goal could earn around ${fmt(yearly)} a year.",
            action = RecommendationAction.GOALS,
            actionLabel = "See goals",
            yearlyImpact = yearly,
        )
    }

    private fun reviewTransactions(input: AdvisorInput): Recommendation? {
        val since = input.today.minusDays(30)
        val uncategorized = input.transactions.count {
            it.date.isAfter(since) && !it.excluded && (it.categoryId == null || it.categoryId == Categories.UNCATEGORIZED)
        }
        if (uncategorized < 5) return null
        return Recommendation(
            id = "review-${monthKey(input)}",
            severity = Severity.SUGGESTION,
            title = "$uncategorized transactions need a category",
            message = "Categorizing them keeps your budget accurate. Each one you fix teaches the app how to handle that merchant next time.",
            action = RecommendationAction.TRANSACTIONS,
            actionLabel = "Review",
        )
    }

    private fun savingsRate(input: AdvisorInput): Recommendation? {
        val rate = input.stats.savingsRate ?: return null
        if (!input.stats.hasHistory) return null
        return when {
            rate >= 0.2 -> Recommendation(
                id = "savings-great-${monthKey(input)}",
                severity = Severity.POSITIVE,
                title = "You're saving ${(rate * 100).roundToInt()}% of your income",
                message = "That beats the 20% guideline. Keep it going by giving every surplus dollar a job: a goal, extra debt payments, or investing.",
                action = RecommendationAction.GOALS,
                actionLabel = "View goals",
            )
            rate in 0.0..0.1 -> Recommendation(
                id = "savings-low-${monthKey(input)}",
                severity = Severity.SUGGESTION,
                title = "Aim to save 10-20% of income",
                message = "You're keeping about ${(rate * 100).roundToInt()}% of what you earn. " +
                    "Reaching 10% means setting aside ${fmt((input.stats.avgMonthlyIncome * 0.1).roundToLong() - input.stats.avgMonthlyNet.coerceAtLeast(0))} more a month.",
                action = RecommendationAction.BUDGET,
                actionLabel = "Find room in budget",
                yearlyImpact = (input.stats.avgMonthlyIncome * 0.1).roundToLong() * 12 / 4,
            )
            else -> null
        }
    }

    /** Liquid savings expressed as months of essential spending. */
    fun emergencyMonths(accounts: List<Account>, avgEssentialSpending: Long): Double? {
        if (avgEssentialSpending <= 0) return null
        val liquid = accounts.filter { it.type == AccountType.SAVINGS && !it.isHidden }.sumOf { it.balance }
        return liquid.toDouble() / avgEssentialSpending
    }
}
