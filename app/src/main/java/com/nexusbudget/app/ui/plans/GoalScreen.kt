package com.nexusbudget.app.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.components.ConfirmDialog
import com.nexusbudget.app.ui.components.DatePickerSheet
import com.nexusbudget.app.ui.components.MoneyField
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.centsToInput
import com.nexusbudget.app.ui.components.inputToCents
import com.nexusbudget.app.ui.components.money
import com.nexusbudget.app.ui.long
import com.nexusbudget.core.engine.GoalEngine
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Goal
import com.nexusbudget.core.model.GoalType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun GoalScreen(nav: NavHostController, goalId: String) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var existing by remember { mutableStateOf<Goal?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GoalType.EMERGENCY_FUND) }
    var target by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf<LocalDate?>(null) }
    var linkedAccount by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addAmount by remember { mutableStateOf("") }

    LaunchedEffect(goalId) {
        if (goalId.isNotEmpty()) {
            container.finance.goal(goalId)?.let { g ->
                existing = g
                name = g.name
                type = g.type
                target = centsToInput(g.target)
                saved = centsToInput(g.saved)
                monthly = centsToInput(g.monthlyContribution)
                targetDate = g.targetDate
                linkedAccount = g.linkedAccountId
            }
        }
    }

    ScreenScaffold(title = if (goalId.isEmpty()) "New goal" else "Edit goal", onBack = { nav.popBackStack() }) { padding ->
        val current = state ?: return@ScreenScaffold
        // Suggest a sensible emergency fund target from real essential spending.
        val suggestedEmergency = GoalEngine.emergencyFundTarget(current.picture.stats.avgEssentialSpending)
        val draft = Goal(
            id = existing?.id ?: "new",
            name = name,
            type = type,
            target = inputToCents(target) ?: 0,
            saved = inputToCents(saved) ?: 0,
            monthlyContribution = inputToCents(monthly) ?: 0,
            targetDate = targetDate,
            linkedAccountId = linkedAccount,
        )
        val projection = GoalEngine.project(draft, current.accounts, current.picture.today)

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = {
                            type = option
                            if (name.isBlank() || GoalType.entries.any { it.label == name }) name = option.label
                            if (option == GoalType.EMERGENCY_FUND && target.isBlank() && suggestedEmergency > 0) target = centsToInput(suggestedEmergency)
                        },
                        label = { Text("${option.icon} ${option.label}") },
                    )
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            MoneyField(
                target, { target = it }, "Target amount",
                supportingText = if (type == GoalType.EMERGENCY_FUND && suggestedEmergency > 0) "3 months of your essential spending is about ${money(suggestedEmergency)}" else null,
            )
            MoneyField(monthly, { monthly = it }, "Monthly contribution")

            NexusCard(contentPadding = 12) {
                Text("Where is the money?", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().clickable { linkedAccount = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(linkedAccount == null, { linkedAccount = null })
                    Text("I'll track it myself")
                }
                current.accounts.filter { it.type == AccountType.SAVINGS || it.type.group == com.nexusbudget.core.model.AccountGroup.INVESTMENTS }.forEach { account ->
                    Row(Modifier.fillMaxWidth().clickable { linkedAccount = account.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(linkedAccount == account.id, { linkedAccount = account.id })
                        Text("${account.name} (${money(account.balance)})")
                    }
                }
            }
            if (linkedAccount == null) MoneyField(saved, { saved = it }, "Saved so far")

            NexusCard(onClick = { pickDate = true }, contentPadding = 12) {
                Text("Target date (optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(targetDate?.long() ?: "No deadline", style = MaterialTheme.typography.bodyLarge)
            }

            NexusCard(contentPadding = 12) {
                Text("Projection", style = MaterialTheme.typography.titleSmall)
                val text = when {
                    draft.target <= 0 -> "Enter a target to see a projection."
                    projection.remaining == 0L -> "You've already reached this goal."
                    projection.projectedDate != null -> "At ${money(draft.monthlyContribution)}/month you'll reach it around ${projection.projectedDate!!.long()}."
                    else -> "Add a monthly contribution to see when you'll reach it."
                }
                Text(text, style = MaterialTheme.typography.bodyMedium)
                projection.requiredMonthly?.let {
                    Text("To hit your date, save ${money(it)}/month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (existing != null && linkedAccount == null) {
                NexusCard(contentPadding = 12) {
                    Text("Add money", style = MaterialTheme.typography.titleSmall)
                    MoneyField(addAmount, { addAmount = it }, "Amount added")
                    OutlinedButton(
                        enabled = (inputToCents(addAmount) ?: 0) > 0,
                        onClick = {
                            val added = inputToCents(addAmount) ?: 0
                            saved = centsToInput((inputToCents(saved) ?: 0) + added)
                            addAmount = ""
                        },
                    ) { Text("Add to saved") }
                }
            }

            Button(
                enabled = name.isNotBlank() && draft.target > 0,
                onClick = {
                    scope.launch {
                        container.finance.saveGoal(
                            draft.copy(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                createdOn = existing?.createdOn ?: LocalDate.now(),
                            ),
                        )
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save goal") }
            if (existing != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete goal", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (pickDate) {
        DatePickerSheet(targetDate, onPick = { targetDate = it; pickDate = false }, onDismiss = { pickDate = false }, allowClear = true)
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete goal?", "Your saved money isn't affected, only the goal.", "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    existing?.let { container.finance.deleteGoal(it.id) }
                    nav.popBackStack()
                }
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
