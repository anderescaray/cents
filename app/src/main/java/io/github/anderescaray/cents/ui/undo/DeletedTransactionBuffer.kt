package io.github.anderescaray.cents.ui.undo

import io.github.anderescaray.cents.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the movement that was just deleted, so the list can offer to put it back.
 *
 * It lives above both screens because the delete happens on the form and the offer to undo
 * it appears on the list the form pops back to. Deliberately in memory only: an undo that
 * survives closing the app is not an undo, it is a recycle bin, and nobody asked for one.
 */
@Singleton
class DeletedTransactionBuffer
    @Inject
    constructor() {
        private val state = MutableStateFlow<Transaction?>(null)

        /** The movement waiting to be restored, or null when there is nothing to undo. */
        val deleted: StateFlow<Transaction?> = state.asStateFlow()

        fun remember(transaction: Transaction) {
            state.value = transaction
        }

        /** Takes the movement out of the buffer, so it can only be restored once. */
        fun take(): Transaction? = state.value.also { state.value = null }

        fun clear() {
            state.value = null
        }
    }
