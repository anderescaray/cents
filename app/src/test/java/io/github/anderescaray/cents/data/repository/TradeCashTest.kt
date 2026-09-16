package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Quantity
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Buying spends the account's cash, and selling puts it back.
 *
 * What this is really protecting is one sentence: **an investments account's balance is the cash
 * left in it**. Before this, a broker account showed everything ever transferred into it while
 * the portfolio showed what that money had become, and the two were both true and did not add
 * up to anything. The price of fixing it is that a trade now writes two records, so most of
 * what is below is about them never drifting apart.
 */
@RunWith(RobolectricTestRunner::class)
class TradeCashTest {
    private lateinit var database: CentsDatabase
    private lateinit var trades: RoomTradeRepository
    private lateinit var holdings: RoomHoldingRepository

    private var brokerId: Long = 0
    private var holdingId: Long = 0

    private val clock = Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneOffset.UTC)
    private val date = LocalDate.of(2026, 9, 16)

    @Before
    fun setUp() =
        runTest {
            database = createTestDatabase()
            trades =
                RoomTradeRepository(
                    database.tradeDao(),
                    database.holdingDao(),
                    database.transactionDao(),
                    database,
                    clock,
                )
            holdings = RoomHoldingRepository(database.holdingDao(), database.transactionDao(), database, clock)

            // A thousand euros transferred in, which is where an investments account's money
            // comes from (D11). The opening balance stands in for the transfer.
            brokerId = database.accountDao().insert(account(name = "Broker", openingBalanceMinor = 100_000))
            holdingId = database.holdingDao().insert(holding(brokerId, symbol = "NVD.DE"))
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `buying four hundred euros of shares leaves six hundred in cash`() =
        runTest {
            trades.addTrade(buy(units = 2, priceMinor = 20_000))

            // The done-when this branch was written against: 1.000 in, 400 spent, 600 left.
            assertEquals(60_000L, balance())

            val cash =
                database
                    .transactionDao()
                    .observeByAccount(brokerId)
                    .first()
                    .single()
            assertEquals(TransactionKind.EXPENSE, cash.kind)
            assertEquals(40_000L, cash.amountMinor)
            assertEquals(date, cash.date)
            assertNull("the cash of a trade is not classified", cash.categoryId)
        }

    @Test
    fun `the fee comes out of the account too, because it was paid`() =
        runTest {
            trades.addTrade(buy(units = 2, priceMinor = 20_000, feeMinor = 150))

            assertEquals(
                40_150L,
                database
                    .transactionDao()
                    .observeByAccount(brokerId)
                    .first()
                    .single()
                    .amountMinor,
            )
            assertEquals(59_850L, balance())
        }

    @Test
    fun `selling puts the money back, profit included`() =
        runTest {
            trades.addTrade(buy(units = 2, priceMinor = 20_000))
            trades.addTrade(sell(units = 2, priceMinor = 25_000))

            // 1.000 - 400 + 500. The gain reaches the account because a sale is real money
            // arriving, which is the thing a balance derived from the cost basis could not say.
            assertEquals(110_000L, balance())
            val rows = database.transactionDao().observeByAccount(brokerId).first()
            assertEquals(2, rows.size)
            assertEquals(50_000L, rows.single { it.kind == TransactionKind.INCOME }.amountMinor)
        }

    @Test
    fun `a correction moves no money at all`() =
        runTest {
            trades.addTrade(buy(units = 2, priceMinor = 20_000))
            trades.addTrade(
                Trade(
                    id = 0,
                    holdingId = holdingId,
                    side = TradeSide.CORRECTION,
                    quantity = Quantity.ofUnits(3),
                    pricePerUnit = Money.zero("EUR"),
                    fee = Money.zero("EUR"),
                    date = date,
                    note = null,
                ),
            )

            // Discovering that a number was wrong costs nothing, so the account does not move
            // and no second row appears beside the purchase.
            assertEquals(60_000L, balance())
            assertEquals(
                1,
                database
                    .transactionDao()
                    .observeByAccount(brokerId)
                    .first()
                    .size,
            )
        }

    @Test
    fun `editing a purchase rewrites its cash instead of recording a second lot`() =
        runTest {
            val id = trades.addTrade(buy(units = 2, priceMinor = 20_000))
            val rowId =
                database
                    .transactionDao()
                    .observeByAccount(brokerId)
                    .first()
                    .single()
                    .id

            trades.updateTrade(buy(units = 2, priceMinor = 15_000).copy(id = id))

            val rows = database.transactionDao().observeByAccount(brokerId).first()
            assertEquals("one purchase is one movement, whatever it is edited to", 1, rows.size)
            assertEquals("and it is the same row, so it keeps its place in the list", rowId, rows.single().id)
            assertEquals(30_000L, rows.single().amountMinor)
            assertEquals(70_000L, balance())
        }

    @Test
    fun `a purchase edited into a correction takes its cash back with it`() =
        runTest {
            val id = trades.addTrade(buy(units = 2, priceMinor = 20_000))

            trades.updateTrade(
                Trade(
                    id = id,
                    holdingId = holdingId,
                    side = TradeSide.CORRECTION,
                    quantity = Quantity.ofUnits(2),
                    pricePerUnit = Money.zero("EUR"),
                    fee = Money.zero("EUR"),
                    date = date,
                    note = null,
                ),
            )

            // A trade that has stopped moving money must not leave its old movement standing:
            // the account would be short by an amount whose reason no longer exists.
            assertTrue(
                database
                    .transactionDao()
                    .observeByAccount(brokerId)
                    .first()
                    .isEmpty(),
            )
            assertEquals(100_000L, balance())
        }

    @Test
    fun `a sale whose fee swallows it reads as money leaving`() =
        runTest {
            trades.addTrade(buy(units = 2, priceMinor = 20_000))
            trades.addTrade(sell(units = 1, priceMinor = 10_000, feeMinor = 12_000))

            // Ten euros back, twelve paid to get it: the account is two euros lighter, and a
            // pair of cases keyed on "a sale puts money in" would have got this backwards.
            val cash = database.transactionDao().observeByAccount(brokerId).first()
            val proceeds = cash.single { it.tradeId != null && it.amountMinor == 2_000L }
            assertEquals(TransactionKind.EXPENSE, proceeds.kind)
            assertEquals(58_000L, balance())
        }

    @Test
    fun `deleting a trade takes its cash, and deleting a holding takes all of it`() =
        runTest {
            val id = trades.addTrade(buy(units = 1, priceMinor = 20_000))
            trades.addTrade(buy(units = 1, priceMinor = 30_000))

            trades.deleteTrade(id)
            assertEquals(
                1,
                database
                    .transactionDao()
                    .observeByAccount(brokerId)
                    .first()
                    .size,
            )
            assertEquals(70_000L, balance())

            holdings.deleteHolding(holdingId)
            assertTrue(
                "cash left behind by a deleted holding is money missing for no reason",
                database
                    .transactionDao()
                    .observeByAccount(brokerId)
                    .first()
                    .isEmpty(),
            )
            assertEquals(100_000L, balance())
        }

    @Test
    fun `the cash of a trade is in no report, and in every balance`() =
        runTest {
            trades.addTrade(buy(units = 2, priceMinor = 20_000))
            val dao = database.transactionDao()
            val from = LocalDate.of(2026, 9, 1)
            val to = LocalDate.of(2026, 9, 30)

            val totals = dao.observeTotalsBetween(from, to).first()
            assertEquals("buying is not spending", 0L, totals.expenseMinor)
            assertEquals(0L, totals.incomeMinor)
            assertTrue(dao.observeTotalsByCategory(TransactionKind.EXPENSE, from, to, null).first().isEmpty())
            assertTrue(dao.observeMonthlyTotals(from, to, null).first().isEmpty())

            // The balance line is the other half of the rule: the money did leave.
            assertEquals(
                -40_000L,
                dao
                    .observeDailyNet(from, to, brokerId)
                    .first()
                    .single()
                    .netMinor,
            )
        }

    @Test
    fun `an uncategorized drill-down does not open onto a purchase`() =
        runTest {
            trades.addTrade(buy(units = 2, priceMinor = 20_000))

            val uncategorized =
                database
                    .transactionDao()
                    .observeFilteredWithNames(
                        accountId = null,
                        categoryId = null,
                        uncategorized = true,
                        kind = null,
                        from = null,
                        to = null,
                        weekday = null,
                        minAmountMinor = null,
                        maxAmountMinor = null,
                        query = null,
                    ).first()

            assertTrue("a purchase is not an unclassified movement", uncategorized.isEmpty())
        }

    @Test
    fun `the list knows what was bought and where to find it`() =
        runTest {
            val id = trades.addTrade(buy(units = 2, priceMinor = 20_000))

            val row =
                database
                    .transactionDao()
                    .observeByAccountWithNames(brokerId)
                    .first()
                    .single()

            assertEquals(id, row.tradeId)
            assertEquals("NVD.DE", row.tradeSymbol)
            assertEquals(holdingId, row.tradeHoldingId)
        }

    private suspend fun balance(): Long = database.accountDao().observeBalance(brokerId).first() ?: 0L

    private fun buy(
        units: Long,
        priceMinor: Long,
        feeMinor: Long = 0,
    ) = trade(TradeSide.BUY, units, priceMinor, feeMinor)

    private fun sell(
        units: Long,
        priceMinor: Long,
        feeMinor: Long = 0,
    ) = trade(TradeSide.SELL, units, priceMinor, feeMinor)

    private fun trade(
        side: TradeSide,
        units: Long,
        priceMinor: Long,
        feeMinor: Long,
    ) = Trade(
        id = 0,
        holdingId = holdingId,
        side = side,
        quantity = Quantity.ofUnits(units),
        pricePerUnit = Money(priceMinor, "EUR"),
        fee = Money(feeMinor, "EUR"),
        date = date,
        note = null,
    )
}
