package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.data.repository.RoomTransferRepository
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.Transfer
import io.github.anderescaray.cents.ui.feature.transactions.TransactionsViewModel
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import io.github.anderescaray.cents.ui.undo.DeletedTransaction
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionsViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var deleted: DeletedTransactionBuffer
    private lateinit var filters: TransactionFilterStore
    private lateinit var viewModel: TransactionsViewModel

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        transactions = RoomTransactionRepository(database.transactionDao(), database, clock)
        // Real time on purpose: runTest races virtual time ahead while the database
        // works, which would expire the undo window mid-test. Its expiry has its
        // own test, with a scheduler it controls.
        deleted = DeletedTransactionBuffer(CoroutineScope(Dispatchers.Unconfined))
        filters = TransactionFilterStore()
        viewModel =
            viewModels.keep(
                TransactionsViewModel(
                    transactionRepository = transactions,
                    deletedTransactions = deleted,
                    filters = filters,
                    accountRepository =
                        RoomAccountRepository(
                            database.accountDao(),
                            database.transactionDao(),
                            database,
                            clock,
                        ),
                    today = Today(clock),
                ),
            )
    }

    @After
    fun tearDown() {
        try {
            // Every view model this class made, stopped the way `onCleared` stops one on the
            // phone. `uiState` is shared with `WhileSubscribed(5s)`, so without this the Room
            // query behind it keeps running against a database that is about to be closed.
            viewModels.cancelAll()
            // `lateinit`: a `setUp` that dies part-way leaves this unset, and reading it
            // would throw on the way down.
            if (::database.isInitialized) database.close()
        } finally {
            // Unconditionally, and last. Anything escaping this method leaves
            // `Dispatchers.Main` overridden for the rest of the JVM, and the next class to
            // call `setMain` dies on it rather than on anything of its own — one failed
            // teardown here takes down every class that runs after it.
            Dispatchers.resetMain()
        }
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
    fun `left open across midnight into a new month, the list moves with the date`() =
        runTest {
            var now = Instant.parse("2026-08-31T23:00:00Z")
            val movingClock =
                object : Clock() {
                    override fun instant(): Instant = now

                    override fun getZone(): ZoneId = ZoneOffset.UTC

                    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
                }
            val today = Today(movingClock)
            val accountId = database.accountDao().insert(account())
            movement(accountId, LocalDate.of(2026, 8, 18), 1_250)
            val openAcrossMidnight =
                viewModels.keep(
                    TransactionsViewModel(
                        transactionRepository = transactions,
                        deletedTransactions = deleted,
                        filters = filters,
                        accountRepository =
                            RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                        today = today,
                    ),
                )

            openAcrossMidnight.uiState.test {
                val august = awaitStateThat { it.monthSummary?.expenses == Money(1_250, "EUR") }
                assertEquals(LocalDate.of(2026, 8, 31), august.today)

                // What the activity does on the system's minute tick after midnight.
                now = Instant.parse("2026-09-01T00:01:00Z")
                today.refresh()

                // "This month" is September now, which has nothing in it yet, and the labels
                // count from the new day: the 31st is Yesterday.
                val september =
                    awaitStateThat {
                        it.today == LocalDate.of(2026, 9, 1) && it.monthSummary?.expenses == Money.zero("EUR")
                    }
                assertEquals(1, september.days.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

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

    @Test
    fun `undoing a deleted transfer restores both legs and both balances`() =
        runTest {
            val accountRepository =
                RoomAccountRepository(
                    database.accountDao(),
                    database.transactionDao(),
                    database,
                    clock,
                )
            val transferRepo =
                RoomTransferRepository(
                    database.transactionDao(),
                    database,
                    clock,
                )

            val fromAccount = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 10_000))
            val toAccount = database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 5_000))

            val transferId =
                transferRepo.saveTransfer(
                    Transfer(
                        id = Transfer.NEW,
                        fromAccountId = fromAccount,
                        toAccountId = toAccount,
                        amount = Money(2_000, "EUR"),
                        fee = Money.zero("EUR"),
                        date = LocalDate.of(2026, 8, 18),
                        note = "To savings",
                    ),
                )

            val legs = transactions.findTransferLegs(transferId)
            assertEquals(2, legs.size)

            val initialChecking = accountRepository.observeAccountWithBalance(fromAccount).first()!!.balance
            val initialSavings = accountRepository.observeAccountWithBalance(toAccount).first()!!.balance
            assertEquals(Money(8_000, "EUR"), initialChecking)
            assertEquals(Money(7_000, "EUR"), initialSavings)

            // TransferFormViewModel deletion sequence:
            val legsToBuffer = transactions.findTransferLegs(transferId)
            transferRepo.deleteTransfer(transferId)
            deleted.remember(legsToBuffer)

            val deletedChecking = accountRepository.observeAccountWithBalance(fromAccount).first()!!.balance
            val deletedSavings = accountRepository.observeAccountWithBalance(toAccount).first()!!.balance
            assertEquals(Money(10_000, "EUR"), deletedChecking)
            assertEquals(Money(5_000, "EUR"), deletedSavings)

            viewModel.uiState.test {
                val bannerState = awaitStateThat { it.undoableDeletion != null }
                assertTrue(bannerState.undoableDeletion is DeletedTransaction.Transfer)

                viewModel.undoDeletion()

                awaitStateThat { it.undoableDeletion == null && it.days.isNotEmpty() }
                cancelAndIgnoreRemainingEvents()
            }

            // Both legs restored with original ids, amounts, accounts, dates, and transferId
            val restoredLegs = transactions.findTransferLegs(transferId)
            assertEquals(2, restoredLegs.size)
            assertEquals(legs.map { it.id }.toSet(), restoredLegs.map { it.id }.toSet())
            assertEquals(legs.map { it.amount }.toSet(), restoredLegs.map { it.amount }.toSet())
            assertEquals(legs.map { it.accountId }.toSet(), restoredLegs.map { it.accountId }.toSet())
            assertEquals(legs.map { it.date }.toSet(), restoredLegs.map { it.date }.toSet())
            assertEquals(legs.map { it.transferId }.toSet(), restoredLegs.map { it.transferId }.toSet())

            // Both balances restored
            val restoredChecking = accountRepository.observeAccountWithBalance(fromAccount).first()!!.balance
            val restoredSavings = accountRepository.observeAccountWithBalance(toAccount).first()!!.balance
            assertEquals(Money(8_000, "EUR"), restoredChecking)
            assertEquals(Money(7_000, "EUR"), restoredSavings)
        }

    @Test
    fun `letting the undo window pass leaves the transfer deleted`() =
        runTest {
            val transferRepo =
                RoomTransferRepository(
                    database.transactionDao(),
                    database,
                    clock,
                )
            val fromAccount = database.accountDao().insert(account(name = "Checking"))
            val toAccount = database.accountDao().insert(account(name = "Savings"))

            val transferId =
                transferRepo.saveTransfer(
                    Transfer(
                        id = Transfer.NEW,
                        fromAccountId = fromAccount,
                        toAccountId = toAccount,
                        amount = Money(2_000, "EUR"),
                        fee = Money.zero("EUR"),
                        date = LocalDate.of(2026, 8, 18),
                        note = null,
                    ),
                )

            val legs = transactions.findTransferLegs(transferId)
            transferRepo.deleteTransfer(transferId)
            deleted.remember(legs)

            viewModel.uiState.test {
                awaitStateThat { it.undoableDeletion != null }

                viewModel.dismissUndo()

                awaitStateThat { it.undoableDeletion == null }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(emptyList<Transaction>(), transactions.findTransferLegs(transferId))
        }

    @Test
    fun `a filter narrows the list and the list says what it found`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking"))
            movement(accountId = id, date = LocalDate.of(2026, 7, 4), minor = 3_450)
            movement(accountId = id, date = LocalDate.of(2026, 8, 3), minor = 5_000)

            viewModel.uiState.test {
                awaitStateThat { it.days.size == 2 }

                filters.apply(
                    TransactionFilter(
                        from = LocalDate.of(2026, 7, 1),
                        to = LocalDate.of(2026, 7, 31),
                    ),
                )

                val narrowed = awaitStateThat { it.isNarrowed && it.matchCount == 1 }
                assertEquals(1, narrowed.days.size)
                // The headline number is the answer to the question, not the month it
                // happens to sit in.
                assertEquals(Money(3_450, "EUR"), narrowed.matchSummary?.expenses)
                assertEquals(Money(0, "EUR"), narrowed.matchSummary?.income)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the cash of a share purchase is on a filtered list but not in its totals`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Broker"))
            movement(accountId = id, date = LocalDate.of(2026, 8, 3), minor = 5_000)
            val tradeId = database.tradeDao().insert(trade(database.holdingDao().insert(holding(id))))
            database.transactionDao().insert(
                transaction(id, amountMinor = 40_000, date = LocalDate.of(2026, 8, 4), tradeId = tradeId),
            )

            viewModel.uiState.test {
                awaitStateThat { it.days.size == 2 }

                filters.apply(
                    TransactionFilter(
                        from = LocalDate.of(2026, 8, 1),
                        to = LocalDate.of(2026, 8, 31),
                    ),
                )

                // Both rows are listed, because the purchase happened; only the 50 € is
                // spending, which is what the income-and-expenses column for August says too.
                val narrowed = awaitStateThat { it.isNarrowed && it.matchCount == 2 }
                assertEquals(Money(5_000, "EUR"), narrowed.matchSummary?.expenses)
                assertEquals(Money(0, "EUR"), narrowed.matchSummary?.income)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an empty result is told apart from an empty ledger`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking"))
            movement(accountId = id, date = LocalDate.of(2026, 8, 3), minor = 5_000)

            viewModel.uiState.test {
                awaitStateThat { it.days.isNotEmpty() }

                filters.apply(TransactionFilter(category = CategoryFilter.Uncategorized, minAmountMinor = 900_000))

                val nothing = awaitStateThat { it.isEmpty }
                // Both are "no rows on screen", and they need different words: one says
                // "start recording", the other says "you asked for something too narrow".
                assertTrue(nothing.isNarrowed)
                assertEquals(0, nothing.matchCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * What the row on the list itself is wired to. The screen test proves the row is there
     * only when something is narrowing the list; this proves that taking it puts every
     * movement back, whichever of the two kinds of narrowing was on.
     */
    @Test
    fun `clearing from the list puts back the movements a form filter was hiding`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking"))
            movement(accountId = id, date = LocalDate.of(2026, 7, 4), minor = 3_450)
            movement(accountId = id, date = LocalDate.of(2026, 8, 3), minor = 5_000)

            viewModel.uiState.test {
                awaitStateThat { it.days.size == 2 }

                filters.apply(TransactionFilter(minAmountMinor = 4_000))
                awaitStateThat { it.isNarrowed && it.days.size == 1 }

                viewModel.clearFilter()

                val restored = awaitStateThat { it.days.size == 2 }
                assertEquals(TransactionFilter.None, restored.filter)
                assertEquals(false, restored.isNarrowed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the search field is part of the filter, and clearing takes it with everything else`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking"))
            movement(accountId = id, date = LocalDate.of(2026, 8, 3), minor = 5_000)

            viewModel.uiState.test {
                awaitStateThat { it.days.isNotEmpty() }

                viewModel.setQuery("nothing like this")
                // The field shows what was typed straight away; the filter catches up once
                // the typing pauses. A field fed from the applied filter drops characters.
                val typed = awaitStateThat { it.query == "nothing like this" }
                // Whether the narrowing arrives in that same emission or in a later one is
                // scheduling rather than behaviour: the query goes through a debounce and
                // the rows come back from Room on its own executor. Demanding a *second*
                // emission is what made this time out on a loaded CI machine, so what is
                // waited for is the state, and only if it is not already in hand.
                val narrowed =
                    typed.takeIf { it.isNarrowed && it.days.isEmpty() }
                        ?: awaitStateThat { it.isNarrowed && it.days.isEmpty() }
                assertEquals("nothing like this", narrowed.filter.query)

                viewModel.clearFilter()
                val restored = awaitStateThat { it.days.isNotEmpty() }
                assertEquals("", restored.query)
                assertEquals("", restored.filter.query)
                assertEquals(false, restored.isNarrowed)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
