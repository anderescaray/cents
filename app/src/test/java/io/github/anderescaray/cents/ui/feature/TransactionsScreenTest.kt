package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.SETTINGS_BUTTON_TAG
import io.github.anderescaray.cents.ui.feature.transactions.CLEAR_FILTER_TAG
import io.github.anderescaray.cents.ui.feature.transactions.FILTERS_BUTTON_TAG
import io.github.anderescaray.cents.ui.feature.transactions.FILTERS_COUNT_TAG
import io.github.anderescaray.cents.ui.feature.transactions.SHOW_EVERYTHING_TAG
import io.github.anderescaray.cents.ui.feature.transactions.TransactionDay
import io.github.anderescaray.cents.ui.feature.transactions.TransactionsList
import io.github.anderescaray.cents.ui.feature.transactions.TransactionsUiState
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The way out of a filter, as the list presents it.
 *
 * What is being proved is that it costs nothing when no filter is on, that it is there
 * without a trip to the form when one is, and that it never appears twice on the same
 * screen. That it actually restores every movement is proved on the view model, which is
 * where the store and the database are.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var cleared = 0
    private var settingsOpened = 0
    private val openedMovements = mutableListOf<Long>()
    private val openedTransfers = mutableListOf<Long>()
    private val openedTrades = mutableListOf<Pair<Long, Long>>()

    @Test
    fun `an unfiltered list carries no control it has no use for`() {
        setContent(unfiltered())

        composeRule.onNodeWithTag(CLEAR_FILTER_TAG).assertDoesNotExist()
        // The month is still the headline, which is what says nothing is being hidden.
        composeRule.onNodeWithText("THIS MONTH").assertExists()
        composeRule.onNodeWithText("MATCHES").assertDoesNotExist()
    }

    @Test
    fun `a filtered list offers the way out beside what the filter matched`() {
        setContent(filtered())

        composeRule.onNodeWithText("MATCHES").assertExists()
        composeRule.onNodeWithTag(CLEAR_FILTER_TAG).assertExists()
    }

    @Test
    fun `text alone narrows the list, so it too can be undone from the list`() {
        setContent(unfiltered().copy(filter = TransactionFilter(query = "coffee"), query = "coffee"))

        composeRule.onNodeWithTag(CLEAR_FILTER_TAG).assertExists()
    }

    @Test
    fun `taking the way out asks for exactly one clearing`() {
        setContent(filtered())

        composeRule.onNodeWithTag(CLEAR_FILTER_TAG).performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun `emptying the search box as well is spelled out when there is text to lose`() {
        setContent(filtered().copy(query = "coffee"))

        composeRule.onNodeWithText(CLEAR_FOOTER).assertExists()
    }

    @Test
    fun `with the search box empty there is nothing to warn about`() {
        setContent(filtered())

        composeRule.onNodeWithText(CLEAR_FOOTER).assertDoesNotExist()
    }

    @Test
    fun `nothing matching leaves one way out rather than two`() {
        setContent(filtered().copy(days = emptyList(), matchCount = 0, matchSummary = null))

        // The no-matches section already says it, directly underneath. The same row twice,
        // one above the other, reads as two different actions.
        composeRule.onNodeWithTag(CLEAR_FILTER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SHOW_EVERYTHING_TAG).assertExists()
    }

    @Test
    fun `a transfer leg names the account at the other end rather than reading as uncategorized`() {
        setContent(unfiltered().copy(days = listOf(TransactionDay(TODAY, listOf(leg(), arriving())))))

        // Neither leg has a category, and *Uncategorized* is what a movement row would call
        // that — filing a transfer beside the movements that genuinely want classifying.
        composeRule.onNodeWithText("Transfer to Savings").assertExists()
        composeRule.onNodeWithText("Transfer from Checking").assertExists()
        composeRule.onNodeWithText("Uncategorized").assertDoesNotExist()
    }

    @Test
    fun `tapping a leg opens the transfer rather than the movement behind it`() {
        setContent(unfiltered().copy(days = listOf(TransactionDay(TODAY, listOf(leg())))))

        // The row's own click action, rather than a tap at its coordinates: on Robolectric's
        // small default screen this row sits under the collapsing title, so a synthetic touch
        // is swallowed before it arrives. What is being proved is where the row leads.
        composeRule.onNodeWithText("Transfer to Savings").assertHasClickAction()
        composeRule
            .onNodeWithText("Transfer to Savings")
            .performSemanticsAction(SemanticsActions.OnClick)

        // Editing one side on a form that knows nothing about the other is how the two stop
        // agreeing about how much money moved.
        assertEquals(listOf(77L), openedTransfers)
        assertEquals(emptyList<Long>(), openedMovements)
    }

    @Test
    fun `a purchase says what was bought and opens the trade behind it`() {
        setContent(unfiltered().copy(days = listOf(TransactionDay(TODAY, listOf(purchase())))))

        // Without this it reads *Uncategorized* with an amount, which says nothing about the
        // one thing worth knowing.
        composeRule.onNodeWithText("Bought NVD.DE").assertExists()
        composeRule.onNodeWithText("Uncategorized").assertDoesNotExist()

        composeRule
            .onNodeWithText("Bought NVD.DE")
            .performSemanticsAction(SemanticsActions.OnClick)

        // The money is a consequence of the units: editing it on its own would make the two
        // disagree about what the purchase was.
        assertEquals(listOf(9L to 42L), openedTrades)
        assertEquals(emptyList<Long>(), openedMovements)
    }

    @Test
    fun `the filters sit on the right, clear of the title, and there is no add button`() {
        setContent(unfiltered())

        val filters = composeRule.onNodeWithTag(FILTERS_BUTTON_TAG)
        filters.assertContentDescriptionEquals("Filters")
        composeRule.onNodeWithTag(FILTERS_COUNT_TAG, useUnmergedTree = true).assertDoesNotExist()

        val title = composeRule.onNodeWithText("Transactions").getUnclippedBoundsInRoot()
        val button = filters.getUnclippedBoundsInRoot()
        // `docs/next-improvements.md` §2: the title and the action no longer share a corner.
        assertTrue("the button is clear of the title", button.bottom <= title.top || button.left >= title.right)
        // The capsule's add button is the one way to add, on this tab as on every other.
        composeRule.onNodeWithContentDescription("New transaction").assertDoesNotExist()
    }

    @Test
    fun `an active filter is counted on the button, and said`() {
        setContent(filtered())

        composeRule.onNodeWithTag(FILTERS_BUTTON_TAG).assertContentDescriptionEquals("Filters (1)")
        composeRule.onNodeWithTag(FILTERS_COUNT_TAG, useUnmergedTree = true).assertExists()
    }

    private fun unfiltered() =
        TransactionsUiState(
            days = listOf(TransactionDay(TODAY, listOf(details()))),
            monthSummary = summary(),
            isLoading = false,
            today = TODAY,
        )

    private fun filtered() =
        unfiltered().copy(
            monthSummary = null,
            filter = TransactionFilter(accountId = 1),
            matchCount = 1,
            matchSummary = summary(),
        )

    private fun summary() = PeriodSummary(income = Money(0, "EUR"), expenses = Money(2_500, "EUR"))

    private fun details() =
        TransactionDetails(
            transaction =
                Transaction(
                    id = 1,
                    accountId = 1,
                    categoryId = null,
                    kind = TransactionKind.EXPENSE,
                    amount = Money(2_500, "EUR"),
                    date = TODAY,
                    note = null,
                ),
            accountName = "Checking",
            categoryName = "Groceries",
            categoryColorArgb = null,
        )

    /** The cash a purchase took out: an expense in Checking, belonging to trade 42. */
    private fun purchase() =
        TransactionDetails(
            transaction =
                Transaction(
                    id = 4,
                    accountId = 1,
                    categoryId = null,
                    kind = TransactionKind.EXPENSE,
                    amount = Money(40_000, "EUR"),
                    date = TODAY,
                    note = null,
                    tradeId = 42,
                ),
            accountName = "Broker",
            categoryName = null,
            categoryColorArgb = null,
            tradeSymbol = "NVD.DE",
            tradeHoldingId = 9,
        )

    /** The side money left: an expense in Checking, belonging to transfer 77. */
    private fun leg() =
        TransactionDetails(
            transaction =
                Transaction(
                    id = 2,
                    accountId = 1,
                    categoryId = null,
                    kind = TransactionKind.EXPENSE,
                    amount = Money(50_000, "EUR"),
                    date = TODAY,
                    note = null,
                    transferId = 77,
                ),
            accountName = "Checking",
            categoryName = null,
            categoryColorArgb = null,
            counterpartAccountName = "Savings",
        )

    /** The side it arrived on, read from the other account. */
    private fun arriving() =
        TransactionDetails(
            transaction =
                Transaction(
                    id = 3,
                    accountId = 2,
                    categoryId = null,
                    kind = TransactionKind.INCOME,
                    amount = Money(50_000, "EUR"),
                    date = TODAY,
                    note = null,
                    transferId = 77,
                ),
            accountName = "Savings",
            categoryName = null,
            categoryColorArgb = null,
            counterpartAccountName = "Checking",
        )

    @Test
    fun `settings is the last button in the header, with the filters beside it`() {
        setContent(unfiltered())

        composeRule.onNodeWithTag(SETTINGS_BUTTON_TAG).performClick()
        assertEquals(1, settingsOpened)
        val settings = composeRule.onNodeWithTag(SETTINGS_BUTTON_TAG).getUnclippedBoundsInRoot()
        val filters = composeRule.onNodeWithTag(FILTERS_BUTTON_TAG).getUnclippedBoundsInRoot()
        // Side by side, Settings to the right: where every tab has it.
        assertTrue(filters.right <= settings.left)
        assertEquals(filters.top, settings.top)
    }

    private fun setContent(state: TransactionsUiState) {
        composeRule.setContent {
            CentsTheme {
                TransactionsList(
                    state = state,
                    onOpenTransaction = { openedMovements += it },
                    onOpenTransfer = { openedTransfers += it },
                    onOpenTrade = { holdingId, tradeId -> openedTrades += holdingId to tradeId },
                    onOpenFilters = {},
                    onQueryChange = {},
                    onClearFilter = { cleared++ },
                    onOpenSettings = { settingsOpened++ },
                )
            }
        }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 8, 30)

        const val CLEAR_FOOTER =
            "Clears every filter and the search, so the list goes back to every movement."
    }
}
