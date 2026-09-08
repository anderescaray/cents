package io.github.anderescaray.cents.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.anderescaray.cents.ui.components.CentsTabBar
import io.github.anderescaray.cents.ui.components.CentsTabBarShelf
import io.github.anderescaray.cents.ui.components.CentsTabItem
import io.github.anderescaray.cents.ui.feature.accounts.AccountDetailScreen
import io.github.anderescaray.cents.ui.feature.accounts.AccountEditScreen
import io.github.anderescaray.cents.ui.feature.accounts.AccountsScreen
import io.github.anderescaray.cents.ui.feature.categories.CategoriesScreen
import io.github.anderescaray.cents.ui.feature.categories.CategoryEditScreen
import io.github.anderescaray.cents.ui.feature.filters.TransactionFiltersScreen
import io.github.anderescaray.cents.ui.feature.home.HomeScreen
import io.github.anderescaray.cents.ui.feature.home.HomeUiState
import io.github.anderescaray.cents.ui.feature.home.HomeViewModel
import io.github.anderescaray.cents.ui.feature.reports.ReportsScreen
import io.github.anderescaray.cents.ui.feature.settings.SettingsScreen
import io.github.anderescaray.cents.ui.feature.transactionform.TransactionFormScreen
import io.github.anderescaray.cents.ui.feature.transactions.TransactionsScreen
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab

@Composable
fun CentsApp(
    navController: NavHostController = rememberNavController(),
    home: HomeViewModel = hiltViewModel(),
) {
    val backStack by navController.currentBackStack.collectAsState()
    val currentTab = backStack.selectedTab()
    val homeState by home.state.collectAsStateWithLifecycle()

    RestoreLastSection(state = homeState, currentTab = currentTab, navController = navController, home = home)

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
            CentsNavHost(navController = navController, home = home, homeState = homeState)
        }

        // No bar on the canvas: the bubbles are the way in, and a tab bar under them would be
        // the same four destinations offered twice.
        if (currentTab != null) {
            CentsTabBarShelf(
                collapsed = homeState.tabBarCollapsed,
                onCollapsedChange = home::setTabBarCollapsed,
            ) {
                CentsTabBar(
                    items = tabs,
                    selectedKey = currentTab.name,
                    onSelect = { key -> navController.switchToTab(TopLevelTab.valueOf(key).destination) },
                )
            }
        }
    }
}

/**
 * Reopening where the app was left, and remembering where it is now — in that order, which is
 * the whole subtlety.
 *
 * The canvas is the start destination, so on a cold start the current tab is null, and writing
 * that down before the stored section has been read would erase it a frame before it was used.
 * So nothing is remembered until the one restore has been attempted, and the flag saying so is
 * saveable: after process death Android restores the back stack itself, and a second restore
 * would push a section on top of one already open.
 */
@Composable
private fun RestoreLastSection(
    state: HomeUiState,
    currentTab: TopLevelTab?,
    navController: NavHostController,
    home: HomeViewModel,
) {
    var restored by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.loaded) {
        if (state.loaded && !restored) {
            restored = true
            state.lastSection?.let { navController.switchToTab(it.destination) }
        }
    }
    LaunchedEffect(currentTab, restored) {
        if (restored) home.rememberSection(currentTab)
    }
}

/** The navigation stack, with iOS push and pop transitions. */
@Composable
private fun CentsNavHost(
    navController: NavHostController,
    home: HomeViewModel,
    homeState: HomeUiState,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Home,
        // Horizontal push and pop, the way an iOS navigation stack moves.
        enterTransition = { arriving() },
        exitTransition = { leaving() },
        popEnterTransition = { arriving(BACKWARDS) },
        popExitTransition = { leaving(BACKWARDS) },
    ) {
        composable<Destination.Home> {
            HomeScreen(
                state = homeState,
                onMove = home::move,
                onOpenSection = { tab -> navController.switchToTab(tab.destination) },
            )
        }
        transactionRoutes(navController)
        accountRoutes(navController)
        categoryRoutes(navController)

        composable<Destination.Reports> {
            ReportsScreen(
                // The same move the tab bar makes, because a drill-down *is* switching to
                // the movements tab — with the filter already applied. Pushing the list on
                // top instead would leave two of it on the stack, one filtered and one not.
                onOpenMovements = { navController.openMovementsList() },
            )
        }
        composable<Destination.Settings> {
            SettingsScreen(
                onOpenCategories = { navController.navigate(Destination.Categories) },
                onResetHomeLayout = home::resetLayout,
            )
        }
    }
}

/** The movement list and the form that adds to and edits it. */
private fun NavGraphBuilder.transactionRoutes(navController: NavHostController) {
    composable<Destination.Transactions> {
        TransactionsScreen(
            // Single top: during the push animation a second tap would otherwise stack a
            // second copy of the form behind the first.
            onAddTransaction = {
                navController.navigate(Destination.TransactionForm()) { launchSingleTop = true }
            },
            onOpenTransaction = { id ->
                navController.navigate(Destination.TransactionForm(transactionId = id)) {
                    launchSingleTop = true
                }
            },
            onOpenFilters = {
                navController.navigate(Destination.TransactionFilters) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.TransactionFilters> {
        TransactionFiltersScreen(onDone = { navController.popBackStack() })
    }
    composable<Destination.TransactionForm> {
        TransactionFormScreen(
            onDone = { navController.popBackStack() },
            // Straight to the editor: with nothing to pick from, the list would be one
            // more empty screen between the owner and their first category.
            onCreateCategory = { kind -> navController.navigate(Destination.CategoryEdit(kind = kind)) },
            onCreateAccount = { navController.navigate(Destination.AccountEdit()) },
        )
    }
}

/** The accounts tab, one account on its own, and the form behind both. */
private fun NavGraphBuilder.accountRoutes(navController: NavHostController) {
    composable<Destination.Accounts> {
        AccountsScreen(
            onNewAccount = {
                navController.navigate(Destination.AccountEdit()) { launchSingleTop = true }
            },
            onOpenAccount = { id ->
                navController.navigate(Destination.AccountDetail(id)) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.AccountDetail> {
        AccountDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { id ->
                navController.navigate(Destination.AccountEdit(accountId = id)) {
                    launchSingleTop = true
                }
            },
            onOpenTransaction = { id ->
                navController.navigate(Destination.TransactionForm(transactionId = id)) {
                    launchSingleTop = true
                }
            },
        )
    }
    composable<Destination.AccountEdit> {
        AccountEditScreen(onDone = { navController.popBackStack() })
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
