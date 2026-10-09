package io.github.anderescaray.cents.ui.undo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Something just removed, offered back for [UNDO_WINDOW_MS] and then forgotten (rule 7).
 *
 * **The window is counted on the scope it is given, never on a view model's.** `viewModelScope`
 * runs on `Dispatchers.Main`, and a test's virtual time skips a delay there the moment the test
 * waits for Room: the offer was gone before the write it belongs to had landed. Injected, the
 * scope is the application's on the phone and whatever the test needs in a test. Every undo that
 * expires goes through here, so that trap is not set twice.
 */
class UndoWindow<T : Any>(
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<T?>(null)
    private var expiry: Job? = null

    /** What can still be put back, or null once the window has closed. */
    val offered: StateFlow<T?> = state.asStateFlow()

    /**
     * Offers [item] back for a fresh window, replacing whatever was offered before it. The clock
     * starts before the offer shows, so no one ever sees an offer with no clock behind it.
     */
    fun offer(item: T) {
        expiry?.cancel()
        expiry =
            scope.launch {
                delay(UNDO_WINDOW_MS)
                state.value = null
            }
        state.value = item
    }

    /** Takes what was offered, so it can only be put back once, and stops the clock. */
    fun take(): T? {
        expiry?.cancel()
        return state.value.also { state.value = null }
    }

    fun clear() {
        expiry?.cancel()
        state.value = null
    }

    companion object {
        /** Long enough to notice the banner, short enough to belong to the moment. */
        const val UNDO_WINDOW_MS = 6_000L
    }
}
