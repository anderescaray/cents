package io.github.anderescaray.cents.ui

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.anderescaray.cents.ui.feature.settings.SettingsScreen
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab

/**
 * Settings, pushed inside the tab it was opened from, so back returns there and the capsule stays
 * on that tab. Single top, so a second tap during the slide does not stack a second copy.
 */
internal fun NavHostController.openSettings() {
    navigate(Destination.Settings) { launchSingleTop = true }
}

/** Settings, which every tab's header opens. What lies behind it has its own routes. */
internal fun NavGraphBuilder.settingsRoutes(navController: NavHostController) {
    composable<Destination.Settings> {
        // The tab underneath, which every tab's header can be: back says where it goes.
        val from = remember { navController.previousBackStackEntry?.destination?.owningTab() ?: TopLevelTab.HOME }
        SettingsScreen(
            onBack = { navController.popBackStack() },
            backLabel = from.labelRes,
            onOpenCategories = { navController.navigate(Destination.Categories) },
            onOpenSubscriptions = { navController.navigate(Destination.Subscriptions) },
            onOpenGroups = { navController.navigate(Destination.Groups) },
            onOpenCapture = { navController.navigate(Destination.Capture) },
        )
    }
}
