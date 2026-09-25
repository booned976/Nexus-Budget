package com.nexusbudget.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nexusbudget.app.sync.Alerts
import com.nexusbudget.app.sync.SyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NexusApp : Application() {

    lateinit var container: AppContainer
        private set

    private var backgroundedAt = 0L

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Alerts.createChannel(this)
        SyncWorker.schedule(this)

        // Lock immediately on a cold start when app lock is on (a quick read of one preference).
        container.locked.value = runBlocking { container.settings.settings.first().appLock }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundedAt = System.currentTimeMillis()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (backgroundedAt == 0L) return
                val away = System.currentTimeMillis() - backgroundedAt
                container.appScope.launch {
                    if (away > LOCK_AFTER_MS && container.settings.current().appLock) container.locked.value = true
                }
            }
        })
    }

    companion object {
        private const val LOCK_AFTER_MS = 30_000L
    }
}
