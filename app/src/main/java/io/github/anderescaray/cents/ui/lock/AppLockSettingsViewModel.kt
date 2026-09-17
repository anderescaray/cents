package io.github.anderescaray.cents.ui.lock

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anderescaray.cents.domain.repository.AppLockRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AppLockSettingsUiState(
    val enabled: Boolean = false,
    /**
     * Whether this phone can ask at all.
     *
     * False on a device with no fingerprint enrolled and no PIN set, where turning the lock on
     * would shut the owner out of their own app with nothing able to let them back in.
     */
    val available: Boolean = true,
)

/**
 * The switch in Settings, and whether it can honestly be offered.
 *
 * Separate from [AppLockViewModel], which is the lock itself: that one lives for as long as the
 * activity and decides what is on screen, this one lives with a settings screen and writes a
 * preference. Sharing one would tie the gate in front of the whole app to the lifetime of a tab.
 *
 * Whether the device *can* authenticate is read once, when this is created. It can change while
 * the app is open — the owner could go and enrol a fingerprint — and re-reading it on every
 * recomposition to catch that would be a system call per frame for a case that resolves itself
 * the next time Settings is opened.
 */
@HiltViewModel
class AppLockSettingsViewModel
    @Inject
    constructor(
        private val repository: AppLockRepository,
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val available = BiometricAuthenticator.canAuthenticate(context)

        val uiState: StateFlow<AppLockSettingsUiState> =
            repository
                .observeEnabled()
                .map { enabled ->
                    // A phone that cannot ask reads as unlocked whatever is stored — otherwise a
                    // setting carried over by a restore from another phone would shut this one.
                    AppLockSettingsUiState(enabled = enabled && available, available = available)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = AppLockSettingsUiState(available = available),
                )

        fun setEnabled(enabled: Boolean) {
            if (!available) return
            viewModelScope.launch { repository.setEnabled(enabled) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
