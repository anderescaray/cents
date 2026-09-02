package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.accounts.AccountsViewModel
import io.github.anderescaray.cents.ui.feature.accounts.RemovalStep
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountsViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private lateinit var viewModel: AccountsViewModel

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        viewModel =
            viewModels.keep(
                AccountsViewModel(
                    RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                ),
            )
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
    fun `the balance shown is the opening balance plus the movements that followed`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 10_000))
            database.transactionDao().insert(
                transaction(accountId = id, kind = TransactionKind.EXPENSE, amountMinor = 2_550),
            )
            database.transactionDao().insert(
                transaction(accountId = id, kind = TransactionKind.INCOME, amountMinor = 5_000),
            )

            viewModel.uiState.test {
                val state = awaitStateThat { it.active.isNotEmpty() }
                // 100,00 − 25,50 + 50,00
                assertEquals(Money(12_450, "EUR"), state.active.single().balance)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an account with no movements shows exactly its opening balance`() =
        runTest {
            database.accountDao().insert(account(name = "Cash", openingBalanceMinor = 8_500))

            viewModel.uiState.test {
                val state = awaitStateThat { it.active.isNotEmpty() }
                assertEquals(Money(8_500, "EUR"), state.active.single().balance)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `accounts sharing a currency are totalled`() =
        runTest {
            database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 10_000))
            database.accountDao().insert(account(name = "Cash", openingBalanceMinor = 2_500))

            viewModel.uiState.test {
                val state = awaitStateThat { it.active.size == 2 }
                assertEquals(Money(12_500, "EUR"), state.total)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `accounts in different currencies are not added up`() =
        runTest {
            database.accountDao().insert(account(name = "Euros", currency = "EUR", openingBalanceMinor = 10_000))
            database.accountDao().insert(account(name = "Dollars", currency = "USD", openingBalanceMinor = 10_000))

            viewModel.uiState.test {
                val state = awaitStateThat { it.active.size == 2 }
                // Adding them needs a rate the app does not have, so there is no total.
                assertNull(state.total)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `archived accounts are listed apart, still carrying their balance`() =
        runTest {
            database.accountDao().insert(account(name = "Open", openingBalanceMinor = 100))
            database.accountDao().insert(account(name = "Closed", openingBalanceMinor = 4_200, archived = true))

            viewModel.uiState.test {
                val state = awaitStateThat { it.active.isNotEmpty() && it.archived.isNotEmpty() }
                assertEquals(listOf("Open"), state.active.map { it.account.name })
                assertEquals(Money(4_200, "EUR"), state.archived.single().balance)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a database with no accounts asks for the first one`() =
        runTest {
            viewModel.uiState.test {
                assertTrue(awaitStateThat { !it.isLoading }.showEmptyState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `swiping an empty account asks once before it goes`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Mistake"))

            viewModel.uiState.test {
                val listed = awaitStateThat { it.active.isNotEmpty() }
                viewModel.removal.request(listed.active.single().account)

                // One yes or no, because there is only one thing delete could mean here.
                assertEquals(
                    RemovalStep.CONFIRM_EMPTY,
                    viewModel.removal.state
                        .first { it.asking }
                        .step,
                )

                viewModel.removal.confirmEmpty()
                awaitStateThat { it.active.isEmpty() }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(null, database.accountDao().findById(id))
        }

    @Test
    fun `swiping an account with movements opens the same choice the editor does`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Old bank"))
            database.accountDao().insert(account(name = "New bank"))
            database.transactionDao().insert(transaction(accountId = id, amountMinor = 3_000))

            viewModel.uiState.test {
                val listed = awaitStateThat { it.active.size == 2 }
                viewModel.removal.request(listed.active.first { it.account.id == id }.account)

                val asked = viewModel.removal.state.first { it.asking }
                // A swipe is a faster way to the question, never a way past it (D7).
                assertEquals(RemovalStep.CHOOSE, asked.step)
                assertEquals(1, asked.transactionCount)
                assertTrue(asked.canReassign)
                assertEquals(listOf("New bank"), asked.reassignTargets.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }

            // Nothing has happened yet: asking is not doing.
            assertEquals(2, database.accountDao().countAll())
            assertEquals(1, database.transactionDao().count())
        }
}
