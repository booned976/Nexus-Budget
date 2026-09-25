package com.nexusbudget.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.budget.TransactionRow
import com.nexusbudget.app.ui.components.ListRow
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.Pill
import com.nexusbudget.app.ui.components.ProgressMeter
import com.nexusbudget.app.ui.components.SectionHeader
import com.nexusbudget.app.ui.components.money
import com.nexusbudget.app.ui.label
import com.nexusbudget.app.ui.theme.LocalChartColors
import com.nexusbudget.core.engine.Debt
import com.nexusbudget.core.engine.DebtPayoffEngine
import com.nexusbudget.core.engine.PayoffStrategy
import com.nexusbudget.core.model.Account
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun AccountDetailScreen(nav: NavHostController, accountId: String) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val holdings by remember(accountId) { container.finance.holdings(accountId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val account = state?.accounts?.firstOrNull { it.id == accountId }

    ScreenScaffold(
        title = account?.name ?: "Account",
        onBack = { nav.popBackStack() },
        actions = {
            if (account != null) {
                IconButton(onClick = { nav.navigate(Routes.editAccount(account.id)) }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit account") }
            }
        },
    ) { padding ->
        val current = state ?: return@ScreenScaffold
        if (account == null) {
            Text("This account no longer exists.", modifier = Modifier.padding(padding).padding(16.dp))
            return@ScreenScaffold
        }
        val transactions = current.transactions.filter { it.accountId == account.id }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BalanceCard(account) }
            if (account.type.isLiability && account.balance > 0) item { DebtCard(account) }
            if (holdings.isNotEmpty()) {
                item {
                    NexusCard {
                        Text("Holdings", style = MaterialTheme.typography.titleMedium)
                        holdings.forEach { h ->
                            ListRow(
                                title = h.ticker ?: h.name,
                                subtitle = "${if (h.ticker != null) h.name + " · " else ""}${"%,.4f".format(h.quantity).trimEnd('0').trimEnd('.')} shares",
                                trailing = {
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                        MoneyText(h.value, style = MaterialTheme.typography.bodyLarge)
                                        h.gain?.let { gain ->
                                            Text(
                                                money(gain, signed = true),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (gain >= 0) LocalChartColors.current.positiveText else LocalChartColors.current.negativeText,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (account.isManual) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { nav.navigate(Routes.addTransaction(account.id)) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add transaction")
                        }
                        OutlinedButton(onClick = { nav.navigate(Routes.import(account.id)) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import CSV")
                        }
                    }
                }
            }
            item { SectionHeader("Transactions") }
            if (transactions.isEmpty()) {
                item {
                    Text(
                        if (account.isManual) "No transactions yet. Add one or import a CSV from your bank's website." else "No transactions synced yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(transactions.take(300), key = { it.id }) { txn ->
                TransactionRow(txn, current.categories, onClick = { nav.navigate(Routes.transaction(txn.id)) })
            }
        }
    }
}

@Composable
private fun BalanceCard(account: Account) {
    NexusCard {
        Row {
            Pill(account.type.label)
            if (account.isManual) {
                Spacer(Modifier.width(6.dp))
                Pill("Tracked manually")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (account.type.isLiability) "Amount owed" else "Balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(account.balance, style = MaterialTheme.typography.headlineMedium, showCents = true)
        account.available?.takeIf { it != account.balance }?.let {
            Text("Available ${money(it, showCents = true)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val details = listOfNotNull(account.institution, account.mask?.let { "Ending in $it" }).joinToString(" · ")
        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        account.lastUpdated?.let {
            val time = LocalDateTime.ofInstant(it, ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
            Text("Updated $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DebtCard(account: Account) {
    val debt = Debt.fromAccounts(listOf(account)).firstOrNull() ?: return
    val minimumOnly = remember(debt) { DebtPayoffEngine.simulate(listOf(debt), PayoffStrategy.MINIMUM_ONLY) }
    val withExtra = remember(debt) { DebtPayoffEngine.simulate(listOf(debt), PayoffStrategy.AVALANCHE, 100_00) }
    val colors = LocalChartColors.current
    NexusCard {
        Text("Debt details", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("APR", if (account.apr != null) "${"%.2f".format(account.apr)}%" else "~${"%.0f".format(debt.apr)}% (est.)", Modifier.weight(1f))
            Stat("Minimum", money(debt.minimumPayment) + if (debt.minimumEstimated) " (est.)" else "", Modifier.weight(1f))
            Stat("Due", account.paymentDueDay?.let { "Day $it" } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text("Interest costs about ${money(debt.monthlyInterest)} a month.", style = MaterialTheme.typography.bodyMedium)
        account.utilization?.let { utilization ->
            Spacer(Modifier.height(12.dp))
            Text("Credit used: ${(utilization * 100).roundToInt()}% of ${money(account.creditLimit ?: 0)}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            ProgressMeter(
                utilization.toFloat(),
                color = when {
                    utilization >= 0.5 -> colors.critical
                    utilization >= 0.3 -> colors.warning
                    else -> colors.series1
                },
            )
            Text("Under 30% is best for your credit score.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        if (minimumOnly.feasible) {
            Text(
                "Paying the minimum: paid off ${minimumOnly.debtFreeMonth?.label()} (${money(minimumOnly.totalInterest)} interest).",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("The minimum payment doesn't cover the interest, so this balance won't shrink on its own.", style = MaterialTheme.typography.bodyMedium, color = colors.negativeText)
        }
        if (withExtra.feasible) {
            Text(
                "With \$100 extra a month: ${withExtra.debtFreeMonth?.label()} (${money(withExtra.totalInterest)} interest).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
