package io.github.anderescaray.cents.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.capture.OpenCapturedFromNotification
import io.github.anderescaray.cents.ui.components.CentsTabBar
import io.github.anderescaray.cents.ui.components.CentsTabItem
import io.github.anderescaray.cents.ui.components.EntryKind
import io.github.anderescaray.cents.ui.feature.accounts.AccountBalanceScreen
import io.github.anderescaray.cents.ui.feature.accounts.AccountDetailScreen
import io.github.anderescaray.cents.ui.feature.accounts.AccountEditScreen
import io.github.anderescaray.cents.ui.feature.accounts.AccountsScreen
import io.github.anderescaray.cents.ui.feature.budgets.BudgetEditScreen
import io.github.anderescaray.cents.ui.feature.budgets.BudgetsScreen
import io.github.anderescaray.cents.ui.feature.capture.captureRoutes
import io.github.anderescaray.cents.ui.feature.categories.CategoriesScreen
import io.github.anderescaray.cents.ui.feature.categories.CategoryEditScreen
import io.github.anderescaray.cents.ui.feature.filters.TransactionFiltersScreen
import io.github.anderescaray.cents.ui.feature.groups.GroupDetailScreen
import io.github.anderescaray.cents.ui.feature.groups.GroupEditScreen
import io.github.anderescaray.cents.ui.feature.groups.GroupsScreen
import io.github.anderescaray.cents.ui.feature.home.HomeScreen
import io.github.anderescaray.cents.ui.feature.inbox.InboxScreen
import io.github.anderescaray.cents.ui.feature.investments.HoldingDetailScreen
import io.github.anderescaray.cents.ui.feature.investments.HoldingEditScreen
import io.github.anderescaray.cents.ui.feature.investments.PortfolioScreen
import io.github.anderescaray.cents.ui.feature.investments.PriceUpdateScreen
import io.github.anderescaray.cents.ui.feature.investments.TradeFormScreen
import io.github.anderescaray.cents.ui.feature.recurring.GenerateDueMovements
import io.github.anderescaray.cents.ui.feature.recurring.PendingChargesScreen
import io.github.anderescaray.cents.ui.feature.recurring.RuleFormScreen
import io.github.anderescaray.cents.ui.feature.recurring.SubscriptionsScreen
import io.github.anderescaray.cents.ui.feature.reports.ReportsCustomizeScreen
import io.github.anderescaray.cents.ui.feature.reports.ReportsScreen
import io.github.anderescaray.cents.ui.feature.settings.SettingsScreen
import io.github.anderescaray.cents.ui.feature.transactionform.TransactionFormScreen
import io.github.anderescaray.cents.ui.feature.transactions.TransactionsScreen
import io.github.anderescaray.cents.ui.feature.transferform.TransferFormScreen
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.FullScreenForms
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import io.github.anderescaray.cents.ui.theme.CentsTheme

