package io.github.anderescaray.cents.ui.feature.settings

import android.app.PendingIntent
import androidx.compose.runtime.Immutable
import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.domain.model.DriveBackupState

enum class DriveBackupStep {
    IDLE,
    AUTHORIZING,
    WAITING_FOR_AUTHORIZATION,
    CHANGING_AUTOMATIC,
    BACKING_UP,
    LISTING,
    DOWNLOADING,
}

enum class DriveBackupMessage {
    BACKED_UP,
    CONNECTED,
    AUTHORIZATION_CANCELLED,
}

@Immutable
data class DriveBackupUiState(
    val preferences: DriveBackupState = DriveBackupState(),
    val loaded: Boolean = false,
    val step: DriveBackupStep = DriveBackupStep.IDLE,
    val resolution: PendingIntent? = null,
    val showBackups: Boolean = false,
    val backups: List<DriveBackup> = emptyList(),
    val message: DriveBackupMessage? = null,
    val failure: DriveBackupFailureKind? = null,
) {
    val isBusy: Boolean get() = step != DriveBackupStep.IDLE
    val canAct: Boolean get() = loaded && !isBusy
}

@Immutable
data class DriveBackupActions(
    val onAutomaticChanged: (Boolean) -> Unit = {},
    val onBackUp: () -> Unit = {},
    val onReconnect: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onChooseBackup: (DriveBackup) -> Unit = {},
    val onCloseBackups: () -> Unit = {},
)
