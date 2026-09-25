package com.nexusbudget.app.ui.budget

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.components.CategoryPickerDialog
import com.nexusbudget.app.ui.components.ConfirmDialog
import com.nexusbudget.app.ui.components.DatePickerSheet
import com.nexusbudget.app.ui.components.EmojiBadge
import com.nexusbudget.app.ui.components.MoneyField
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.amountColor
import com.nexusbudget.app.ui.components.inputToCents
import com.nexusbudget.app.ui.long
import com.nexusbudget.core.model.Category
import com.nexusbudget.core.model.Transaction
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TransactionScreen(nav: NavHostController, transactionId: String) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var txn by remember { mutableStateOf<Transaction?>(null) }
    var merchant by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var excluded by remember { mutableStateOf(false) }
    var rememberRule by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        container.finance.transaction(transactionId)?.let {
            txn = it
            merchant = it.merchant
            notes = it.notes.orEmpty()
            category = it.categoryId
            excluded = it.excluded
        }
    }

    ScreenScaffold(title = "Transaction", onBack = { nav.popBackStack() }) { padding ->
        val current = state ?: return@ScreenScaffold
        val original = txn ?: return@ScreenScaffold
        val account = current.accounts.firstOrNull { it.id == original.accountId }
        val selectedCategory = current.categories[category]
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NexusCard {
                MoneyText(original.amount, signed = true, showCents = true, style = MaterialTheme.typography.headlineMedium, color = amountColor(original.amount))
                Text(original.date.long() + if (original.pending) " · Pending" else "", style = MaterialTheme.typography.bodyMedium)
                account?.let { Text(it.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(4.dp))
                Text("Bank description: ${original.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CategoryField(selectedCategory, onClick = { picking = true })
            if (category != original.categoryId) {
                Row(Modifier.fillMaxWidth().clickable { rememberRule = !rememberRule }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rememberRule, { rememberRule = it })
                    Text("Always use ${selectedCategory.name} for ${merchant.ifBlank { original.merchant }}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exclude from budget and reports")
                    Text("For reimbursed expenses or one-off transfers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(excluded, { excluded = it })
            }
            Button(
                onClick = {
                    scope.launch {
                        container.finance.updateTransaction(
                            original.copy(
                                merchant = merchant.trim().ifBlank { original.merchant },
                                categoryId = category,
                                notes = notes.trim().ifBlank { null },
                                excluded = excluded,
                            ),
                            rememberForMerchant = rememberRule && category != original.categoryId,
                        )
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            if (account?.isManual != false) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete transaction", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (picking) {
            CategoryPickerDialog(current.categories, category, onPick = { category = it.id; picking = false }, onDismiss = { picking = false })
        }
        if (confirmDelete) {
            ConfirmDialog(
                "Delete transaction?", "This can't be undone.", "Delete",
                onConfirm = {
                    confirmDelete = false
                    scope.launch {
                        container.finance.deleteTransaction(original.id)
                        nav.popBackStack()
                    }
                },
                onDismiss = { confirmDelete = false },
                destructive = true,
            )
        }
    }
}

@Composable
private fun CategoryField(category: Category, onClick: () -> Unit) {
    NexusCard(onClick = onClick, contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(category.icon, size = 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(category.name, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(nav: NavHostController, initialAccountId: String) {
    val container = LocalAppContainer.current
    val state by container.finance.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var accountId by rememberSaveable { mutableStateOf(initialAccountId) }
    var isExpense by rememberSaveable { mutableStateOf(true) }
    var amount by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Add transaction", onBack = { nav.popBackStack() }) { padding ->
        val current = state ?: return@ScreenScaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = isExpense, onClick = { isExpense = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Money out") }
                SegmentedButton(selected = !isExpense, onClick = { isExpense = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Money in") }
            }
            MoneyField(amount, { amount = it }, "Amount")
            OutlinedTextField(description, { description = it }, label = { Text("Description or merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NexusCard(onClick = { pickingDate = true }, contentPadding = 12) {
                Text("Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date.long(), style = MaterialTheme.typography.bodyLarge)
            }
            CategoryField(current.categories[category], onClick = { picking = true })
            Text("Account", style = MaterialTheme.typography.titleSmall)
            current.accounts.filter { !it.isHidden }.forEach { account ->
                Row(Modifier.fillMaxWidth().clickable { accountId = account.id }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(accountId == account.id, { accountId = account.id })
                    Text(account.displayName)
                }
            }
            val cents = inputToCents(amount)
            Button(
                enabled = cents != null && cents > 0 && description.isNotBlank() && accountId.isNotEmpty(),
                onClick = {
                    val signed = if (isExpense) -(cents ?: 0) else (cents ?: 0)
                    scope.launch {
                        container.finance.addManualTransaction(accountId, date, signed, description.trim(), category)
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add") }
            Text(
                "For manually tracked accounts, the balance updates automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (picking) {
            CategoryPickerDialog(current.categories, category, onPick = { category = it.id; picking = false }, onDismiss = { picking = false })
        }
        if (pickingDate) {
            DatePickerSheet(date, onPick = { picked -> picked?.let { date = it }; pickingDate = false }, onDismiss = { pickingDate = false })
        }
    }
}