@Composable
fun CentsApp(
    /** True when a reminder asking for a figure was tapped: go straight to the screen that takes it. */
    openPending: Boolean = false,
    /** The movement a capture notification was tapped for, opened once and then forgotten. */
    openCapturedTransactionId: Long? = null,
    onCapturedOpened: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val backStack by navController.currentBackStack.collectAsState()
    val currentTab = backStack.selectedTab()
    val top = backStack.lastOrNull()?.destination
    val onForm = top?.let { FullScreenForms.any { route -> it.hasRoute(route) } } == true
    // Swiping changes tab only on a tab's own screen: on anything pushed above it, a sideways
    // swipe would leave a half-finished form or a detail without going back through it.
    val onTabRoot = currentTab != null && top?.hasRoute(currentTab.destination::class) == true

    // Before anything is drawn from the database: whatever repeats has to be in it first, or
    // the list opens a frame short of the rent.
    GenerateDueMovements()

    OpenPendingFromNotification(openPending, navController)
    OpenCapturedFromNotification(
        transactionId = openCapturedTransactionId,
        // On top of the movements list, so back from the form shows it among the rest.
        onOpenTransaction = { id ->
            navController.openMovementsList()
            navController.navigate(Destination.TransactionForm(transactionId = id)) { launchSingleTop = true }
        },
        onOpened = onCapturedOpened,
    )

    val tabs =
        TopLevelTab.entries.map { tab ->
            CentsTabItem(
                key = tab.name,
                label = stringResource(tab.labelRes),
                iconRes = tab.iconRes,
            )
        }

    // Test tags are exposed as resource ids so that the benchmarks, which drive the app from
    // outside its process, can find a tab without depending on the phone's language.
    // The canvas is painted here, under every screen, and is what shows through anything not
    // opaque edge to edge: a tab change's cross-fade, a sheet's rounded corners. Without it the
    // window showed instead, and a tab change flashed white. The window is the canvas too
    // (themes.xml), for the frames before this one exists.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .semantics { testTagsAsResourceId = true },
    ) {
        SwipeableTabs(
            navController = navController,
            swipeFrom = currentTab.takeIf { onTabRoot },
            modifier = Modifier.weight(1f),
        ) { slideTabs -> CentsNavHost(navController = navController, slideTabs = slideTabs) }

        // Not under a form: its keypad or its own button is where the capsule would be.
        AnimatedVisibility(
            visible = currentTab != null && !onForm,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            CentsTabBar(
                items = tabs,
                selectedKey = currentTab?.name.orEmpty(),
                onSelect = { key -> navController.selectTab(TopLevelTab.valueOf(key), currentTab) },
                onAdd = { navController.openNewMovement(TransactionKind.EXPENSE) },
                addLabel = stringResource(R.string.action_add_movement),
            )
        }
    }
}

/** The movement form, adding: from the capsule's button, or one of Home's three. */
private fun NavHostController.openNewMovement(kind: TransactionKind) {
    navigate(Destination.TransactionForm(kind = kind)) { launchSingleTop = true }
}

/**
 * Lands a tapped reminder on the screen that takes the figure.
 *
 * Keyed on the flag rather than run once, because a second notification tapped while the app is
 * already open arrives through `onNewIntent` and has to move the screen again — and a tap on the
 * *same* one twice should be idempotent, which `launchSingleTop` makes it.
 */
@Composable
private fun OpenPendingFromNotification(
    openPending: Boolean,
    navController: NavHostController,
) {
    LaunchedEffect(openPending) {
        if (openPending) {
            navController.navigate(Destination.PendingCharges) { launchSingleTop = true }
        }
    }
}

/**
 * The navigation stack: pushes slide, tab changes fade (or slide, when [slideTabs] says the change
 * came from a swipe), and the movement forms rise.
 */
