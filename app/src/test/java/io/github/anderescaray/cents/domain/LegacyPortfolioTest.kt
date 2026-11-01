package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.usecase.LegacyHolding
import io.github.anderescaray.cents.domain.usecase.LegacyQuote
import io.github.anderescaray.cents.domain.usecase.LegacyTrade
import io.github.anderescaray.cents.domain.usecase.convertLegacyPortfolio
import io.github.anderescaray.cents.domain.usecase.positionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A holding kept in units before D40 becomes amounts, keeping what the owner already saw. */
class LegacyPortfolioTest {
    private val nvidia = LegacyHolding(7, 1, "STOCK", "NVD.DE", "NVIDIA", "EUR", "note")
    private val bought = LocalDate.of(2026, 9, 1)
    private val sold = LocalDate.of(2026, 9, 20)

    @Test
    fun `a purchase becomes what it cost, fee included, keeping its id`() {
        val converted =
            convertLegacyPortfolio(
                listOf(nvidia),
                listOf(trade(11, "BUY", units = 2, price = 10_000, fee = 150)),
                emptyList(),
            )

        val trade = converted.trades.single()
        assertEquals(11L, trade.id)
        assertEquals(TradeSide.BUY, trade.side)
        assertEquals(Money(20_150, "EUR"), trade.amount)
        val holding = converted.holdings.single()
        assertEquals("NVIDIA", holding.name)
        assertEquals(AssetClass.STOCK, holding.assetClass)
        assertEquals("note", holding.note)
    }

    @Test
    fun `a part sale keeps the result the average cost gave it`() {
        // Four units at 100 €, then one sold at 150 €: the old screen said +50 €.
        val converted =
            convertLegacyPortfolio(
                listOf(nvidia),
                listOf(
                    trade(11, "BUY", units = 4, price = 10_000),
                    trade(12, "SELL", units = 1, price = 15_000, date = sold),
                ),
                emptyList(),
            )

        val position = positionOf(converted.holdings.single(), converted.trades, converted.values)
        assertEquals(Money(5_000, "EUR"), position.sales.single().result)
        assertFalse(converted.trades.last().sellsAll)
        assertEquals(Money(30_000, "EUR"), position.cost)
    }

    @Test
    fun `selling every unit empties the asset`() {
        val converted =
            convertLegacyPortfolio(
                listOf(nvidia),
                listOf(
                    trade(11, "BUY", units = 2, price = 10_000),
                    trade(12, "SELL", units = 2, price = 9_000, date = sold),
                ),
                emptyList(),
            )

        assertTrue(converted.trades.last().sellsAll)
        val position = positionOf(converted.holdings.single(), converted.trades, converted.values)
        assertEquals(Money(-2_000, "EUR"), position.sales.single().result)
        assertFalse(position.isOpen)
    }

    @Test
    fun `the last price becomes the value, so what it is worth now is what the old screen said`() {
        val converted =
            convertLegacyPortfolio(
                listOf(nvidia),
                listOf(trade(11, "BUY", units = 3, price = 10_000)),
                listOf(LegacyQuote("NVD.DE", 12_000, "EUR", LocalDate.of(2026, 9, 25))),
            )

        val position = positionOf(converted.holdings.single(), converted.trades, converted.values)
        assertEquals(Money(36_000, "EUR"), position.value)
        assertEquals(LocalDate.of(2026, 9, 25), position.lastValuedOn)
    }

    @Test
    fun `a price older than the last trade is dated with the trade`() {
        val converted =
            convertLegacyPortfolio(
                listOf(nvidia),
                listOf(
                    trade(11, "BUY", units = 1, price = 10_000),
                    trade(12, "BUY", units = 1, price = 11_000, date = sold),
                ),
                listOf(LegacyQuote("NVD.DE", 12_000, "EUR", bought)),
            )

        val position = positionOf(converted.holdings.single(), converted.trades, converted.values)
        assertEquals(Money(24_000, "EUR"), position.value)
        assertEquals(sold, position.lastValuedOn)
    }

    @Test
    fun `a holding without a name is named by its symbol`() {
        val converted = convertLegacyPortfolio(listOf(nvidia.copy(name = " ")), emptyList(), emptyList())

        assertEquals("NVD.DE", converted.holdings.single().name)
    }

    @Test
    fun `a correction moves no money and only counts in the last value`() {
        val converted =
            convertLegacyPortfolio(
                listOf(nvidia),
                listOf(
                    trade(11, "BUY", units = 2, price = 10_000),
                    trade(12, "CORRECTION", units = 3, price = 0, date = sold),
                ),
                listOf(LegacyQuote("NVD.DE", 10_000, "EUR", sold)),
            )

        assertEquals(listOf(11L), converted.trades.map { it.id })
        assertEquals(Money(30_000, "EUR"), converted.values.single().value)
    }

    private fun trade(
        id: Long,
        side: String,
        units: Long,
        price: Long,
        fee: Long = 0,
        date: LocalDate = bought,
    ) = LegacyTrade(id, nvidia.id, side, units * 100_000_000, price, fee, "EUR", date, null)
}
