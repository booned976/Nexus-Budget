package com.nexusbudget.app.ui.accounts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.components.ListRow
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.short
import com.nexusbudget.core.importer.CsvImportResult
import com.nexusbudget.core.importer.CsvImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportScreen(nav: NavHostController, initialAccountId: String) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var accountId by remember { mutableStateOf(initialAccountId) }
    var fileText by remember { mutableStateOf<String?>(null) }
    var invert by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                fileText = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                }
                if (fileText == null) snackbar.showSnackbar("Couldn't read that file.")
            }
        }
    }
    val result: CsvImportResult? = remember(fileText, invert) { fileText?.let { CsvImporter.parse(it, invert) } }

    ScreenScaffold(title = "Import CSV", onBack = { nav.popBackStack() }, snackbarHostState = snackbar) { padding ->
        val current = state ?: return@ScreenScaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Most banks let you download transactions as a CSV file from their website. Nexus Budget finds the date, " +
                    "description and amount columns automatically.",
                style = MaterialTheme.typography.bodyMedium,
            )
            NexusCard {
                Text("1. Choose the account", style = MaterialTheme.typography.titleSmall)
                val accounts = current.accounts.filter { !it.isHidden }
                if (accounts.isEmpty()) {
                    Text("Create an account first.", style = MaterialTheme.typography.bodyMedium)
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
                Text("2. Pick the file", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = {
                    picker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                }) { Text(if (fileText == null) "Choose CSV file" else "Choose a different file") }
            }
            result?.let { parsed ->
                NexusCard {
                    Text("3. Check and import", style = MaterialTheme.typography.titleSmall)
                    if (parsed.rows.isEmpty()) {
                        Text(parsed.errors.firstOrNull() ?: "No transactions found.", color = MaterialTheme.colorScheme.error)
                    } else {
                        val first = parsed.rows.minOf { it.date }
                        val last = parsed.rows.maxOf { it.date }
                        Text("${parsed.rows.size} transactions from ${first.short()} to ${last.short()}" +
                            if (parsed.skipped > 0) " · ${parsed.skipped} rows skipped" else "", style = MaterialTheme.typography.bodyMedium)
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
                                    snackbar.showSnackbar("Imported $count new transactions")
                                    nav.popBackStack()
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
