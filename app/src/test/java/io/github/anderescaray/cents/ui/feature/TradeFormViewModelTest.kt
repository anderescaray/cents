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
import io.github.anderescaray.cents.data.repository.RoomTradeRepository
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Quantity
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.ui.feature.investments.TradeFormViewModel
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
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TradeFormViewModelTest {
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
    fun `a purchase saves what was typed, as exact integers`() =
        runTest {
            val viewModel = formFor()
            viewModel.onQuantityTyped("2,5")
            viewModel.onPriceTyped("123,45")
            viewModel.onFeeTyped("1,20")

            viewModel.uiState.test {
                assertTrue(awaitStateThat { it.canSave }.canSave)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.save()

            val saved =
                database
                    .tradeDao()
                    .observeForHolding(holdingId)
                    .first()
                    .single()
            assertEquals(TradeSide.BUY, saved.side)
            assertEquals(Quantity.SCALE * 5 / 2, saved.quantityScaled)
            assertEquals(12_345L, saved.pricePerUnitMinor)
            assertEquals(120L, saved.feeMinor)
        }

    @Test
    fun `what a purchase costs includes the fee, and a sale returns less because of it`() =
        runTest {
            val viewModel = formFor()
            viewModel.onQuantityTyped("2")
            viewModel.onPriceTyped("100")
            viewModel.onFeeTyped("5")

            viewModel.uiState.test {
                val buying = awaitStateThat { it.total.minorUnits != 0L }
                assertEquals(Money(20_500, "EUR"), buying.total)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.onSideChanged(TradeSide.SELL)

            viewModel.uiState.test {
                val selling = awaitStateThat { it.side == TradeSide.SELL }
                assertEquals(Money(19_500, "EUR"), selling.total)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a sale of more than is held cannot be saved, and says how much there is`() =
        runTest {
            database.tradeDao().insert(trade(holdingId, units = 3, pricePerUnitMinor = 10_000))

            val viewModel = formFor()
            viewModel.onSideChanged(TradeSide.SELL)
            viewModel.onPriceTyped("120")
            viewModel.onQuantityTyped("4")

            viewModel.uiState.test {
                val state =
                    awaitStateThat {
                        it.heldElsewhere == Quantity.ofUnits(3) &&
                            it.quantity == Quantity.ofUnits(4)
                    }

                assertTrue(state.sellsMoreThanHeld)
                assertFalse("the form must refuse it rather than clamp it", state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selling exactly what is held is allowed`() =
        runTest {
            database.tradeDao().insert(trade(holdingId, units = 3, pricePerUnitMinor = 10_000))

            val viewModel = formFor()
            viewModel.onSideChanged(TradeSide.SELL)
            viewModel.onPriceTyped("120")
            viewModel.onQuantityTyped("3")

            viewModel.uiState.test {
                val state =
                    awaitStateThat {
                        it.quantity == Quantity.ofUnits(3) &&
                            it.heldElsewhere == Quantity.ofUnits(3)
                    }

                assertFalse(state.sellsMoreThanHeld)
                assertTrue(state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editing a sale measures it against the position without that sale in it`() =
        runTest {
            database.tradeDao().insert(
                trade(holdingId, units = 5, pricePerUnitMinor = 10_000, date = LocalDate.of(2026, 1, 1)),
            )
            val saleId =
                database.tradeDao().insert(
                    trade(
                        holdingId,
                        side = TradeSide.SELL,
                        units = 2,
                        pricePerUnitMinor = 12_000,
                        date = LocalDate.of(2026, 2, 1),
                    ),
                )

            val viewModel = formFor(tradeId = saleId)

            viewModel.uiState.test {
                // Three are held *with* the sale counted; five are held without it, and five is
                // what this sale is allowed to grow to.
                val state = awaitStateThat { it.heldElsewhere == Quantity.ofUnits(5) }

                viewModel.onQuantityTyped("5")
                assertFalse(awaitStateThat { it.quantity == Quantity.ofUnits(5) }.sellsMoreThanHeld)
                assertEquals(TradeSide.SELL, state.side)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleting a trade takes it out of the position behind it`() =
        runTest {
            val tradeId = database.tradeDao().insert(trade(holdingId, units = 2, pricePerUnitMinor = 10_000))

            val viewModel = formFor(tradeId = tradeId)
            viewModel.confirmDeletion()

            assertTrue(
                database
                    .tradeDao()
                    .observeForHolding(holdingId)
                    .first()
                    .isEmpty(),
            )
        }

    private fun formFor(tradeId: Long = Destination.TradeForm.NEW): TradeFormViewModel =
        viewModels.keep(
            TradeFormViewModel(
                RoomTradeRepository(database.tradeDao(), clock),
                RoomHoldingRepository(database.holdingDao(), clock),
                RoomPortfolioRepository(database.holdingDao(), database.tradeDao(), database.priceQuoteDao()),
                clock,
                SavedStateHandle(mapOf("holdingId" to holdingId, "tradeId" to tradeId)),
            ),
        )
}
