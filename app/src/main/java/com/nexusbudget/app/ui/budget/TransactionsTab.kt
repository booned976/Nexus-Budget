package com.nexusbudget.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nexusbudget.app.data.FinanceState
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.components.EmojiBadge
import com.nexusbudget.app.ui.components.ListRow
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.amountColor
import com.nexusbudget.app.ui.relative
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryIndex
import com.nexusbudget.core.model.Transaction
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TransactionRow(txn: Transaction, categories: CategoryIndex, onClick: () -> Unit, accountName: String? = null) {
    val category = categories[txn.categoryId]
    val subtitle = listOfNotNull(
        category.name,
        accountName,
        if (txn.pending) "Pending" else null,
        if (txn.excluded) "Excluded" else null,
    ).joinToString(" · ")
    ListRow(
        title = txn.merchant,
        subtitle = subtitle,
        leading = { EmojiBadge(category.icon, size = 36) },
        trailing = { MoneyText(txn.amount, signed = true, showCents = true, style = MaterialTheme.typography.bodyMedium, color = amountColor(txn.amount)) },
        onClick = onClick,
    )
}

@Composable
fun TransactionsTab(
    nav: NavHostController,
    state: FinanceState,
    categoryFilter: String?,
    onClearFilter: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var onlyUncategorized by rememberSaveable { mutableStateOf(false) }
    var categorizing by remember { mutableStateOf(false) }
    val accountNames = remember(state.accounts) { state.accounts.associate { it.id to it.name } }

    val filtered = remember(state.transactions, query, onlyUncategorized, categoryFilter) {
        state.transactions.filter { txn ->
            (query.isBlank() || txn.merchant.contains(query, ignoreCase = true) || txn.description.contains(query, ignoreCase = true)) &&
                (!onlyUncategorized || txn.categoryId == null || txn.categoryId == Categories.UNCATEGORIZED) &&
                (categoryFilter == null || state.categories[txn.categoryId].id == categoryFilter)
        }
    }
    val uncategorizedCount = remember(state.transactions) {
        state.transactions.count { !it.userCategorized && (it.categoryId == null || it.categoryId == Categories.UNCATEGORIZED) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search merchants") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = onlyUncategorized,
                        onClick = { onlyUncategorized = !onlyUncategorized },
                        label = { Text("Uncategorized ($uncategorizedCount)") },
                    )
                    if (categoryFilter != null) {
                        InputChip(
                            selected = true,
                            onClick = onClearFilter,
                            label = { Text(state.categories[categoryFilter].name) },
                            trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Clear filter", modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            if (uncategorizedCount > 0 && container.assistant.hasApiKey()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (categorizing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Categorizing with AI…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            TextButton(onClick = {
                                categorizing = true
                                scope.launch {
                                    val message = runCatching { container.assistant.categorizeWithAi() }
                                        .fold({ "Categorized $it transactions" }, { it.message ?: "Couldn't categorize right now." })
                                    categorizing = false
                                    snackbar.showSnackbar(message)
                                }
                            }) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Categorize $uncategorizedCount with AI")
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No transactions match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            var lastDate: LocalDate? = null
            filtered.take(500).forEach { txn ->
                if (txn.date != lastDate) {
                    lastDate = txn.date
                    item(key = "date-${txn.date}") {
                        Column(Modifier.padding(top = 12.dp)) {
                            Text(txn.date.relative(state.picture.today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item(key = txn.id) {
                    TransactionRow(txn, state.categories, onClick = { nav.navigate(Routes.transaction(txn.id)) }, accountName = accountNames[txn.accountId])
                }
            }
        }
        SmallFloatingActionButton(
            onClick = { nav.navigate(Routes.addTransaction()) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Add transaction")
        }
    }
}
