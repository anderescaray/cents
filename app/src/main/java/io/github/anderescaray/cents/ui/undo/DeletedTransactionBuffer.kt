package io.github.anderescaray.cents.ui.undo

import io.github.anderescaray.cents.di.ApplicationScope
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
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
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val window = UndoWindow<DeletedTransaction>(scope)

        /** The movement or transfer waiting to be restored, or null when there is nothing to undo. */
        val deleted: StateFlow<DeletedTransaction?> = window.offered

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

        fun remember(item: DeletedTransaction) = window.offer(item)

        /** Takes the deleted item out of the buffer, so it can only be restored once. */
        fun take(): DeletedTransaction? = window.take()

        fun clear() = window.clear()
    }
