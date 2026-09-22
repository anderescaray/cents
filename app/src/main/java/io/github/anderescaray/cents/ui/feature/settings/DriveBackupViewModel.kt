package io.github.anderescaray.cents.ui.feature.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.domain.model.DriveBackupException
import io.github.anderescaray.cents.domain.model.DriveBackupFailureKind
import io.github.anderescaray.cents.domain.repository.DriveBackupRepository
import io.github.anderescaray.cents.ui.backup.DriveBackupScheduler
import io.github.anderescaray.cents.ui.backup.DriveBackupSession
import io.github.anderescaray.cents.ui.backup.DriveGrant
import io.github.anderescaray.cents.ui.backup.backupFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DriveBackupViewModel
    @Inject
    constructor(
        private val preferences: DriveBackupRepository,
        private val session: DriveBackupSession,
        private val scheduler: DriveBackupScheduler,
    ) : ViewModel() {
        private val state = MutableStateFlow(DriveBackupUiState())
        val uiState: StateFlow<DriveBackupUiState> = state.asStateFlow()
        private var pending: DriveAction? = null
        private var operation: Job? = null

        init {
            viewModelScope.launch {
                preferences
                    .observe()
                    .catch { state.update { it.copy(loaded = false, failure = DriveBackupFailureKind.OTHER) } }
                    .collect { preferences ->
                        state.update {
                            it.copy(
                                preferences = preferences,
                                loaded = true,
                                failure =
                                    if (preferences.lastSuccessAt !=
                                        it.preferences.lastSuccessAt
                                    ) {
                                        null
                                    } else {
                                        it.failure
                                    },
                            )
                        }
                    }
            }
        }

        fun setAutomaticEnabled(enabled: Boolean) {
            if (!state.value.canAct) return
            if (enabled) {
                request(DriveAction.Enable)
            } else {
                state.update { it.copy(step = DriveBackupStep.CHANGING_AUTOMATIC, failure = null, message = null) }
                launchOperation {
                    changeAutomatic(false)
                    finish()
                }
            }
        }

        fun backUpNow() = request(DriveAction.BackUp)

        fun reconnect() = request(DriveAction.Reconnect)

        fun showBackups() = request(DriveAction.ListBackups)

        fun chooseBackup(
            backup: DriveBackup,
            onDownloaded: (String) -> Unit,
        ) = request(DriveAction.Download(backup, onDownloaded))

        fun closeBackups() {
            if (!state.value.showBackups) return
            operation?.cancel()
            pending = null
            state.update {
                it.copy(step = DriveBackupStep.IDLE, resolution = null, showBackups = false, backups = emptyList())
            }
        }

        fun resolutionLaunched() {
            state.update { it.copy(resolution = null) }
        }

        fun authorizationResult(
            resultCode: Int,
            intent: Intent?,
        ) {
            if (state.value.step != DriveBackupStep.WAITING_FOR_AUTHORIZATION || pending == null) return
            when {
                intent?.hasExtra(StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true -> {
                    authorizationLaunchFailed(DriveBackupException(DriveBackupFailureKind.OTHER))
                }

                resultCode != Activity.RESULT_OK -> {
                    pending = null
                    state.update { it.copy(showBackups = false) }
                    finish(DriveBackupMessage.AUTHORIZATION_CANCELLED)
                }

                else -> {
                    launchOperation {
                        val data = intent ?: throw DriveBackupException(DriveBackupFailureKind.OTHER)
                        val grant = session.fromIntent(data)
                        if (grant !is DriveGrant.Authorized) {
                            throw DriveBackupException(DriveBackupFailureKind.NEEDS_RECONNECTING)
                        }
                        accept(grant)
                    }
                }
            }
        }

        fun authorizationLaunchFailed(failure: Exception) {
            launchOperation { throw failure }
        }

        private fun request(action: DriveAction) {
            if (!state.value.canAct) return
            pending = action
            state.update {
                it.copy(
                    step = DriveBackupStep.AUTHORIZING,
                    message = null,
                    failure = null,
                    showBackups = it.showBackups || action == DriveAction.ListBackups,
                    backups = if (action == DriveAction.ListBackups) emptyList() else it.backups,
                )
            }
            launchOperation { accept(session.authorize()) }
        }

        private suspend fun accept(grant: DriveGrant) {
            when (grant) {
                is DriveGrant.Resolution -> {
                    state.update {
                        it.copy(
                            step = DriveBackupStep.WAITING_FOR_AUTHORIZATION,
                            resolution = grant.pendingIntent,
                        )
                    }
                }

                is DriveGrant.Authorized -> {
                    val action = pending ?: return
                    pending = null
                    perform(action, grant.accessToken)
                }
            }
        }

        private suspend fun perform(
            action: DriveAction,
            accessToken: String,
        ) {
            when (action) {
                DriveAction.Enable -> {
                    state.update { it.copy(step = DriveBackupStep.CHANGING_AUTOMATIC) }
                    preferences.clearReconnectRequired()
                    changeAutomatic(true)
                    finish(DriveBackupMessage.CONNECTED)
                }

                DriveAction.Reconnect -> {
                    preferences.clearReconnectRequired()
                    finish(DriveBackupMessage.CONNECTED)
                }

                DriveAction.BackUp -> {
                    state.update { it.copy(step = DriveBackupStep.BACKING_UP) }
                    session.backUp(accessToken)
                    finish(DriveBackupMessage.BACKED_UP)
                }

                DriveAction.ListBackups -> {
                    state.update { it.copy(step = DriveBackupStep.LISTING) }
                    val files = session.list(accessToken)
                    preferences.clearReconnectRequired()
                    state.update { it.copy(backups = files) }
                    finish()
                }

                is DriveAction.Download -> {
                    download(action, accessToken)
                }
            }
        }

        private suspend fun download(
            action: DriveAction.Download,
            accessToken: String,
        ) {
            state.update { it.copy(step = DriveBackupStep.DOWNLOADING) }
            val contents = session.download(accessToken, action.backup.id)
            preferences.clearReconnectRequired()
            currentCoroutineContext().ensureActive()
            state.update { it.copy(showBackups = false, backups = emptyList()) }
            action.onDownloaded(contents)
            finish()
        }

        private suspend fun changeAutomatic(enabled: Boolean) {
            withContext(NonCancellable) {
                preferences.setAutomaticEnabled(enabled)
                var scheduled = false
                try {
                    scheduler.setEnabled(enabled)
                    scheduled = true
                } finally {
                    if (enabled && !scheduled) preferences.setAutomaticEnabled(false)
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun launchOperation(action: suspend () -> Unit) {
            operation =
                viewModelScope.launch {
                    try {
                        action()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        failed(failure)
                    }
                }
        }

        private suspend fun failed(failure: Exception) {
            try {
                session.recordFailure(failure)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            }
            pending = null
            state.update {
                it.copy(
                    step = DriveBackupStep.IDLE,
                    resolution = null,
                    message = null,
                    failure = failure.backupFailureKind(),
                )
            }
        }

        private fun finish(message: DriveBackupMessage? = null) {
            state.update { it.copy(step = DriveBackupStep.IDLE, resolution = null, message = message, failure = null) }
        }
    }

private sealed interface DriveAction {
    data object Enable : DriveAction

    data object BackUp : DriveAction

    data object Reconnect : DriveAction

    data object ListBackups : DriveAction

    class Download(
        val backup: DriveBackup,
        val onDownloaded: (String) -> Unit,
    ) : DriveAction
}