@Composable
private fun CentsNavHost(
    navController: NavHostController,
    slideTabs: () -> Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Home,
        enterTransition = { arriving(slideTabs = slideTabs()) },
        exitTransition = { leaving(slideTabs = slideTabs()) },
        // A swipe back to Home is a pop (switchToTab pops to it), and slides like any other swipe.
        popEnterTransition = { arriving(BACKWARDS, slideTabs = slideTabs()) },
        popExitTransition = { leaving(BACKWARDS, slideTabs = slideTabs()) },
    ) {
        composable<Destination.Home> {
            HomeScreen(
                onAdd = { kind -> navController.openNewMovement(kind) },
                onTransfer = { navController.navigate(Destination.TransferForm()) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Destination.Settings) { launchSingleTop = true } },
                onOpenInbox = { navController.navigate(Destination.Inbox) { launchSingleTop = true } },
                onOpenTransaction = { id ->
                    navController.navigate(Destination.TransactionForm(transactionId = id)) { launchSingleTop = true }
                },
                onOpenPending = { navController.navigate(Destination.PendingCharges) { launchSingleTop = true } },
                onOpenMovements = { navController.openMovementsList() },
                onCreateAccount = { navController.navigate(Destination.AccountEdit()) { launchSingleTop = true } },
            )
        }
        transactionRoutes(navController)
        // The uncategorized inbox (PRD §4.6), pushed from Home or from the movements list.
        composable<Destination.Inbox> {
            InboxScreen(
                onBack = { navController.popBackStack() },
                // The full form, for a movement that needs more than a category.
                onOpenTransaction = { id ->
                    navController.navigate(Destination.TransactionForm(transactionId = id)) { launchSingleTop = true }
                },
            )
        }
        accountRoutes(navController)
        categoryRoutes(navController)
        recurringRoutes(navController)
        groupRoutes(navController)
        captureRoutes(navController)
        investmentRoutes(navController)

        composable<Destination.Reports> {
            ReportsScreen(
                // The same move the tab bar makes, because a drill-down *is* switching to
                // the movements tab — with the filter already applied. Pushing the list on
                // top instead would leave two of it on the stack, one filtered and one not.
                onOpenMovements = { navController.openMovementsList() },
                onOpenBudgets = { navController.navigate(Destination.Budgets) { launchSingleTop = true } },
                onCustomize = { navController.navigate(Destination.ReportsCustomize) { launchSingleTop = true } },
            )
        }
        composable<Destination.ReportsCustomize> {
            ReportsCustomizeScreen(onBack = { navController.popBackStack() })
        }
        composable<Destination.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenCategories = { navController.navigate(Destination.Categories) },
                onOpenSubscriptions = { navController.navigate(Destination.Subscriptions) },
                onOpenGroups = { navController.navigate(Destination.Groups) },
                onOpenCapture = { navController.navigate(Destination.Capture) },
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
            // A transfer leg opens the transfer, not the movement form: editing one side of
            // a pair on a form that knows nothing about the other is how the two stop
            // agreeing about how much money moved.
            onOpenTransfer = { id ->
                navController.navigate(Destination.TransferForm(transferId = id)) {
                    launchSingleTop = true
                }
            },
            // And a purchase opens the trade behind it, for the same reason: the money is a
            // consequence of the units, and editing it on its own would make the two disagree.
            onOpenTrade = { holdingId, tradeId ->
                navController.navigate(Destination.TradeForm(holdingId = holdingId, tradeId = tradeId)) {
                    launchSingleTop = true
                }
            },
            onOpenFilters = {
                navController.navigate(Destination.TransactionFilters) { launchSingleTop = true }
            },
            onOpenInbox = { navController.navigate(Destination.Inbox) { launchSingleTop = true } },
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
            onCreateGroup = { navController.navigate(Destination.GroupEdit()) { launchSingleTop = true } },
            onSwitchToTransfer = { amount ->
                navController.switchForm<Destination.TransactionForm>(
                    Destination.TransferForm(amountInput = amount),
                )
            },
        )
    }
    composable<Destination.TransferForm> {
        TransferFormScreen(
            onDone = { navController.popBackStack() },
            onSwitchKind = { kind, amount ->
                navController.switchForm<Destination.TransferForm>(
                    Destination.TransactionForm(
                        amountInput = amount,
                        kind = if (kind == EntryKind.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE,
                    ),
                )
            },
            onCreateAccount = { navController.navigate(Destination.AccountEdit()) },
        )
    }
}

/**
 * Replaces the entry form on screen with the other one, rather than stacking it on top.
 *
 * The two forms are one choice with three settings, so back from either has to return where
 * the owner came from — the list, or an account — and not to the form they were just looking
 * at. Popping the one being left is what makes the segmented control feel like a switch
 * rather than a journey, and what stops a few changes of mind building a pile of forms.
 */
private inline fun <reified T : Destination> NavHostController.switchForm(destination: Destination) {
    navigate(destination) {
        launchSingleTop = true
        popUpTo<T> { inclusive = true }
    }
}

