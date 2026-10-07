package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomGroupRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.ui.components.SEARCH_FIELD_TAG
import io.github.anderescaray.cents.ui.feature.transactionform.ACCOUNT_ROW_TAG
import io.github.anderescaray.cents.ui.feature.transactionform.AMOUNT_HEADER_TAG
import io.github.anderescaray.cents.ui.feature.transactionform.CATEGORY_ROW_TAG
import io.github.anderescaray.cents.ui.feature.transactionform.DATE_CHIP_TAG
import io.github.anderescaray.cents.ui.feature.transactionform.NOTE_FIELD_TAG
import io.github.anderescaray.cents.ui.feature.transactionform.TRANSACTION_FORM_BODY_TAG
import io.github.anderescaray.cents.ui.feature.transactionform.TransactionFormScreen
import io.github.anderescaray.cents.ui.feature.transactionform.TransactionFormUiState
import io.github.anderescaray.cents.ui.feature.transactionform.TransactionFormViewModel
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The movement form as it is touched (`docs/next-improvements.md` §3, §4 and §5): the keypad is up
 * only while the amount is being typed, a picker opens on the first tap even while it is up, and
 * the category and account are chosen from a list rather than a strip of chips.
 *
 * On a phone-sized screen, so the form fits and a tap on its empty part lands on empty space.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TransactionFormScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private val clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC)
    private var accountsCreated = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
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
    fun `a new movement opens on the keypad, and the category row puts it away and opens the list in one tap`() {
        runBlocking {
            database.accountDao().insert(account())
            database.categoryDao().insert(category(name = "Groceries"))
            database.categoryDao().insert(category(name = "Rent"))
        }
        showForm { it.categories.size == 2 }
        keypad().assertExists()

        composeRule.onNodeWithTag(CATEGORY_ROW_TAG).performClick()

        keypad().assertDoesNotExist()
        composeRule.onNodeWithText("Rent").assertExists()
        composeRule.onNodeWithText("Groceries").performClick()

        // Chosen, closed, and said on the row: the list is gone and the row names the choice.
        composeRule.onNodeWithText("Rent").assertDoesNotExist()
        composeRule.onNodeWithTag(CATEGORY_ROW_TAG).assertTextContains("Groceries")
    }

    @Test
    fun `the keypad comes back with the figure as it was`() {
        runBlocking { database.accountDao().insert(account()) }
        val form = showForm { it.accounts.isNotEmpty() }
        composeRule.onNodeWithText("1").performClick()
        composeRule.onNodeWithText("2").performClick()

        composeRule.onNodeWithTag(DATE_CHIP_TAG).performClick()
        // The calendar opened on the same tap that put the keypad away.
        keypad().assertDoesNotExist()
        form.dismissDatePicker()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AMOUNT_HEADER_TAG).performClick()
        keypad().assertExists()
        assertEquals("12", form.uiState.value.amountInput)
    }

    @Test
    fun `the note takes its own tap, so the keypad goes and the note keeps the focus`() {
        runBlocking { database.accountDao().insert(account()) }
        showForm { it.accounts.isNotEmpty() }

        composeRule.onNodeWithTag(NOTE_FIELD_TAG).performClick()

        composeRule.onNodeWithTag(NOTE_FIELD_TAG).assertIsFocused()
        keypad().assertDoesNotExist()
    }

    @Test
    fun `a tap on the empty part of the form puts the keypad and the keyboard away`() {
        runBlocking { database.accountDao().insert(account()) }
        showForm { it.accounts.isNotEmpty() }
        composeRule.onNodeWithTag(NOTE_FIELD_TAG).performClick()
        composeRule.onNodeWithTag(NOTE_FIELD_TAG).performTextInput("Bread")
        composeRule.onNodeWithTag(AMOUNT_HEADER_TAG).performClick()
        keypad().assertExists()

        composeRule.onNodeWithTag(TRANSACTION_FORM_BODY_TAG).performTouchInput {
            click(Offset(centerX, bottom - 4f))
        }

        keypad().assertDoesNotExist()
        composeRule.onNodeWithTag(NOTE_FIELD_TAG).assertIsNotFocused()
        composeRule.onNodeWithText("Bread").assertExists()
    }

    @Test
    fun `a long list of categories can be searched, accents aside`() {
        runBlocking {
            database.accountDao().insert(account())
            listOf("Café", "Groceries", "Rent", "Fuel", "Gym", "Books", "Gifts", "Taxes")
                .forEach { database.categoryDao().insert(category(name = it)) }
        }
        showForm { it.categories.size == 8 }
        composeRule.onNodeWithTag(CATEGORY_ROW_TAG).performClick()

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("cafe")

        composeRule.onNodeWithText("Café").assertExists()
        composeRule.onNodeWithText("Groceries").assertDoesNotExist()
    }

    @Test
    fun `leaving a movement uncategorized is its own row, offered once one is chosen`() {
        val groceries =
            runBlocking {
                database.accountDao().insert(account())
                database.categoryDao().insert(category(name = "Groceries"))
            }
        val form = showForm { it.categories.isNotEmpty() }
        composeRule.onNodeWithTag(CATEGORY_ROW_TAG).performClick()
        composeRule.onNodeWithText("No category").assertDoesNotExist()
        composeRule.onNodeWithText("Groceries").performClick()
        assertEquals(groceries, form.uiState.value.selectedCategoryId)

        composeRule.onNodeWithTag(CATEGORY_ROW_TAG).performClick()
        composeRule.onNodeWithText("No category").performClick()

        assertEquals(null, form.uiState.value.selectedCategoryId)
    }

    @Test
    fun `the account list leaves brokers out and says so`() {
        runBlocking {
            database.accountDao().insert(account(name = "Checking"))
            database.accountDao().insert(account(name = "Savings"))
            database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))
        }
        val form = showForm { it.accounts.size == 2 }

        composeRule.onNodeWithTag(ACCOUNT_ROW_TAG).performClick()

        composeRule.onNodeWithText("Broker").assertDoesNotExist()
        composeRule
            .onNodeWithText("Broker accounts are not offered here", substring = true)
            .assertExists()
        composeRule.onNodeWithText("Savings").performClick()
        assertEquals(
            "Savings",
            form.uiState.value.selectedAccount
                ?.name,
        )
        composeRule.onNodeWithTag(ACCOUNT_ROW_TAG).assertTextContains("Savings")
    }

    @Test
    fun `with no account at all, the account row goes straight to making one`() {
        showForm { true }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ACCOUNT_ROW_TAG).performClick()

        assertEquals(1, accountsCreated)
        composeRule.onAllNodesWithText("Cancel").assertCountEquals(1)
    }

    /** The keypad, found by its delete key: a bare digit would also find a day in the calendar. */
    private fun keypad() = composeRule.onNodeWithContentDescription("Delete")

    private fun showForm(ready: (TransactionFormUiState) -> Boolean): TransactionFormViewModel {
        val form =
            viewModels.keep(
                TransactionFormViewModel(
                    transactionRepository = RoomTransactionRepository(database.transactionDao(), database, clock),
                    deletedTransactions = DeletedTransactionBuffer(CoroutineScope(Dispatchers.Unconfined)),
                    entryDefaults = FakeEntryDefaults(),
                    accountRepository =
                        RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                    categoryRepository = RoomCategoryRepository(database.categoryDao(), database, clock),
                    groupRepository = RoomGroupRepository(database.groupDao(), database.transactionDao(), clock),
                    savedStateHandle = SavedStateHandle(mapOf("transactionId" to Destination.TransactionForm.NEW)),
                    clock = clock,
                ),
            )
        composeRule.setContent {
            CentsTheme {
                TransactionFormScreen(
                    onDone = {},
                    onCreateCategory = {},
                    onCreateAccount = { accountsCreated++ },
                    onSwitchToTransfer = {},
                    viewModel = form,
                )
            }
        }
        composeRule.waitUntil(WAIT_MS) { ready(form.uiState.value) }
        composeRule.waitForIdle()
        return form
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
