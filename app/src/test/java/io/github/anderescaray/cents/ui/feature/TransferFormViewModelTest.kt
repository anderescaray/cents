package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomTransferRepository
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.Transfer
import io.github.anderescaray.cents.domain.repository.TransferRepository
import io.github.anderescaray.cents.ui.feature.transferform.TransferField
import io.github.anderescaray.cents.ui.feature.transferform.TransferFormViewModel
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class TransferFormViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()

    private var checking: Long = 0
    private var savings: Long = 0

    private val clock = Clock.fixed(Instant.parse("2026-09-14T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            database = createTestDatabase()
            checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
            savings = database.accountDao().insert(account(name = "Savings"))
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

    @Test
    fun `the form opens on the first account and cannot save until it has a destination`() =
        runTest {
            val viewModel = formFor()

            viewModel.uiState.test {
                val state = awaitStateThat { it.accounts.size == 2 }
                assertEquals(checking, state.fromAccountId)
                assertNull("nowhere is chosen yet, and guessing would move money", state.toAccountId)
                assertFalse(state.canSave)
                assertEquals(listOf("Savings"), state.destinations.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an amount typed on the movement form arrives already typed`() =
        runTest {
            val viewModel = formFor(amountInput = "12.50")

            viewModel.uiState.test {
                assertEquals(Money(1_250, "EUR"), awaitStateThat { it.accounts.isNotEmpty() }.amount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the keypad types into whichever of the two numbers was tapped`() =
        runTest {
            val viewModel = readyForm()
            viewModel.appendDigit('5')
            viewModel.typeInto(TransferField.FEE)
            viewModel.appendDigit('3')

            viewModel.uiState.test {
                val state = awaitStateThat { it.feeInput.isNotEmpty() }
                assertEquals(Money(500, "EUR"), state.amount)
                assertEquals(Money(300, "EUR"), state.fee)
                assertEquals("the source account is short by both", Money(800, "EUR"), state.amountLeaving)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving writes both legs`() =
        runTest {
            val viewModel = readyForm()
            viewModel.selectTo(savings)
            viewModel.appendDigit('5')
            viewModel.appendDigit('0')
            viewModel.appendDigit('0')

            viewModel.uiState.test {
                assertTrue(awaitStateThat { it.canSave }.canSave)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.save()

            val rows = database.transactionDao().observeAll().first()
            assertEquals(2, rows.size)
            assertEquals(setOf(checking, savings), rows.map { it.accountId }.toSet())
            assertEquals(1, rows.map { it.transferId }.distinct().size)
            assertEquals(50_000L, rows.single { it.kind == TransactionKind.INCOME }.amountMinor)
        }

    @Test
    fun `swapping the ends turns the transfer around and keeps the amount`() =
        runTest {
            val viewModel = readyForm()
            viewModel.selectTo(savings)
            viewModel.appendDigit('9')
            viewModel.swapAccounts()

            viewModel.uiState.test {
                val state = awaitStateThat { it.fromAccountId == savings }
                assertEquals(checking, state.toAccountId)
                assertEquals(Money(900, "EUR"), state.amount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a transfer opened for editing comes back with its fee separated out`() =
        runTest {
            val id =
                RoomTransferRepository(database.transactionDao(), database, clock).saveTransfer(
                    Transfer(
                        id = Transfer.NEW,
                        fromAccountId = checking,
                        toAccountId = savings,
                        amount = Money(50_000, "EUR"),
                        fee = Money(300, "EUR"),
                        date = LocalDate.of(2026, 9, 14),
                        note = "To the broker",
                    ),
                )

            val viewModel = formFor(transferId = id)

            viewModel.uiState.test {
                val state = awaitStateThat { it.isEditing }
                assertEquals(Money(50_000, "EUR"), state.amount)
                assertEquals(Money(300, "EUR"), state.fee)
                assertEquals(checking, state.fromAccountId)
                assertEquals(savings, state.toAccountId)
                assertEquals("To the broker", state.note)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a transfer that exists is not offered the other two kinds`() =
        runTest {
            val id = savedTransfer()

            val recording = readyForm()
            val editing = readyForm(transferId = id)

            editing.uiState.test {
                // Leaving for the movement form would leave both of this transfer's legs
                // standing and add a third row on top of them: the owner would save one
                // movement and own three.
                assertFalse(awaitStateThat { it.isEditing }.canChangeKind)
                cancelAndIgnoreRemainingEvents()
            }
            recording.uiState.test {
                assertTrue(awaitStateThat { it.accountsLoaded }.canChangeKind)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an account archived after the transfer was recorded is still shown as its destination`() =
        runTest {
            val id = savedTransfer()
            database.accountDao().setArchived(savings, archived = true)

            // The form loads two things independently: the transfer, and the accounts it can
            // offer. **The accounts are made to land first here, deliberately.** In that order
            // the list is filtered while nothing is selected yet, so an archived account is
            // dropped from it — and re-deriving from the filtered list can never bring it back.
            // Left to their own timing the two usually settle the other way round, which is why
            // this is gated rather than left to chance: a race that only sometimes loses is one
            // a test only sometimes catches.
            val gate = CompletableDeferred<Unit>()
            val viewModel = formFor(transferId = id, gate = gate)
            viewModel.uiState.first { it.accountsLoaded }
            gate.complete(Unit)

            viewModel.uiState.test {
                // Archiving means stop offering it, not pretend the money never went there.
                val state = awaitStateThat { it.isEditing }
                assertEquals(savings, state.toAccountId)
                assertEquals(listOf("Checking", "Savings"), state.accounts.map { it.name })
                assertFalse(state.needsSecondAccount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an empty form does not claim there is one account before it has looked`() =
        runTest {
            val viewModel = formFor()

            viewModel.uiState.test {
                // The first state is the one the screen paints, and "create a second account"
                // is a wrong thing to say to a two-account install on the way to every
                // transfer.
                assertFalse(awaitItem().needsSecondAccount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleting a transfer takes both legs`() =
        runTest {
            val id =
                RoomTransferRepository(database.transactionDao(), database, clock).saveTransfer(
                    Transfer(
                        id = Transfer.NEW,
                        fromAccountId = checking,
                        toAccountId = savings,
                        amount = Money(50_000, "EUR"),
                        fee = Money.zero("EUR"),
                        date = LocalDate.of(2026, 9, 14),
                        note = null,
                    ),
                )

            val viewModel = readyForm(transferId = id)
            viewModel.confirmDeletion()

            assertTrue(
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `with one account there is nowhere to transfer to, and the form says so`() =
        runTest {
            database.accountDao().deleteById(savings)

            val viewModel = readyForm(accountCount = 1)

            viewModel.uiState.test {
                assertTrue(awaitStateThat { it.accounts.size == 1 }.needsSecondAccount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * A form with its accounts already loaded, which is the only state it can be tapped in.
     *
     * The list arrives from a Room flow on another dispatcher, so a test that calls
     * [TransferFormViewModel.selectTo] before it lands is choosing a destination the form is
     * about to prune — a race no finger can lose, and one that would make this file flaky
     * rather than informative.
     */
    private suspend fun savedTransfer(): Long =
        RoomTransferRepository(database.transactionDao(), database, clock).saveTransfer(
            Transfer(
                id = Transfer.NEW,
                fromAccountId = checking,
                toAccountId = savings,
                amount = Money(50_000, "EUR"),
                fee = Money.zero("EUR"),
                date = LocalDate.of(2026, 9, 14),
                note = null,
            ),
        )

    private suspend fun readyForm(
        transferId: Long = Destination.TransferForm.NEW,
        amountInput: String = "",
        accountCount: Int = 2,
    ): TransferFormViewModel =
        formFor(transferId = transferId, amountInput = amountInput).also { viewModel ->
            viewModel.uiState.first { it.accounts.size == accountCount }
        }

    private fun formFor(
        transferId: Long = Destination.TransferForm.NEW,
        amountInput: String = "",
        gate: CompletableDeferred<Unit>? = null,
    ): TransferFormViewModel {
        val repository = RoomTransferRepository(database.transactionDao(), database, clock)
        return viewModels.keep(
            TransferFormViewModel(
                gate?.let { HeldBack(repository, it) } ?: repository,
                RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                SavedStateHandle(mapOf("transferId" to transferId, "amountInput" to amountInput)),
                clock,
            ),
        )
    }
}

/**
 * The real repository with its read held at the door, so a test can decide which of the form's
 * two loads lands first.
 *
 * Only [findTransfer] is gated, because that is the one the form races against the accounts
 * flow. Everything else goes straight through: a fake that reimplemented the writes would be
 * testing itself.
 */
private class HeldBack(
    private val real: TransferRepository,
    private val gate: CompletableDeferred<Unit>,
) : TransferRepository {
    override suspend fun findTransfer(transferId: Long): Transfer? {
        gate.await()
        return real.findTransfer(transferId)
    }

    override suspend fun saveTransfer(transfer: Transfer): Long = real.saveTransfer(transfer)

    override suspend fun deleteTransfer(transferId: Long) = real.deleteTransfer(transferId)
}
