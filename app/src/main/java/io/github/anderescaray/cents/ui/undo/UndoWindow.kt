package io.github.anderescaray.cents.ui.undo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Something just removed, offered back for [UNDO_WINDOW_MS] and then forgotten (rule 7).
 *
 * **The window is counted on the scope it is given, never on a view model's.** `viewModelScope`
 * runs on `Dispatchers.Main`, and a test's virtual time skips a delay there the moment the test
 * waits for Room: the offer was gone before the write it belongs to had landed. Injected, the
 * scope is the application's on the phone, and one on a clock the test moves in a test.
 *
 * **Safe whichever thread the clock ends on.** The application's scope runs on
 * `Dispatchers.Default` while offers and takes come from the main thread, so every change holds
 * one lock, and a clock may only close the offer it was started for. A clock that ran out just as
 * a newer offer arrived would otherwise take that newer offer away with it.
 */
class UndoWindow<T : Any>(
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val state = MutableStateFlow<T?>(null)

    /** The clock of the offer showing now. Read and written under [lock] only. */
    private var expiry: Job? = null

    /** What can still be put back, or null once the window has closed. */
    val offered: StateFlow<T?> = state.asStateFlow()

    /**
     * Offers [item] back for a fresh window, replacing whatever was offered before it. The clock
     * starts before the offer shows, so no one ever sees an offer with no clock behind it.
     */
    fun offer(item: T) {
        synchronized(lock) {
            expiry?.cancel()
            expiry =
                scope.launch {
                    delay(UNDO_WINDOW_MS)
                    close(coroutineContext.job)
                }
            state.value = item
        }
    }

    /** Takes what was offered, so it can only be put back once, and stops the clock. */
    fun take(): T? =
        synchronized(lock) {
            stopClock()
            state.value.also { state.value = null }
        }

    fun clear() {
        synchronized(lock) {
            stopClock()
            state.value = null
        }
    }

    /** Closes the window, unless a newer offer has started a clock of its own since. */
    private fun close(clock: Job) {
        synchronized(lock) {
            if (expiry === clock) {
                expiry = null
                state.value = null
            }
        }
    }

    private fun stopClock() {
        expiry?.cancel()
        expiry = null
    }

    companion object {
        /** Long enough to notice the banner, short enough to belong to the moment. */
        const val UNDO_WINDOW_MS = 6_000L
    }
}
