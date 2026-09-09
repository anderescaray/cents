package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.quote
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomPortfolioRepository
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.ui.feature.investments.PortfolioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class PortfolioViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private lateinit var viewModel: PortfolioViewModel
    private var accountId: Long = 0

    private val clock = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            database = createTestDatabase()
            accountId = database.accountDao().insert(account(name = "Broker"))
            viewModel =
                viewModels.keep(
                    PortfolioViewModel(
                        RoomPortfolioRepository(database.holdingDao(), database.tradeDao(), database.priceQuoteDao()),
                        RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                    ),
                )
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
    fun `holdings are listed under their own kind, in the order the kinds are declared`() =
        runTest {
            val nvidia = database.holdingDao().insert(holding(accountId, symbol = "NVDA", name = "NVIDIA"))
            val bitcoin =
                database.holdingDao().insert(
                    holding(accountId, symbol = "BTC", name = "Bitcoin", assetClass = AssetClass.CRYPTO),
                )
            database.tradeDao().insert(trade(nvidia, units = 1, pricePerUnitMinor = 10_000))
            database.tradeDao().insert(trade(bitcoin, units = 1, pricePerUnitMinor = 5_000_000))

            viewModel.uiState.test {
                val state = awaitStateThat { it.groups.size == 2 }

                assertEquals(listOf(AssetClass.STOCK, AssetClass.CRYPTO), state.groups.map { it.assetClass })
                assertEquals(
                    "NVIDIA",
                    state.groups
                        .first()
                        .positions
                        .single()
                        .holding.name,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a total counts what has a price and says how much it left out`() =
        runTest {
            val priced = database.holdingDao().insert(holding(accountId, symbol = "NVDA", name = "NVIDIA"))
            val unpriced = database.holdingDao().insert(holding(accountId, symbol = "VWCE", name = "Vanguard"))
            database.tradeDao().insert(trade(priced, units = 2, pricePerUnitMinor = 10_000))
            database.tradeDao().insert(trade(unpriced, units = 3, pricePerUnitMinor = 9_000))
            database.priceQuoteDao().upsert(quote(symbol = "NVDA", priceMinor = 12_500))

            viewModel.uiState.test {
                val state = awaitStateThat { it.totals.isNotEmpty() }
                val total = state.totals.single()

                // Two units at 125,00, against the 200,00 that *that* holding cost.
                assertEquals(Money(25_000, "EUR"), total.marketValue)
                assertEquals(Money(5_000, "EUR"), total.profit)
                // What everything cost is known whether or not it has a price: 200,00 + 270,00.
                assertEquals(Money(47_000, "EUR"), total.costBasis)
                assertEquals(Money(20_000, "EUR"), total.pricedCostBasis)
                assertEquals("the unpriced holding is counted, not silently included", 1, total.unpricedCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a holding that has been sold out of stops being listed`() =
        runTest {
            val id = database.holdingDao().insert(holding(accountId, symbol = "NVDA"))
            database.tradeDao().insert(trade(id, units = 1, pricePerUnitMinor = 10_000))
            database.tradeDao().insert(trade(id, side = TradeSide.SELL, units = 1, pricePerUnitMinor = 12_000))

            viewModel.uiState.test {
                val state = awaitStateThat { !it.loading }

                assertTrue("nothing is held, so nothing is listed", state.isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `with no account at all the screen knows to send the owner elsewhere`() =
        runTest {
            val empty = createTestDatabase()
            val standalone =
                viewModels.keep(
                    PortfolioViewModel(
                        RoomPortfolioRepository(empty.holdingDao(), empty.tradeDao(), empty.priceQuoteDao()),
                        RoomAccountRepository(empty.accountDao(), empty.transactionDao(), empty, clock),
                    ),
                )

            standalone.uiState.test {
                val state = awaitStateThat { !it.loading }

                assertTrue(state.isEmpty)
                assertEquals(false, state.hasAccount)
                cancelAndIgnoreRemainingEvents()
            }
            empty.close()
        }
}
