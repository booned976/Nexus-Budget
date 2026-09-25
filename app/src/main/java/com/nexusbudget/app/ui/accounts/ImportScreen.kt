package com.nexusbudget.app.ui.accounts

import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.nexusbudget.app.data.ImportFile
import com.nexusbudget.app.data.ImportFiles
import com.nexusbudget.app.data.StatementImportResult
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.components.ListRow
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.short
import com.nexusbudget.core.importer.CsvImportResult
import com.nexusbudget.core.importer.CsvImporter
import com.nexusbudget.core.importer.OfxImporter
import com.nexusbudget.core.importer.Statement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Imports a statement file (OFX, QFX or QBO), which fills in accounts, balances and transactions by
 * itself, or a CSV export into an account the user picks. Files opened or shared into the app arrive
 * here through [com.nexusbudget.app.AppContainer.pendingImport].
 */
@Composable
fun ImportScreen(nav: NavHostController, initialAccountId: String) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val pending by container.pendingImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var file by remember { mutableStateOf<ImportFile?>(null) }
    var accountId by remember { mutableStateOf(initialAccountId) }
    var invert by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pending) {
        pending?.let {
            file = it
            done = null
            container.pendingImport.value = null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) { ImportFiles.read(context, uri) }
                if (picked == null) snackbar.showSnackbar("Couldn't read that file.") else {
                    file = picked
                    done = null
                }
            }
        }
    }
    // Statement files are recognized by their contents, whatever the file is called.
    val statements: List<Statement>? = remember(file) {
        file?.text?.takeIf(OfxImporter::looksLikeOfx)?.let { runCatching { OfxImporter.parse(it) }.getOrDefault(emptyList()) }
    }
    val csv: CsvImportResult? = remember(file, invert) {
        val text = file?.text
        if (text != null && statements == null) CsvImporter.parse(text, invert) else null
    }

    ScreenScaffold(title = "Import a statement", onBack = { nav.popBackStack() }, snackbarHostState = snackbar) { padding ->
        val current = state ?: return@ScreenScaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val result = done
            if (result != null) {
                NexusCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Import complete", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(result, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showAccounts(nav) }, modifier = Modifier.fillMaxWidth()) { Text("View accounts") }
                    TextButton(onClick = { file = null; done = null }) { Text("Import another file") }
                }
                return@Column
            }

            NexusCard {
                Text("Get a file from your bank", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sign in to your bank, card, brokerage or loan website and look for Download, Export or Statements. " +
                        "Choose OFX, QFX or QBO if offered (these include balances and account details), otherwise CSV.\n\n" +
                        "On your phone you can simply open the downloaded file and pick Nexus Budget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text(if (file == null) "Choose file" else "Choose a different file")
                }
            }

            if (statements != null) {
                StatementPreview(
                    statements = statements,
                    importing = importing,
                    onImport = {
                        importing = true
                        scope.launch {
                            val summary = runCatching { container.finance.importStatements(statements) }
                            importing = false
                            summary.onSuccess { done = it.describe() }
                                .onFailure { snackbar.showSnackbar("Import failed: ${it.message ?: "unknown error"}") }
                        }
                    },
                )
            }

            csv?.let { parsed ->
                NexusCard {
                    Text("Which account is this for?", style = MaterialTheme.typography.titleSmall)
                    val accounts = current.accounts.filter { !it.isHidden }
                    if (accounts.isEmpty()) {
                        Text("CSV files don't say which account they're from. Create the account first.", style = MaterialTheme.typography.bodyMedium)
                    }
                    accounts.forEach { account ->
                        Row(
                            Modifier.fillMaxWidth().clickable { accountId = account.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = accountId == account.id, onClick = { accountId = account.id })
                            Text(account.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    TextButton(onClick = { nav.navigate(Routes.editAccount()) }) { Text("Create a new account") }
                }
                NexusCard {
                    Text("Check and import", style = MaterialTheme.typography.titleSmall)
                    if (parsed.rows.isEmpty()) {
                        Text(parsed.errors.firstOrNull() ?: "No transactions found.", color = MaterialTheme.colorScheme.error)
                    } else {
                        val first = parsed.rows.minOf { it.date }
                        val last = parsed.rows.maxOf { it.date }
                        Text(
                            "${parsed.rows.size} transactions from ${first.short()} to ${last.short()}" +
                                if (parsed.skipped > 0) " · ${parsed.skipped} rows skipped" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Flip signs")
                                Text("Turn on if purchases show as positive numbers below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(invert, { invert = it })
                        }
                        parsed.rows.take(5).forEach { row ->
                            ListRow(
                                title = row.description,
                                subtitle = row.date.short(),
                                trailing = { MoneyText(row.amount, signed = true, showCents = true, style = MaterialTheme.typography.bodyMedium) },
                            )
                        }
                        Button(
                            enabled = accountId.isNotEmpty() && !importing,
                            onClick = {
                                importing = true
                                scope.launch {
                                    val count = container.finance.importCsv(accountId, parsed.rows)
                                    importing = false
                                    done = "$count new transactions added."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (accountId.isEmpty()) "Choose an account first" else "Import ${parsed.rows.size} transactions") }
                        Text("Transactions already imported are skipped automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementPreview(statements: List<Statement>, importing: Boolean, onImport: () -> Unit) {
    val container = LocalAppContainer.current
    var targets by remember(statements) { mutableStateOf<List<String?>?>(null) }
    LaunchedEffect(statements) { targets = container.finance.previewStatements(statements) }

    NexusCard {
        if (statements.isEmpty()) {
            Text("This file doesn't contain any account statements.", color = MaterialTheme.colorScheme.error)
            return@NexusCard
        }
        Text(if (statements.size == 1) "1 account found" else "${statements.size} accounts found", style = MaterialTheme.typography.titleSmall)
        statements.forEachIndexed { index, statement ->
            val title = statement.suggestedType.label + if (statement.mask.isNotEmpty()) " ••${statement.mask}" else ""
            val details = buildList {
                statement.institution?.let { add(it) }
                add(if (statement.transactions.size == 1) "1 transaction" else "${statement.transactions.size} transactions")
                statement.balanceDate?.let { add("balance on ${it.short()}") }
            }.joinToString(" · ")
            val target = targets?.getOrNull(index)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    statement.balance?.let { MoneyText(it, showCents = true, style = MaterialTheme.typography.bodyLarge) }
                }
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (target != null) "Adds to $target" else "Creates a new account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onImport, enabled = !importing && targets != null, modifier = Modifier.fillMaxWidth()) {
            Text(if (statements.size == 1) "Import" else "Import ${statements.size} accounts")
        }
        Text(
            "Balances and transactions come from the file. Importing a newer file later updates them, and transactions already imported are skipped.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Goes back to the Accounts list. Switching tabs normally restores that tab's saved screens, which
 * would include this import screen when it was opened from Accounts, so pop back to it instead.
 */
private fun showAccounts(nav: NavHostController) {
    if (!nav.popBackStack(Routes.ACCOUNTS, inclusive = false)) {
        nav.navigate(Routes.ACCOUNTS) {
            popUpTo(nav.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }
}

private fun StatementImportResult.describe(): String {
    val parts = buildList {
        if (accountsAdded > 0) add(if (accountsAdded == 1) "1 account added" else "$accountsAdded accounts added")
        if (accountsUpdated > 0) add(if (accountsUpdated == 1) "1 account updated" else "$accountsUpdated accounts updated")
        add(if (newTransactions == 1) "1 new transaction" else "$newTransactions new transactions")
    }
    return parts.joinToString(", ") + "."
}
