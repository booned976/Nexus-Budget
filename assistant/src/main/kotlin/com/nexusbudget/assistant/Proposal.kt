package com.nexusbudget.assistant

import com.nexusbudget.core.model.GoalType
import java.time.LocalDate

/**
 * A change the assistant suggests. Nothing is applied until the user taps "Apply" in the chat,
 * so the AI can never modify budgets or goals on its own.
 */
sealed class Proposal {
    abstract val id: String
    abstract val rationale: String

    data class BudgetChanges(
        override val id: String,
        val changes: List<BudgetChange>,
        override val rationale: String,
    ) : Proposal()

    data class NewGoal(
        override val id: String,
        val name: String,
        val type: GoalType,
        val target: Long,
        val monthlyContribution: Long,
        val targetDate: LocalDate?,
        override val rationale: String,
    ) : Proposal()
}

data class BudgetChange(val categoryId: String, val categoryName: String, val monthlyAmount: Long, val previousAmount: Long?)
