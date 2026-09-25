package com.nexusbudget.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.components.AccountTypePickerDialog
import com.nexusbudget.app.ui.components.ConfirmDialog
import com.nexusbudget.app.ui.components.MoneyField
import com.nexusbudget.app.ui.components.centsToInput
import com.nexusbudget.app.ui.components.inputToCents
import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.AccountType
import kotlinx.coroutines.launch
import java.util.UUID

/** Create a manually tracked account, or edit any account's details. */
@Composable
fun EditAccountScreen(nav: NavHostController, accountId: String, initialType: String) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Account?>(null) }
    var ready by remember { mutableStateOf(accountId.isEmpty()) }

    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AccountType.entries.firstOrNull { it.name == initialType } ?: AccountType.CHECKING) }
    var institution by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var apr by rememberSaveable { mutableStateOf("") }
    var minimum by rememberSaveable { mutableStateOf("") }
    var dueDay by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("") }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var inNetWorth by rememberSaveable { mutableStateOf(true) }
    var pickType by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) {
        if (accountId.isNotEmpty() && loaded == null) {
            container.finance.account(accountId)?.let { a ->
                loaded = a
                name = a.name
                type = a.type
                institution = a.institution.orEmpty()
                balance = centsToInput(a.balance)
                apr = a.apr?.toString().orEmpty()
                minimum = centsToInput(a.minimumPayment)
                dueDay = a.paymentDueDay?.toString().orEmpty()
                limit = centsToInput(a.creditLimit)
                hidden = a.isHidden
                inNetWorth = a.includeInNetWorth
            }
            ready = true
        }
    }

    val isNew = accountId.isEmpty()
    val synced = loaded?.isManual == false

    ScreenScaffold(title = if (isNew) "Add account" else "Edit account", onBack = { nav.popBackStack() }) { padding ->
        if (!ready) return@ScreenScaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = type.label,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Type") },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable { pickType = true },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            OutlinedTextField(institution, { institution = it }, label = { Text("Bank or lender (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!synced) {
                MoneyField(balance, { balance = it }, if (type.isLiability) "Amount owed" else "Current balance")
            }
            if (type.isLiability) {
                OutlinedTextField(
                    apr, { apr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Interest rate (APR %)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Find it on your statement. Used for payoff plans.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(minimum, { minimum = it }, "Minimum monthly payment")
                OutlinedTextField(
                    dueDay, { dueDay = it.filter(Char::isDigit).take(2) },
                    label = { Text("Payment due day of month") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type.isRevolving) MoneyField(limit, { limit = it }, "Credit limit")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include in net worth", modifier = Modifier.weight(1f))
                Switch(inNetWorth, { inNetWorth = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide account")
                    Text("Hidden accounts are left out of totals and plans.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(hidden, { hidden = it })
            }
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = loaded ?: Account(id = "manual:${UUID.randomUUID()}", name = name, type = type, balance = 0)
                    val updated = base.copy(
                        name = name.trim(),
                        type = type,
                        institution = institution.trim().ifBlank { null },
                        balance = if (synced) base.balance else inputToCents(balance) ?: 0,
                        apr = if (type.isLiability) apr.toDoubleOrNull() else null,
                        minimumPayment = if (type.isLiability) inputToCents(minimum) else null,
                        paymentDueDay = if (type.isLiability) dueDay.toIntOrNull()?.coerceIn(1, 31) else null,
                        creditLimit = if (type.isRevolving) inputToCents(limit) else base.creditLimit,
                        isHidden = hidden,
                        includeInNetWorth = inNetWorth,
                    )
                    scope.launch {
                        if (isNew) container.finance.saveManualAccount(updated) else container.finance.updateAccountDetails(updated)
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            if (!isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
                if (synced) {
                    Text(
                        "This account syncs from your bank, so it will come back on the next sync. Hide it instead, or remove the connection in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (pickType) {
        AccountTypePickerDialog(type, onPick = { type = it; pickType = false }, onDismiss = { pickType = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${loaded?.name ?: "account"}?",
            body = "This removes the account and its transactions from this phone.",
            confirm = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    container.finance.deleteAccount(accountId)
                    nav.popBackStack(Routes.ACCOUNTS, inclusive = false)
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
