package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.transactions.TransactionsViewModel
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionsViewModelTest {
    private lateinit var database: CentsDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var deleted: DeletedTransactionBuffer
    private lateinit var viewModel: TransactionsViewModel

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        transactions = RoomTransactionRepository(database.transactionDao(), clock)
        deleted = DeletedTransactionBuffer()
        viewModel =
            TransactionsViewModel(
                transactionRepository = transactions,
                deletedTransactions = deleted,
                accountRepository = RoomAccountRepository(database.accountDao(), clock),
                clock = clock,
            )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun movement(
        accountId: Long,
        date: LocalDate,
        minor: Long,
    ): Long =
        transactions.addTransaction(
            Transaction(
                id = 0,
                accountId = accountId,
                categoryId = null,
                kind = TransactionKind.EXPENSE,
                amount = Money(minor, "EUR"),
                date = date,
                note = null,
            ),
        )

    @Test
    fun `movements are grouped by day, newest day first`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            movement(accountId, LocalDate.of(2026, 8, 16), 100)
            movement(accountId, LocalDate.of(2026, 8, 18), 200)
            movement(accountId, LocalDate.of(2026, 8, 18), 300)

            viewModel.uiState.test {
                val state = awaitStateThat { it.days.size == 2 }
                assertEquals(LocalDate.of(2026, 8, 18), state.days.first().date)
                assertEquals(
                    2,
                    state.days
                        .first()
                        .items.size,
                )
                assertEquals(LocalDate.of(2026, 8, 16), state.days.last().date)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a deleted movement can be put back exactly as it was`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val id = movement(accountId, LocalDate.of(2026, 8, 18), 1_250)
            val original = requireNotNull(transactions.findById(id))

            // What the form does when the deletion is confirmed.
            transactions.deleteTransaction(id)
            deleted.remember(original)

            viewModel.uiState.test {
                awaitStateThat { it.undoableDeletion != null }

                viewModel.undoDeletion()

                awaitStateThat { it.undoableDeletion == null && it.days.isNotEmpty() }
                cancelAndIgnoreRemainingEvents()
            }

            val restored =
                transactions
                    .observeTransactions()
                    .first()
                    .single()
                    .transaction
            assertEquals(id, restored.id)
            assertEquals(Money(1_250, "EUR"), restored.amount)
            assertEquals(LocalDate.of(2026, 8, 18), restored.date)
        }

    @Test
    fun `letting the undo window pass leaves the movement deleted`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val id = movement(accountId, LocalDate.of(2026, 8, 18), 900)
            deleted.remember(requireNotNull(transactions.findById(id)))
            transactions.deleteTransaction(id)

            viewModel.uiState.test {
                awaitStateThat { it.undoableDeletion != null }

                // What the screen does once the banner's few seconds are up.
                viewModel.dismissUndo()

                awaitStateThat { it.undoableDeletion == null }
                cancelAndIgnoreRemainingEvents()
            }

            assertNull(transactions.findById(id))
        }
}
