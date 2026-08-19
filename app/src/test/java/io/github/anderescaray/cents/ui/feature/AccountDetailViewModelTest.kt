package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.accounts.AccountDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun detailFor(accountId: Long) =
        AccountDetailViewModel(
            accountRepository = RoomAccountRepository(database.accountDao(), clock),
            transactionRepository = RoomTransactionRepository(database.transactionDao(), clock),
            savedStateHandle = SavedStateHandle(mapOf("accountId" to accountId)),
            clock = clock,
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
                        .amount.minorUnits,
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
    fun `an account with nothing in it says so rather than showing an empty page`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Fresh", openingBalanceMinor = 0))

            detailFor(id).uiState.test {
                assertEquals(true, awaitStateThat { !it.isLoading }.isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
