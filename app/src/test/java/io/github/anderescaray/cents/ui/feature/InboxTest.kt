package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.inbox.InboxContent
import io.github.anderescaray.cents.ui.feature.inbox.InboxItem
import io.github.anderescaray.cents.ui.feature.inbox.InboxUiState
import io.github.anderescaray.cents.ui.feature.inbox.InboxViewModel
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.Today
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The uncategorized inbox (PRD §4.6): what waits there, and filing it with one tap. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InboxTest {
    private lateinit var database: CentsDatabase
    private lateinit var transactions: RoomTransactionRepository
    private val viewModels = ViewModelKeeper()
    private val clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC)

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

    @Test
    fun `the count is movements without a category, and never a transfer or a purchase`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val broker = database.accountDao().insert(account(name = "Broker"))
            val groceries = database.categoryDao().insert(category())
            database.transactionDao().insert(transaction(checking, amountMinor = 700))
            database.transactionDao().insert(transaction(checking, kind = TransactionKind.INCOME, amountMinor = 5_000))
            database.transactionDao().insert(transaction(checking, groceries, amountMinor = 2_550))
            database.transactionDao().insert(transaction(checking, amountMinor = 50_000, transferId = 1))
            val tradeId = database.tradeDao().insert(trade(database.holdingDao().insert(holding(broker))))
            database.transactionDao().insert(transaction(broker, amountMinor = 40_000, tradeId = tradeId))

            assertEquals(2, transactions.observeUncategorizedCount().first())
        }

    @Test
    fun `filing a movement gives it the category and changes nothing else`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category())
            val id =
                database.transactionDao().insert(
                    transaction(checking, amountMinor = 700, note = "Bread", date = LocalDate.of(2026, 9, 20)),
                )

            assertTrue(transactions.categorize(id, groceries))

            val filed = database.transactionDao().findById(id)!!
            assertEquals(groceries, filed.categoryId)
            assertEquals(700L, filed.amountMinor)
            assertEquals("Bread", filed.note)
            assertEquals(LocalDate.of(2026, 9, 20), filed.date)
            assertEquals(0, transactions.observeUncategorizedCount().first())
        }

    @Test
    fun `a transfer leg cannot be filed, because it must never count as spending`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category())
            val leg = database.transactionDao().insert(transaction(checking, amountMinor = 50_000, transferId = 1))

            assertFalse(transactions.categorize(leg, groceries))
            assertNull(database.transactionDao().findById(leg)?.categoryId)
        }

    @Test
    fun `each movement is offered only live categories of its own kind, and filing takes it off`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            database.categoryDao().insert(category(name = "Old", archived = true))
            database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))
            val bread = database.transactionDao().insert(transaction(checking, amountMinor = 700))
            val inbox =
                viewModels.keep(
                    InboxViewModel(
                        transactions,
                        RoomCategoryRepository(database.categoryDao(), database, clock),
                        Today(clock),
                    ),
                )

            val state = inbox.uiState.first { !it.isLoading && it.items.isNotEmpty() }
            assertEquals(
                listOf("Groceries"),
                state.items
                    .single()
                    .choices
                    .map { it.name },
            )

            inbox.categorize(bread, groceries)

            assertTrue(inbox.uiState.first { it.isEmpty }.isEmpty)
            assertEquals(groceries, database.transactionDao().findById(bread)?.categoryId)
        }
}

/** The inbox drawn from a state, with no database behind it. */
@RunWith(RobolectricTestRunner::class)
class InboxScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping a category chip files that movement under it`() {
        val filed = mutableListOf<Pair<Long, Long>>()
        composeRule.setContent {
            CentsTheme {
                InboxContent(
                    state =
                        InboxUiState(
                            items = listOf(InboxItem(details(id = 7, note = "Bread"), listOf(expense(3, "Groceries")))),
                            today = LocalDate.of(2026, 9, 25),
                            isLoading = false,
                        ),
                    onBack = {},
                    onOpenTransaction = {},
                    onCategorize = { movement, category -> filed += movement to category },
                )
            }
        }

        composeRule.onNodeWithText("Bread").assertExists()
        composeRule.onNodeWithText("Groceries").performClick()

        assertEquals(listOf(7L to 3L), filed)
    }

    @Test
    fun `an empty inbox says what it is for`() {
        composeRule.setContent {
            CentsTheme {
                InboxContent(
                    state = InboxUiState(isLoading = false),
                    onBack = {},
                    onOpenTransaction = {},
                    onCategorize = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Nothing to categorize").assertExists()
    }

    private fun details(
        id: Long,
        note: String,
    ) = TransactionDetails(
        transaction =
            Transaction(
                id = id,
                accountId = 1,
                categoryId = null,
                kind = TransactionKind.EXPENSE,
                amount = Money(700, "EUR"),
                date = LocalDate.of(2026, 9, 25),
                note = note,
            ),
        accountName = "Checking",
        categoryName = null,
        categoryColorArgb = null,
    )

    private fun expense(
        id: Long,
        name: String,
    ) = Category(
        id = id,
        name = name,
        kind = CategoryKind.EXPENSE,
        colorArgb = 0xFFEF6C00.toInt(),
        parentId = null,
        archived = false,
        sortOrder = 0,
    )
}
