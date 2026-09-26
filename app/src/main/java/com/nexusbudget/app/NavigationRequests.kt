package com.nexusbudget.app

import com.nexusbudget.core.model.GoalType

/** Something another screen (a recommendation, a Home card) asked the Budget tab to show. */
sealed interface BudgetRequest {
    /** A sub-tab (0 budget, 1 transactions, 2 recurring, 3 trends), optionally filtering transactions to a category. */
    data class ShowTab(val tab: Int, val categoryFilter: String? = null) : BudgetRequest

    /** One category's budget editor, with the reasoning behind its amount. */
    data class EditCategory(val categoryId: String) : BudgetRequest

    /** The "build my budget" suggestion. */
    data object BuildBudget : BudgetRequest
}

/** A section of the Plans tab to scroll to. */
enum class PlansSection { DEBT_PLAN, GOALS }

/** A new goal to start with suggested values. */
data class GoalDraft(val type: GoalType, val target: Long?)
