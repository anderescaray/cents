package io.github.anderescaray.cents.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.anderescaray.cents.ui.components.CentsTabBar
import io.github.anderescaray.cents.ui.components.CentsTabItem
import io.github.anderescaray.cents.ui.feature.accounts.AccountDetailScreen
import io.github.anderescaray.cents.ui.feature.accounts.AccountEditScreen
import io.github.anderescaray.cents.ui.feature.accounts.AccountsScreen
import io.github.anderescaray.cents.ui.feature.categories.CategoriesScreen
import io.github.anderescaray.cents.ui.feature.categories.CategoryEditScreen
import io.github.anderescaray.cents.ui.feature.filters.TransactionFiltersScreen
import io.github.anderescaray.cents.ui.feature.reports.ReportsScreen
import io.github.anderescaray.cents.ui.feature.settings.SettingsScreen
import io.github.anderescaray.cents.ui.feature.transactionform.TransactionFormScreen
import io.github.anderescaray.cents.ui.feature.transactions.TransactionsScreen
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import io.github.anderescaray.cents.ui.navigation.routes

/** How long a push or pop takes. iOS uses a brisk slide, not a slow fade. */
private const val PUSH_DURATION_MS = 320

@Composable
fun CentsApp(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStack.collectAsState()
    val currentTab = backStack.selectedTab()

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
            onSelect = { key -> navController.switchToTab(TopLevelTab.valueOf(key).destination) },
        )
    }
}

/**
 * The tab a destination belongs to: the tab it *is*, or the tab it was pushed inside.
 *
 * Null for a screen more than one tab can reach — see [TopLevelTab.owns], which is where that
 * is decided, so the bar and the slide can never disagree about who owns what.
 */
internal fun NavDestination.owningTab(): TopLevelTab? =
    TopLevelTab.entries.firstOrNull { tab -> tab.routes.any { hasRoute(it) } }

/**
 * The tab the bar shows as selected: the nearest screen *down* the stack that a tab owns.
 *
 * Reading only the top of the stack is what used to light *Transactions* over an account's
 * detail screen — a pushed destination matched no tab and the bar fell back to the first one.
 * Reading the whole stack answers the screens two tabs can reach as well, and answers them
 * correctly rather than by convention: the movement form sits above an account's detail when it
 * was opened from an account, and directly above the list when it was opened from the list, so
 * the same screen is filed under wherever you actually opened it.
 *
 * **Remembering the last tab instead would be wrong, and it is the version this branch shipped
 * first.** Each tab keeps its own saved stack (`switchToTab`), so leaving Accounts with a
 * movement form open, visiting Reports and coming back restores that form — and a single
 * remembered value would still be saying *Reports*, a tab the owner had just left. The stack is
 * the thing that knows, and it survives a rotation and process death without being saved
 * separately.
 *
 * Empty only before the graph is set, where the start destination is the honest answer.
 */
internal fun List<NavBackStackEntry>.selectedTab(): TopLevelTab =
    asReversed().firstNotNullOfOrNull { it.destination.owningTab() } ?: TopLevelTab.TRANSACTIONS

/**
 * Which way this navigation is travelling, so the slide agrees with it.
 *
 * Every `navigate()` is a push as far as the `NavHost` is concerned, and a tab switch is a
 * `navigate()` — so with a single direction the animation said "forward" even when the tab
 * bar had moved you *left*, which reads as wrong every time and is the animation seen most.
 * `TopLevelTab` already orders the tabs, so the sign is knowable: moving to a lower tab is
 * travelling back, and the screen should arrive from the side it went out to.
 *
 * It asks [owningTab] rather than "is this destination a tab", so switching to a tab whose
 * saved stack has a detail screen on top travels the way the bar moved: the screen arriving is
 * an account's detail, but it is Accounts you went to. A push inside a tab has the same tab on
 * both sides and stays a forward slide, and so does anything involving a screen no tab owns.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.travelDirection() =
    slideDirectionFor(
        from = initialState.destination.owningTab(),
        to = targetState.destination.owningTab(),
    )

/**
 * Separated from the transition so it can be tested: reading a slide direction off a running
 * animation is not something a JVM test can do, but the rule deciding it is arithmetic.
 */
internal fun slideDirectionFor(
    from: TopLevelTab?,
    to: TopLevelTab?,
): AnimatedContentTransitionScope.SlideDirection =
    if (from != null && to != null && to.ordinal < from.ordinal) {
        AnimatedContentTransitionScope.SlideDirection.End
    } else {
        AnimatedContentTransitionScope.SlideDirection.Start
    }

/** Tabs switch, they do not stack: one entry per tab, state preserved. */
internal fun NavHostController.switchToTab(destination: Destination) {
    navigate(destination) {
        popUpTo(Destination.Transactions) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * A drill-down arrives at the movements list itself, never at whatever was left open on top
 * of it. `switchToTab` restores the tab's saved stack, and that stack can be holding the
 * filters screen — which is precisely where you go to read the filter a previous drill-down
 * applied. Restoring it would answer "show me this category" with the filter form.
 */
private fun NavHostController.openMovementsList() {
    switchToTab(Destination.Transactions)
    popBackStack(Destination.Transactions, inclusive = false)
}

/** The navigation stack, with iOS push and pop transitions. */
@Composable
private fun CentsNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Destination.Transactions,
        // Horizontal push and pop, the way an iOS navigation stack moves.
        enterTransition = {
            slideIntoContainer(travelDirection(), animationSpec = tween(PUSH_DURATION_MS))
        },
        exitTransition = {
            slideOutOfContainer(travelDirection(), animationSpec = tween(PUSH_DURATION_MS))
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
            SettingsScreen(onOpenCategories = { navController.navigate(Destination.Categories) })
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
