package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.portfolioRepository
import io.github.anderescaray.cents.data.local.tradeCashWriter
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Assets, trades and values through the real repositories and database (`DECISIONS.md` D40):
 * the cash each write leaves in the account, and what the screens read back.
 */
@RunWith(RobolectricTestRunner::class)
class InvestmentRepositoriesTest {
    private lateinit var database: CentsDatabase
    private lateinit var holdings: RoomHoldingRepository
    private lateinit var trades: RoomTradeRepository
    private lateinit var values: RoomHoldingValueRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC)
    private val bought = LocalDate.of(2026, 10, 5)
    private val valued = LocalDate.of(2026, 10, 19)
    private val sold = LocalDate.of(2026, 10, 26)
    private var brokerId = 0L

    @Before
    fun setUp() {
        database = createTestDatabase()
        val cash = tradeCashWriter(database, clock)
        holdings =
            RoomHoldingRepository(
                database.holdingDao(),
                database.tradeDao(),
                database.transactionDao(),
                cash,
                database,
                clock,
            )
        trades = RoomTradeRepository(database.tradeDao(), database.transactionDao(), cash, database, clock)
        values = RoomHoldingValueRepository(database.holdingValueDao(), database.holdingDao(), cash, database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a new asset spends its first purchase out of the account's cash`() =
        runTest {
            val holdingId = newFund(purchase = 100_000)

            assertEquals(50_000L, cash())
            val position = portfolioRepository(database, clock).observePosition(holdingId).first()!!
            assertEquals(Money(100_000, "EUR"), position.value)
        }

    @Test
    fun `a sale returns its cost to the cash and writes its result apart`() =
        runTest {
            val holdingId = newFund(purchase = 100_000)
            values.setValue(holdingId, valued, Money(120_000, "EUR"))
            trades.addTrade(sale(holdingId, 30_000))

            // A quarter of the worth sold: 25 000 of cost back, 5 000 made. 50 000 + 30 000.
            assertEquals(80_000L, cash())
            val rows = database.transactionDao().cashForHolding(holdingId)
            assertEquals(listOf(false, false, true), rows.map { it.tradeResult })
            assertEquals(listOf(100_000L, 25_000L, 5_000L), rows.map { it.amountMinor })
        }

    @Test
    fun `a value typed before a sale changes what the sale made`() =
        runTest {
            val holdingId = newFund(purchase = 100_000)
            trades.addTrade(sale(holdingId, 30_000))
            assertEquals(0L, result(holdingId))

            values.setValue(holdingId, valued, Money(120_000, "EUR"))

            assertEquals(5_000L, result(holdingId))
            assertEquals(80_000L, cash())
        }

    @Test
    fun `a second value the same day replaces the first`() =
        runTest {
            val holdingId = newFund(purchase = 100_000)
            values.setValue(holdingId, valued, Money(105_000, "EUR"))
            values.setValue(holdingId, valued, Money(107_000, "EUR"))

            val position = portfolioRepository(database, clock).observePosition(holdingId).first()!!
            assertEquals(1, position.values.size)
            assertEquals(Money(107_000, "EUR"), position.value)
        }

    @Test
    fun `deleting an asset takes its trades, values and cash with it`() =
        runTest {
            val holdingId = newFund(purchase = 100_000)
            values.setValue(holdingId, valued, Money(110_000, "EUR"))
            trades.addTrade(sale(holdingId, 20_000))

            holdings.deleteHolding(holdingId)

            assertEquals(150_000L, cash())
            assertTrue(database.holdingValueDao().all().isEmpty())
            assertTrue(
                database
                    .tradeDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `the lists show a sale's result and never a purchase`() =
        runTest {
            val holdingId = newFund(purchase = 100_000)
            trades.addTrade(sale(holdingId, 120_000, all = true))

            val listed =
                RoomTransactionRepository(database.transactionDao(), database, clock)
                    .observeTransactions(TransactionFilter.None)
                    .first()
                    .filter { it.transaction.tradeId != null }

            val row = listed.single()
            assertTrue(row.tradeResult)
            assertEquals("World fund", row.tradeAssetName)
            assertEquals(Money(20_000, "EUR"), row.transaction.amount)
        }

    @Test
    fun `an account reads its cash, what is invested, and its cash through time`() =
        runTest {
            val holdingId = newFund(purchase = 100_000)
            values.setValue(holdingId, valued, Money(110_000, "EUR"))

            val account = portfolioRepository(database, clock).observeAccount(brokerId).first()!!

            assertEquals(Money(50_000, "EUR"), account.cash)
            assertEquals(Money(110_000, "EUR"), account.invested)
            assertEquals(Money(160_000, "EUR"), account.total)
            assertEquals(Money(10_000, "EUR"), account.profit)
            // Before the deposit there was nothing; after it, 150 000; after the purchase, 50 000
            // in cash and 100 000 in the asset.
            assertEquals(Money.zero("EUR"), account.totalOn(bought.minusDays(10)))
            assertEquals(Money(150_000, "EUR"), account.totalOn(bought))
            assertEquals(Money(160_000, "EUR"), account.totalOn(valued))
        }

    @Test
    fun `Invested lists every active broker and adds them up`() =
        runTest {
            newFund(purchase = 100_000)
            database.accountDao().insert(
                account(name = "Second broker", type = AccountType.BROKER, openingBalanceMinor = 5_000),
            )
            database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 999_999))

            val overview = portfolioRepository(database, clock).observeOverview().first()

            assertEquals(listOf("Broker", "Second broker"), overview.accounts.map { it.account.name }.sorted())
            assertEquals(Money(155_000, "EUR"), overview.sums?.total)
            assertEquals(Money(100_000, "EUR"), overview.sums?.cost)
        }

    /** A broker that received 150 000, and an asset bought for [purchase]. */
    private suspend fun newFund(purchase: Long): Long {
        brokerId = database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))
        database.transactionDao().insert(
            transaction(brokerId, kind = TransactionKind.INCOME, amountMinor = 150_000, date = bought.minusDays(3)),
        )
        return holdings.createHolding(
            Holding(0, brokerId, AssetClass.INDEX_FUND, "World fund", "EUR", null),
            Trade(0, 0, TradeSide.BUY, Money(purchase, "EUR"), bought, null),
        )
    }

    private fun sale(
        holdingId: Long,
        minor: Long,
        all: Boolean = false,
    ) = Trade(0, holdingId, TradeSide.SELL, Money(minor, "EUR"), sold, null, sellsAll = all)

    private suspend fun cash(): Long = database.accountDao().balanceOf(brokerId)!!

    private suspend fun result(holdingId: Long): Long {
        val row = database.transactionDao().cashForHolding(holdingId).firstOrNull { it.tradeResult } ?: return 0
        return if (row.kind == TransactionKind.INCOME) row.amountMinor else -row.amountMinor
    }
}
