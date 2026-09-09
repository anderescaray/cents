package io.github.anderescaray.cents.data.local

import android.database.sqlite.SQLiteConstraintException
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.QuoteSource
import io.github.anderescaray.cents.domain.model.TradeSide
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class InvestmentDaoTest {
    private lateinit var database: CentsDatabase
    private var accountId: Long = 0

    @Before
    fun setUp() =
        runTest {
            database = createTestDatabase()
            accountId = database.accountDao().insert(account(name = "Broker"))
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `holdings come back in name order`() =
        runTest {
            database.holdingDao().insert(holding(accountId, symbol = "VWCE", name = "Vanguard All-World"))
            database.holdingDao().insert(
                holding(accountId, symbol = "BTC", name = "Bitcoin", assetClass = AssetClass.CRYPTO),
            )

            val names =
                database
                    .holdingDao()
                    .observeAll()
                    .first()
                    .map { it.name }

            assertEquals(listOf("Bitcoin", "Vanguard All-World"), names)
        }

    @Test
    fun `the same symbol cannot be held twice in one account`() =
        runTest {
            database.holdingDao().insert(holding(accountId, symbol = "NVDA"))

            // Two rows for one instrument would split a single position, and every average and
            // total would then be right about each half and wrong about the holding.
            val failure =
                runCatching {
                    database.holdingDao().insert(holding(accountId, symbol = "NVDA", name = "NVIDIA again"))
                }.exceptionOrNull()

            assertTrue("expected the unique index to refuse it, got $failure", failure is SQLiteConstraintException)
        }

    @Test
    fun `the same symbol in two accounts is fine, and they share one price`() =
        runTest {
            val second = database.accountDao().insert(account(name = "Second broker"))
            database.holdingDao().insert(holding(accountId, symbol = "NVDA"))
            database.holdingDao().insert(holding(second, symbol = "NVDA"))
            database.priceQuoteDao().upsert(quote(symbol = "NVDA", priceMinor = 12_345))

            assertEquals(
                2,
                database
                    .holdingDao()
                    .observeAll()
                    .first()
                    .size,
            )
            assertEquals(
                1,
                database
                    .priceQuoteDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun `a price replaces the one before it rather than piling up`() =
        runTest {
            database.priceQuoteDao().upsert(quote(priceMinor = 10_000, asOf = Instant.parse("2026-09-01T00:00:00Z")))
            database.priceQuoteDao().upsert(quote(priceMinor = 11_000, asOf = Instant.parse("2026-09-08T00:00:00Z")))

            val stored = database.priceQuoteDao().observeBySymbol("NVDA").first()

            assertNotNull(stored)
            assertEquals(11_000L, stored!!.priceMinor)
            assertEquals(QuoteSource.MANUAL, stored.source)
            assertEquals(
                1,
                database
                    .priceQuoteDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun `trades for a holding come back newest first`() =
        runTest {
            val id = database.holdingDao().insert(holding(accountId))
            database.tradeDao().insert(trade(id, date = LocalDate.of(2026, 1, 5)))
            database.tradeDao().insert(trade(id, side = TradeSide.SELL, date = LocalDate.of(2026, 3, 5)))

            val dates =
                database
                    .tradeDao()
                    .observeForHolding(id)
                    .first()
                    .map { it.date }

            assertEquals(listOf(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 1, 5)), dates)
        }

    @Test
    fun `deleting a holding takes its trades with it, and only its own`() =
        runTest {
            val kept = database.holdingDao().insert(holding(accountId, symbol = "VWCE", name = "Vanguard"))
            val doomed = database.holdingDao().insert(holding(accountId, symbol = "NVDA"))
            database.tradeDao().insert(trade(kept))
            database.tradeDao().insert(trade(doomed))
            database.tradeDao().insert(trade(doomed, side = TradeSide.SELL))

            assertEquals(2, database.holdingDao().countTrades(doomed))

            database.holdingDao().deleteWithTrades(doomed)

            assertNull(database.holdingDao().findById(doomed))
            assertTrue(
                database
                    .tradeDao()
                    .observeForHolding(doomed)
                    .first()
                    .isEmpty(),
            )
            assertEquals("the other holding keeps its history", 1, database.holdingDao().countTrades(kept))
        }

    @Test
    fun `an account holding investments cannot be deleted out from under them`() =
        runTest {
            database.holdingDao().insert(holding(accountId))

            // The foreign key is RESTRICT, the same bargain a movement strikes with its
            // account: what "delete" means for an account that holds things is a question,
            // not something the database answers by quietly erasing rows.
            val failure = runCatching { database.accountDao().deleteById(accountId) }.exceptionOrNull()

            assertTrue("expected the foreign key to refuse it, got $failure", failure is SQLiteConstraintException)
        }

    @Test
    fun `looking a symbol up in an account finds only that account's`() =
        runTest {
            val second = database.accountDao().insert(account(name = "Second broker"))
            database.holdingDao().insert(holding(accountId, symbol = "NVDA"))

            assertNotNull(database.holdingDao().findBySymbol(accountId, "NVDA"))
            assertNull(database.holdingDao().findBySymbol(second, "NVDA"))
        }
}
