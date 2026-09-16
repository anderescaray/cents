package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.repository.RoomHoldingRepository
import io.github.anderescaray.cents.data.repository.RoomPortfolioRepository
import io.github.anderescaray.cents.data.repository.RoomPriceQuoteRepository
import io.github.anderescaray.cents.data.repository.RoomTradeRepository
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.feature.investments.HoldingDetailViewModel
import io.github.anderescaray.cents.ui.feature.investments.PriceBasis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pricing a holding by what the whole lot is worth, which is how a broker's app shows it and
 * therefore how the owner reads it.
 *
 * One price per unit is still what gets stored — a total is a fact about a quantity, and the
 * quantity changes the next time anything is traded. What these pin is that the division happens
 * once, exactly, and that switching between the two ways of typing never changes the money.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HoldingPriceEntryTest {
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
            holdingId = database.holdingDao().insert(holding(accountId, symbol = "NVD.DE"))
            // Four units, so a total divides into a price that is not the total itself.
            database.tradeDao().insert(trade(holdingId, units = 4, pricePerUnitMinor = 10_000))
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
    fun `typing what the whole lot is worth stores the price of one unit`() =
        runTest {
            val viewModel = detailFor()
            viewModel.uiState.test {
                awaitStateThat { it.position != null }
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.startEditingPrice()
            viewModel.onPriceBasisChanged(PriceBasis.TOTAL)
            // "My broker says these four are worth 600,00 €."
            viewModel.onPriceTyped("600")
            viewModel.savePrice()

            // `savePrice()` has no `finished` to wait on, so the row itself is the signal:
            // the flow re-emits when the write lands, and its first emission can predate it.
            val stored = database.priceQuoteDao().observeBySymbol("NVD.DE").first { it != null }
            assertEquals(15_000L, stored?.priceMinor)

            viewModel.uiState.test {
                val state = awaitStateThat { it.position?.marketValue != null }

                assertEquals("and the lot is worth what was typed", Money(60_000, "EUR"), state.position?.marketValue)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `switching between the two ways of typing keeps the same money on screen`() =
        runTest {
            val viewModel = detailFor()
            viewModel.uiState.test {
                awaitStateThat { it.position != null }
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.startEditingPrice()
            viewModel.onPriceTyped("150")

            viewModel.price.test {
                viewModel.onPriceBasisChanged(PriceBasis.TOTAL)
                // Four units at 150,00 each is 600,00 for the lot: the same money, said the
                // other way round, so the owner never has to redo the arithmetic themselves.
                assertEquals("600", awaitStateThat { it.basis == PriceBasis.TOTAL }.input)

                viewModel.onPriceBasisChanged(PriceBasis.PER_UNIT)
                assertEquals("150", awaitStateThat { it.basis == PriceBasis.PER_UNIT }.input)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a total that does not divide evenly rounds once, and only once`() =
        runTest {
            val viewModel = detailFor()
            viewModel.uiState.test {
                awaitStateThat { it.position != null }
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.startEditingPrice()
            viewModel.onPriceBasisChanged(PriceBasis.TOTAL)
            // 100,10 over four units is 25,025 — half a cent, which rounds away from zero.
            viewModel.onPriceTyped("100,10")
            viewModel.savePrice()

            // `savePrice()` has no `finished` to wait on, so the row itself is the signal:
            // the flow re-emits when the write lands, and its first emission can predate it.
            val stored = database.priceQuoteDao().observeBySymbol("NVD.DE").first { it != null }
            assertEquals(2_503L, stored?.priceMinor)
        }

    @Test
    fun `the price carries the date it was true, not the date it was typed`() =
        runTest {
            val viewModel = detailFor()
            viewModel.uiState.test {
                awaitStateThat { it.position != null }
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.startEditingPrice()
            viewModel.onPriceTyped("120")
            viewModel.onPriceDateSelected(viewModel.today().minusDays(3))
            viewModel.savePrice()

            // `savePrice()` has no `finished` to wait on, so the row itself is the signal:
            // the flow re-emits when the write lands, and its first emission can predate it.
            val stored = database.priceQuoteDao().observeBySymbol("NVD.DE").first { it != null }
            val expected =
                viewModel
                    .today()
                    .minusDays(3)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
            assertEquals(expected, stored?.asOf)
        }

    private fun detailFor(): HoldingDetailViewModel =
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
