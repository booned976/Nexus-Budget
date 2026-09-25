package com.nexusbudget.app

import android.content.Context
import com.nexusbudget.app.data.AssistantRepository
import com.nexusbudget.app.data.ConnectionRepository
import com.nexusbudget.app.data.FinanceRepository
import com.nexusbudget.app.data.ImportFile
import com.nexusbudget.app.data.SecureStore
import com.nexusbudget.app.data.SettingsRepository
import com.nexusbudget.app.data.UpdateChecker
import com.nexusbudget.app.data.db.NexusDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Simple manual dependency container, created once by [NexusApp]. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = NexusDatabase.build(context)
    val secureStore = SecureStore(context)
    val settings = SettingsRepository(context)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val finance = FinanceRepository(database, settings, appScope)
    val connections = ConnectionRepository(database, secureStore, settings, finance, http)
    val assistant = AssistantRepository(database, secureStore, settings, finance, appScope)
    val updates = UpdateChecker(http)

    /** Fires when the user returns from Plaid's Hosted Link page. */
    val plaidReturns = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A question queued for the assistant from elsewhere in the app ("Ask AI" buttons). */
    val pendingAssistantPrompt = MutableStateFlow<String?>(null)

    /** Which Budget sub-tab to open next (0 budget, 1 transactions, 2 recurring, 3 trends). */
    val requestedBudgetTab = MutableStateFlow<Int?>(null)

    /** A statement file opened or shared into the app, waiting for the import screen. */
    val pendingImport = MutableStateFlow<ImportFile?>(null)

    /** True while the app is locked behind biometrics / device credential. */
    val locked = MutableStateFlow(false)
}
