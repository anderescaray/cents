package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.export.BackupParse
import io.github.anderescaray.cents.data.export.BackupReader
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.backupRepository
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.domain.model.TradeSide
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * A backup written before D40 (v14 and earlier) holds symbols, units and prices. It restores as
 * amounts and values, the way the v15 migration turns a database, and a sale's cash splits into
 * the cost it returned and its result.
 */
@RunWith(RobolectricTestRunner::class)
class LegacyInvestmentsRestoreTest {
    private lateinit var database: CentsDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an old backup's holding comes back as amounts, with its sale's result apart`() =
        runTest {
            val parsed = BackupReader.parse(V14, CentsDatabase.VERSION) as BackupParse.Restorable

            backupRepository(database, clock).restore(parsed.backup)

            assertEquals("NVIDIA", database.holdingDao().findById(5)!!.name)
            val trades = database.tradeDao().forHolding(5)
            assertEquals(listOf(TradeSide.BUY, TradeSide.SELL), trades.map { it.side })
            assertEquals(listOf(40_100L, 15_000L), trades.map { it.amountMinor })
            val cash = database.transactionDao().cashForHolding(5)
            assertEquals(listOf(40_100L, 10_025L, 4_975L), cash.map { it.amountMinor })
            assertEquals(listOf(false, false, true), cash.map { it.tradeResult })
            assertEquals(74_900L, database.accountDao().balanceOf(2))
            // The sale's own day and the last price, each a value.
            assertEquals(listOf(60_000L, 36_000L), database.holdingValueDao().forHolding(5).map { it.valueMinor })
        }

    private companion object {
        /** Noon UTC on epoch day 20 641, the day after the sale. */
        const val QUOTE_AT = 20_641L * 86_400_000L + 43_200_000L

        val V14 =
            """
            {
              "schemaVersion": 14,
              "exportedAt": "2026-10-31T10:00:00Z",
              "accounts": [{"id":2,"name":"Broker","type":"BROKER","currency":"EUR",
                            "openingBalanceMinor":100000,"colorArgb":0,"archived":false,"sortOrder":0}],
              "categories": [],
              "transactions": [
                {"id":20,"accountId":2,"categoryId":null,"kind":"EXPENSE","amountMinor":40100,
                 "currency":"EUR","date":20600,"note":null,"transferId":null,"tradeId":11},
                {"id":21,"accountId":2,"categoryId":null,"kind":"INCOME","amountMinor":15000,
                 "currency":"EUR","date":20640,"note":null,"transferId":null,"tradeId":12}
              ],
              "holdings": [{"id":5,"accountId":2,"assetClass":"STOCK","symbol":"NVD.DE","name":"NVIDIA",
                            "currency":"EUR","note":null}],
              "trades": [
                {"id":11,"holdingId":5,"side":"BUY","quantityScaled":400000000,"pricePerUnitMinor":10000,
                 "feeMinor":100,"currency":"EUR","date":20600,"note":null},
                {"id":12,"holdingId":5,"side":"SELL","quantityScaled":100000000,"pricePerUnitMinor":15000,
                 "feeMinor":0,"currency":"EUR","date":20640,"note":null}
              ],
              "quotes": [{"symbol":"NVD.DE","priceMinor":12000,"currency":"EUR","asOf":$QUOTE_AT,"source":"MANUAL"}]
            }
            """.trimIndent()
    }
}
