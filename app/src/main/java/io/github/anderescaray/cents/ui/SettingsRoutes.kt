package io.github.anderescaray.cents.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.anderescaray.cents.ui.feature.settings.SettingsScreen
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab

/**
 * Settings, from any tab's header. It lives in Home's stack wherever it is opened from, so there is
 * only ever one: a restore or a Drive backup running in it is the same screen whichever tab's
 * button is tapped next, and two of them could not each start one. From another tab this switches
 * to Home, keeping where that tab was, and the capsule goes with it.
 *
 * Home's saved stack may already hold Settings, left open with something on top of it; that one is
 * returned to rather than a second pushed above it.
 */
internal fun NavHostController.openSettings() {
    if (currentBackStack.value.selectedTab() != TopLevelTab.HOME) switchToTab(Destination.Home)
    if (!popBackStack(Destination.Settings, inclusive = false)) {
        navigate(Destination.Settings) { launchSingleTop = true }
    }
}

/** Settings itself. What lies behind it has its own routes. */
internal fun NavGraphBuilder.settingsRoutes(navController: NavHostController) {
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
