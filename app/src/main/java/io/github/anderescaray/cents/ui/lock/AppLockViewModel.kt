package io.github.anderescaray.cents.ui.lock

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.repository.AppLockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@Immutable
data class AppLockUiState(
    val enabled: Boolean = false,
    val state: LockState = LockState.LOCKED,
    /** Until the setting has been read, nothing is decided and nothing is shown. */
    val loaded: Boolean = false,
) {
    /**
     * Whether the app's contents are hidden right now.
     *
     * False until the setting has been read — for the frame or two that takes, the app shows
     * nothing at all rather than guessing. Guessing *open* would flash a ledger at somebody the
     * lock exists to stop; guessing *locked* would make every launch of an unlocked app blink.
     */
    val isShut: Boolean get() = loaded && enabled && state != LockState.OPEN

    /** Whether the prompt should be put up. */
    val shouldAsk: Boolean get() = loaded && enabled && state == LockState.LOCKED

    /** Whether the lock screen offers to ask again, which it does only once nothing is asking. */
    val offersRetry: Boolean get() = state == LockState.DISMISSED
}

/**
 * The lock in front of the app.
 *
 * It holds [AppLock] — which is where the rule about *when* lives, and is a plain object with no
 * Android in it — and joins it to the stored setting and the clock. The prompt itself is raised
 * by the screen, because it needs an activity.
 */
@HiltViewModel
class AppLockViewModel
    @Inject
    constructor(
        private val repository: AppLockRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val lock = AppLock()
        private val state = MutableStateFlow(AppLockUiState())
        val uiState: StateFlow<AppLockUiState> = state.asStateFlow()

        init {
            viewModelScope.launch {
                repository.observeEnabled().collect { enabled ->
                    lock.onEnabledChanged(enabled)
                    state.update { it.copy(enabled = enabled, state = lock.state, loaded = true) }
                }
            }
        }

        fun onAsking() = publish { lock.onAsking() }

        fun onUnlocked() = publish { lock.onUnlocked() }

        fun onAskingFinished() = publish { lock.onAskingFinished() }

        /** The owner tapped *Unlock* on the lock screen. */
        fun onRetry() = publish { lock.onRetry() }

        fun onBackgrounded() = publish { lock.onBackgrounded(now()) }

        fun onForegrounded() = publish { lock.onForegrounded(state.value.enabled, now()) }

        private fun now(): Instant = clock.instant()

        private fun publish(change: () -> Unit) {
            change()
            state.update { it.copy(state = lock.state) }
        }
    }
