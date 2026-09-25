package com.nexusbudget.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nexusbudget.app.MainActivity
import com.nexusbudget.app.NexusApp
import com.nexusbudget.core.model.Money
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter

private data class WidgetData(val safe: String, val perDay: String, val netWorth: String)

/** Home screen widget: safe-to-spend and net worth at a glance. Tapping opens the app. */
class SafeToSpendWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent {
            GlanceTheme {
                Content(data)
            }
        }
    }

    private suspend fun load(context: Context): WidgetData? {
        val container = (context.applicationContext as NexusApp).container
        val settings = container.settings.current()
        // Respect app lock and "hide amounts": the widget is visible on the home screen.
        if (settings.appLock || settings.hideAmounts) return WidgetData("••••", "Open the app to see details", "")
        val state = withTimeoutOrNull(20_000) { container.finance.state.filterNotNull().first() } ?: return null
        if (state.accounts.isEmpty()) return null
        val safe = state.picture.safeToSpend
        val until = safe.until.format(DateTimeFormatter.ofPattern("MMM d"))
        return WidgetData(
            safe = Money.format(safe.amount, showCents = false),
            perDay = "${Money.format(safe.perDay, showCents = false)}/day until $until",
            netWorth = "Net worth ${Money.formatCompact(state.picture.netWorth.total)}",
        )
    }

    @Composable
    private fun Content(data: WidgetData?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("Safe to spend", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            if (data == null) {
                Spacer(GlanceModifier.height(6.dp))
                Text("Open Nexus Budget to get started", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
            } else {
                Text(data.safe, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                Text(data.perDay, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                Spacer(GlanceModifier.height(8.dp))
                if (data.netWorth.isNotEmpty()) {
                    Text(data.netWorth, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeToSpendWidget()
}
