package io.github.anderescaray.cents.ui.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun DriveBackupAuthorization(
    state: DriveBackupUiState,
    viewModel: DriveBackupViewModel,
) {
    val launcher =
        rememberLauncherForActivityResult(StartIntentSenderForResult()) {
            viewModel.authorizationResult(it.resultCode, it.data)
        }
    LaunchedEffect(state.resolution) {
        val pendingIntent = state.resolution ?: return@LaunchedEffect
        viewModel.resolutionLaunched()
        try {
            launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } catch (failure: ActivityNotFoundException) {
            viewModel.authorizationLaunchFailed(failure)
        }
    }
}
