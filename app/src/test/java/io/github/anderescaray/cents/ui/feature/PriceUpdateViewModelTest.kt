package io.github.anderescaray.cents.ui.feature

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.quote
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.repository.RoomPortfolioRepository
import io.github.anderescaray.cents.data.repository.RoomPriceQuoteRepository
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.ui.feature.investments.PriceUpdateViewModel
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Typing every price in one sitting.
 *
 * No free provider carries the listings this portfolio holds (`DECISIONS.md` D20), so typing is
 * how prices get in rather than a fallback. What matters most here is the restraint: a row left
 * alone must stay exactly as it was, because updating three prices out of eight without
 * disturbing the other five is the whole reason the screen exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PriceUpdateViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()

    private val clock = Clock.fixed(Instant.parse("2026-09-17T18:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() =
        runTest {
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
    fun `every open holding gets a row, with what is known about it`() =
        runTest {
            hold("NVD.DE", units = 2)
            hold("VWCE", units = 5)
            database.priceQuoteDao().upsert(quote(symbol = "NVD.DE", priceMinor = 21_000))

            val viewModel = updater()

            val rows = viewModel.uiState.first { it.loaded }.rows
            assertEquals(setOf("NVD.DE", "VWCE"), rows.map { it.symbol }.toSet())
            assertEquals(21_000L, rows.single { it.symbol == "NVD.DE" }.current?.minorUnits)
            // Nothing typed for it yet is not the same as worth nothing.
            assertNull(rows.single { it.symbol == "VWCE" }.current)
        }

    @Test
    fun `a sold position is not on the screen`() =
        runTest {
            val holdingId = hold("NVD.DE", units = 2)
            database.tradeDao().insert(
                trade(holdingId, side = TradeSide.SELL, units = 2, pricePerUnitMinor = 25_000),
            )

            val viewModel = updater()

            // It has no quantity, so a price for it would be a number about nothing (D17).
            assertTrue(viewModel.uiState.first { it.loaded }.isEmpty)
        }

    @Test
    fun `only what was typed is written`() =
        runTest {
            hold("NVD.DE", units = 2)
            hold("VWCE", units = 5)
            database.priceQuoteDao().upsert(quote(symbol = "VWCE", priceMinor = 10_000))

            val viewModel = updater()
            viewModel.uiState.first { it.loaded }
            viewModel.onTyped("NVD.DE", "215.50")
            viewModel.save()
            viewModel.uiState.first { it.finished }

            assertEquals(21_550L, priceOf("NVD.DE"))
            // The row nobody touched keeps the price it had, to the cent. Reading an empty
            // field as zero is the failure this screen would be worst at.
            assertEquals(10_000L, priceOf("VWCE"))
        }

    @Test
    fun `an empty screen cannot be saved`() =
        runTest {
            hold("NVD.DE", units = 2)

            val viewModel = updater()
            val state = viewModel.uiState.first { it.loaded }

            assertFalse(state.canSave)
            assertEquals(0, state.changedCount)
        }

    @Test
    fun `the count says how many are about to change`() =
        runTest {
            hold("NVD.DE", units = 2)
            hold("VWCE", units = 5)

            val viewModel = updater()
            viewModel.uiState.first { it.loaded }
            viewModel.onTyped("NVD.DE", "215.50")
            viewModel.onTyped("VWCE", "128")

            assertEquals(2, viewModel.uiState.value.changedCount)
            assertTrue(viewModel.uiState.value.canSave)
        }

    @Test
    fun `typing something that is not a number does not become one`() =
        runTest {
            hold("NVD.DE", units = 2)

            val viewModel = updater()
            viewModel.uiState.first { it.loaded }
            viewModel.onTyped("NVD.DE", "21,5,5abc")

            // The same rules as every other amount in the app (`AmountEntry`): digits and one
            // separator survive, a second separator is dropped rather than moving the first,
            // letters never arrive, and a third decimal would not go in.
            assertEquals(
                "21.55",
                viewModel.uiState.value.rows
                    .single()
                    .input,
            )

            viewModel.onTyped("NVD.DE", "abc")
            assertEquals(
                "",
                viewModel.uiState.value.rows
                    .single()
                    .input,
            )
        }

    @Test
    fun `a price typed as zero is not a price`() =
        runTest {
            hold("NVD.DE", units = 2)
            database.priceQuoteDao().upsert(quote(symbol = "NVD.DE", priceMinor = 21_000))

            val viewModel = updater()
            viewModel.uiState.first { it.loaded }
            viewModel.onTyped("NVD.DE", "0")

            // Nothing in this portfolio is worth nothing, and a stray keystroke must not wipe a
            // real price. It reads as "leave it alone", exactly like an empty field.
            assertFalse(viewModel.uiState.value.canSave)
        }

    @Test
    fun `the date is what the prices were true on, not when they were typed`() =
        runTest {
            hold("NVD.DE", units = 2)
            val friday = LocalDate.of(2026, 9, 11)

            val viewModel = updater()
            viewModel.uiState.first { it.loaded }
            viewModel.setDate(friday)
            viewModel.onTyped("NVD.DE", "215.50")
            viewModel.save()
            viewModel.uiState.first { it.finished }

            // A price read on Friday and typed on Sunday is Friday's, which is what every screen
            // showing its age depends on.
            val stored = database.priceQuoteDao().observeBySymbol("NVD.DE").first { it != null }
            assertEquals(friday, stored!!.asOf.atZone(ZoneId.systemDefault()).toLocalDate())
        }

    private suspend fun hold(
        symbol: String,
        units: Long,
    ): Long {
        val accountId = database.accountDao().insert(account(name = "Broker $symbol"))
        val holdingId = database.holdingDao().insert(holding(accountId, symbol = symbol))
        database.tradeDao().insert(trade(holdingId, units = units, pricePerUnitMinor = 10_000))
        return holdingId
    }

    private suspend fun priceOf(symbol: String): Long? =
        database
            .priceQuoteDao()
            .observeBySymbol(symbol)
            .first()
            ?.priceMinor

    private fun updater(): PriceUpdateViewModel =
        viewModels.keep(
            PriceUpdateViewModel(
                RoomPortfolioRepository(database.holdingDao(), database.tradeDao(), database.priceQuoteDao()),
                RoomPriceQuoteRepository(database.priceQuoteDao()),
                clock,
            ),
        )
}
