package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomBalanceRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.data.repository.RoomTransferRepository
import io.github.anderescaray.cents.domain.model.BalanceChange
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.Transfer
import io.github.anderescaray.cents.ui.feature.accounts.AccountDetailUiState
import io.github.anderescaray.cents.ui.feature.accounts.AccountDetailViewModel
import io.github.anderescaray.cents.ui.undo.DeletedTransaction
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import io.github.anderescaray.cents.ui.undo.UndoWindow
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class AccountDetailViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var transactions: RoomTransactionRepository

    // Both undo windows, the deleted movement's and the removed correction's, count on a clock
    // only the test moves. runTest's own would skip all six seconds the moment a test waits for
    // Room, and real time would make the test of a window closing last six seconds.
    private val undoClock = TestCoroutineScheduler()
    private val undoScope = CoroutineScope(StandardTestDispatcher(undoClock))
    private val deleted = DeletedTransactionBuffer(undoScope)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        transactions = RoomTransactionRepository(database.transactionDao(), database, clock)
    }

    @After
    fun tearDown() {
        try {
            viewModels.cancelAll()
            if (::database.isInitialized) database.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun detailFor(accountId: Long) =
        viewModels.keep(
            AccountDetailViewModel(
                accountRepository =
                    RoomAccountRepository(
                        database.accountDao(),
                        database.transactionDao(),
                        database,
                        clock,
                    ),
                transactionRepository = transactions,
                balanceRepository = RoomBalanceRepository(database, clock),
                deletedTransactions = deleted,
                savedStateHandle = SavedStateHandle(mapOf("accountId" to accountId)),
                today = Today(clock),
                undoScope = undoScope,
            ),
        )

    @Test
    fun `an account shows only its own movements`() =
        runTest {
            val mine = database.accountDao().insert(account(name = "Checking"))
            val other = database.accountDao().insert(account(name = "Cash"))
            database.transactionDao().insert(transaction(accountId = mine, amountMinor = 1_000))
            database.transactionDao().insert(transaction(accountId = other, amountMinor = 9_999))

            detailFor(mine).uiState.test {
                val state = awaitStateThat { it.days.isNotEmpty() }
                assertEquals(1, state.days.sumOf { it.items.size })
                assertEquals(
                    1_000L,
                    state.days
                        .first()
                        .items
                        .single()
                        .transaction.amount.minorUnits,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the balance at the top is the one the movements below produce`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 20_000))
            database.transactionDao().insert(
                transaction(accountId = id, kind = TransactionKind.EXPENSE, amountMinor = 4_000),
            )
            database.transactionDao().insert(
                transaction(accountId = id, kind = TransactionKind.INCOME, amountMinor = 1_500),
            )

            detailFor(id).uiState.test {
                val state = awaitStateThat { it.account != null && it.days.isNotEmpty() }
                assertEquals(Money(17_500, "EUR"), requireNotNull(state.account).balance)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the month summary counts this account alone`() =
        runTest {
            val mine = database.accountDao().insert(account(name = "Checking"))
            val other = database.accountDao().insert(account(name = "Cash"))
            database.transactionDao().insert(
                transaction(
                    accountId = mine,
                    kind = TransactionKind.INCOME,
                    amountMinor = 5_000,
                    date = LocalDate.of(2026, 8, 3),
                ),
            )
            database.transactionDao().insert(
                transaction(
                    accountId = other,
                    kind = TransactionKind.INCOME,
                    amountMinor = 7_000,
                    date = LocalDate.of(2026, 8, 3),
                ),
            )

            detailFor(mine).uiState.test {
                val state = awaitStateThat { it.monthSummary != null && it.days.isNotEmpty() }
                assertEquals(Money(5_000, "EUR"), requireNotNull(state.monthSummary).income)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `each movement carries the category that classified it`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking"))
            val categoryId = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insert(
                transaction(accountId = id, categoryId = categoryId, amountMinor = 1_000),
            )
            database.transactionDao().insert(transaction(accountId = id, amountMinor = 500))

            detailFor(id).uiState.test {
                val state = awaitStateThat { it.days.isNotEmpty() }
                val names = state.days.flatMap { day -> day.items.map { it.categoryName } }
                // The uncategorized one keeps a null, which the row turns into a label.
                assertEquals(listOf("Groceries", null), names.sortedWith(nullsLast(naturalOrder())))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an account with nothing in it says so rather than showing an empty page`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Fresh", openingBalanceMinor = 0))

            detailFor(id).uiState.test {
                assertEquals(true, awaitStateThat { !it.isLoading }.isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an account that is deleted while open reports itself as gone`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Doomed"))
            database.transactionDao().insert(transaction(accountId = id))
            val viewModel = detailFor(id)

            viewModel.uiState.test {
                awaitStateThat { it.account != null }

                database.transactionDao().deleteByAccount(id)
                database.accountDao().deleteById(id)

                // The screen watching this pops itself rather than sitting on a fallback
                // title with no balance, waiting to be backed out of by hand.
                assertTrue(awaitStateThat { it.isGone }.isGone)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a movement deleted from the account is offered back here and restored as it was`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking"))
            val movementId = database.transactionDao().insert(transaction(accountId = id, amountMinor = 1_250))
            val original = requireNotNull(transactions.findById(movementId))
            val viewModel = detailFor(id)

            // What the form does when the deletion is confirmed.
            transactions.deleteTransaction(movementId)
            deleted.remember(original)

            viewModel.uiState.test {
                val offered = awaitStateThat { it.undoableDeletion != null }
                assertTrue(offered.undoableDeletion is DeletedTransaction.Movement)

                viewModel.undoDeletion()

                val restored = awaitStateThat { it.undoableDeletion == null && it.days.isNotEmpty() }
                val row =
                    restored.days
                        .single()
                        .items
                        .single()
                        .transaction
                assertEquals(movementId, row.id)
                assertEquals(Money(1_250, "EUR"), row.amount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a transfer deleted from the account comes back with both legs`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 10_000))
            val savings = database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 5_000))
            val transferId =
                RoomTransferRepository(database.transactionDao(), database, clock).saveTransfer(
                    Transfer(
                        id = Transfer.NEW,
                        fromAccountId = checking,
                        toAccountId = savings,
                        amount = Money(2_000, "EUR"),
                        fee = Money.zero("EUR"),
                        date = LocalDate.of(2026, 8, 18),
                        note = null,
                    ),
                )
            val legs = transactions.findTransferLegs(transferId)
            val viewModel = detailFor(checking)

            // What the transfer form does when the deletion is confirmed.
            RoomTransferRepository(database.transactionDao(), database, clock).deleteTransfer(transferId)
            deleted.remember(legs)

            // The balance and the movements are two queries, so a state can carry one of them
            // before the other has caught up: wait for the balance itself rather than asserting it
            // on the first state whose movements moved.
            viewModel.uiState.test {
                val offered =
                    awaitStateThat {
                        it.undoableDeletion != null && it.days.isEmpty() &&
                            it.account?.balance == Money(10_000, "EUR")
                    }
                assertTrue(offered.undoableDeletion is DeletedTransaction.Transfer)

                viewModel.undoDeletion()

                awaitStateThat {
                    it.undoableDeletion == null && it.days.isNotEmpty() &&
                        it.account?.balance == Money(8_000, "EUR")
                }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(legs.map { it.id }.toSet(), transactions.findTransferLegs(transferId).map { it.id }.toSet())
        }

    @Test
    fun `dismissing the offer leaves the movement deleted`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking"))
            val movementId = database.transactionDao().insert(transaction(accountId = id))
            val original = requireNotNull(transactions.findById(movementId))
            val viewModel = detailFor(id)
            transactions.deleteTransaction(movementId)
            deleted.remember(original)

            viewModel.uiState.test {
                awaitStateThat { it.undoableDeletion != null }

                viewModel.dismissUndo()

                awaitStateThat { it.undoableDeletion == null }
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(transactions.findById(movementId))
        }

    @Test
    fun `a correction is removed only after asking, and can be put back`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 7_000))
            val balances = RoomBalanceRepository(database, clock)
            balances.setBalance(id, Money(9_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = august18)
            val correction = balances.observeAdjustments(id).first().single()
            val viewModel = detailFor(id)

            viewModel.uiState.test {
                awaitStateThat { it.days.isNotEmpty() }

                viewModel.askToRemoveCorrection(correction.id)
                val asking = awaitStateThat { it.removal != null }
                assertEquals("what the alert names", Money(7_000, "EUR"), asking.removal?.balanceAfter)
                assertEquals(1, database.balanceAdjustmentDao().all().size)

                viewModel.confirmCorrectionRemoval()
                val removed = awaitStateThat { it.undoableCorrection != null && balanceIn(it) == 7_000L }
                assertNull(removed.removal)

                viewModel.undoCorrectionRemoval()
                val back = awaitStateThat { it.undoableCorrection == null && balanceIn(it) == 9_000L }
                assertFalse(back.corrections.failed)
                cancelAndIgnoreRemainingEvents()
            }
            val ids = database.balanceAdjustmentDao().all().map { it.id }
            assertEquals("the same row, id and all", listOf(correction.id), ids)
        }

    @Test
    fun `a removed correction is offered back only while the window is open`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 7_000))
            val balances = RoomBalanceRepository(database, clock)
            balances.setBalance(id, Money(9_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = august18)
            val correction = balances.observeAdjustments(id).first().single()
            val viewModel = detailFor(id)

            viewModel.uiState.test {
                awaitStateThat { it.days.isNotEmpty() }
                viewModel.askToRemoveCorrection(correction.id)
                awaitStateThat { it.removal != null }
                viewModel.confirmCorrectionRemoval()
                // The removal has landed, balance and all, before the window is let run out.
                awaitStateThat { it.undoableCorrection != null && balanceIn(it) == 7_000L }

                undoClock.advanceTimeBy(UndoWindow.UNDO_WINDOW_MS + 1)

                val closed = awaitStateThat { it.undoableCorrection == null }
                assertEquals("still removed", 7_000L, balanceIn(closed))
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(database.balanceAdjustmentDao().all().isEmpty())
        }

    @Test
    fun `a correction removed just before a movement is deleted can still be put back after`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 7_000))
            val movementId =
                database.transactionDao().insert(
                    transaction(accountId = id, kind = TransactionKind.EXPENSE, amountMinor = 1_000),
                )
            val movement = requireNotNull(transactions.findById(movementId))
            val balances = RoomBalanceRepository(database, clock)
            balances.setBalance(id, Money(9_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = august18)
            val correction = balances.observeAdjustments(id).first().single()
            val viewModel = detailFor(id)

            viewModel.uiState.test {
                awaitStateThat { it.days.isNotEmpty() }
                viewModel.askToRemoveCorrection(correction.id)
                awaitStateThat { it.removal != null }
                viewModel.confirmCorrectionRemoval()
                awaitStateThat { it.undoableCorrection != null && balanceIn(it) == 6_000L }

                // Three seconds into the correction's window, a movement is deleted from its form.
                undoClock.advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2)
                transactions.deleteTransaction(movementId)
                deleted.remember(movement)
                val movementShowing = awaitStateThat { it.undoableDeletion != null && balanceIn(it) == 7_000L }
                assertNull("one banner at a time", movementShowing.undoableCorrection)

                // The movement's window runs out, and the correction's first six seconds with it.
                undoClock.advanceTimeBy(UndoWindow.UNDO_WINDOW_MS + 1)
                awaitStateThat { it.undoableDeletion == null && it.undoableCorrection != null }

                viewModel.undoCorrectionRemoval()
                val back = awaitStateThat { it.undoableCorrection == null && balanceIn(it) == 10_000L }
                assertFalse(back.corrections.failed)
                cancelAndIgnoreRemainingEvents()
            }
            val ids = database.balanceAdjustmentDao().all().map { it.id }
            assertEquals("the same row, id and all", listOf(correction.id), ids)
        }

    @Test
    fun `cancelling the question removes nothing`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 7_000))
            val balances = RoomBalanceRepository(database, clock)
            balances.setBalance(id, Money(9_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, on = august18)
            val viewModel = detailFor(id)

            viewModel.uiState.test {
                awaitStateThat { it.days.isNotEmpty() }
                viewModel.askToRemoveCorrection(
                    balances
                        .observeAdjustments(id)
                        .first()
                        .single()
                        .id,
                )
                awaitStateThat { it.removal != null }
                viewModel.cancelCorrectionRemoval()
                awaitStateThat { it.removal == null }
                viewModel.confirmCorrectionRemoval()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, database.balanceAdjustmentDao().all().size)
        }

    private val august18 = LocalDate.of(2026, 8, 18)

    private fun balanceIn(state: AccountDetailUiState): Long? = state.account?.balance?.minorUnits
}
