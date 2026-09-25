package com.nexusbudget.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexusbudget.app.NexusApp
import com.nexusbudget.app.widget.SafeToSpendWidget
import androidx.glance.appwidget.updateAll
import java.util.concurrent.TimeUnit

/** Refreshes every connection a few times a day, then checks for alerts and updates the widget. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NexusApp).container
        val settings = container.settings.current()
        if (!settings.demoMode) {
            runCatching { container.connections.syncAll() }
        }
        runCatching { Alerts.check(applicationContext, container) }
        runCatching { SafeToSpendWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nexus-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(8, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
