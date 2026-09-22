package io.github.anderescaray.cents.ui.feature.investments

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.entity.TradeEntity
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.repository.RoomHoldingRepository
import io.github.anderescaray.cents.data.repository.RoomPortfolioRepository
import io.github.anderescaray.cents.data.repository.RoomTradeRepository
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.ui.feature.ViewModelKeeper
import io.github.anderescaray.cents.ui.feature.awaitStateThat
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The note on a trade: typed, kept, and — the reason this exists — not erased by a form that
 * edits the trade. Before the form offered the field it saved `null`, so opening a trade restored
 * from a backup and saving it without touching anything threw its note away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TradeFormNoteTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private var holdingId: Long = 0

    private val clock = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            database = createTestDatabase()
            val accountId = database.accountDao().insert(account(name = "Broker"))
            holdingId = database.holdingDao().insert(holding(accountId, symbol = "NVDA"))
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
    fun `a new trade saves its note trimmed`() =
        runTest {
            val viewModel = form()
            viewModel.onQuantityTyped("2")
            viewModel.onPriceTyped("100")
            viewModel.onNoteChanged("  Monthly plan  ")

            saveAndWait(viewModel)

            assertEquals("Monthly plan", savedTrade().note)
        }

    @Test
    fun `a blank note is saved as no note at all`() =
        runTest {
            val viewModel = form()
            viewModel.onQuantityTyped("2")
            viewModel.onPriceTyped("100")
            viewModel.onNoteChanged("   ")

            saveAndWait(viewModel)

            assertNull(savedTrade().note)
        }

    @Test
    fun `editing a trade that has a note loads it and saves it back unchanged`() =
        runTest {
            val tradeId =
                database.tradeDao().insert(trade(holdingId, units = 3).copy(note = "Restored from a backup"))

            val viewModel = form(tradeId = tradeId)
            viewModel.uiState.test {
                assertEquals("Restored from a backup", awaitStateThat { it.note.isNotEmpty() }.note)
                cancelAndIgnoreRemainingEvents()
            }

            saveAndWait(viewModel)

            assertEquals("Restored from a backup", savedTrade().note)
        }

    @Test
    fun `a correction keeps its note`() =
        runTest {
            val tradeId =
                database.tradeDao().insert(
                    trade(holdingId, side = TradeSide.CORRECTION, units = 5, pricePerUnitMinor = 0)
                        .copy(note = "Broker statement, August"),
                )

            val viewModel = form(tradeId = tradeId)
            viewModel.uiState.test {
                val state = awaitStateThat { it.isCorrection && it.note.isNotEmpty() }
                assertEquals("Broker statement, August", state.note)
                cancelAndIgnoreRemainingEvents()
            }

            saveAndWait(viewModel)

            val saved = savedTrade()
            assertEquals(TradeSide.CORRECTION, saved.side)
            assertEquals("Broker statement, August", saved.note)
        }

    @Test
    fun `a new correction saves the note typed with it`() =
        runTest {
            val viewModel = form(correcting = true)
            viewModel.onQuantityTyped("4")
            viewModel.onNoteChanged("Stock split")

            saveAndWait(viewModel)

            val saved = savedTrade()
            assertEquals(TradeSide.CORRECTION, saved.side)
            assertEquals("Stock split", saved.note)
        }

    /** Saves and waits for `finished`: reading the database before the write lands races it. */
    private suspend fun saveAndWait(viewModel: TradeFormViewModel) {
        viewModel.uiState.test {
            awaitStateThat { it.canSave }
            viewModel.save()
            awaitStateThat { it.finished }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun savedTrade(): TradeEntity =
        database
            .tradeDao()
            .observeForHolding(holdingId)
            .first()
            .single()

    private fun form(
        tradeId: Long = Destination.TradeForm.NEW,
        correcting: Boolean = false,
    ): TradeFormViewModel =
        viewModels.keep(
            TradeFormViewModel(
                RoomTradeRepository(
                    database.tradeDao(),
                    database.holdingDao(),
                    database.transactionDao(),
                    database,
                    clock,
                ),
                RoomHoldingRepository(database.holdingDao(), database.transactionDao(), database, clock),
                RoomPortfolioRepository(database.holdingDao(), database.tradeDao(), database.priceQuoteDao()),
                clock,
                SavedStateHandle(
                    mapOf("holdingId" to holdingId, "tradeId" to tradeId, "correcting" to correcting),
                ),
            ),
        )
}
