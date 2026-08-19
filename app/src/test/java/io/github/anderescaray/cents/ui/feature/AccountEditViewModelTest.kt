package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.feature.accounts.AccountEditViewModel
import io.github.anderescaray.cents.ui.feature.accounts.AccountRemoval
import io.github.anderescaray.cents.ui.navigation.Destination
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
class AccountEditViewModelTest {
    private lateinit var database: CentsDatabase
    private lateinit var accounts: RoomAccountRepository

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        accounts = RoomAccountRepository(database.accountDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun editorFor(accountId: Long = Destination.AccountEdit.NEW) =
        AccountEditViewModel(
            accountRepository = accounts,
            savedStateHandle = SavedStateHandle(mapOf("accountId" to accountId)),
        )

    @Test
    fun `a new account is stored with the opening balance typed in minor units`() =
        runTest {
            val editor = editorFor()

            editor.uiState.test {
                editor.setName("  Checking  ")
                editor.setType(AccountType.BANK)
                "12550".forEach(editor::appendDigit)
                awaitStateThat { it.openingDigits == "12550" }

                editor.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            val stored = accounts.observeAccounts().first().single()
            assertEquals("Checking", stored.name)
            assertEquals(Money(12_550, stored.currency), stored.openingBalance)
        }

    @Test
    fun `an account can start in the red, for money already owed`() =
        runTest {
            val editor = editorFor()

            editor.uiState.test {
                editor.setName("Credit card")
                editor.setType(AccountType.CARD)
                "45000".forEach(editor::appendDigit)
                editor.toggleSign()
                awaitStateThat { it.openingNegative && it.openingDigits == "45000" }

                editor.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                -45_000L,
                accounts
                    .observeAccounts()
                    .first()
                    .single()
                    .openingBalance.minorUnits,
            )
        }

    @Test
    fun `a name another account already uses is refused`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val editor = editorFor()

            editor.uiState.test {
                editor.setName("checking")
                assertFalse(awaitStateThat { it.nameTaken }.canSave)

                editor.save()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, accounts.observeAccounts().first().size)
        }

    @Test
    fun `the currency is fixed once the account holds movements`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Checking", currency = "EUR"))
            database.transactionDao().insert(transaction(accountId = id))
            val editor = editorFor(id)

            editor.uiState.test {
                val state = awaitStateThat { it.transactionCount > 0 }
                assertTrue(state.currencyLocked)

                // The movements are already denominated in euros: changing it here would
                // restate every one of them, so the call does nothing at all.
                editor.setCurrency("USD")
                expectNoEvents()
                assertEquals("EUR", editor.uiState.value.currency)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an empty account is deleted outright`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Mistake"))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { !it.isNew }
                editor.requestRemoval()
                assertEquals(AccountRemoval.OUTRIGHT, awaitStateThat { it.confirmingRemoval }.removal)

                editor.confirmRemoval()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(emptyList<String>(), accounts.observeAccounts(includeArchived = true).first().map { it.name })
        }

    @Test
    fun `an account holding movements is archived, and keeps every one of them`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Old bank"))
            database.transactionDao().insert(transaction(accountId = id, amountMinor = 3_000))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { !it.isNew }
                editor.requestRemoval()
                val state = awaitStateThat { it.confirmingRemoval }
                assertEquals(AccountRemoval.ARCHIVE, state.removal)
                assertEquals(1, state.transactionCount)

                editor.confirmRemoval()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(
                accounts
                    .observeAccounts(includeArchived = true)
                    .first()
                    .single()
                    .archived,
            )
            assertEquals(emptyList<String>(), accounts.observeAccounts().first().map { it.name })
            assertEquals(
                1,
                database
                    .transactionDao()
                    .observeByAccount(id)
                    .first()
                    .size,
            )
        }

    @Test
    fun `restoring an archived account puts it back in the pickers`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Reopened", archived = true))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.archived }
                editor.restore()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("Reopened"), accounts.observeAccounts().first().map { it.name })
        }
}
