package io.github.anderescaray.cents.data.local

import io.github.anderescaray.cents.data.repository.toDomain
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.positionOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * v14 to v15 (`DECISIONS.md` D40): holdings in units at a price become amounts and values, and
 * each sale's cash splits into the cost it returned and its result, with every id kept.
 */
@RunWith(RobolectricTestRunner::class)
class InvestmentsMigrationTest {
    private lateinit var old: MigrationTestDatabase

    @Before
    fun setUp() {
        old = MigrationTestDatabase()
    }

    @Test
    fun `a version 14 holding comes through as amounts, with the result the owner already saw`() =
        runTest {
            old.createVersion(14)
            old.writeRaw(
                "INSERT INTO accounts (id, name, type, currency, openingBalanceMinor, colorArgb, archived, " +
                    "sortOrder, createdAt) VALUES (2, 'Broker', 'BROKER', 'EUR', 100000, 0, 0, 1, $AT)",
                "INSERT INTO holdings (id, accountId, assetClass, symbol, name, currency, note, createdAt, " +
                    "updatedAt) VALUES (5, 2, 'STOCK', 'NVD.DE', 'NVIDIA', 'EUR', 'long term', $AT, $AT)",
                // Four units at 100 €, 1 € fee; then one sold at 150 €.
                "INSERT INTO trades (id, holdingId, side, quantityScaled, pricePerUnitMinor, feeMinor, currency, " +
                    "date, createdAt) VALUES (11, 5, 'BUY', 400000000, 10000, 100, 'EUR', $BOUGHT, $AT)",
                "INSERT INTO trades (id, holdingId, side, quantityScaled, pricePerUnitMinor, feeMinor, currency, " +
                    "date, createdAt) VALUES (12, 5, 'SELL', 100000000, 15000, 0, 'EUR', $SOLD, $AT)",
                "INSERT INTO transactions (id, accountId, kind, amountMinor, currency, date, tradeId, createdAt, " +
                    "updatedAt) VALUES (20, 2, 'EXPENSE', 40100, 'EUR', $BOUGHT, 11, $AT, $AT)",
                "INSERT INTO transactions (id, accountId, kind, amountMinor, currency, date, tradeId, createdAt, " +
                    "updatedAt) VALUES (21, 2, 'INCOME', 15000, 'EUR', $SOLD, 12, $AT, $AT)",
                // 120 € a unit, at noon on the day after the sale.
                "INSERT INTO price_quotes (symbol, priceMinor, currency, asOf, source) " +
                    "VALUES ('NVD.DE', 12000, 'EUR', ${(SOLD + 1) * 86_400_000L + 43_200_000L}, 'MANUAL')",
            )

            val database = old.openWithMigrations()
            try {
                val holding = database.holdingDao().findById(5)!!
                assertEquals("NVIDIA", holding.name)
                assertEquals("long term", holding.note)

                val trades = database.tradeDao().forHolding(5)
                assertEquals(listOf(11L, 12L), trades.map { it.id })
                assertEquals(listOf(TradeSide.BUY, TradeSide.SELL), trades.map { it.side })
                assertEquals(listOf(40_100L, 15_000L), trades.map { it.amountMinor })
                assertFalse(trades.last().sellsAll)

                // The buy's row is untouched; the sale's first row keeps its id and is now the
                // cost it returned, 40 100 / 4; its result, 49,75 €, is a row of its own.
                val cash = database.transactionDao().cashForHolding(5)
                assertEquals(listOf(20L, 21L), cash.take(2).map { it.id })
                assertEquals(listOf(40_100L, 10_025L, 4_975L), cash.map { it.amountMinor })
                assertEquals(listOf(false, false, true), cash.map { it.tradeResult })
                assertEquals(TransactionKind.INCOME, cash.last().kind)
                assertEquals("the account's cash is what it was", 74_900L, database.accountDao().balanceOf(2))

                // Three units left, at the last price: what the old screen said they were worth.
                val values = database.holdingValueDao().forHolding(5)
                val position =
                    positionOf(
                        holding.toDomain(),
                        trades.map { it.toDomain() },
                        values.map { it.toDomain() },
                    )
                assertEquals(36_000L, position.value.minorUnits)
                assertEquals(30_075L, position.cost.minorUnits)
                assertEquals(LocalDate.ofEpochDay(SOLD + 1), position.lastValuedOn)

                assertTrue("no price table is left", tables(database).none { it == "price_quotes" })
                val listed =
                    database
                        .transactionDao()
                        .observeAll()
                        .first()
                        .map { it.id }
                assertTrue(listed.containsAll(listOf(1L, 20L, 21L)))
            } finally {
                database.close()
            }
        }

    @Test
    fun `a version 14 database with no investments opens as it was`() =
        runTest {
            old.createVersion(14)

            val database = old.openWithMigrations()
            try {
                assertEquals("the money written at v14 should survive", 2_550L, moneyIn(database))
                assertTrue(database.holdingDao().all().isEmpty())
            } finally {
                database.close()
            }
        }

    private fun tables(database: CentsDatabase): List<String> =
        database.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
            buildList { while (rows.moveToNext()) add(rows.getString(0)) }
        }

    private companion object {
        const val BOUGHT = 20_600L
        const val SOLD = 20_640L
        const val AT = 1_755_511_200_000L
    }
}
