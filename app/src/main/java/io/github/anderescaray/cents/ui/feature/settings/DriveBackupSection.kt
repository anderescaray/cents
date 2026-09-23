package io.github.anderescaray.cents.ui.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwitch
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
internal fun DriveBackupSection(
    state: DriveBackupUiState,
    actions: DriveBackupActions,
    enabled: Boolean,
) {
    val canAct = enabled && state.canAct
    CentsListSection(
        header = stringResource(R.string.drive_backup_section),
        footer = stringResource(R.string.drive_backup_footer),
    ) {
        AutomaticBackupRow(state.preferences.automaticEnabled, canAct, actions.onAutomaticChanged)
        CentsListRow(
            title = stringResource(R.string.drive_backup_now),
            subtitle = driveOperationMessage(state),
            modifier = backupProgressModifier(state.isBusy),
            onClick = actions.onBackUp.takeIf { canAct },
        )
        DriveBackupStatus(state, actions.onReconnect.takeIf { canAct })
        CentsListRow(
            title = stringResource(R.string.drive_backup_restore),
            showChevron = true,
            showSeparator = false,
            onClick = actions.onRestore.takeIf { canAct },
        )
    }
}

@Composable
private fun AutomaticBackupRow(
    enabled: Boolean,
    canChange: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    val label = stringResource(R.string.drive_backup_automatic)
    CentsListRow(
        title = label,
        trailing = {
            CentsSwitch(
                checked = enabled,
                onCheckedChange = onChanged,
                enabled = canChange,
                modifier =
                    Modifier.semantics {
                        contentDescription = label
                        toggleableState = if (enabled) ToggleableState.On else ToggleableState.Off
                    },
            )
        },
    )
}

@Composable
private fun DriveBackupStatus(
    state: DriveBackupUiState,
    onReconnect: (() -> Unit)?,
) {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(TIME_REFRESH_MILLIS)
            value = Instant.now()
        }
    }
    val lastSuccess = relativeBackupTime(state.preferences.lastSuccessAt, now)
    CentsListRow(title = stringResource(R.string.drive_backup_last), subtitle = lastSuccess)
    val failure = state.failure ?: state.preferences.lastFailure?.kind
    if (failure != null) {
        val failedAt = state.preferences.lastFailure?.at
        val whenFailed = relativeBackupTime(failedAt, now)
        val reason = driveFailureMessage(failure)
        val timedReason = stringResource(R.string.drive_backup_problem_at, reason, whenFailed)
        CentsListRow(
            title = stringResource(R.string.drive_backup_last_problem),
            subtitle = if (failedAt == null) reason else timedReason,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (failure == DriveBackupFailureKind.NEEDS_RECONNECTING) {
            CentsListRow(title = stringResource(R.string.drive_backup_reconnect), onClick = onReconnect)
        }
    }
}

@Composable
internal fun DriveBackupPicker(
    state: DriveBackupUiState,
    actions: DriveBackupActions,
) {
    if (!state.showBackups) return
    Dialog(onDismissRequest = actions.onCloseBackups) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .background(
                        CentsTheme.colors.groupedBackground,
                        RoundedCornerShape(CentsDimens.groupedCornerRadius),
                    ).padding(vertical = CentsDimens.spacingL),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
        ) {
            Text(
                text = stringResource(R.string.drive_backup_list_title),
                style = CentsTheme.typography.headline,
                color = CentsTheme.colors.label,
                modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
            )
            DriveBackupFiles(state, actions, Modifier.weight(1f, fill = false))
            Text(
                text = stringResource(R.string.drive_backup_list_footer),
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
            )
            CentsListSection {
                CentsListRow(
                    title = stringResource(R.string.action_cancel),
                    onClick = actions.onCloseBackups,
                    showSeparator = false,
                )
            }
        }
    }
}

@Composable
private fun DriveBackupFiles(
    state: DriveBackupUiState,
    actions: DriveBackupActions,
    modifier: Modifier,
) {
    CentsListSection(modifier = modifier) {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            if (state.isBusy) {
                item(key = "progress") {
                    CentsListRow(
                        title = driveOperationMessage(state).orEmpty(),
                        modifier = backupProgressModifier(true),
                        showSeparator = false,
                    )
                }
            } else {
                if (state.failure != null) {
                    item(key = "failure") { CentsListRow(title = driveFailureMessage(state.failure)) }
                    item(key = "retry") {
                        CentsListRow(title = stringResource(R.string.drive_backup_retry), onClick = actions.onRestore)
                    }
                } else if (state.backups.isEmpty()) {
                    item(key = "empty") {
                        CentsListRow(title = stringResource(R.string.drive_backup_empty), showSeparator = false)
                    }
                }
                items(state.backups, key = { it.id }) { backup ->
                    DriveBackupFileRow(backup, actions.onChooseBackup)
                }
            }
        }
    }
}

@Composable
private fun DriveBackupFileRow(
    backup: DriveBackup,
    onChoose: (DriveBackup) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val date = remember(backup.createdAt, configuration) { backupDateTime(context, backup.createdAt) }
    val size =
        remember(backup.sizeBytes, configuration) {
            backup.sizeBytes?.let { Formatter.formatFileSize(context, it) }
        }
    CentsListRow(title = date, subtitle = size, showChevron = true, onClick = { onChoose(backup) })
}

@Composable
private fun driveOperationMessage(state: DriveBackupUiState): String? {
    val resource =
        when (state.step) {
            DriveBackupStep.AUTHORIZING -> {
                R.string.drive_backup_authorizing
            }

            DriveBackupStep.WAITING_FOR_AUTHORIZATION -> {
                R.string.drive_backup_waiting
            }

            DriveBackupStep.CHANGING_AUTOMATIC -> {
                R.string.drive_backup_scheduling
            }

            DriveBackupStep.BACKING_UP -> {
                R.string.drive_backup_running
            }

            DriveBackupStep.LISTING -> {
                R.string.drive_backup_listing
            }

            DriveBackupStep.DOWNLOADING -> {
                R.string.drive_backup_downloading
            }

            DriveBackupStep.IDLE -> {
                when (state.message) {
                    DriveBackupMessage.BACKED_UP -> R.string.drive_backup_saved
                    DriveBackupMessage.CONNECTED -> R.string.drive_backup_connected
                    DriveBackupMessage.AUTHORIZATION_CANCELLED -> R.string.drive_backup_authorization_cancelled
                    null -> return null
                }
            }
        }
    return stringResource(resource)
}

@Composable
private fun driveFailureMessage(kind: DriveBackupFailureKind): String =
    stringResource(
        when (kind) {
            DriveBackupFailureKind.NEEDS_RECONNECTING -> R.string.drive_backup_error_reconnect
            DriveBackupFailureKind.NETWORK -> R.string.drive_backup_error_network
            DriveBackupFailureKind.DRIVE_FULL -> R.string.drive_backup_error_full
            DriveBackupFailureKind.OTHER -> R.string.drive_backup_error_other
        },
    )

private fun backupProgressModifier(busy: Boolean): Modifier =
    Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
        if (busy) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }

private const val TIME_REFRESH_MILLIS = 60_000L
