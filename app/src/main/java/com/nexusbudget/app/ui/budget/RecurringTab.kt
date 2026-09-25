package com.nexusbudget.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusbudget.app.data.FinanceState
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.components.EmojiBadge
import com.nexusbudget.app.ui.components.EmptyState
import com.nexusbudget.app.ui.components.ListRow
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.Pill
import com.nexusbudget.app.ui.components.money
import com.nexusbudget.app.ui.relative
import com.nexusbudget.app.ui.short
import com.nexusbudget.app.ui.theme.LocalChartColors
import com.nexusbudget.core.engine.RecurringSeries
import com.nexusbudget.core.model.CategoryKind
import kotlinx.coroutines.launch

@Composable
fun RecurringTab(state: FinanceState) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<RecurringSeries?>(null) }
    val recurring = state.picture.recurring
    val income = recurring.filter { it.isIncome }
    val subscriptions = recurring.filter { it.isSubscription }
    val bills = recurring.filter { it.isBill && !it.isSubscription }
    val transfers = recurring.filter { it.kind == CategoryKind.TRANSFER && it.amount < 0 }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recurring.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.EventRepeat,
                    title = "No recurring charges yet",
                    body = "Bills, subscriptions and paychecks show up here automatically after they repeat a couple of times.",
                )
            }
        } else {
            item {
                NexusCard {
                    Text("Every month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Total("Bills", bills.sumOf { -it.monthlyAmount })
                        Total("Subscriptions", subscriptions.sumOf { -it.monthlyAmount })
                        Total("Income", income.sumOf { it.monthlyAmount })
                    }
                }
            }
        }
        section("Income", income, state) { selected = it }
        section("Bills", bills, state) { selected = it }
        section("Subscriptions", subscriptions, state) { selected = it }
        section("Transfers & savings", transfers, state) { selected = it }
        if (state.settings.dismissedRecurring.isNotEmpty()) {
            item {
                OutlinedButton(onClick = { scope.launch { container.settings.restoreRecurring() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show ${state.settings.dismissedRecurring.size} hidden items again")
                }
            }
        }
    }

    selected?.let { series ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(series.merchant) },
            text = {
                Column {
                    Text("${series.frequency.label} · about ${money(kotlin.math.abs(series.amount), showCents = true)}")
                    Text("Next expected ${series.nextDate.short()} · seen ${series.occurrences} times")
                    Text("About ${money(kotlin.math.abs(series.monthlyAmount) * 12)} a year")
                    if (series.amountVaries) Text("The amount changes from time to time.")
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { container.settings.dismissRecurring(series.key) }
                    selected = null
                }) { Text("Not recurring") }
            },
        )
    }
}

@Composable
private fun Total(label: String, cents: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(cents, style = MaterialTheme.typography.titleMedium)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<RecurringSeries>,
    state: FinanceState,
    onClick: (RecurringSeries) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "section-$title") {
        NexusCard(contentPadding = 12) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            items.forEachIndexed { index, series ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ListRow(
                    title = series.merchant,
                    subtitle = "${series.frequency.label} · next ${series.nextDate.relative(state.picture.today)}",
                    leading = { EmojiBadge(state.categories[series.categoryId].icon, size = 36) },
                    trailing = {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            MoneyText(kotlin.math.abs(series.amount), showCents = true, style = MaterialTheme.typography.bodyLarge)
                            series.priceChange?.takeIf { it > 0 }?.let {
                                Pill("+${money(it, showCents = true)}", color = LocalChartColors.current.serious.copy(alpha = 0.18f), contentColor = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(Modifier.width(2.dp))
                    },
                    onClick = { onClick(series) },
                )
            }
        }
    }
}
