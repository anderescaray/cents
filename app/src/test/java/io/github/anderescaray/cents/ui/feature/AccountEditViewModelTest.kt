package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.recurringRule
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
    private val viewModels = ViewModelKeeper()
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
        try {
            viewModels.cancelAll()
            if (::database.isInitialized) database.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun editorFor(accountId: Long = Destination.AccountEdit.NEW) =
        viewModels.keep(
            AccountEditViewModel(
                accountRepository = accounts,
                savedStateHandle = SavedStateHandle(mapOf("accountId" to accountId)),
            ),
        )

    @Test
    fun `a new account is stored with the opening balance typed in euros`() =
        runTest {
            val editor = editorFor()

            editor.uiState.test {
                editor.setName("  Checking  ")
                editor.setType(AccountType.BANK)
                "125".forEach(editor::appendDigit)
                editor.appendDecimalSeparator()
                "50".forEach(editor::appendDigit)
                awaitStateThat { it.openingInput == "125.50" }

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
                "450".forEach(editor::appendDigit)
                editor.toggleSign()
                awaitStateThat { it.openingNegative && it.openingInput == "450" }

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
                // Wait for the row itself to arrive, not for a side effect of loading it.
                assertEquals("USD", awaitStateThat { it.name == "Old dollars" }.currency)

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
                assertEquals(
                    RemovalStep.CONFIRM_EMPTY,
                    editor.removal.state
                        .first { it.asking }
                        .step,
                )

                editor.removal.confirmEmpty()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(emptyList<String>(), accounts.observeAccounts(includeArchived = true).first().map { it.name })
        }

    @Test
    fun `an account holding investments is refused rather than offered a delete that cannot work`() =
        runTest {
            // `holdings.accountId` is RESTRICT and none of the three removals touches holdings,
            // so every one of them would be refused by the database. Before this, an account
            // with a holding and no movements took the "nothing lives in it" path and ended in
            // a constraint violation reported as "try again" — advice that could never work.
            val id = database.accountDao().insert(account(name = "Broker"))
            database.holdingDao().insert(holding(id, symbol = "NVDA"))
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.name.isNotEmpty() }
                editor.requestRemoval()
                val state = editor.removal.state.first { it.asking }

                assertEquals(RemovalStep.BLOCKED_BY_HOLDINGS, state.step)
                assertEquals(1, state.holdingCount)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                listOf("Broker"),
                accounts.observeAccounts(includeArchived = true).first().map { it.name },
            )
        }

    @Test
    fun `an account a subscription records into is refused the same way`() =
        runTest {
            // Same shape as the holdings refusal, and the same reason: `recurring_rules.
            // accountId` is RESTRICT, and no removal in this flow can point a rule somewhere
            // else. A stopped rule counts too — it is still a row referring to this account.
            val id = database.accountDao().insert(account(name = "Checking"))
            database.recurringRuleDao().insert(
                recurringRule(accountId = id, name = "Netflix"),
            )
            val editor = editorFor(id)

            editor.uiState.test {
                awaitStateThat { it.name.isNotEmpty() }
                editor.requestRemoval()
                val state = editor.removal.state.first { it.asking }

                assertEquals(RemovalStep.BLOCKED_BY_RULES, state.step)
                assertEquals(1, state.ruleCount)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                listOf("Checking"),
                accounts.observeAccounts(includeArchived = true).first().map { it.name },
            )
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
                val state = editor.removal.state.first { it.asking }

                // Not a deletion and not an archive: a question, with both answers live.
                assertEquals(RemovalStep.CHOOSE, state.step)
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
                editor.removal.state.first { it.step == RemovalStep.CHOOSE }

                editor.removal.chooseReassign()
                editor.removal.state.first { it.step == RemovalStep.PICK_TARGET }

                editor.removal.reassignTo(target)
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
                editor.removal.state.first { it.step == RemovalStep.CHOOSE }

                // Choosing the destructive path only opens the second question.
                editor.removal.chooseDestructive()
                editor.removal.state.first { it.step == RemovalStep.CONFIRM_DESTRUCTIVE }
                assertEquals(2, database.transactionDao().count())

                editor.removal.confirmDestructive()
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
                val state = editor.removal.state.first { it.asking }
                assertEquals(RemovalStep.CHOOSE, state.step)
                assertFalse(state.canReassign)

                // Tapping it anyway is a no-op rather than a dead end to back out of.
                editor.removal.chooseReassign()
                expectNoEvents()
                assertEquals(RemovalStep.CHOOSE, editor.removal.state.value.step)
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
                val state = editor.removal.state.first { it.asking }

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
