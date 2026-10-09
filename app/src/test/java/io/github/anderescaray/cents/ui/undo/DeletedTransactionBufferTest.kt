package io.github.anderescaray.cents.ui.undo

import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DeletedTransactionBufferTest {
    private val movement =
        Transaction(
            id = 7,
            accountId = 1,
            categoryId = null,
            kind = TransactionKind.EXPENSE,
            amount = Money(1_250, "EUR"),
            date = LocalDate.of(2026, 8, 18),
            note = null,
        )

    private val transferLeg1 =
        Transaction(
            id = 10,
            accountId = 1,
            categoryId = null,
            kind = TransactionKind.EXPENSE,
            amount = Money(2_000, "EUR"),
            date = LocalDate.of(2026, 8, 18),
            note = null,
            transferId = 100,
        )

    private val transferLeg2 =
        Transaction(
            id = 11,
            accountId = 2,
            categoryId = null,
            kind = TransactionKind.INCOME,
            amount = Money(2_000, "EUR"),
            date = LocalDate.of(2026, 8, 18),
            note = null,
            transferId = 100,
        )

    @Test
    fun `the offer expires on its own, without a screen keeping the time`() =
        runTest {
            val buffer = DeletedTransactionBuffer(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            buffer.remember(movement)
            assertNotNull(buffer.deleted.value)

            // Whatever the owner is doing by now — another tab, another screen — the offer
            // belongs to the moment it was made.
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS + 1)

            assertNull(buffer.deleted.value)
        }

    @Test
    fun `taking the movement stops the clock, so nothing clears it twice`() =
        runTest {
            val buffer = DeletedTransactionBuffer(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            buffer.remember(movement)

            assertEquals(DeletedTransaction.Movement(movement), buffer.take())
            assertNull(buffer.take())

            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS + 1)
            assertNull(buffer.deleted.value)
        }

    @Test
    fun `a second deletion replaces the first, with a fresh window`() =
        runTest {
            val buffer = DeletedTransactionBuffer(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            buffer.remember(movement)

            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2)
            val second = movement.copy(id = 8, amount = Money(300, "EUR"))
            buffer.remember(second)

            // The first one's timer must not take the second one away with it.
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2 + 1)
            assertEquals(DeletedTransaction.Movement(second), buffer.deleted.value)
        }

    @Test
    fun `remembering a transfer holds both legs and expires on its own`() =
        runTest {
            val buffer = DeletedTransactionBuffer(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            val legs = listOf(transferLeg1, transferLeg2)
            buffer.remember(legs)

            assertEquals(DeletedTransaction.Transfer(legs), buffer.deleted.value)

            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS + 1)
            assertNull(buffer.deleted.value)
        }

    @Test
    fun `taking the transfer stops the clock and empties the buffer`() =
        runTest {
            val buffer = DeletedTransactionBuffer(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            val legs = listOf(transferLeg1, transferLeg2)
            buffer.remember(legs)

            assertEquals(DeletedTransaction.Transfer(legs), buffer.take())
            assertNull(buffer.take())

            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS + 1)
            assertNull(buffer.deleted.value)
        }

    @Test
    fun `a transfer deletion replaces a movement deletion and resets the window`() =
        runTest {
            val buffer = DeletedTransactionBuffer(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            buffer.remember(movement)

            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2)
            val legs = listOf(transferLeg1, transferLeg2)
            buffer.remember(legs)

            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2 + 1)
            assertEquals(DeletedTransaction.Transfer(legs), buffer.deleted.value)
        }
}
