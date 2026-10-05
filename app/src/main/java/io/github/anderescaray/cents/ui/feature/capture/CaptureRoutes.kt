package io.github.anderescaray.cents.ui.feature.capture

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.anderescaray.cents.domain.model.CaptureWordList
import io.github.anderescaray.cents.ui.navigation.Destination

/** Capture from notifications (PRD §4.6): its settings, the app picker, the word lists and the try-out. */
internal fun NavGraphBuilder.captureRoutes(navController: NavHostController) {
    composable<Destination.Capture> {
        CaptureScreen(
            onBack = { navController.popBackStack() },
            onAddApp = { navController.navigate(Destination.CaptureApps) { launchSingleTop = true } },
            onOpenWords = { kind, packageName ->
                navController.navigate(Destination.CaptureWords(CaptureWordList.of(kind), packageName)) {
                    launchSingleTop = true
                }
            },
            onOpenIgnoreWords = {
                navController.navigate(Destination.CaptureWords(CaptureWordList.IGNORE)) { launchSingleTop = true }
            },
            onTry = { navController.navigate(Destination.CaptureTry) { launchSingleTop = true } },
        )
    }
    composable<Destination.CaptureApps> {
        CaptureAppsScreen(onDone = { navController.popBackStack() })
    }
    composable<Destination.CaptureWords> {
        CaptureWordsScreen(onBack = { navController.popBackStack() })
    }
    composable<Destination.CaptureTry> {
        CaptureTryScreen(onBack = { navController.popBackStack() })
    }
}
