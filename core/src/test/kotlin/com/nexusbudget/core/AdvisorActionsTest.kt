package com.nexusbudget.core

import com.nexusbudget.core.demo.DemoData
import com.nexusbudget.core.engine.FinancialPicture
import com.nexusbudget.core.engine.RecommendationAction
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.GoalType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every recommendation button needs somewhere specific to go. */
class AdvisorActionsTest {

    private val today = LocalDate.of(2026, 9, 18)
    private val demo = DemoData.generate(today)
    private val categories = CategoryIndex(Categories.defaults)

    @Test
    fun `goal recommendations point at a goal or a prefilled new goal`() {
        val picture = FinancialPicture.compute(today, demo.accounts, demo.transactions, categories, demo.budgets, demo.goals, demo.snapshots)
        val behind = picture.recommendations.first { it.title.startsWith("Summer trip") }
        assertEquals("demo-goal-trip", behind.goalId)

        val noGoals = FinancialPicture.compute(today, demo.accounts, demo.transactions, categories, demo.budgets, emptyList(), demo.snapshots)
        val efund = noGoals.recommendations.first { it.id.startsWith("efund") }
        assertEquals(GoalType.EMERGENCY_FUND, efund.newGoalType)
        assertNotNull(efund.newGoalTarget)
    }

    @Test
    fun `budget setup opens the budget builder and every action has a label`() {
        val picture = FinancialPicture.compute(today, demo.accounts, demo.transactions, categories, emptyList(), demo.goals, demo.snapshots)
        val setup = picture.recommendations.first { it.id == "budget-setup" }
        assertEquals(RecommendationAction.BUILD_BUDGET, setup.action)
        picture.recommendations.forEach { rec ->
            assertTrue(rec.action == RecommendationAction.NONE || rec.actionLabel.isNotBlank(), rec.toString())
            if (rec.actionLabel == "Set a budget" || rec.actionLabel == "Adjust budget") assertNotNull(rec.categoryId, rec.toString())
        }
    }
}
