package com.nexusbudget.app.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nexusbudget.app.AppContainer
import com.nexusbudget.app.BudgetRequest
import com.nexusbudget.app.GoalDraft
import com.nexusbudget.app.PlansSection
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.SeverityIcon
import com.nexusbudget.app.ui.navigateToTab
import com.nexusbudget.core.engine.Recommendation
import com.nexusbudget.core.engine.RecommendationAction
import com.nexusbudget.core.model.Categories

/** Sends the user exactly where a recommendation's button says it will. */
fun openRecommendation(rec: Recommendation, nav: NavHostController, container: AppContainer) {
    fun budget(request: BudgetRequest) {
        container.budgetRequest.value = request
        nav.navigateToTab(Routes.BUDGET)
    }
    fun plans(section: PlansSection) {
        container.plansRequest.value = section
        nav.navigateToTab(Routes.PLANS)
    }
    when (rec.action) {
        RecommendationAction.DEBT_PLAN -> plans(PlansSection.DEBT_PLAN)
        RecommendationAction.GOALS -> {
            val goalId = rec.goalId
            val newType = rec.newGoalType
            when {
                goalId != null -> nav.navigate(Routes.goal(goalId))
                newType != null -> {
                    container.goalDraft.value = GoalDraft(newType, rec.newGoalTarget)
                    nav.navigate(Routes.goal())
                }
                else -> plans(PlansSection.GOALS)
            }
        }
        RecommendationAction.BUDGET -> budget(rec.categoryId?.let { BudgetRequest.EditCategory(it) } ?: BudgetRequest.ShowTab(0))
        RecommendationAction.BUILD_BUDGET -> budget(BudgetRequest.BuildBudget)
        RecommendationAction.TRANSACTIONS -> budget(BudgetRequest.ShowTab(1, categoryFilter = rec.categoryId ?: Categories.UNCATEGORIZED))
        RecommendationAction.RECURRING -> budget(BudgetRequest.ShowTab(2))
        RecommendationAction.ACCOUNTS -> nav.navigateToTab(Routes.ACCOUNTS)
        RecommendationAction.ASSISTANT -> askAssistant(rec.assistantPrompt ?: rec.title, nav, container)
        RecommendationAction.NONE -> Unit
    }
}

fun askAssistant(prompt: String, nav: NavHostController, container: AppContainer) {
    container.pendingAssistantPrompt.value = prompt
    nav.navigateToTab(Routes.ASSISTANT)
}

@Composable
fun RecommendationCard(
    rec: Recommendation,
    onAction: () -> Unit,
    onAskAi: (() -> Unit)?,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val actionable = rec.action != RecommendationAction.NONE
    NexusCard(modifier = modifier, onClick = if (actionable) onAction else null) {
        Row(verticalAlignment = Alignment.Top) {
            SeverityIcon(rec.severity)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    rec.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 3 else Int.MAX_VALUE,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Dismiss") }
            }
        }
        if (!compact) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (onAskAi != null) {
                    TextButton(onClick = onAskAi) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ask AI")
                    }
                }
                if (actionable) FilledTonalButton(onClick = onAction) { Text(rec.actionLabel) }
            }
        }
    }
}
