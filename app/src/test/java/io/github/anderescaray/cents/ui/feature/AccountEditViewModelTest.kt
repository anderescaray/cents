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
import io.github.anderescaray.cents.ui.feature.accounts.RemovalStep
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
        accounts = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock)
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
    fun `an account keeps the currency it was created with, and nothing can change it`() =
        runTest {
            // Predates D2: an install could once create an account in another currency, and
            // the amounts recorded in it are real. The editor shows it and leaves it alone
            // rather than restating a year of dollars as euros.
            val id = database.accountDao().insert(account(name = "Old dollars", currency = "USD"))
            database.transactionDao().insert(transaction(accountId = id, currency = "USD"))
            val editor = editorFor(id)

            editor.uiState.test {
                assertEquals("USD", awaitStateThat { it.transactionCount > 0 }.currency)

                editor.setName("Renamed")
                editor.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                "USD",
                accounts
                    .observeAccounts()
                    .first()
                    .single()
                    .currency,
            )
        }

    @Test
    fun `a new account takes the device currency, even when the name is typed first`() =
        runTest {
            val editor = editorFor()

            editor.uiState.test {
                // Typing immediately is the realistic order, and it used to be the bug:
                // the currency was applied only if nothing had been edited yet, so a name
                // typed before the accounts query returned left the account on the "EUR"
                // fallback. With no picker left (D2), nothing could then correct it.
                editor.setName("Checking")
                editor.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                defaultDeviceCurrency(),
                accounts
                    .observeAccounts()
                    .first()
                    .single()
                    .currency,
            )
        }

    @Test
    fun `an empty account is deleted outright, with one yes or no`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Mistake"))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.name.isNotEmpty() }
                editor.requestRemoval()
                assertEquals(RemovalStep.CONFIRM_EMPTY, awaitStateThat { it.removing }.removalStep)

                editor.confirmEmptyRemoval()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(emptyList<String>(), accounts.observeAccounts(includeArchived = true).first().map { it.name })
        }

    @Test
    fun `an account holding movements asks what should become of them`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Old bank"))
            database.accountDao().insert(account(name = "New bank"))
            database.transactionDao().insert(transaction(accountId = id, amountMinor = 3_000))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.name.isNotEmpty() }
                editor.requestRemoval()
                val state = awaitStateThat { it.removing }

                // Not a deletion and not an archive: a question, with both answers live.
                assertEquals(RemovalStep.CHOOSE, state.removalStep)
                assertEquals(1, state.transactionCount)
                assertTrue(state.canReassign)
                assertEquals(listOf("New bank"), state.reassignTargets.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }

            // Nothing has happened yet: asking is not doing.
            assertEquals(2, accounts.observeAccounts(includeArchived = true).first().size)
        }

    @Test
    fun `reassigning moves every movement to the other account and then deletes this one`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Old bank"))
            val target = database.accountDao().insert(account(name = "New bank"))
            database.transactionDao().insert(transaction(accountId = id, amountMinor = 3_000))
            database.transactionDao().insert(transaction(accountId = id, amountMinor = 1_500))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.name.isNotEmpty() }
                editor.requestRemoval()
                awaitStateThat { it.removalStep == RemovalStep.CHOOSE }

                editor.chooseReassign()
                awaitStateThat { it.removalStep == RemovalStep.PICK_TARGET }

                editor.reassignTo(target)
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("New bank"), accounts.observeAccounts(includeArchived = true).first().map { it.name })
            // The history survives whole, under its new account.
            assertEquals(
                listOf(3_000L, 1_500L).sorted(),
                database
                    .transactionDao()
                    .observeByAccount(target)
                    .first()
                    .map { it.amountMinor }
                    .sorted(),
            )
        }

    @Test
    fun `deleting an account with its movements takes both, and only when asked twice`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Wrong account"))
            val kept = database.accountDao().insert(account(name = "Real account"))
            database.transactionDao().insert(transaction(accountId = id, amountMinor = 3_000))
            database.transactionDao().insert(transaction(accountId = kept, amountMinor = 999))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.name.isNotEmpty() }
                editor.requestRemoval()
                awaitStateThat { it.removalStep == RemovalStep.CHOOSE }

                // Choosing the destructive path only opens the second question.
                editor.chooseDestructive()
                awaitStateThat { it.removalStep == RemovalStep.CONFIRM_DESTRUCTIVE }
                assertEquals(2, database.transactionDao().count())

                editor.confirmDestructiveRemoval()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                listOf("Real account"),
                accounts.observeAccounts(includeArchived = true).first().map { it.name },
            )
            // Its movements went with it. Every other account's did not.
            assertEquals(
                emptyList<Long>(),
                database
                    .transactionDao()
                    .observeByAccount(id)
                    .first()
                    .map { it.id },
            )
            assertEquals(1, database.transactionDao().count())
        }

    @Test
    fun `reassigning is offered but disabled when there is nowhere to move the movements`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Only account"))
            database.transactionDao().insert(transaction(accountId = id))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.name.isNotEmpty() }
                editor.requestRemoval()
                val state = awaitStateThat { it.removing }
                assertEquals(RemovalStep.CHOOSE, state.removalStep)
                assertFalse(state.canReassign)

                // Tapping it anyway is a no-op rather than a dead end to back out of.
                editor.chooseReassign()
                expectNoEvents()
                assertEquals(RemovalStep.CHOOSE, editor.uiState.value.removalStep)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an account in another currency is not offered as somewhere to move movements`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Euro account", currency = "EUR"))
            database.accountDao().insert(account(name = "Dollar account", currency = "USD"))
            database.accountDao().insert(account(name = "Other euros", currency = "EUR"))
            database.transactionDao().insert(transaction(accountId = id, currency = "EUR"))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.name.isNotEmpty() }
                editor.requestRemoval()
                val state = awaitStateThat { it.removing }

                // A movement keeps the currency it was recorded in, and a balance sums its
                // account's movements under that account's currency: moving euros into a
                // dollar account would not convert them, it would corrupt the total.
                assertEquals(listOf("Other euros"), state.reassignTargets.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `archiving keeps the account and every movement in it`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Old bank"))
            database.transactionDao().insert(transaction(accountId = id, amountMinor = 3_000))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.name.isNotEmpty() }
                editor.archive()
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

    /** What the app is denominated in on the machine running the test. */
    private fun defaultDeviceCurrency(): String =
        runCatching {
            java.util.Currency
                .getInstance(java.util.Locale.getDefault())
                .currencyCode
        }.getOrDefault("EUR")
}
