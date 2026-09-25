package com.nexusbudget.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Category
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.CategoryKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun CategoryPickerDialog(
    categories: CategoryIndex,
    selectedId: String?,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
    kinds: Set<CategoryKind> = CategoryKind.entries.toSet(),
) {
    var query by remember { mutableStateOf("") }
    val options = categories.all
        .filter { it.kind in kinds }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.group.contains(query, ignoreCase = true) }
        .sortedWith(compareBy({ it.kind.ordinal }, { it.group }, { it.name }))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a category") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    var lastGroup: String? = null
                    options.forEach { category ->
                        if (category.group != lastGroup) {
                            lastGroup = category.group
                            item(key = "group-${category.group}") {
                                Text(
                                    category.group,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                        }
                        item(key = category.id) {
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(category) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(category.icon)
                                Spacer(Modifier.width(12.dp))
                                Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (category.id == selectedId) Icon(Icons.Outlined.Check, contentDescription = "Selected")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AccountTypePickerDialog(selected: AccountType, onPick: (AccountType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account type") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(AccountType.entries) { type ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(type) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(accountIcon(type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(type.label, style = MaterialTheme.typography.bodyLarge)
                            Text(type.group.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (type == selected) Icon(Icons.Outlined.Check, contentDescription = "Selected")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(initial: LocalDate?, onPick: (LocalDate?) -> Unit, onDismiss: () -> Unit, allowClear: Boolean = false) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onPick(null) }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
