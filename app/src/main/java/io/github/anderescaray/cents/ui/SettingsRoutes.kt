package io.github.anderescaray.cents.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.anderescaray.cents.ui.feature.settings.SettingsScreen
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.TopLevelTab

/** The tab whose header opened Settings, kept on Settings' own entry so it outlives the process. */
private const val SETTINGS_ORIGIN = "settingsOrigin"

/**
 * Settings, from any tab's header. It lives in Home's stack wherever it is opened from, so there is
 * only ever one: a restore or a Drive backup running in it is the same screen whichever tab's
 * button is tapped next, and two of them could not each start one. From another tab this switches
 * to Home, keeping where that tab was, and the capsule goes with it.
 *
 * Home's saved stack may already hold Settings, left open with something on top of it; that one is
 * returned to rather than a second pushed above it.
 *
 * **The tab that asked is remembered**, so back from Settings returns to it ([leaveSettings]).
 */
internal fun NavHostController.openSettings() {
    val origin = currentBackStack.value.selectedTab() ?: TopLevelTab.HOME
    if (origin != TopLevelTab.HOME) switchToTab(Destination.Home)
    if (!popBackStack(Destination.Settings, inclusive = false)) {
        navigate(Destination.Settings) { launchSingleTop = true }
    }
    settingsEntry()?.savedStateHandle?.set(SETTINGS_ORIGIN, origin.name)
}

/**
 * Back from Settings: off Home's stack, and on to the tab that opened it, as it was left. Settings
 * is popped before the switch, so Home is saved at its first screen rather than with Settings
 * still in it.
 */
internal fun NavHostController.leaveSettings(origin: TopLevelTab) {
    if (popBackStack(Destination.Settings, inclusive = true) && origin != TopLevelTab.HOME) {
        switchToTab(origin.destination)
    }
}

/** Back from Settings goes to Home from now on: see `selectTab`. */
internal fun NavHostController.forgetSettingsOrigin() {
    settingsEntry()?.savedStateHandle?.set(SETTINGS_ORIGIN, TopLevelTab.HOME.name)
}

/** The tab Settings goes back to, Home when none was recorded. */
internal fun NavBackStackEntry.settingsOrigin(): TopLevelTab = tabNamed(savedStateHandle[SETTINGS_ORIGIN])

private fun tabNamed(name: String?): TopLevelTab =
    TopLevelTab.entries.firstOrNull { it.name == name } ?: TopLevelTab.HOME

private fun NavHostController.settingsEntry(): NavBackStackEntry? =
    currentBackStack.value.lastOrNull { it.destination.hasRoute(Destination.Settings::class) }

/** Settings itself. What lies behind it has its own routes. */
internal fun NavGraphBuilder.settingsRoutes(navController: NavHostController) {
    composable<Destination.Settings> { entry -> SettingsRoute(entry, navController) }
}

@Composable
private fun SettingsRoute(
    entry: NavBackStackEntry,
    navController: NavHostController,
) {
    val originName by entry.savedStateHandle
        .getStateFlow(SETTINGS_ORIGIN, TopLevelTab.HOME.name)
        .collectAsStateWithLifecycle()
    val origin = tabNamed(originName)
    val lifecycle by entry.lifecycle.currentStateAsState()
    val leave = { navController.leaveSettings(origin) }
    // Back to Home is the ordinary pop, predictive gesture and all; to another tab it is a switch,
    // which only this screen knows to make. Only while Settings is the screen shown, never while a
    // push on top of it is still animating in.
    BackHandler(enabled = origin != TopLevelTab.HOME && lifecycle.isAtLeast(Lifecycle.State.RESUMED), onBack = leave)
    SettingsScreen(
        onBack = leave,
        onOpenCategories = { navController.navigate(Destination.Categories) },
        onOpenSubscriptions = { navController.navigate(Destination.Subscriptions) },
        onOpenGroups = { navController.navigate(Destination.Groups) },
        onOpenCapture = { navController.navigate(Destination.Capture) },
        backLabel = origin.labelRes,
    )
}
