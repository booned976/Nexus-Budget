package com.nexusbudget.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusbudget.app.data.FinanceState
import com.nexusbudget.app.ui.charts.BarItem
import com.nexusbudget.app.ui.charts.BarList
import com.nexusbudget.app.ui.charts.ColumnChart
import com.nexusbudget.app.ui.charts.ColumnGroup
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.money
import com.nexusbudget.app.ui.label
import com.nexusbudget.app.ui.theme.LocalChartColors
import com.nexusbudget.app.ui.theme.LocalHideAmounts
import com.nexusbudget.core.engine.BudgetEngine
import com.nexusbudget.core.engine.CashFlowAnalyzer
import com.nexusbudget.core.model.Money
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TrendsTab(state: FinanceState, onCategory: (String) -> Unit) {
    val colors = LocalChartColors.current
    val hide = LocalHideAmounts.current
    val trend = remember(state.transactions, state.categories) {
        CashFlowAnalyzer.trend(state.transactions, state.categories, state.picture.today, 6)
    }
    var useLastMonth by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.from(state.picture.today).let { if (useLastMonth) it.minusMonths(1) else it }
    val byCategory = remember(state.transactions, month) {
        BudgetEngine.spendingByCategory(month, state.transactions, state.categories).filterValues { it > 0 }.toList().sortedByDescending { it.second }
    }
    val monthFormat = DateTimeFormatter.ofPattern("MMM")

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NexusCard {
                Text("Income vs. spending", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (hide) {
                    Text("Amounts are hidden.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    ColumnChart(
                        groups = trend.map { ColumnGroup(it.month.format(monthFormat), listOf(it.income / 100.0, it.spending / 100.0)) },
                        series = listOf("Income" to colors.series1, "Spending" to colors.series2),
                        valueLabel = { Money.formatCompact(Math.round(it * 100)) },
                        description = trend.joinToString("; ") {
                            "${it.month.label()}: income ${Money.format(it.income, showCents = false)}, spending ${Money.format(it.spending, showCents = false)}"
                        },
                    )
                }
                val stats = state.picture.stats
                stats.savingsRate?.let { rate ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "On average you keep ${(rate * 100).roundToInt()}% of your income " +
                            "(${money(stats.avgMonthlyNet)} a month).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            NexusCard {
                Text("Where the money went", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !useLastMonth, onClick = { useLastMonth = false }, label = { Text("This month") })
                    FilterChip(selected = useLastMonth, onClick = { useLastMonth = true }, label = { Text("Last month") })
                }
                Spacer(Modifier.height(8.dp))
                if (byCategory.isEmpty()) {
                    Text("No spending yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val total = byCategory.sumOf { it.second }
                    Text("Total ${money(total)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    BarList(
                        items = byCategory.map { (id, spent) ->
                            val category = state.categories[id]
                            BarItem(id, category.name, spent, "${money(spent)} · ${(spent * 100 / total.coerceAtLeast(1))}%", category.icon)
                        },
                        onClick = { onCategory(it.key) },
                    )
                }
            }
        }
    }
}
