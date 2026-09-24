package com.nexusbudget.core.engine

import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.Goal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class GoalStatus(val label: String) {
    COMPLETE("Complete"),
    ON_TRACK("On track"),
    BEHIND("Behind"),
    NO_PLAN("Needs a plan"),
}

data class GoalProjection(
    val goal: Goal,
    val saved: Long,
    val remaining: Long,
    val progress: Float,
    /** Estimated completion date at the planned monthly contribution. */
    val projectedDate: LocalDate?,
    /** Monthly amount needed to reach the goal by its target date. */
    val requiredMonthly: Long?,
    val status: GoalStatus,
)

object GoalEngine {

    fun project(goal: Goal, accounts: List<Account>, today: LocalDate): GoalProjection {
        val saved = goal.linkedAccountId
            ?.let { id -> accounts.firstOrNull { it.id == id }?.balance }
            ?: goal.saved
        val remaining = (goal.target - saved).coerceAtLeast(0)
        val progress = if (goal.target > 0) (saved.toFloat() / goal.target).coerceIn(0f, 1f) else 1f

        val projectedDate = when {
            remaining == 0L -> today
            goal.monthlyContribution > 0 -> today.plusMonths(ceilDiv(remaining, goal.monthlyContribution))
            else -> null
        }
        val requiredMonthly = goal.targetDate?.let { target ->
            val months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), target.withDayOfMonth(1)).coerceAtLeast(1)
            ceilDiv(remaining, months)
        }
        val status = when {
            remaining == 0L -> GoalStatus.COMPLETE
            requiredMonthly != null && goal.monthlyContribution >= requiredMonthly -> GoalStatus.ON_TRACK
            requiredMonthly != null -> if (goal.monthlyContribution > 0 || progress > 0f) GoalStatus.BEHIND else GoalStatus.NO_PLAN
            goal.monthlyContribution > 0 -> GoalStatus.ON_TRACK
            else -> GoalStatus.NO_PLAN
        }
        return GoalProjection(goal, saved, remaining, progress, projectedDate, requiredMonthly, status)
    }

    /** Recommended emergency fund: [months] of essential spending (default three). */
    fun emergencyFundTarget(avgEssentialSpending: Long, months: Int = 3): Long =
        com.nexusbudget.core.model.Money.roundUp(avgEssentialSpending * months, 100_00)

    private fun ceilDiv(a: Long, b: Long): Long = if (b <= 0) 0 else (a + b - 1) / b
}
