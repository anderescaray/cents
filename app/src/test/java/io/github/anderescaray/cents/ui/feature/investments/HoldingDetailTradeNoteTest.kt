package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.repository.RoomHoldingRepository
import io.github.anderescaray.cents.data.repository.RoomPortfolioRepository
import io.github.anderescaray.cents.data.repository.RoomPriceQuoteRepository
import io.github.anderescaray.cents.data.repository.RoomTradeRepository
import io.github.anderescaray.cents.ui.feature.ViewModelKeeper
import io.github.anderescaray.cents.ui.theme.CentsTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A trade's note is shown on its row in the holding, under the date and the price. */
@RunWith(RobolectricTestRunner::class)
class HoldingDetailTradeNoteTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private var holdingId: Long = 0

    private val clock = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() =
        runBlocking {
            database = createTestDatabase()
            val accountId = database.accountDao().insert(account(name = "Broker"))
            holdingId = database.holdingDao().insert(holding(accountId, symbol = "NVDA"))
        }

    @After
    fun tearDown() {
        viewModels.cancelAll()
        if (::database.isInitialized) database.close()
    }

    @Test
    fun `a trade with a note shows it on its row`() {
        runBlocking {
            database.tradeDao().insert(trade(holdingId, units = 2).copy(note = "Monthly plan"))
        }

        showDetail()
        scrollTo("Monthly plan")

        compose.onNodeWithText("Monthly plan", substring = true).assertIsDisplayed()
        // On a line of its own, not run into "date · at price" where it would read as part of it.
        val lines = tradeRowLines()
        assertFalse(lines.single { " · " in it }.contains("Monthly plan"))
        assertTrue(lines.contains("Monthly plan"))
    }

    @Test
    fun `a note is trimmed, and one that is only whitespace shows no line at all`() {
        runBlocking {
            database.tradeDao().insert(trade(holdingId, units = 2).copy(note = "  Monthly plan  "))
            database.tradeDao().insert(trade(holdingId, units = 3).copy(note = "   "))
        }

        showDetail()
        scrollTo("Bought 3")

        assertTrue(tradeRowLines("Bought 2").contains("Monthly plan"))
        // The title, "date · at price" and the amount: no fourth line for a note that is not there.
        val blankNoted = tradeRowLines("Bought 3")
        assertEquals(3, blankNoted.size)
        assertFalse(blankNoted.any { it.isBlank() })
    }

    @Test
    fun `a trade without a note shows only its date and price`() {
        runBlocking {
            database.tradeDao().insert(trade(holdingId, units = 2))
        }

        showDetail()
        scrollTo("Bought 2")

        compose.onNodeWithText("Bought 2").assertIsDisplayed()
        compose.onNodeWithText("\n", substring = true).assertDoesNotExist()
    }

    /**
     * Every line of text on the row titled [title], one entry per line: the row merges its title
     * and subtitle into one node, and the subtitle breaks onto a second line for the note.
     */
    private fun tradeRowLines(title: String = "Bought 2"): List<String> =
        compose
            .onNode(hasText(title, substring = true) and hasText(" · ", substring = true))
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .flatMap { it.text.lines() }

    /** The detail is a `LazyColumn`, and the trades sit below the fold until scrolled to. */
    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun showDetail() {
        val viewModel = detailViewModel()
        compose.setContent {
            CentsTheme {
                HoldingDetailScreen(
                    onBack = {},
                    onEdit = {},
                    onAddTrade = {},
                    onCorrect = {},
                    onOpenTrade = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }
        // The trades arrive from Room's executor, so the row is waited for rather than assumed.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("NVIDIA", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun detailViewModel(): HoldingDetailViewModel =
        viewModels.keep(
            HoldingDetailViewModel(
                RoomPortfolioRepository(database.holdingDao(), database.tradeDao(), database.priceQuoteDao()),
                RoomTradeRepository(
                    database.tradeDao(),
                    database.holdingDao(),
                    database.transactionDao(),
                    database,
                    clock,
                ),
                RoomPriceQuoteRepository(database.priceQuoteDao()),
                RoomHoldingRepository(database.holdingDao(), database.transactionDao(), database, clock),
                clock,
                SavedStateHandle(mapOf("holdingId" to holdingId)),
            ),
        )
}
