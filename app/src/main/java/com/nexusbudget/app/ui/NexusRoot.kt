package com.nexusbudget.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexusbudget.app.AppContainer
import com.nexusbudget.app.ui.accounts.AccountDetailScreen
import com.nexusbudget.app.ui.accounts.AccountsScreen
import com.nexusbudget.app.ui.accounts.ConnectScreen
import com.nexusbudget.app.ui.accounts.EditAccountScreen
import com.nexusbudget.app.ui.accounts.ImportScreen
import com.nexusbudget.app.ui.assistant.AssistantScreen
import com.nexusbudget.app.ui.budget.AddTransactionScreen
import com.nexusbudget.app.ui.budget.BudgetScreen
import com.nexusbudget.app.ui.budget.TransactionScreen
import com.nexusbudget.app.ui.home.HomeScreen
import com.nexusbudget.app.ui.lock.LockScreen
import com.nexusbudget.app.ui.onboarding.OnboardingScreen
import com.nexusbudget.app.ui.plans.GoalScreen
import com.nexusbudget.app.ui.plans.PlansScreen
import com.nexusbudget.app.ui.settings.RulesScreen
import com.nexusbudget.app.ui.settings.SettingsScreen
import com.nexusbudget.app.ui.theme.NexusTheme
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.ACCOUNTS, "Accounts", Icons.Outlined.AccountBalance, Icons.Filled.AccountBalance),
    Tab(Routes.BUDGET, "Budget", Icons.Outlined.PieChart, Icons.Filled.PieChart),
    Tab(Routes.PLANS, "Plans", Icons.Outlined.Flag, Icons.Filled.Flag),
    Tab(Routes.ASSISTANT, "Ask AI", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
)

@Composable
fun NexusRoot(container: AppContainer, activity: FragmentActivity) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val locked by container.locked.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalAppContainer provides container) {
        NexusTheme(hideAmounts = settings?.hideAmounts == true) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val current = settings
                when {
                    current == null -> Box(Modifier.fillMaxSize())
                    !current.onboardingDone -> OnboardingScreen(onFinished = { scope.launch { container.settings.setOnboardingDone(true) } })
                    else -> MainNavigation()
                }
                if (locked) LockScreen(activity = activity, onUnlocked = { container.locked.value = false })
            }
        }
    }
}

/** Navigates to a top-level tab, keeping each tab's own back stack. */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            modifier = Modifier.testTag("tab-${tab.route}"),
                            selected = selected,
                            onClick = { nav.navigateToTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.ACCOUNTS) { AccountsScreen(nav) }
            composable(Routes.BUDGET) { BudgetScreen(nav) }
            composable(Routes.PLANS) { PlansScreen(nav) }
            composable(Routes.ASSISTANT) { AssistantScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.CONNECT) { ConnectScreen(nav) }
            composable(Routes.RULES) { RulesScreen(nav) }
            composable(Routes.ACCOUNT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                AccountDetailScreen(nav, entry.arguments?.getString("id").orEmpty())
            }
            composable(
                Routes.EDIT_ACCOUNT,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("type") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                EditAccountScreen(nav, entry.arguments?.getString("id").orEmpty(), entry.arguments?.getString("type").orEmpty())
            }
            composable(Routes.TRANSACTION, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                TransactionScreen(nav, entry.arguments?.getString("id").orEmpty())
            }
            composable(Routes.ADD_TRANSACTION, arguments = listOf(navArgument("accountId") { type = NavType.StringType; defaultValue = "" })) { entry ->
                AddTransactionScreen(nav, entry.arguments?.getString("accountId").orEmpty())
            }
            composable(Routes.GOAL, arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) { entry ->
                GoalScreen(nav, entry.arguments?.getString("id").orEmpty())
            }
            composable(Routes.IMPORT, arguments = listOf(navArgument("accountId") { type = NavType.StringType; defaultValue = "" })) { entry ->
                ImportScreen(nav, entry.arguments?.getString("accountId").orEmpty())
            }
        }
    }
}
