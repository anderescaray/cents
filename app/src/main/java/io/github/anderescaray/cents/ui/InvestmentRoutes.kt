package io.github.anderescaray.cents.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.ui.feature.investments.AssetActions
import io.github.anderescaray.cents.ui.feature.investments.AssetScreen
import io.github.anderescaray.cents.ui.feature.investments.HoldingEditScreen
import io.github.anderescaray.cents.ui.feature.investments.InvestmentAccountScreen
import io.github.anderescaray.cents.ui.feature.investments.InvestmentsScreen
import io.github.anderescaray.cents.ui.feature.investments.TradeFormScreen
import io.github.anderescaray.cents.ui.feature.investments.ValueUpdateScreen
import io.github.anderescaray.cents.ui.navigation.Destination

/** Invested, one broker account, one asset, and the forms behind them (D40). */
internal fun NavGraphBuilder.investmentRoutes(navController: NavHostController) {
    composable<Destination.Investments> {
        InvestmentsScreen(
            onBack = { navController.popBackStack() },
            onOpenAccount = { id ->
                navController.navigate(Destination.InvestmentAccount(id)) { launchSingleTop = true }
            },
            onCreateBrokerAccount = {
                navController.navigate(Destination.AccountEdit(type = AccountType.BROKER)) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.InvestmentAccount> {
        InvestmentAccountScreen(
            onBack = { navController.popBackStack() },
            onAddAsset = { accountId ->
                navController.navigate(Destination.HoldingEdit(accountId = accountId)) { launchSingleTop = true }
            },
            onOpenAsset = { id -> navController.navigate(Destination.HoldingDetail(id)) { launchSingleTop = true } },
            onUpdateValues = { accountId ->
                navController.navigate(Destination.ValueUpdate(accountId = accountId)) { launchSingleTop = true }
            },
            onOpenMovements = { accountId ->
                navController.navigate(Destination.AccountDetail(accountId)) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.HoldingDetail> {
        AssetScreen(
            onBack = { navController.popBackStack() },
            actions = navController.assetActions(),
        )
    }
    investmentFormRoutes(navController)
}

/** The forms behind the investment screens: an asset, a purchase or sale, the week's values. */
private fun NavGraphBuilder.investmentFormRoutes(navController: NavHostController) {
    composable<Destination.HoldingEdit> {
        HoldingEditScreen(
            onDone = { navController.popBackStack() },
            // A new asset can only live in a broker, so the account it makes is one.
            onCreateBrokerAccount = {
                navController.navigate(Destination.AccountEdit(type = AccountType.BROKER)) { launchSingleTop = true }
            },
        )
    }
    composable<Destination.TradeForm> {
        TradeFormScreen(onDone = { navController.popBackStack() })
    }
    composable<Destination.ValueUpdate> {
        ValueUpdateScreen(onDone = { navController.popBackStack() })
    }
}

/** Where the asset screen leads. */
private fun NavHostController.assetActions() =
    AssetActions(
        onEdit = { id ->
            navigate(Destination.HoldingEdit(holdingId = id)) {
                launchSingleTop = true
            }
        },
        onUpdateValue = { accountId, holdingId ->
            navigate(Destination.ValueUpdate(accountId = accountId, holdingId = holdingId)) {
                launchSingleTop = true
            }
        },
        onBuy = { id ->
            navigate(Destination.TradeForm(holdingId = id)) {
                launchSingleTop = true
            }
        },
        onSell = { id ->
            navigate(Destination.TradeForm(holdingId = id, selling = true)) {
                launchSingleTop = true
            }
        },
        onOpenTrade = { holdingId, tradeId ->
            navigate(Destination.TradeForm(holdingId = holdingId, tradeId = tradeId)) {
                launchSingleTop = true
            }
        },
    )

/** The Friday reminder lands on Invested, as a reminder asking for a figure lands on its screen. */
@Composable
internal fun OpenInvestmentsFromNotification(
    open: Boolean,
    navController: NavHostController,
) {
    LaunchedEffect(open) {
        if (open) navController.navigate(Destination.Investments) { launchSingleTop = true }
    }
}
