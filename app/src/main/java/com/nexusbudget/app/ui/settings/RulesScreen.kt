package com.nexusbudget.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.components.CategoryPickerDialog
import com.nexusbudget.app.ui.components.EmojiBadge
import com.nexusbudget.app.ui.components.EmptyState
import com.nexusbudget.app.ui.components.ListRow
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.core.model.CategoryIndex
import kotlinx.coroutines.launch

@Composable
fun RulesScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val rules by container.finance.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by container.finance.categories.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Categorization rules",
        onBack = { nav.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, contentDescription = null) }, text = { Text("Add rule") })
        },
    ) { padding ->
        val index = categories ?: return@ScreenScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Rules run before automatic categorization. When you change a transaction's category and choose \"always\", a rule is created for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rules.isEmpty()) {
                item { EmptyState(Icons.Outlined.Rule, "No rules yet", "Add one, like \"GREENLEAF\" → Groceries.") }
            }
            items(rules, key = { it.id }) { rule ->
                val category = index[rule.categoryId]
                NexusCard(contentPadding = 8) {
                    ListRow(
                        title = "Contains \"${rule.pattern}\"",
                        subtitle = "→ ${category.name}" + (rule.renameTo?.let { " · rename to $it" } ?: ""),
                        leading = { EmojiBadge(category.icon, size = 36) },
                        trailing = {
                            IconButton(onClick = { scope.launch { container.finance.deleteRule(rule) } }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete rule")
                            }
                        },
                    )
                }
            }
        }
        if (adding) {
            AddRuleDialog(index, onDismiss = { adding = false }) { pattern, categoryId, rename ->
                scope.launch { container.finance.addRule(pattern, categoryId, rename) }
                adding = false
            }
        }
    }
}

@Composable
private fun AddRuleDialog(categories: CategoryIndex, onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    var pattern by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pattern, { pattern = it }, label = { Text("Description contains") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(categoryId?.let { "${categories[it].icon} ${categories[it].name}" } ?: "Choose category")
                }
                OutlinedTextField(rename, { rename = it }, label = { Text("Rename merchant to (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = pattern.isNotBlank() && categoryId != null, onClick = { onSave(pattern, categoryId!!, rename.ifBlank { null }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        CategoryPickerDialog(categories, categoryId, onPick = { categoryId = it.id; picking = false }, onDismiss = { picking = false })
    }
}
