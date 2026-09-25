package com.nexusbudget.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexusbudget.app.AppContainer
import com.nexusbudget.app.MainActivity
import com.nexusbudget.app.R
import com.nexusbudget.app.data.ConnectionStatus
import com.nexusbudget.core.engine.BudgetHealth
import com.nexusbudget.core.model.Money
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Local notifications: budget limits, bills due tomorrow, low-balance forecasts and broken connections. */
object Alerts {

    private const val CHANNEL_ID = "alerts"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Budget limits, upcoming bills and low balance warnings"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    suspend fun check(context: Context, container: AppContainer) {
        val settings = container.settings.current()
        if (!settings.notifications || settings.demoMode || !canNotify(context)) return
        val state = withTimeoutOrNull(30_000) { container.finance.state.filterNotNull().first() } ?: return
        val picture = state.picture
        val today = LocalDate.now()
        val sent = settings.notifiedKeys
        val pending = mutableListOf<Triple<String, String, String>>()
        fun fmt(cents: Long) = Money.format(cents, showCents = false)

        for (status in picture.budget.categories) {
            val month = picture.budget.month
            if (status.health == BudgetHealth.OVER) {
                pending += Triple("over-${status.category.id}-$month", "${status.category.name} is over budget",
                    "You've spent ${fmt(status.spent)} of ${fmt(status.limit)} this month.")
            } else if (status.limit > 0 && status.spent >= status.limit * 9 / 10) {
                pending += Triple("near-${status.category.id}-$month", "${status.category.name} is almost used up",
                    "${fmt(status.available)} left for the next ${picture.budget.daysLeft} days.")
            }
        }
        picture.upcomingBills.filter { it.date == today.plusDays(1) }.forEach { bill ->
            pending += Triple("bill-${bill.name}-${bill.date}", "${bill.name} is due tomorrow", "About ${Money.format(bill.amount)}.")
        }
        picture.forecast.lowest?.takeIf { it.balance < 0 }?.let { low ->
            pending += Triple("low-${low.date}", "Checking may run low", "Forecast shows about ${fmt(low.balance)} around " +
                low.date.format(DateTimeFormatter.ofPattern("MMM d")) + ". Consider moving money or holding off on big purchases.")
        }
        container.connections.connections.first().filter { it.status == ConnectionStatus.NEEDS_REAUTH }.forEach { connection ->
            pending += Triple("reauth-${connection.id}-$today", "Reconnect ${connection.displayName}", "Your bank connection needs you to sign in again.")
        }

        val manager = NotificationManagerCompat.from(context)
        pending.filter { it.first !in sent }.take(3).forEach { (key, title, body) ->
            val intent = PendingIntent.getActivity(
                context, key.hashCode(),
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            try {
                manager.notify(key.hashCode(), notification)
                container.settings.markNotified(key)
            } catch (e: SecurityException) {
                return
            }
        }
    }
}
