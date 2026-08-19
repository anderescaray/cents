package io.github.anderescaray.cents.ui.undo

import io.github.anderescaray.cents.di.ApplicationScope
import io.github.anderescaray.cents.domain.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the movement that was just deleted, so the list can offer to put it back.
 *
 * It lives above both screens because the delete happens on the form and the offer to undo
 * it appears on the list the form pops back to.
 *
 * **The window is counted here, not by whoever is showing it.** A screen's timer dies with
 * the screen, and a buffer that outlives its own offer would put "Movement deleted / Undo"
 * back on screen minutes later, for a deletion nobody remembers making. Deliberately in
 * memory only: an undo that survives closing the app is a recycle bin, not an undo.
 */
@Singleton
class DeletedTransactionBuffer
    @Inject
    constructor(
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val state = MutableStateFlow<Transaction?>(null)
        private var expiry: Job? = null

        /** The movement waiting to be restored, or null when there is nothing to undo. */
        val deleted: StateFlow<Transaction?> = state.asStateFlow()

        fun remember(transaction: Transaction) {
            expiry?.cancel()
            state.value = transaction
            expiry =
                scope.launch {
                    delay(UNDO_WINDOW_MS)
                    state.value = null
                }
        }

        /** Takes the movement out of the buffer, so it can only be restored once. */
        fun take(): Transaction? {
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
