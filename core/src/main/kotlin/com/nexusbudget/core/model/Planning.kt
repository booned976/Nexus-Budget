package com.nexusbudget.core.model

import java.time.LocalDate
import java.time.YearMonth

/** A monthly spending limit for one category. */
data class BudgetTarget(
    val categoryId: String,
    val monthlyAmount: Long,
    /** Carry unspent money (or overspending) into the next month. */
    val rollover: Boolean = false,
    val startMonth: YearMonth = YearMonth.now(),
)

enum class GoalType(val label: String, val icon: String) {
    EMERGENCY_FUND("Emergency fund", "🛟"),
    SAVINGS("Savings", "🐖"),
    PURCHASE("Big purchase", "🛍️"),
    HOME("Home", "🏡"),
    TRAVEL("Travel", "✈️"),
    EDUCATION("Education", "🎓"),
    RETIREMENT("Retirement", "🌅"),
    OTHER("Other", "🎯"),
}

data class Goal(
    val id: String,
    val name: String,
    val type: GoalType,
    val target: Long,
    /** Amount saved so far. Ignored when [linkedAccountId] is set (the account balance is used instead). */
    val saved: Long,
    val monthlyContribution: Long,
    val targetDate: LocalDate? = null,
    val linkedAccountId: String? = null,
    val createdOn: LocalDate = LocalDate.now(),
    val archived: Boolean = false,
)

/** Rules the user creates, e.g. "descriptions containing GREENLEAF are Groceries". */
data class CategoryRule(
    val id: String,
    val pattern: String,
    val categoryId: String,
    /** Optionally rename the merchant when the rule matches. */
    val renameTo: String? = null,
)

data class NetWorthSnapshot(
    val date: LocalDate,
    val assets: Long,
    val liabilities: Long,
) {
    val netWorth: Long get() = assets - liabilities
}
