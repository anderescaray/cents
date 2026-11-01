package io.github.anderescaray.cents.ui.feature.investments

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.portfolioRepository
import io.github.anderescaray.cents.data.local.tradeCashWriter
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomHoldingRepository
import io.github.anderescaray.cents.data.repository.RoomHoldingValueRepository
import io.github.anderescaray.cents.data.repository.RoomTradeRepository
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.ViewModelKeeper
import io.github.anderescaray.cents.ui.feature.awaitStateThat
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

/** The three investment forms against a real database (`DECISIONS.md` D40). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InvestmentFormsTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private val clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 1)
    private var brokerId = 0L

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
    fun `a new asset is saved with its first purchase, in the broker it was opened from`() =
        runTest {
            broker()
            val form =
                viewModels.keep(
                    HoldingEditViewModel(
                        holdings(),
                        accounts(),
                        clock,
                        SavedStateHandle(mapOf("holdingId" to 0L, "accountId" to brokerId)),
                    ),
                )
            form.uiState.test {
                awaitStateThat { it.accountsLoaded }
                form.onNameChanged("My world fund")
                form.onAssetClassChanged(AssetClass.ETF)
                form.onAmountTyped("500")
                form.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            val position = portfolioRepository(database, clock).observePositions().first().single()
            assertEquals("My world fund", position.holding.name)
            assertEquals(AssetClass.ETF, position.holding.assetClass)
            assertEquals(Money(50_000, "EUR"), position.cost)
            assertEquals(today, position.trades.single().date)
            assertEquals(100_000L, database.accountDao().balanceOf(brokerId))
        }

    @Test
    fun `a new sale says what it makes before it is saved`() =
        runTest {
            val holdingId = fund(cost = 100_000)
            RoomHoldingValueRepository(
                database.holdingValueDao(),
                database.holdingDao(),
                tradeCashWriter(database, clock),
                database,
                clock,
            ).setValue(holdingId, today.minusDays(1), Money(120_000, "EUR"))
            val form = tradeForm(holdingId, selling = true)

            form.uiState.test {
                awaitStateThat { it.position != null }
                form.onAmountTyped("300")
                assertEquals(Money(5_000, "EUR"), awaitStateThat { it.resultPreview != null }.resultPreview)
                form.onSellsAllChanged(true)
                assertEquals(Money(-70_000, "EUR"), awaitStateThat { it.sellsAll }.resultPreview)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a part sale of at least the last value waits until it is said to sell everything`() =
        runTest {
            // Bought for 1 000, never valued since: grown, the owner sells 1 200 of it.
            val holdingId = fund(cost = 100_000)
            val form = tradeForm(holdingId, selling = true)

            form.uiState.test {
                awaitStateThat { it.position != null }
                form.onAmountTyped("1200")
                val warned = awaitStateThat { it.emptiesAgainst != null }
                assertEquals(Money(100_000, "EUR"), warned.emptiesAgainst)
                assertFalse(warned.canSave)
                form.onAmountTyped("999")
                assertTrue(awaitStateThat { it.emptiesAgainst == null && it.amountMinor == 99_900L }.canSave)
                form.onAmountTyped("1200")
                awaitStateThat { it.emptiesAgainst != null }
                form.onSellsAllChanged(true)
                assertTrue(awaitStateThat { it.sellsAll && it.emptiesAgainst == null }.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an asset that went to zero can be sold whole for nothing`() =
        runTest {
            val holdingId = fund(cost = 50_000)
            val form = tradeForm(holdingId, selling = true)

            form.uiState.test {
                assertFalse(awaitStateThat { it.position != null }.canSave)
                form.onSellsAllChanged(true)
                val whole = awaitStateThat { it.sellsAll }
                assertTrue(whole.canSave)
                assertEquals(Money(-50_000, "EUR"), whole.resultPreview)
                form.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            val position = portfolioRepository(database, clock).observePosition(holdingId).first()!!
            assertFalse(position.isOpen)
            assertEquals(Money(-50_000, "EUR"), position.realised)
        }

    @Test
    fun `the week's values are saved for the assets typed, and the others keep theirs`() =
        runTest {
            val first = fund(cost = 100_000)
            val second =
                holdings().createHolding(
                    Holding(0, brokerId, AssetClass.CRYPTO, "Bitcoin", "EUR", null),
                    Trade(0, 0, TradeSide.BUY, Money(20_000, "EUR"), today.minusDays(30), null),
                )
            val form =
                viewModels.keep(
                    ValueUpdateViewModel(
                        portfolioRepository(database, clock),
                        RoomHoldingValueRepository(
                            database.holdingValueDao(),
                            database.holdingDao(),
                            tradeCashWriter(database, clock),
                            database,
                            clock,
                        ),
                        clock,
                        SavedStateHandle(mapOf("accountId" to brokerId, "holdingId" to 0L)),
                    ),
                )

            form.uiState.test {
                awaitStateThat { it.entries.size == 2 }
                form.onValueTyped(first, "1100")
                val typed = awaitStateThat { it.canSave }
                assertEquals(Money(10_000, "EUR"), typed.entries.first { it.position.holding.id == first }.change)
                form.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            val positions =
                portfolioRepository(
                    database,
                    clock,
                ).observePositions().first().associateBy { it.holding.id }
            assertEquals(Money(110_000, "EUR"), positions.getValue(first).value)
            assertEquals(today, positions.getValue(first).lastValuedOn)
            assertEquals(Money(20_000, "EUR"), positions.getValue(second).value)
        }

    private suspend fun broker() {
        brokerId = database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))
        database.transactionDao().insert(transaction(brokerId, kind = TransactionKind.INCOME, amountMinor = 150_000))
    }

    private suspend fun fund(cost: Long): Long {
        broker()
        return holdings().createHolding(
            Holding(0, brokerId, AssetClass.INDEX_FUND, "World fund", "EUR", null),
            Trade(0, 0, TradeSide.BUY, Money(cost, "EUR"), today.minusDays(30), null),
        )
    }

    private fun tradeForm(
        holdingId: Long,
        selling: Boolean,
    ) = viewModels.keep(
        TradeFormViewModel(
            RoomTradeRepository(
                database.tradeDao(),
                database.transactionDao(),
                tradeCashWriter(database, clock),
                database,
                clock,
            ),
            portfolioRepository(database, clock),
            clock,
            SavedStateHandle(mapOf("holdingId" to holdingId, "tradeId" to 0L, "selling" to selling)),
        ),
    )

    private fun holdings() =
        RoomHoldingRepository(
            database.holdingDao(),
            database.tradeDao(),
            database.transactionDao(),
            tradeCashWriter(database, clock),
            database,
            clock,
        )

    private fun accounts() = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock)
}
