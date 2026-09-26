package com.nexusbudget.app.ui.plans

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.PlansSection
import com.nexusbudget.app.data.FinanceState
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.charts.ChartSeries
import com.nexusbudget.app.ui.charts.LineChart
import com.nexusbudget.app.ui.components.EmptyState
import com.nexusbudget.app.ui.components.ListRow
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.ProgressMeter
import com.nexusbudget.app.ui.components.SectionHeader
import com.nexusbudget.app.ui.components.money
import com.nexusbudget.app.ui.label
import com.nexusbudget.app.ui.navigateToTab
import com.nexusbudget.app.ui.relative
import com.nexusbudget.app.ui.short
import com.nexusbudget.app.ui.theme.HeroNumber
import com.nexusbudget.app.ui.theme.LocalChartColors
import com.nexusbudget.app.ui.theme.LocalHideAmounts
import com.nexusbudget.core.engine.DebtPayoffEngine
import com.nexusbudget.core.engine.GoalStatus
import com.nexusbudget.core.engine.PayoffStrategy
import com.nexusbudget.core.model.Money
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PlansScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val request by container.plansRequest.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // A recommendation (here or on Home) asked for a section: scroll to it once the list is laid out.
    LaunchedEffect(request, state != null) {
        val section = request ?: return@LaunchedEffect
        val recommendations = state?.picture?.recommendations ?: return@LaunchedEffect
        val debtHeader = 1 + maxOf(recommendations.size, 1)
        val target = if (section == PlansSection.DEBT_PLAN) debtHeader else debtHeader + 2
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > target }
        listState.animateScrollToItem(target)
        container.plansRequest.value = null
    }

    ScreenScaffold(title = "Plans", topLevel = true) { padding ->
        val current = state ?: return@ScreenScaffold
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val recs = current.picture.recommendations
            item { SectionHeader("Recommendations") }
            if (recs.isEmpty()) {
                item {
                    Text(
                        "Nothing needs your attention right now. Nice work!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(recs, key = { it.id }) { rec ->
                RecommendationCard(
                    rec = rec,
                    onAction = { openRecommendation(rec, nav, container) },
                    onAskAi = { askAssistant(rec.assistantPrompt ?: "Help me with this: ${rec.title}. ${rec.message}", nav, container) },
                    onDismiss = { scope.launch { container.settings.dismissRecommendation(rec.id) } },
                )
            }

            // Keep these right after the recommendations: the scroll above counts on their positions.
            item(key = "debt-header") { SectionHeader("Debt payoff plan") }
            item(key = "debt-planner") { DebtPlanner(current, nav) }

            item(key = "goals-header") { SectionHeader("Goals", actionLabel = "New goal", onAction = { nav.navigate(Routes.goal()) }) }
            if (current.picture.goals.isEmpty()) {
                item {
                    NexusCard(onClick = { nav.navigate(Routes.goal()) }) {
                        Text("Set your first goal", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "An emergency fund, a trip, a down payment… Goals show how much to save each month and when you'll get there.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(current.picture.goals, key = { "goal-${it.goal.id}" }) { goal ->
                NexusCard(onClick = { nav.navigate(Routes.goal(goal.goal.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.goal.type.icon)
                        Spacer(Modifier.width(8.dp))
                        Text(goal.goal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(goal.status.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        MoneyText(goal.saved, style = MaterialTheme.typography.titleMedium)
                        Text(" of ${money(goal.goal.target)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressMeter(goal.progress, color = if (goal.status == GoalStatus.BEHIND) LocalChartColors.current.warning else LocalChartColors.current.series1)
                    Spacer(Modifier.height(6.dp))
                    val projected = goal.projectedDate
                    val detail = when {
                        goal.status == GoalStatus.COMPLETE -> "Goal reached"
                        projected != null -> "${money(goal.goal.monthlyContribution)}/month · reached around ${projected.short()} ${projected.year}"
                        else -> "Set a monthly amount to see when you'll get there"
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    goal.requiredMonthly?.takeIf { goal.status == GoalStatus.BEHIND }?.let {
                        Text("Needs ${money(it)}/month to hit the target date", style = MaterialTheme.typography.bodySmall, color = LocalChartColors.current.negativeText)
                    }
                }
            }

            item { SectionHeader("Cash flow, next 30 days") }
            item { ForecastCard(current) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtPlanner(state: FinanceState, nav: NavHostController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val debts = state.picture.debts
    if (debts.isEmpty()) {
        NexusCard {
            EmptyState(
                icon = Icons.Outlined.Celebration,
                title = "No debts to pay off",
                body = "If you have loans or cards that aren't connected, add them as accounts to build a payoff plan.",
            ) {
                OutlinedButton(onClick = { nav.navigate(Routes.CONNECT) }) { Text("Add a debt") }
            }
        }
        return
    }
    val strategy = state.settings.payoffStrategy.takeIf { it == PayoffStrategy.SNOWBALL } ?: PayoffStrategy.AVALANCHE
    var extra by remember { mutableFloatStateOf((state.settings.extraDebtPayment / 100).toFloat()) }
    LaunchedEffect(state.settings.extraDebtPayment) { extra = (state.settings.extraDebtPayment / 100).toFloat() }
    val extraCents = (extra.roundToLong() * 100)
    val start = YearMonth.from(state.picture.today)
    val plan = remember(debts, strategy, extraCents) { DebtPayoffEngine.simulate(debts, strategy, extraCents, start) }
    val baseline = remember(debts) { DebtPayoffEngine.simulate(debts, PayoffStrategy.MINIMUM_ONLY, 0, start) }
    val colors = LocalChartColors.current
    val hide = LocalHideAmounts.current
    val monthFormat = DateTimeFormatter.ofPattern("MMM yyyy")
    val maxExtra = maxOf(1000f, (state.picture.stats.avgMonthlyNet / 100).toFloat().coerceAtLeast(0f) * 2)

    NexusCard {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(PayoffStrategy.AVALANCHE, PayoffStrategy.SNOWBALL).forEachIndexed { index, option ->
                SegmentedButton(
                    selected = strategy == option,
                    onClick = { scope.launch { container.settings.setPayoffStrategy(option) } },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(option.label) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(strategy.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text("Extra each month: ${money(extraCents)}", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = extra.coerceIn(0f, maxExtra),
            onValueChange = { extra = (it / 25).roundToLong() * 25f },
            onValueChangeFinished = { scope.launch { container.settings.setExtraDebtPayment(extraCents) } },
            valueRange = 0f..maxExtra,
        )
        Text(
            "Total payment ${money(plan.monthlyPayment)}/month (minimums ${money(debts.sumOf { it.minimumPayment })} + extra)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (plan.feasible) {
            Text("Debt-free by", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(plan.debtFreeMonth?.label() ?: "", style = HeroNumber)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Interest paid", money(plan.totalInterest))
                if (baseline.feasible) {
                    Stat("Interest saved", money(baseline.totalInterest - plan.totalInterest))
                    Stat("Time saved", monthsText(baseline.months - plan.months))
                } else {
                    Stat("vs. minimums", "Never paid off")
                }
            }
        } else {
            Text(
                "At this payment the balances don't go down. Increase the extra payment to see a payoff date.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.negativeText,
            )
        }
        if (!hide && plan.feasible) {
            Spacer(Modifier.height(16.dp))
            val points = maxOf(plan.balanceTimeline.size, minOf(baseline.balanceTimeline.size, plan.balanceTimeline.size * 3))
            val step = ceil(points / 120.0).toInt().coerceAtLeast(1)
            fun sample(values: List<Long>) = (0 until points step step).map { i -> (values.getOrNull(i) ?: 0L) / 100.0 }
            LineChart(
                series = listOf(
                    ChartSeries("Your plan", sample(plan.balanceTimeline), colors.series1),
                    ChartSeries("Minimums only", sample(baseline.balanceTimeline), colors.series2),
                ),
                xLabel = { start.plusMonths((it * step).toLong()).format(monthFormat) },
                valueLabel = { Money.formatCompact((it * 100).roundToLong()) },
                includeZero = true,
                height = 170,
                description = "Remaining debt over time. Your plan reaches zero in ${plan.debtFreeMonth?.label()}.",
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Payoff order", style = MaterialTheme.typography.titleSmall)
        plan.debts.forEachIndexed { index, result ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListRow(
                title = "${result.order}. ${result.debt.name}",
                subtitle = "${money(result.debt.balance)} · ${"%.2f".format(result.debt.apr)}%" + if (result.debt.minimumEstimated) " · min. estimated" else "",
                trailing = { Text(result.payoffMonth?.format(monthFormat) ?: "—", style = MaterialTheme.typography.bodyMedium) },
            )
        }
        if (plan.firstMonthPayments.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Pay this month", style = MaterialTheme.typography.titleSmall)
            plan.firstMonthPayments.forEach { payment ->
                ListRow(
                    title = payment.debtName,
                    subtitle = if (payment.isExtra) "Minimum + extra" else "Minimum",
                    trailing = { MoneyText(payment.amount, style = MaterialTheme.typography.bodyLarge) },
                )
            }
        }
        if (debts.any { it.minimumEstimated } || state.picture.accounts.any { it.type.isLiability && it.apr == null && it.balance > 0 }) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { nav.navigateToTab(Routes.ACCOUNTS) }) {
                Text("Some rates or minimums are estimates. Add the real ones for an exact plan.")
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = {
            askAssistant(
                "Look at my debts and build me a realistic payoff plan. I can put about ${Money.format(extraCents, showCents = false)} extra per month toward debt. " +
                    "Tell me what to pay this month and what to watch out for.",
                nav, container,
            )
        }) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Talk it through with AI")
        }
    }
}

private fun monthsText(months: Int): String = when {
    months <= 0 -> "—"
    months < 12 -> "$months mo"
    months % 12 == 0 -> "${months / 12} yr"
    else -> "${months / 12} yr ${months % 12} mo"
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ForecastCard(state: FinanceState) {
    val forecast = state.picture.forecast
    val colors = LocalChartColors.current
    val hide = LocalHideAmounts.current
    NexusCard {
        val lowest = forecast.lowest
        if (lowest != null) {
            Text("Lowest projected checking balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(lowest.balance, style = MaterialTheme.typography.headlineSmall, color = if (lowest.balance < 0) colors.negativeText else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text("around ${lowest.date.short()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Includes detected bills and paychecks plus about ${money(forecast.dailySpending)}/day of everyday spending.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hide && forecast.points.size >= 2) {
            Spacer(Modifier.height(12.dp))
            LineChart(
                series = listOf(ChartSeries("Projected balance", forecast.points.map { it.balance / 100.0 }, colors.series1)),
                xLabel = { forecast.points[it].date.short() },
                valueLabel = { Money.formatCompact((it * 100).roundToLong()) },
                includeZero = true,
                markLowest = true,
                height = 160,
                description = "Projected checking balance for the next 30 days, lowest ${Money.format(lowest?.balance ?: 0)}.",
            )
        }
        val upcoming = forecast.events.take(6)
        if (upcoming.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            upcoming.forEach { event ->
                ListRow(
                    title = event.name,
                    subtitle = event.date.relative(state.picture.today),
                    trailing = { MoneyText(event.amount, signed = true, showCents = true, style = MaterialTheme.typography.bodyMedium) },
                )
            }
        }
    }
}
