package com.nexusbudget.app.ui.accounts

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.nexusbudget.app.AppContainer
import com.nexusbudget.app.data.ConnectionRepository
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.appViewModel
import com.nexusbudget.app.ui.components.IconBadge
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.openUrl
import com.nexusbudget.connectors.plaid.PlaidLinkPurpose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val waitingForPlaid: Boolean = false,
    val finished: Boolean = false,
)

class ConnectViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ConnectUiState(waitingForPlaid = container.connections.hasPendingPlaidLink()))
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    val plaidConfigured: Boolean get() = container.connections.plaidConfigured()

    init {
        viewModelScope.launch { container.plaidReturns.collect { finishPlaid() } }
    }

    fun connectSimpleFin(token: String) = run("Connecting and downloading your accounts…") {
        val connection = container.connections.connectSimpleFin(token)
        _state.update { it.copy(finished = true, message = "Connected ${connection.displayName}.") }
    }

    fun startPlaid(context: Context, purpose: PlaidLinkPurpose) = run("Opening Plaid…") {
        val token = container.connections.startPlaidLink(purpose)
        _state.update { it.copy(waitingForPlaid = true) }
        openUrl(context, token.hostedLinkUrl!!)
    }

    fun finishPlaid() = run("Finishing up and downloading your accounts…") {
        val created = container.connections.completePlaidLink()
        if (created.isEmpty()) {
            _state.update { it.copy(message = "Plaid hasn't reported a finished connection yet. Finish in the browser, then try again.") }
        } else {
            _state.update { it.copy(waitingForPlaid = false, finished = true, message = "Connected ${created.joinToString { c -> c.displayName }}.") }
        }
    }

    fun cancelPlaid() {
        container.connections.cancelPlaidLink()
        _state.update { it.copy(waitingForPlaid = false) }
    }

    private fun run(progress: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = progress, error = null) }
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Something went wrong.", message = null) }
            } finally {
                _state.update { it.copy(busy = false, message = if (it.finished || it.error != null || it.message != progress) it.message else null) }
            }
        }
    }
}

@Composable
fun ConnectScreen(nav: NavHostController) {
    val vm = appViewModel { ConnectViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var token by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf(PlaidLinkPurpose.BANKING) }

    LaunchedEffect(state.finished) {
        if (state.finished) nav.popBackStack()
    }

    ScreenScaffold(title = "Add accounts", onBack = { nav.popBackStack() }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NexusCard(contentPadding = 14) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Connections are read-only. Nexus Budget can see balances and transactions but can never move money. " +
                            "Your data is stored only on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.busy || state.message != null || state.error != null) {
                NexusCard(contentPadding = 14) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            state.error ?: state.message.orEmpty(),
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // SimpleFIN
            NexusCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Link)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Connect with SimpleFIN", style = MaterialTheme.typography.titleMedium)
                        Text("Recommended · works with thousands of banks, cards, brokerages and loan servicers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "1. Create a SimpleFIN Bridge account and link your institutions there (SimpleFIN charges a small subscription fee).\n" +
                        "2. Create a setup token for Nexus Budget and copy it.\n" +
                        "3. Paste it below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { openUrl(context, ConnectionRepository.SIMPLEFIN_CREATE_URL) }) { Text("Get a setup token") }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("Setup token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.connectSimpleFin(token) }, enabled = token.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect")
                }
            }

            // Plaid
            NexusCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.AccountBalance)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Connect with Plaid", style = MaterialTheme.typography.titleMedium)
                        Text("Uses your own free Plaid developer keys · adds loan APRs and investment holdings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (!vm.plaidConfigured) {
                    Text("Add your Plaid client ID and secret in Settings first. The setup guide explains how to get them.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Text("Open settings") }
                } else if (state.waitingForPlaid) {
                    Text("Finish connecting in the browser tab. You'll come back here automatically.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.finishPlaid() }, enabled = !state.busy) { Text("I've finished") }
                        TextButton(onClick = { vm.cancelPlaid() }) { Text("Cancel") }
                    }
                } else {
                    Text("What are you connecting?", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            PlaidLinkPurpose.BANKING to "Bank & cards",
                            PlaidLinkPurpose.INVESTMENTS to "Investments",
                            PlaidLinkPurpose.LOANS to "Loans",
                        ).forEach { (option, label) ->
                            FilterChip(selected = purpose == option, onClick = { purpose = option }, label = { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.startPlaid(context, purpose) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue to Plaid")
                    }
                }
            }

            // Manual & CSV
            NexusCard(onClick = { nav.navigate(Routes.editAccount()) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.EditNote)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Add an account manually", style = MaterialTheme.typography.titleMedium)
                        Text("Track cash, a loan, a car or anything else by entering the balance yourself", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            NexusCard(onClick = { nav.navigate(Routes.import()) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.FileUpload)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Import a CSV file", style = MaterialTheme.typography.titleMedium)
                        Text("Download transactions from your bank's website and import them", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
