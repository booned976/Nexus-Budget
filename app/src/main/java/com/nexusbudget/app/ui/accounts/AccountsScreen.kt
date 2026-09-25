package com.nexusbudget.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.data.FinanceState
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.charts.ChartSeries
import com.nexusbudget.app.ui.charts.LineChart
import com.nexusbudget.app.ui.components.IconBadge
import com.nexusbudget.app.ui.components.ListRow
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.accountIcon
import com.nexusbudget.app.ui.components.money
import com.nexusbudget.app.ui.short
import com.nexusbudget.app.ui.theme.LocalChartColors
import com.nexusbudget.app.ui.theme.LocalHideAmounts
import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.AccountGroup
import com.nexusbudget.core.model.Money
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val syncing by container.connections.syncing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val sync: () -> Unit = {
        scope.launch {
            val summary = runCatching { container.connections.syncAll() }.getOrNull()
            val message = when {
                summary == null -> "Sync failed. Check your connection."
                summary.connections == 0 -> "No bank connections yet. Add one to sync automatically."
                summary.problems.isNotEmpty() -> summary.problems.first()
                else -> "Up to date · ${summary.newTransactions} new transactions"
            }
            snackbar.showSnackbar(message)
        }
    }

    ScreenScaffold(
        title = "Accounts",
        topLevel = true,
        snackbarHostState = snackbar,
        actions = { IconButton(onClick = sync) { Icon(Icons.Outlined.Sync, contentDescription = "Sync now") } },
        floatingActionButton = {
            // The extended button hides its text from screen readers, so the icon carries the label.
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.CONNECT) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = "Add account") },
                text = { Text("Add account") },
            )
        },
    ) { padding ->
        val current = state ?: return@ScreenScaffold
        PullToRefreshBox(isRefreshing = syncing, onRefresh = sync, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { NetWorthHeader(current) }
                val visible = current.accounts.filter { !it.isHidden }
                AccountGroup.entries.forEach { group ->
                    val accounts = visible.filter { it.type.group == group }.sortedByDescending { it.balance }
                    if (accounts.isEmpty()) return@forEach
                    item(key = group.name) {
                        GroupCard(group, accounts, onClick = { nav.navigate(Routes.account(it.id)) })
                    }
                }
                val hidden = current.accounts.filter { it.isHidden }
                if (hidden.isNotEmpty()) {
                    item(key = "hidden") {
                        NexusCard {
                            Text("Hidden accounts", style = MaterialTheme.typography.titleSmall)
                            hidden.forEach { account ->
                                ListRow(title = account.name, subtitle = account.type.label, onClick = { nav.navigate(Routes.account(account.id)) })
                            }
                        }
                    }
                }
                current.settings.lastSync?.let { last ->
                    item {
                        Text(
                            "Last synced ${lastSyncedText(Instant.ofEpochMilli(last))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun lastSyncedText(time: Instant): String {
    val minutes = Duration.between(time, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

@Composable
private fun NetWorthHeader(state: FinanceState) {
    val worth = state.picture.netWorth
    val colors = LocalChartColors.current
    val hide = LocalHideAmounts.current
    NexusCard {
        Text("Net worth", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(worth.total, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Assets ${money(worth.assets)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Debts ${money(worth.liabilities)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val history = state.snapshots.takeLast(90)
        if (history.size >= 2 && !hide) {
            Spacer(Modifier.height(12.dp))
            LineChart(
                series = listOf(ChartSeries("Net worth", history.map { it.netWorth / 100.0 }, colors.series1)),
                xLabel = { history[it].date.short() },
                valueLabel = { Money.formatCompact(Math.round(it * 100)) },
                height = 160,
                description = "Net worth over time, from ${Money.format(history.first().netWorth, showCents = false)} " +
                    "to ${Money.format(history.last().netWorth, showCents = false)}",
            )
        }
    }
}

@Composable
private fun GroupCard(group: AccountGroup, accounts: List<Account>, onClick: (Account) -> Unit) {
    NexusCard(contentPadding = 12) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(group.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val total = accounts.sumOf { it.balance }
            MoneyText(if (group.isLiability) -total else total, style = MaterialTheme.typography.titleMedium)
        }
        accounts.forEachIndexed { index, account ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AccountRow(account, onClick = { onClick(account) })
        }
    }
}

@Composable
fun AccountRow(account: Account, onClick: () -> Unit) {
    val subtitle = buildList {
        account.institution?.let { add(it) }
        account.mask?.let { add("••$it") }
        if (account.isManual) add("Manual")
        account.apr?.takeIf { account.type.isLiability }?.let { add("${"%.2f".format(it)}% APR") }
    }.joinToString(" · ")
    ListRow(
        title = account.name,
        subtitle = subtitle.ifBlank { account.type.label },
        leading = { IconBadge(accountIcon(account.type), size = 36) },
        trailing = {
            MoneyText(
                if (account.type.isLiability) -account.balance else account.balance,
                style = MaterialTheme.typography.bodyLarge,
                showCents = true,
            )
        },
        onClick = onClick,
    )
}
