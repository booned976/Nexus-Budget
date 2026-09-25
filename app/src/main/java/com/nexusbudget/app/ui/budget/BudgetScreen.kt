package com.nexusbudget.app.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.data.FinanceState
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.components.CategoryPickerDialog
import com.nexusbudget.app.ui.components.HealthLabel
import com.nexusbudget.app.ui.components.MoneyField
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.ProgressMeter
import com.nexusbudget.app.ui.components.SectionHeader
import com.nexusbudget.app.ui.components.centsToInput
import com.nexusbudget.app.ui.components.healthColor
import com.nexusbudget.app.ui.components.inputToCents
import com.nexusbudget.app.ui.components.money
import com.nexusbudget.app.ui.label
import com.nexusbudget.app.ui.theme.LocalChartColors
import com.nexusbudget.core.engine.BudgetEngine
import com.nexusbudget.core.engine.BudgetPlan
import com.nexusbudget.core.engine.BudgetPlanner
import com.nexusbudget.core.engine.CategoryBudgetStatus
import com.nexusbudget.core.model.Category
import com.nexusbudget.core.model.CategoryKind
import kotlinx.coroutines.launch
import java.time.YearMonth

private val tabTitles = listOf("Budget", "Transactions", "Recurring", "Trends")

@Composable
fun BudgetScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val requested by container.requestedBudgetTab.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(requested) {
        requested?.let {
            tab = it
            container.requestedBudgetTab.value = null
        }
    }

    ScreenScaffold(title = "Budget", topLevel = true, snackbarHostState = snackbar) { padding ->
        val current = state ?: return@ScreenScaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, maxLines = 1) })
                }
            }
            when (tab) {
                0 -> BudgetTab(current, onShowTransactions = { categoryFilter = it; tab = 1 })
                1 -> TransactionsTab(nav, current, categoryFilter, onClearFilter = { categoryFilter = null }, snackbar = snackbar)
                2 -> RecurringTab(current)
                else -> TrendsTab(current, onCategory = { categoryFilter = it; tab = 1 })
            }
        }
    }
}

