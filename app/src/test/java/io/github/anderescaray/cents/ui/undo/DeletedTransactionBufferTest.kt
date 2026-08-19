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

    @Test
    fun `the offer expires on its own, without a screen keeping the time`() =
        runTest {
            val buffer = DeletedTransactionBuffer(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            buffer.remember(movement)
            assertNotNull(buffer.deleted.value)

            // Whatever the owner is doing by now — another tab, another screen — the offer
            // belongs to the moment it was made.
            advanceTimeBy(DeletedTransactionBuffer.UNDO_WINDOW_MS + 1)

            assertNull(buffer.deleted.value)
        }

    @Test
    fun `taking the movement stops the clock, so nothing clears it twice`() =
        runTest {
            val buffer = DeletedTransactionBuffer(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            buffer.remember(movement)

            assertEquals(movement, buffer.take())
            assertNull(buffer.take())

            advanceTimeBy(DeletedTransactionBuffer.UNDO_WINDOW_MS + 1)
            assertNull(buffer.deleted.value)
        }

    @Test
    fun `a second deletion replaces the first, with a fresh window`() =
        runTest {
            val buffer = DeletedTransactionBuffer(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            buffer.remember(movement)

            advanceTimeBy(DeletedTransactionBuffer.UNDO_WINDOW_MS / 2)
            val second = movement.copy(id = 8, amount = Money(300, "EUR"))
            buffer.remember(second)

            // The first one's timer must not take the second one away with it.
            advanceTimeBy(DeletedTransactionBuffer.UNDO_WINDOW_MS / 2 + 1)
            assertEquals(second, buffer.deleted.value)
        }
}
