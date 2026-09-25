package com.nexusbudget.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusbudget.app.AppContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Creates a ViewModel with access to the app's repositories. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

object Routes {
    const val HOME = "home"
    const val ACCOUNTS = "accounts"
    const val BUDGET = "budget"
    const val PLANS = "plans"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"
    const val CONNECT = "connect"
    const val ACCOUNT = "account/{id}"
    const val EDIT_ACCOUNT = "editAccount?id={id}&type={type}"
    const val TRANSACTION = "transaction/{id}"
    const val ADD_TRANSACTION = "addTransaction?accountId={accountId}"
    const val GOAL = "goal?id={id}"
    const val IMPORT = "import?accountId={accountId}"
    const val RULES = "rules"

    fun account(id: String) = "account/${Uri.encode(id)}"
    fun editAccount(id: String? = null, type: String? = null) = "editAccount?id=${Uri.encode(id.orEmpty())}&type=${type.orEmpty()}"
    fun transaction(id: String) = "transaction/${Uri.encode(id)}"
    fun addTransaction(accountId: String? = null) = "addTransaction?accountId=${Uri.encode(accountId.orEmpty())}"
    fun goal(id: String? = null) = "goal?id=${Uri.encode(id.orEmpty())}"
    fun import(accountId: String? = null) = "import?accountId=${Uri.encode(accountId.orEmpty())}"
}

/**
 * Standard screen frame. Top-level tabs sit above the bottom navigation bar, which already
 * handles the system inset, so they don't add their own bottom inset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    topLevel: Boolean = false,
    onBack: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (topLevel) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        content = content,
    )
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d")
private val longDate = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy")

fun LocalDate.short(): String = format(shortDate)
fun LocalDate.long(): String = format(longDate)
fun java.time.YearMonth.label(): String = format(monthYear)

/** "Today", "Tomorrow", "In 3 days" or a date. */
fun LocalDate.relative(today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, this)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days in 2..6 -> "In $days days"
        else -> short()
    }
}

fun openUrl(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
