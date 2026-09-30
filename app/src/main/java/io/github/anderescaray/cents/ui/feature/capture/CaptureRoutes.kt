package io.github.anderescaray.cents.ui.feature.capture

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.anderescaray.cents.ui.navigation.Destination

/** Capture from notifications (PRD §4.6): its settings, the app picker and the two word lists. */
internal fun NavGraphBuilder.captureRoutes(navController: NavHostController) {
    composable<Destination.Capture> {
        CaptureScreen(
            onBack = { navController.popBackStack() },
            onAddApp = { navController.navigate(Destination.CaptureApps) { launchSingleTop = true } },
            onOpenWords = { kind -> navController.navigate(Destination.CaptureWords(kind)) { launchSingleTop = true } },
        )
    }
    composable<Destination.CaptureApps> {
        CaptureAppsScreen(onDone = { navController.popBackStack() })
    }
    composable<Destination.CaptureWords> {
        CaptureWordsScreen(onBack = { navController.popBackStack() })
    }
}
