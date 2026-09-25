package com.nexusbudget.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexusbudget.assistant.AssistantModel
import com.nexusbudget.assistant.ResponseDepth
import com.nexusbudget.connectors.plaid.PlaidEnvironment
import com.nexusbudget.core.engine.PayoffStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val onboardingDone: Boolean = false,
    val demoMode: Boolean = false,
    val aiModel: AssistantModel = AssistantModel.DEFAULT,
    val aiDepth: ResponseDepth = ResponseDepth.DEFAULT,
    val aiShareDetails: Boolean = true,
    val extraDebtPayment: Long = 0,
    val payoffStrategy: PayoffStrategy = PayoffStrategy.AVALANCHE,
    val appLock: Boolean = false,
    val hideAmounts: Boolean = false,
    val notifications: Boolean = true,
    val dismissedRecommendations: Set<String> = emptySet(),
    val dismissedRecurring: Set<String> = emptySet(),
    val notifiedKeys: Set<String> = emptySet(),
    val plaidEnvironment: PlaidEnvironment = PlaidEnvironment.SANDBOX,
    val installId: String = "",
    val lastSync: Long? = null,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val demoMode = booleanPreferencesKey("demo_mode")
        val aiModel = stringPreferencesKey("ai_model")
        val aiDepth = stringPreferencesKey("ai_depth")
        val aiShareDetails = booleanPreferencesKey("ai_share_details")
        val extraDebtPayment = longPreferencesKey("extra_debt_payment")
        val payoffStrategy = stringPreferencesKey("payoff_strategy")
        val appLock = booleanPreferencesKey("app_lock")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val notifications = booleanPreferencesKey("notifications")
        val dismissedRecommendations = stringSetPreferencesKey("dismissed_recommendations")
        val dismissedRecurring = stringSetPreferencesKey("dismissed_recurring")
        val notifiedKeys = stringSetPreferencesKey("notified_keys")
        val plaidEnvironment = stringPreferencesKey("plaid_environment")
        val installId = stringPreferencesKey("install_id")
        val lastSync = longPreferencesKey("last_sync")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        onboardingDone = this[Keys.onboardingDone] ?: false,
        demoMode = this[Keys.demoMode] ?: false,
        aiModel = AssistantModel.fromId(this[Keys.aiModel]),
        aiDepth = ResponseDepth.fromName(this[Keys.aiDepth]),
        aiShareDetails = this[Keys.aiShareDetails] ?: true,
        extraDebtPayment = this[Keys.extraDebtPayment] ?: 0,
        payoffStrategy = PayoffStrategy.entries.firstOrNull { it.name == this[Keys.payoffStrategy] } ?: PayoffStrategy.AVALANCHE,
        appLock = this[Keys.appLock] ?: false,
        hideAmounts = this[Keys.hideAmounts] ?: false,
        notifications = this[Keys.notifications] ?: true,
        dismissedRecommendations = this[Keys.dismissedRecommendations] ?: emptySet(),
        dismissedRecurring = this[Keys.dismissedRecurring] ?: emptySet(),
        notifiedKeys = this[Keys.notifiedKeys] ?: emptySet(),
        plaidEnvironment = PlaidEnvironment.entries.firstOrNull { it.name == this[Keys.plaidEnvironment] } ?: PlaidEnvironment.SANDBOX,
        installId = this[Keys.installId].orEmpty(),
        lastSync = this[Keys.lastSync],
    )

    /** A random id for this installation, used as Plaid's required per-user identifier. */
    suspend fun installId(): String {
        current().installId.takeIf { it.isNotEmpty() }?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.installId] = id }
        return id
    }

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.onboardingDone] = done }
    suspend fun setDemoMode(enabled: Boolean) = context.dataStore.edit { it[Keys.demoMode] = enabled }
    suspend fun setAiModel(model: AssistantModel) = context.dataStore.edit { it[Keys.aiModel] = model.id }
    suspend fun setAiDepth(depth: ResponseDepth) = context.dataStore.edit { it[Keys.aiDepth] = depth.name }
    suspend fun setAiShareDetails(share: Boolean) = context.dataStore.edit { it[Keys.aiShareDetails] = share }
    suspend fun setExtraDebtPayment(cents: Long) = context.dataStore.edit { it[Keys.extraDebtPayment] = cents.coerceAtLeast(0) }
    suspend fun setPayoffStrategy(strategy: PayoffStrategy) = context.dataStore.edit { it[Keys.payoffStrategy] = strategy.name }
    suspend fun setAppLock(enabled: Boolean) = context.dataStore.edit { it[Keys.appLock] = enabled }
    suspend fun setHideAmounts(hidden: Boolean) = context.dataStore.edit { it[Keys.hideAmounts] = hidden }
    suspend fun setNotifications(enabled: Boolean) = context.dataStore.edit { it[Keys.notifications] = enabled }
    suspend fun setPlaidEnvironment(environment: PlaidEnvironment) = context.dataStore.edit { it[Keys.plaidEnvironment] = environment.name }
    suspend fun setLastSync(epochMillis: Long) = context.dataStore.edit { it[Keys.lastSync] = epochMillis }

    suspend fun dismissRecommendation(id: String) = context.dataStore.edit {
        it[Keys.dismissedRecommendations] = (it[Keys.dismissedRecommendations] ?: emptySet()) + id
    }

    suspend fun dismissRecurring(key: String) = context.dataStore.edit {
        it[Keys.dismissedRecurring] = (it[Keys.dismissedRecurring] ?: emptySet()) + key
    }

    suspend fun restoreRecurring() = context.dataStore.edit { it.remove(Keys.dismissedRecurring) }

    /** Records that a notification was sent, keeping the set bounded. */
    suspend fun markNotified(key: String) = context.dataStore.edit {
        it[Keys.notifiedKeys] = ((it[Keys.notifiedKeys] ?: emptySet()) + key).toList().takeLast(200).toSet()
    }

    suspend fun resetAll() = context.dataStore.edit { prefs ->
        val id = prefs[Keys.installId]
        prefs.clear()
        if (id != null) prefs[Keys.installId] = id
    }
}
