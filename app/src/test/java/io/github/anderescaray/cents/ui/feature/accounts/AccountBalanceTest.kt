package io.github.anderescaray.cents.ui.feature.accounts

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomBalanceRepository
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.BalanceAdjustment
import io.github.anderescaray.cents.domain.model.BalanceChange
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.feature.ViewModelKeeper
import io.github.anderescaray.cents.ui.feature.awaitStateThat
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Setting a balance from the screen: what it lets the owner save, and what it shows before they
 * do. The arithmetic of each way is `RoomBalanceRepositoryTest`'s; this is the part a person sees.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class AccountBalanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private val clock = Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        try {
            viewModels.cancelAll()
            database.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun screenFor(accountId: Long) =
        viewModels.keep(
            AccountBalanceViewModel(
                accountRepository =
                    RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                balanceRepository = RoomBalanceRepository(database, clock),
                today = Today(clock),
                savedStateHandle = SavedStateHandle(mapOf("accountId" to accountId)),
            ),
        )

    private suspend fun seventyEuros(): Long {
        val id = database.accountDao().insert(account(openingBalanceMinor = 10_000))
        database.transactionDao().insert(transaction(id, amountMinor = 3_000))
        return id
    }

    @Test
    fun `nothing can be saved until a balance is typed and a way is chosen`() =
        runTest {
            val viewModel = screenFor(seventyEuros())

            viewModel.uiState.test {
                val loaded = awaitStateThat { it.current != null }
                assertFalse(loaded.canSave)
                assertNull("nothing typed is not a target of zero", loaded.difference)

                viewModel.appendDigit('9')
                viewModel.appendDigit('5')
                val typed = awaitStateThat { it.form.input == "95" }
                assertEquals(Money(2_500, "EUR"), typed.difference)
                assertFalse("no way is chosen for the owner", typed.canSave)

                viewModel.choose(BalanceChange.REBASE_OPENING)
                val chosen = awaitStateThat { it.form.change != null }
                assertTrue(chosen.canSave)
                assertEquals("what the opening balance would become", Money(12_500, "EUR"), chosen.rebasedOpening)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the balance it already has cannot be saved again`() =
        runTest {
            val viewModel = screenFor(seventyEuros())

            viewModel.uiState.test {
                awaitStateThat { it.current != null }
                viewModel.appendDigit('7')
                viewModel.appendDigit('0')
                viewModel.choose(BalanceChange.CORRECT_FROM_TODAY)
                val state = awaitStateThat { it.form.change != null && it.hasTarget }
                assertTrue(state.alreadyThere)
                assertFalse(state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving reaches the typed balance, below zero included`() =
        runTest {
            val id = seventyEuros()
            val viewModel = screenFor(id)

            viewModel.uiState.test {
                awaitStateThat { it.current != null }
                viewModel.appendDigit('4')
                viewModel.appendDigit('5')
                viewModel.toggleSign()
                viewModel.choose(BalanceChange.CORRECT_FROM_TODAY)
                assertEquals(Money(-11_500, "EUR"), awaitStateThat { it.canSave }.difference)

                viewModel.save()
                awaitStateThat { it.form.finished }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(-4_500L, database.accountDao().balanceOf(id))
            val correction = database.balanceAdjustmentDao().all().single()
            assertEquals(LocalDate.of(2026, 10, 5), correction.date)
        }

    @Test
    fun `the screen shows the balance now, the difference, and both ways with their numbers`() {
        setContent(typedState(target = 8_250, change = null))

        val difference = MoneyFormatter.formatSigned(euros(1_250))
        val day = LocalDate.of(2026, 10, 5).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val from = MoneyFormatter.format(euros(10_000))
        val to = MoneyFormatter.format(euros(11_250))
        listOf(
            "Balance in Cents",
            MoneyFormatter.format(euros(7_000)),
            difference,
            "A correction of $difference dated $day. Balances before that day do not change.",
            "From $from to $to. Every earlier balance, and the balance chart, moves by $difference.",
        ).forEach { text ->
            scrollTo(hasText(text))
            composeRule.onNodeWithText(text).assertExists()
        }
        scrollTo(hasTestTag(BALANCE_REBASE_TAG))
        composeRule.onNodeWithTag(BALANCE_CORRECT_TAG).assertIsNotSelected()
        composeRule.onNodeWithTag(BALANCE_REBASE_TAG).assertIsNotSelected()
    }

    @Test
    fun `choosing a way ticks it and asks the view model for it`() {
        var chosen: BalanceChange? = null
        setContent(typedState(target = 8_250, change = BalanceChange.REBASE_OPENING)) { chosen = it }

        scrollTo(hasTestTag(BALANCE_REBASE_TAG))
        composeRule.onNodeWithTag(BALANCE_REBASE_TAG).assertIsSelected()
        composeRule.onNodeWithTag(BALANCE_CORRECT_TAG).performClick()

        assertEquals(BalanceChange.CORRECT_FROM_TODAY, chosen)
    }

    @Test
    fun `a correction is listed on its day and removed only after asking`() {
        var removed: Long? = null
        val correction =
            BalanceAdjustment(id = 4, accountId = 1, amount = Money(1_250, "EUR"), date = LocalDate.of(2026, 10, 5))
        composeRule.setContent {
            CentsTheme {
                AccountDetailContent(
                    state =
                        AccountDetailUiState(
                            account = checking(balance = 8_250),
                            days =
                                listOf(
                                    AccountDay(correction.date, items = emptyList(), corrections = listOf(correction)),
                                ),
                            today = LocalDate.of(2026, 10, 5),
                            isLoading = false,
                        ),
                    onBack = {},
                    onEdit = {},
                    onOpenTransaction = {},
                    onOpenTransfer = {},
                    onOpenTrade = { _, _ -> },
                    onUndo = {},
                    onRemoveCorrection = { removed = it },
                )
            }
        }

        composeRule.onNodeWithText("Set current balance").assertExists()
        composeRule.onNodeWithText("Balance correction").performClick()
        composeRule
            .onNodeWithText("The balance goes back to ${MoneyFormatter.format(euros(7_000))}. No movement changes.")
            .assertExists()
        assertNull("not before the confirmation", removed)
        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(4L, removed)
    }

    /** The keypad is up on arrival, so the list under the amount scrolls on a short screen. */
    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun setContent(
        state: AccountBalanceUiState,
        onChoose: (BalanceChange) -> Unit = {},
    ) {
        composeRule.setContent {
            CentsTheme {
                AccountBalanceContent(state = state, actions = AccountBalanceActions(onChoose = onChoose))
            }
        }
    }

    private fun typedState(
        target: Long,
        change: BalanceChange?,
    ) = AccountBalanceUiState(
        account = checking(balance = 7_000),
        form = BalanceForm(input = AmountEntry.fromMinorUnits(target), change = change),
        today = LocalDate.of(2026, 10, 5),
        isLoading = false,
    )

    private fun checking(balance: Long) =
        AccountWithBalance(
            account =
                Account(
                    id = 1,
                    name = "Checking",
                    type = AccountType.BANK,
                    currency = "EUR",
                    openingBalance = Money(10_000, "EUR"),
                    colorArgb = 0,
                    archived = false,
                    sortOrder = 0,
                ),
            balance = euros(balance),
        )

    private fun euros(minorUnits: Long) = Money(minorUnits, "EUR")
}
