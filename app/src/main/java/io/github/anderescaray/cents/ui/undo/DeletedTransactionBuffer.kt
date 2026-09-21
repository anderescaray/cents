package io.github.anderescaray.cents.ui.undo

import io.github.anderescaray.cents.di.ApplicationScope
import io.github.anderescaray.cents.domain.model.Money
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
 * A deletion that can still be put back from the list banner (rule 7).
 *
 * Holds either one ordinary movement or both legs of a transfer. A transfer is two rows
 * sharing one pair id, and half a transfer restored is money arriving from nowhere — so
 * it is buffered whole and restored whole in one database transaction.
 */
sealed interface DeletedTransaction {
    val movements: List<Transaction>

    data class Movement(
        val movement: Transaction,
    ) : DeletedTransaction {
        val id: Long get() = movement.id
        val amount: Money get() = movement.amount
        val note: String? get() = movement.note
        override val movements: List<Transaction> get() = listOf(movement)
    }

    data class Transfer(
        val legs: List<Transaction>,
    ) : DeletedTransaction {
        init {
            require(legs.size == 2) { "A transfer must have exactly two legs" }
        }

        val id: Long get() = legs.first().transferId ?: legs.first().id
        override val movements: List<Transaction> get() = legs
    }
}

/**
 * Holds the movement or transfer that was just deleted, so the list can offer to put it back.
 *
 * It lives above both screens because the delete happens on the form and the offer to undo
 * it appears on the list the form pops back to.
 *
 * **The window is counted here, not by whoever is showing it.** A screen's timer dies with
 * the screen, and a buffer that outlives its own offer would put the banner
 * back on screen minutes later, for a deletion nobody remembers making. Deliberately in
 * memory only: an undo that survives closing the app is a recycle bin, not an undo.
 */
@Singleton
class DeletedTransactionBuffer
    @Inject
    constructor(
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val state = MutableStateFlow<DeletedTransaction?>(null)
        private var expiry: Job? = null

        /** The movement or transfer waiting to be restored, or null when there is nothing to undo. */
        val deleted: StateFlow<DeletedTransaction?> = state.asStateFlow()

        fun remember(transaction: Transaction) {
            remember(DeletedTransaction.Movement(transaction))
        }

        fun remember(legs: List<Transaction>) {
            val item =
                if (legs.size == 1) {
                    DeletedTransaction.Movement(legs.single())
                } else {
                    DeletedTransaction.Transfer(legs)
                }
            remember(item)
        }

        fun remember(item: DeletedTransaction) {
            expiry?.cancel()
            state.value = item
            expiry =
                scope.launch {
                    delay(UNDO_WINDOW_MS)
                    state.value = null
                }
        }

        /** Takes the deleted item out of the buffer, so it can only be restored once. */
        fun take(): DeletedTransaction? {
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
