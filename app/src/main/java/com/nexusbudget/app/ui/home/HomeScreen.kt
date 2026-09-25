package com.nexusbudget.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.data.ConnectionStatus
import com.nexusbudget.app.data.FinanceState
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.charts.Sparkline
import com.nexusbudget.app.ui.components.EmojiBadge
import com.nexusbudget.app.ui.components.EmptyState
import com.nexusbudget.app.ui.components.IconBadge
import com.nexusbudget.app.ui.components.ListRow
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.ProgressMeter
import com.nexusbudget.app.ui.components.SectionHeader
import com.nexusbudget.app.ui.components.amountColor
import com.nexusbudget.app.ui.components.groupIcon
import com.nexusbudget.app.ui.components.healthColor
import com.nexusbudget.app.ui.components.money
import com.nexusbudget.app.ui.navigateToTab
import com.nexusbudget.app.ui.plans.RecommendationCard
import com.nexusbudget.app.ui.plans.askAssistant
import com.nexusbudget.app.ui.plans.openRecommendation
import com.nexusbudget.app.ui.relative
import com.nexusbudget.app.ui.short
import com.nexusbudget.app.ui.theme.HeroNumber
import com.nexusbudget.app.ui.theme.LocalChartColors
import com.nexusbudget.core.model.AccountGroup
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val syncing by container.connections.syncing.collectAsStateWithLifecycle()
    val connections by container.connections.connections.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    ScreenScaffold(
        title = greeting,
        topLevel = true,
        actions = {
            val hidden = state?.settings?.hideAmounts == true
            IconButton(onClick = { scope.launch { container.settings.setHideAmounts(!hidden) } }) {
                Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (hidden) "Show amounts" else "Hide amounts")
            }
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        },
    ) { padding ->
        val current = state
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { scope.launch { runCatching { container.connections.syncAll() } } },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (current == null) return@PullToRefreshBox
            if (current.accounts.isEmpty()) {
                WelcomeEmpty(
                    onConnect = { nav.navigate(Routes.CONNECT) },
                    onDemo = { scope.launch { container.finance.loadDemo() } },
                )
                return@PullToRefreshBox
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (current.settings.demoMode) {
                    item {
                        Banner(
                            text = "You're exploring demo data. Connect your own accounts when you're ready.",
                            action = "Connect",
                            onAction = { nav.navigate(Routes.CONNECT) },
                        )
                    }
                }
                connections.filter { it.status == ConnectionStatus.NEEDS_REAUTH }.forEach { connection ->
                    item(key = "reauth-${connection.id}") {
                        Banner(
                            text = "${connection.displayName} needs you to reconnect.",
                            action = "Fix",
                            onAction = { nav.navigate(Routes.SETTINGS) },
                            warning = true,
                        )
                    }
                }
                item { SafeToSpendCard(current) }
                item { NetWorthCard(current, onClick = { nav.navigateToTab(Routes.ACCOUNTS) }) }
                item { AccountGroupsGrid(current, onClick = { nav.navigateToTab(Routes.ACCOUNTS) }) }
                item { BudgetCard(current, onClick = { container.requestedBudgetTab.value = 0; nav.navigateToTab(Routes.BUDGET) }) }

                val recs = current.picture.recommendations.take(3)
                if (recs.isNotEmpty()) {
                    item { SectionHeader("For you", actionLabel = "See plans", onAction = { nav.navigateToTab(Routes.PLANS) }) }
                    items(recs, key = { "rec-${it.id}" }) { rec ->
                        RecommendationCard(
                            rec = rec,
                            onAction = { openRecommendation(rec, nav, container) },
                            onAskAi = rec.assistantPrompt?.let { prompt -> { askAssistant(prompt, nav, container) } },
                            onDismiss = { scope.launch { container.settings.dismissRecommendation(rec.id) } },
                            compact = true,
                        )
                    }
                }

                item { UpcomingBillsCard(current, onClick = { container.requestedBudgetTab.value = 2; nav.navigateToTab(Routes.BUDGET) }) }
                if (current.picture.goals.isNotEmpty()) item { GoalsRow(current, onClick = { nav.navigate(Routes.goal(it)) }) }
                item {
                    RecentTransactions(
                        current,
                        onClick = { nav.navigate(Routes.transaction(it)) },
                        onSeeAll = { container.requestedBudgetTab.value = 1; nav.navigateToTab(Routes.BUDGET) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun WelcomeEmpty(onConnect: () -> Unit, onDemo: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        EmptyState(
            icon = Icons.Outlined.AccountBalanceWallet,
            title = "Let's see your whole picture",
            body = "Import a statement file from your bank, card, brokerage or loan website, add accounts by hand, " +
                "or set up automatic sync. Everything stays on this phone.",
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConnect) { Text("Add an account") }
                OutlinedButton(onClick = onDemo) { Text("Explore with demo data") }
            }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit, warning: Boolean = false) {
    NexusCard(contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (warning) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = LocalChartColors.current.serious)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SafeToSpendCard(state: FinanceState) {
    val safe = state.picture.safeToSpend
    NexusCard {
        Text("Safe to spend", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(safe.amount, style = HeroNumber, color = if (safe.amount < 0) LocalChartColors.current.negativeText else MaterialTheme.colorScheme.onSurface)
        val until = if (safe.untilPayday) "until payday (${safe.until.plusDays(1).short()})" else "through ${safe.until.short()}"
        Text(
            "${money(safe.perDay)}/day $until",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Breakdown("Cash", safe.spendableCash)
            Breakdown("Bills due", -safe.billsTotal)
            Breakdown("For goals", -safe.goalSetAside)
        }
    }
}

@Composable
private fun Breakdown(label: String, cents: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(cents, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun NetWorthCard(state: FinanceState, onClick: () -> Unit) {
    val worth = state.picture.netWorth
    val change = state.picture.netWorthChange30d
    val colors = LocalChartColors.current
    NexusCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Net worth", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(worth.total, style = MaterialTheme.typography.headlineMedium)
                if (change != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val up = change >= 0
                        Icon(
                            if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = if (up) "Up" else "Down",
                            tint = if (up) colors.positiveText else colors.negativeText,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${money(change, signed = true)} in 30 days",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (up) colors.positiveText else colors.negativeText,
                        )
                    }
                }
            }
            val history = state.snapshots.takeLast(12).map { it.netWorth / 100.0 }
            if (history.size >= 2) Sparkline(history, Modifier.width(96.dp).height(44.dp))
        }
    }
}

@Composable
private fun AccountGroupsGrid(state: FinanceState, onClick: () -> Unit) {
    val groups = listOf(AccountGroup.CASH, AccountGroup.CREDIT, AccountGroup.INVESTMENTS, AccountGroup.LOANS)
    val totals = state.picture.netWorth.byGroup
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { group ->
                    NexusCard(modifier = Modifier.weight(1f), onClick = onClick, contentPadding = 14) {
                        IconBadge(groupIcon(group), size = 32)
                        Spacer(Modifier.height(8.dp))
                        Text(group.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val total = totals[group] ?: 0
                        MoneyText(if (group.isLiability && total > 0) -total else total, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(state: FinanceState, onClick: () -> Unit) {
    val budget = state.picture.budget
    NexusCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${budget.daysLeft} days left", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        if (!budget.hasBudgets) {
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(budget.totalSpent, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(6.dp))
                Text("spent so far", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Set up a budget to track each category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            return@NexusCard
        }
        val limit = budget.categories.sumOf { it.limit }.coerceAtLeast(1)
        Row(verticalAlignment = Alignment.Bottom) {
            MoneyText(budget.budgetedSpent, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(6.dp))
            Text("of ${money(limit)} budgeted", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        val overall = budget.budgetedSpent.toFloat() / limit
        ProgressMeter(overall, color = if (overall > 1f) LocalChartColors.current.critical else LocalChartColors.current.series1)
        Spacer(Modifier.height(12.dp))
        budget.categories.sortedByDescending { it.progress }.take(3).forEach { status ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(status.category.icon)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(status.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (status.available >= 0) "${money(status.available)} left" else "${money(-status.available)} over",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ProgressMeter(status.progress, color = healthColor(status.health), height = 6)
                }
            }
        }
    }
}

@Composable
private fun UpcomingBillsCard(state: FinanceState, onClick: () -> Unit) {
    val bills = state.picture.upcomingBills.take(5)
    NexusCard(onClick = onClick) {
        Text("Upcoming bills", style = MaterialTheme.typography.titleMedium)
        if (bills.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "No bills detected in the next two weeks. Recurring charges appear here once there's enough history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        bills.forEach { bill ->
            ListRow(
                title = bill.name,
                subtitle = bill.date.relative(state.picture.today),
                trailing = { MoneyText(bill.amount, style = MaterialTheme.typography.bodyMedium, showCents = true) },
            )
        }
    }
}

@Composable
private fun GoalsRow(state: FinanceState, onClick: (String) -> Unit) {
    Column {
        SectionHeader("Goals")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.picture.goals, key = { it.goal.id }) { goal ->
                NexusCard(modifier = Modifier.width(200.dp), onClick = { onClick(goal.goal.id) }, contentPadding = 14) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.goal.type.icon)
                        Spacer(Modifier.width(6.dp))
                        Text(goal.goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${money(goal.saved)} of ${money(goal.goal.target)}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    ProgressMeter(goal.progress)
                    Spacer(Modifier.height(6.dp))
                    Text(goal.status.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RecentTransactions(state: FinanceState, onClick: (String) -> Unit, onSeeAll: () -> Unit) {
    val recent = state.transactions.take(6)
    if (recent.isEmpty()) return
    NexusCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
        recent.forEach { txn ->
            val category = state.categories[txn.categoryId]
            ListRow(
                title = txn.merchant,
                subtitle = "${category.name} · ${txn.date.relative(state.picture.today)}${if (txn.pending) " · Pending" else ""}",
                leading = { EmojiBadge(category.icon, size = 36) },
                trailing = { MoneyText(txn.amount, signed = true, showCents = true, style = MaterialTheme.typography.bodyMedium, color = amountColor(txn.amount)) },
                onClick = { onClick(txn.id) },
            )
        }
    }
}
