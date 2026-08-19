package io.github.anderescaray.cents.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.anderescaray.cents.ui.components.CentsTabBar
import io.github.anderescaray.cents.ui.components.CentsTabItem
import io.github.anderescaray.cents.ui.feature.accounts.AccountsScreen
import io.github.anderescaray.cents.ui.feature.categories.CategoriesScreen
import io.github.anderescaray.cents.ui.feature.categories.CategoryEditScreen
import io.github.anderescaray.cents.ui.feature.reports.ReportsScreen
import io.github.anderescaray.cents.ui.feature.settings.SettingsScreen
import io.github.anderescaray.cents.ui.feature.transactionform.TransactionFormScreen
import io.github.anderescaray.cents.ui.feature.transactions.TransactionsScreen
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab

/** How long a push or pop takes. iOS uses a brisk slide, not a slow fade. */
private const val PUSH_DURATION_MS = 320

@Composable
fun CentsApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab =
        TopLevelTab.entries.firstOrNull { tab ->
            backStackEntry?.destination?.hasRoute(tab.destination::class) == true
        } ?: TopLevelTab.TRANSACTIONS

    val tabs =
        TopLevelTab.entries.map { tab ->
            CentsTabItem(
                key = tab.name,
                label = stringResource(tab.labelRes),
                iconRes = tab.iconRes,
            )
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            CentsNavHost(navController)
        }

        CentsTabBar(
            items = tabs,
            selectedKey = currentTab.name,
            onSelect = { key ->
                val tab = TopLevelTab.valueOf(key)
                navController.navigate(tab.destination) {
                    // Tabs switch, they do not stack: one entry per tab, state preserved.
                    popUpTo(Destination.Transactions) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

/** The navigation stack, with iOS push and pop transitions. */
@Composable
private fun CentsNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Destination.Transactions,
        // Horizontal push and pop, the way an iOS navigation stack moves.
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(PUSH_DURATION_MS),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(PUSH_DURATION_MS),
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(PUSH_DURATION_MS),
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(PUSH_DURATION_MS),
            )
        },
    ) {
        transactionRoutes(navController)
        categoryRoutes(navController)

        composable<Destination.Accounts> { AccountsScreen() }
        composable<Destination.Reports> { ReportsScreen() }
        composable<Destination.Settings> {
            SettingsScreen(onOpenCategories = { navController.navigate(Destination.Categories) })
        }
    }
}

/** The movement list and the form that adds to and edits it. */
private fun NavGraphBuilder.transactionRoutes(navController: NavHostController) {
    composable<Destination.Transactions> {
        TransactionsScreen(
            onAddTransaction = { navController.navigate(Destination.TransactionForm()) },
            onOpenTransaction = { id ->
                navController.navigate(Destination.TransactionForm(transactionId = id))
            },
        )
    }
    composable<Destination.TransactionForm> {
        TransactionFormScreen(
            onDone = { navController.popBackStack() },
            // Straight to the editor: with nothing to pick from, the list would be one
            // more empty screen between the owner and their first category.
            onCreateCategory = { kind -> navController.navigate(Destination.CategoryEdit(kind = kind)) },
        )
    }
}

/** Managing the owner's categories, and editing one of them. */
private fun NavGraphBuilder.categoryRoutes(navController: NavHostController) {
    composable<Destination.Categories> {
        CategoriesScreen(
            onBack = { navController.popBackStack() },
            onNewCategory = { kind -> navController.navigate(Destination.CategoryEdit(kind = kind)) },
            onEditCategory = { id, kind ->
                navController.navigate(Destination.CategoryEdit(categoryId = id, kind = kind))
            },
        )
    }
    composable<Destination.CategoryEdit> {
        CategoryEditScreen(onDone = { navController.popBackStack() })
    }
}
