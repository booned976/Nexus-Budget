package com.nexusbudget.app.ui.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.BuildConfig
import com.nexusbudget.app.data.Connection
import com.nexusbudget.app.data.ConnectionStatus
import com.nexusbudget.app.data.Provider
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.components.ConfirmDialog
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.lock.canUseAppLock
import com.nexusbudget.app.ui.openUrl
import com.nexusbudget.app.ui.theme.LocalChartColors
import com.nexusbudget.assistant.AssistantModel
import com.nexusbudget.assistant.ResponseDepth
import com.nexusbudget.connectors.plaid.PlaidEnvironment
import com.nexusbudget.connectors.plaid.PlaidLinkPurpose
import com.nexusbudget.core.model.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SOURCE_URL = "https://github.com/booned976/Nexus-Budget"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val connections by container.connections.connections.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmDemo by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Connection?>(null) }
    var reconnectingSimpleFin by remember { mutableStateOf<Connection?>(null) }
    var editingAiKey by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { container.settings.setNotifications(granted) }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch { snackbar.showSnackbar(exportCsv(container, context, uri)) }
    }

    fun message(text: String) = scope.launch { snackbar.showSnackbar(text) }

    ScreenScaffold(title = "Settings", onBack = { nav.popBackStack() }, snackbarHostState = snackbar) { padding ->
        val current = settings ?: return@ScreenScaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Connections
            Section("Connections") {
                if (connections.isEmpty()) {
                    Text("No bank connections yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                connections.forEachIndexed { index, connection ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ConnectionRow(
                        connection,
                        onSync = {
                            scope.launch {
                                val result = runCatching { container.connections.sync(connection.id) }
                                snackbar.showSnackbar(result.fold({ "${connection.displayName} synced" }, { it.message ?: "Sync failed" }))
                            }
                        },
                        onReconnect = {
                            if (connection.provider == Provider.SIMPLEFIN) {
                                reconnectingSimpleFin = connection
                            } else {
                                scope.launch {
                                    runCatching {
                                        val purpose = if ("investments" in connection.products && "transactions" !in connection.products) PlaidLinkPurpose.INVESTMENTS else PlaidLinkPurpose.BANKING
                                        val token = container.connections.startPlaidLink(purpose, reconnectConnectionId = connection.id)
                                        openUrl(context, token.hostedLinkUrl!!)
                                    }.onFailure { message(it.message ?: "Couldn't start reconnecting") }
                                }
                            }
                        },
                        onRemove = { removing = connection },
                    )
                }
                OutlinedButton(onClick = { nav.navigate(Routes.CONNECT) }) { Text("Add a connection") }
            }

            PlaidKeysSection(current.plaidEnvironment, onSaved = { message("Plaid keys saved") })

            // AI assistant
            Section("AI assistant") {
                val hasKey by container.assistant.hasKey.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hasKey) "Anthropic API key saved" else "No API key yet", modifier = Modifier.weight(1f))
                    TextButton(onClick = { editingAiKey = true }) { Text(if (hasKey) "Change" else "Add key") }
                }
                Text("Model", style = MaterialTheme.typography.labelLarge)
                AssistantModel.entries.forEach { model ->
                    Row(
                        Modifier.fillMaxWidth().clickable { scope.launch { container.settings.setAiModel(model) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(current.aiModel == model, { scope.launch { container.settings.setAiModel(model) } })
                        Column {
                            Text(model.label)
                            Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Response depth", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ResponseDepth.entries.forEachIndexed { index, depth ->
                        SegmentedButton(
                            selected = current.aiDepth == depth,
                            onClick = { scope.launch { container.settings.setAiDepth(depth) } },
                            shape = SegmentedButtonDefaults.itemShape(index, ResponseDepth.entries.size),
                        ) { Text(depth.label) }
                    }
                }
                Text(current.aiDepth.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleRow(
                    "Share transaction details",
                    "Lets the assistant search individual transactions and merchant names. When off it only sees totals by category.",
                    current.aiShareDetails,
                ) { scope.launch { container.settings.setAiShareDetails(it) } }
            }

            // Security
            Section("Security & privacy") {
                ToggleRow("App lock", "Require fingerprint, face or device PIN to open the app. Also hides the app in the recents screen.", current.appLock) { enable ->
                    val activity = context as? FragmentActivity
                    if (enable && (activity == null || !canUseAppLock(activity))) {
                        message("Set up a screen lock on your phone first.")
                    } else {
                        scope.launch { container.settings.setAppLock(enable) }
                    }
                }
                ToggleRow("Hide amounts", "Mask all dollar amounts until you tap the eye icon on Home.", current.hideAmounts) {
                    scope.launch { container.settings.setHideAmounts(it) }
                }
            }

            Section("Notifications") {
                ToggleRow("Budget & bill alerts", "Over-budget categories, bills due tomorrow, low balance forecasts and broken connections.", current.notifications) { enable ->
                    if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        scope.launch { container.settings.setNotifications(enable) }
                    }
                }
            }

            Section("Categories") {
                SettingLink("Categorization rules", "Automatically categorize merchants the way you want") { nav.navigate(Routes.RULES) }
            }

            Section("Your data") {
                SettingLink("Export transactions (CSV)", "Save a spreadsheet-friendly copy") {
                    exporter.launch("nexus-budget-${LocalDate.now()}.csv")
                }
                SettingLink("Import transactions (CSV)", "From your bank's website") { nav.navigate(Routes.import()) }
                SettingLink("Load demo data", "Replaces everything with a sample household") { confirmDemo = true }
                SettingLink("Delete all data", "Erase accounts, transactions, keys and settings from this phone", destructive = true) { confirmDeleteAll = true }
            }

            Section("About") {
                Text("Nexus Budget ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Free and open source under the GNU GPL v3. No ads, no tracking, no accounts. Your financial data is stored only on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { openUrl(context, SOURCE_URL) }) { Text("Source code & documentation") }
                Text(
                    "Nexus Budget provides educational information, not professional financial, tax or legal advice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    removing?.let { connection ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${connection.displayName}?") },
            text = { Text("Stop syncing this connection. You can keep its accounts and history as manual accounts, or delete them.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.connections.remove(connection.id, deleteData = true) }
                    removing = null
                }) { Text("Remove & delete data", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { container.connections.remove(connection.id, deleteData = false) }
                    removing = null
                }) { Text("Keep history") }
            },
        )
    }
    reconnectingSimpleFin?.let { connection ->
        var token by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reconnectingSimpleFin = null },
            title = { Text("Reconnect SimpleFIN") },
            text = {
                Column {
                    Text("Create a new setup token in your SimpleFIN account and paste it here.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(token, { token = it.trim() }, label = { Text("Setup token") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = token.isNotBlank(), onClick = {
                    reconnectingSimpleFin = null
                    scope.launch {
                        val result = runCatching { container.connections.reconnectSimpleFin(connection.id, token) }
                        snackbar.showSnackbar(result.fold({ "Reconnected" }, { it.message ?: "Couldn't reconnect" }))
                    }
                }) { Text("Reconnect") }
            },
            dismissButton = { TextButton(onClick = { reconnectingSimpleFin = null }) { Text("Cancel") } },
        )
    }
    if (editingAiKey) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingAiKey = false },
            title = { Text("Anthropic API key") },
            text = {
                Column {
                    Text("Create a key in the Anthropic Console. It's encrypted on this phone and only sent to Anthropic.")
                    TextButton(onClick = { openUrl(context, "https://console.anthropic.com/settings/keys") }) { Text("Open Anthropic Console") }
                    OutlinedTextField(key, { key = it.trim() }, label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                TextButton(enabled = key.length > 20, onClick = { container.assistant.saveApiKey(key); editingAiKey = false; message("API key saved") }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { container.assistant.saveApiKey(""); editingAiKey = false; message("API key removed") }) { Text("Remove key") }
            },
        )
    }
    if (confirmDemo) {
        ConfirmDialog(
            "Load demo data?", "This replaces all accounts, transactions, budgets and goals on this phone with sample data.", "Load demo",
            onConfirm = {
                confirmDemo = false
                scope.launch {
                    container.connections.disconnectAll()
                    container.finance.loadDemo()
                    message("Demo data loaded")
                }
            },
            onDismiss = { confirmDemo = false },
            destructive = true,
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            "Delete everything?", "All accounts, transactions, bank connections, API keys and settings will be erased from this phone. This can't be undone.", "Delete all",
            onConfirm = {
                confirmDeleteAll = false
                scope.launch {
                    container.assistant.clear()
                    container.connections.disconnectAll()
                    container.secureStore.clear()
                    container.assistant.saveApiKey("")
                    container.finance.clearFinancialData()
                    container.settings.resetAll()
                }
            },
            onDismiss = { confirmDeleteAll = false },
            destructive = true,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    NexusCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun SettingLink(title: String, subtitle: String, destructive: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(title, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectionRow(connection: Connection, onSync: () -> Unit, onReconnect: () -> Unit, onRemove: () -> Unit) {
    val colors = LocalChartColors.current
    val (icon, tint, status) = when (connection.status) {
        ConnectionStatus.OK -> Triple(Icons.Outlined.CheckCircle, colors.good, "Connected")
        ConnectionStatus.NEEDS_REAUTH -> Triple(Icons.Outlined.WarningAmber, colors.serious, "Needs reconnecting")
        ConnectionStatus.ERROR -> Triple(Icons.Outlined.ErrorOutline, colors.critical, "Sync problem")
    }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(connection.displayName)
                val synced = connection.lastSync?.let {
                    " · synced " + DateTimeFormatter.ofPattern("MMM d, h:mm a").format(it.atZone(ZoneId.systemDefault()))
                }.orEmpty()
                Text("${connection.provider.label} · $status$synced", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        connection.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp, top = 2.dp))
        }
        Row(Modifier.padding(start = 24.dp)) {
            TextButton(onClick = onSync) { Text("Sync") }
            if (connection.status != ConnectionStatus.OK) TextButton(onClick = onReconnect) { Text("Reconnect") }
            TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaidKeysSection(environment: PlaidEnvironment, onSaved: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf(container.connections.plaidClientId()) }
    var secret by remember { mutableStateOf("") }
    var env by remember(environment) { mutableStateOf(environment) }
    val configured = container.connections.plaidConfigured()
    Section("Plaid API keys (optional)") {
        Text(
            "Plaid is optional. It needs your own free Plaid developer account; keys stay encrypted on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { openUrl(context, "https://dashboard.plaid.com/developers/keys") }) { Text("Get Plaid keys") }
        OutlinedTextField(clientId, { clientId = it.trim() }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            secret, { secret = it.trim() },
            label = { Text(if (configured) "Secret (saved, enter to replace)" else "Secret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PlaidEnvironment.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = env == option,
                    onClick = { env = option },
                    shape = SegmentedButtonDefaults.itemShape(index, PlaidEnvironment.entries.size),
                ) { Text(option.label) }
            }
        }
        Button(
            enabled = clientId.isNotBlank() && (secret.isNotBlank() || configured),
            onClick = {
                scope.launch {
                    container.connections.savePlaidKeys(clientId, secret, env)
                    secret = ""
                    onSaved()
                }
            },
        ) { Text("Save Plaid keys") }
    }
}

private suspend fun exportCsv(container: com.nexusbudget.app.AppContainer, context: android.content.Context, uri: Uri): String {
    val state = container.finance.state.value ?: return "Nothing to export yet."
    val accounts = state.accounts.associateBy { it.id }
    fun esc(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
    val csv = buildString {
        appendLine("Date,Account,Merchant,Description,Category,Amount,Pending,Excluded,Notes")
        state.transactions.forEach { t ->
            appendLine(
                listOf(
                    t.date.toString(),
                    esc(accounts[t.accountId]?.name.orEmpty()),
                    esc(t.merchant),
                    esc(t.description),
                    esc(state.categories[t.categoryId].name),
                    Money.toPlainString(t.amount),
                    t.pending.toString(),
                    t.excluded.toString(),
                    esc(t.notes.orEmpty()),
                ).joinToString(","),
            )
        }
    }
    return withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            "Exported ${state.transactions.size} transactions"
        }.getOrElse { "Export failed: ${it.message}" }
    }
}