@Composable
private fun BudgetTab(state: FinanceState, onShowTransactions: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(state.picture.today).toString()) }
    val selectedMonth = YearMonth.parse(month)
    val summary = remember(state, month) {
        if (selectedMonth == state.picture.budget.month) {
            state.picture.budget
        } else {
            BudgetEngine.summarize(selectedMonth, state.picture.today, state.transactions, state.categories, state.budgets, state.picture.budget.expectedIncome)
        }
    }
    var editing by remember { mutableStateOf<Category?>(null) }
    var picking by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<BudgetPlan?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = selectedMonth.minusMonths(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(selectedMonth.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(
                    onClick = { month = selectedMonth.plusMonths(1).toString() },
                    enabled = selectedMonth.isBefore(YearMonth.from(state.picture.today)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }
        item {
            NexusCard {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Left to budget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(
                            summary.leftToBudget,
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (summary.leftToBudget < 0) LocalChartColors.current.negativeText else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Expected income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(summary.expectedIncome, style = MaterialTheme.typography.titleSmall)
                    }
                }
                Text(
                    if (summary.leftToBudget >= 0) "Give every dollar a job: assign it to a category, a goal or extra debt payments."
                    else "You've budgeted more than you expect to earn. Trim a category to balance it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Mini("Budgeted", summary.totalBudgeted)
                    Mini("Spent", summary.budgetedSpent)
                    Mini("Remaining", summary.categories.sumOf { it.available })
                }
                if (summary.hasBudgets) {
                    Spacer(Modifier.height(10.dp))
                    val limit = summary.categories.sumOf { it.limit }.coerceAtLeast(1)
                    ProgressMeter(summary.budgetedSpent.toFloat() / limit)
                    Spacer(Modifier.height(4.dp))
                    Text("${(summary.monthProgress * 100).toInt()}% of the month has passed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!summary.hasBudgets) {
            item {
                NexusCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Start with a budget built from your real spending", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "We look at your last few months, then trim wants so at least 20% of income is left for savings and debt payoff. You can change any amount.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { plan = BudgetPlanner.suggest(state.picture.stats, state.categories, state.picture.recurring) }) {
                        Text("Build my budget")
                    }
                }
            }
        }
        val groups = summary.categories.groupBy { it.category.group }
        groups.forEach { (group, statuses) ->
            item(key = "group-$group") {
                NexusCard(contentPadding = 12) {
                    Text(group, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
                    statuses.forEachIndexed { index, status ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        BudgetRow(status, onClick = { editing = status.category })
                    }
                }
            }
        }
        if (summary.unbudgeted.isNotEmpty()) {
            item { SectionHeader("Spending without a budget") }
            items(summary.unbudgeted, key = { "unbudgeted-${it.category.id}" }) { status ->
                NexusCard(onClick = { editing = status.category }, contentPadding = 12) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(status.category.icon)
                        Spacer(Modifier.width(10.dp))
                        Text(status.category.name, modifier = Modifier.weight(1f))
                        MoneyText(status.spent, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Text("Set budget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add a category budget")
            }
        }
    }

    editing?.let { category ->
        val target = state.budgets.firstOrNull { it.categoryId == category.id }
        BudgetEditDialog(
            category = category,
            currentAmount = target?.monthlyAmount,
            rollover = target?.rollover ?: false,
            average = state.picture.stats.avgSpendingByCategory[category.id],
            onSave = { amount, rollover ->
                scope.launch { container.finance.setBudget(category.id, amount, rollover) }
                editing = null
            },
            onRemove = if (target != null) ({ scope.launch { container.finance.removeBudget(category.id) }; editing = null }) else null,
            onShowTransactions = { editing = null; onShowTransactions(category.id) },
            onDismiss = { editing = null },
        )
    }
    if (picking) {
        CategoryPickerDialog(
            categories = state.categories,
            selectedId = null,
            kinds = setOf(CategoryKind.EXPENSE),
            onPick = { picking = false; editing = it },
            onDismiss = { picking = false },
        )
    }
    plan?.let { suggested ->
        BudgetPlanDialog(
            plan = suggested,
            state = state,
            onApply = {
                scope.launch { container.finance.setBudgets(suggested.suggestions.associate { it.categoryId to it.amount }) }
                plan = null
            },
            onDismiss = { plan = null },
        )
    }
}

@Composable
private fun Mini(label: String, cents: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(cents, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun BudgetRow(status: CategoryBudgetStatus, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status.category.icon)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(status.category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${money(status.spent)} of ${money(status.limit)}" + if (status.rolloverIn != 0L) " (incl. ${money(status.rolloverIn, signed = true)} rollover)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (status.available >= 0) "${money(status.available)} left" else "${money(-status.available)} over",
                    style = MaterialTheme.typography.bodyMedium,
                )
                HealthLabel(status.health)
            }
        }
        Spacer(Modifier.height(6.dp))
        ProgressMeter(status.progress, color = healthColor(status.health))
    }
}

@Composable
private fun BudgetEditDialog(
    category: Category,
    currentAmount: Long?,
    rollover: Boolean,
    average: Long?,
    onSave: (Long, Boolean) -> Unit,
    onRemove: (() -> Unit)?,
    onShowTransactions: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(centsToInput(currentAmount ?: average?.let { com.nexusbudget.core.model.Money.roundUp(it, 10_00) })) }
    var roll by remember { mutableStateOf(rollover) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${category.icon} ${category.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(amount, { amount = it }, "Monthly budget", supportingText = average?.let { "You usually spend ${money(it)} a month" })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Roll over")
                        Text("Carry leftover money (or overspending) into next month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(roll, { roll = it })
                }
                TextButton(onClick = onShowTransactions) { Text("View transactions") }
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove budget", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { inputToCents(amount)?.let { onSave(it, roll) } }, enabled = inputToCents(amount) != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BudgetPlanDialog(plan: BudgetPlan, state: FinanceState, onApply: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suggested budget") },
        text = {
            Column {
                Text(
                    "Based on ${money(plan.income)} monthly income: ${money(plan.needs)} needs, ${money(plan.wants)} wants, " +
                        "${money(plan.savings)} left for savings and debt.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(plan.suggestions, key = { it.categoryId }) { s ->
                        val category = state.categories[s.categoryId]
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.icon)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(category.name, style = MaterialTheme.typography.bodyMedium)
                                Text(s.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MoneyText(s.amount, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { FilledTonalButton(onClick = onApply, enabled = plan.suggestions.isNotEmpty()) { Text("Use this budget") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