/** The accounts tab, one account on its own, and the form behind both. */
private fun NavGraphBuilder.accountRoutes(navController: NavHostController) {
    composable<Destination.Accounts> {
        AccountsScreen(
            onOpenInvestments = { navController.navigate(Destination.Investments) { launchSingleTop = true } },
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
            onOpenTransfer = { id ->
                navController.navigate(Destination.TransferForm(transferId = id)) {
                    launchSingleTop = true
                }
            },
            onOpenTrade = { holdingId, tradeId ->
                navController.navigate(Destination.TradeForm(holdingId = holdingId, tradeId = tradeId)) {
                    launchSingleTop = true
                }
            },
            onSetBalance = { id ->
                navController.navigate(Destination.AccountBalance(accountId = id)) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.AccountEdit> {
        AccountEditScreen(onDone = { navController.popBackStack() })
    }
    composable<Destination.AccountBalance> {
        AccountBalanceScreen(onDone = { navController.popBackStack() })
    }
}

/** The portfolio, one holding on its own, and the two forms behind them. */
private fun NavGraphBuilder.investmentRoutes(navController: NavHostController) {
    composable<Destination.Investments> {
        PortfolioScreen(
            onBack = { navController.popBackStack() },
            onAddHolding = {
                navController.navigate(Destination.HoldingEdit()) { launchSingleTop = true }
            },
            onOpenHolding = { id ->
                navController.navigate(Destination.HoldingDetail(id)) { launchSingleTop = true }
            },
            onUpdatePrices = {
                navController.navigate(Destination.PriceUpdate) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.PriceUpdate> {
        PriceUpdateScreen(onDone = { navController.popBackStack() })
    }
    composable<Destination.HoldingDetail> {
        HoldingDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { id ->
                navController.navigate(Destination.HoldingEdit(holdingId = id)) { launchSingleTop = true }
            },
            onAddTrade = { holdingId ->
                navController.navigate(Destination.TradeForm(holdingId = holdingId)) { launchSingleTop = true }
            },
            onCorrect = { holdingId ->
                navController.navigate(Destination.TradeForm(holdingId = holdingId, correcting = true)) {
                    launchSingleTop = true
                }
            },
            onOpenTrade = { holdingId, tradeId ->
                navController.navigate(Destination.TradeForm(holdingId = holdingId, tradeId = tradeId)) {
                    launchSingleTop = true
                }
            },
        )
    }
    composable<Destination.HoldingEdit> {
        HoldingEditScreen(
            onDone = { navController.popBackStack() },
            // A new holding can only live in a broker (§10), so the account it makes is one.
            onCreateBrokerAccount = {
                navController.navigate(Destination.AccountEdit(type = AccountType.BROKER)) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.TradeForm> {
        TradeFormScreen(onDone = { navController.popBackStack() })
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

/** What repeats, and the form that defines one. */
private fun NavGraphBuilder.recurringRoutes(navController: NavHostController) {
    composable<Destination.Subscriptions> {
        SubscriptionsScreen(
            onBack = { navController.popBackStack() },
            onNewRule = { navController.navigate(Destination.RuleForm()) { launchSingleTop = true } },
            onOpenPending = {
                navController.navigate(Destination.PendingCharges) { launchSingleTop = true }
            },
            onEditRule = { id ->
                navController.navigate(Destination.RuleForm(ruleId = id)) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.PendingCharges> {
        PendingChargesScreen(onDone = { navController.popBackStack() })
    }
    composable<Destination.RuleForm> {
        RuleFormScreen(
            onDone = { navController.popBackStack() },
            onCreateAccount = { navController.navigate(Destination.AccountEdit()) },
        )
    }
    composable<Destination.Budgets> {
        BudgetsScreen(
            onBack = { navController.popBackStack() },
            onNewBudget = { navController.navigate(Destination.BudgetEdit()) { launchSingleTop = true } },
            onEditBudget = { id ->
                navController.navigate(Destination.BudgetEdit(budgetId = id)) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.BudgetEdit> {
        BudgetEditScreen(onDone = { navController.popBackStack() })
    }
}

/** Occasions across categories (PRD §4.9): the list, one group, and its editor. */
private fun NavGraphBuilder.groupRoutes(navController: NavHostController) {
    composable<Destination.Groups> {
        GroupsScreen(
            onBack = { navController.popBackStack() },
            onNewGroup = { navController.navigate(Destination.GroupEdit()) { launchSingleTop = true } },
            onOpenGroup = { id ->
                navController.navigate(Destination.GroupDetail(groupId = id)) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.GroupDetail> {
        GroupDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { id ->
                navController.navigate(Destination.GroupEdit(groupId = id)) { launchSingleTop = true }
            },
            // The tab switch a Reports drill-down makes, for the same reason: the movements
            // are the list, narrowed, rather than a second copy of it pushed on top.
            onOpenMovements = { navController.openMovementsList() },
        )
    }
    composable<Destination.GroupEdit> {
        GroupEditScreen(
            onDone = { navController.popBackStack() },
            // Deleted from under its detail screen: back to the list, past a group that is gone.
            onDeleted = { navController.popBackStack(Destination.Groups, inclusive = false) },
        )
    }
}
