package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.usecase.positionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** What an asset's purchases, sales and typed values add up to (`DECISIONS.md` D40). */
class PositionTest {
    private val fund =
        Holding(1, accountId = 1, assetClass = AssetClass.INDEX_FUND, name = "World", currency = "EUR", note = null)
    private val day1 = LocalDate.of(2026, 11, 2)
    private val day2 = day1.plusDays(7)
    private val day3 = day1.plusDays(14)

    @Test
    fun `money put in is worth what was paid until a value says otherwise`() {
        val position = positionOf(fund, listOf(buy(1, 100_000, day1)), emptyList())

        assertEquals(eur(100_000), position.value)
        assertEquals(eur(100_000), position.cost)
        assertTrue(position.profit.isZero)
        assertNull(position.lastValuedOn)
    }

    @Test
    fun `a typed value moves the worth and not the cost`() {
        val position = positionOf(fund, listOf(buy(1, 100_000, day1)), listOf(value(1, 110_000, day2)))

        assertEquals(eur(110_000), position.value)
        assertEquals(eur(100_000), position.cost)
        assertEquals(eur(10_000), position.profit)
        assertEquals(0.1, position.profitRatio!!, 1e-9)
        assertEquals(day2, position.lastValuedOn)
    }

    @Test
    fun `a week with no value keeps the last one`() {
        val position = positionOf(fund, listOf(buy(1, 100_000, day1)), listOf(value(1, 110_000, day2)))

        assertEquals(eur(110_000), position.valueOn(day3))
        assertEquals(eur(100_000), position.valueOn(day2.minusDays(1)))
        assertTrue(position.valueOn(day1.minusDays(1)).isZero)
    }

    @Test
    fun `buying more after a value adds to it`() {
        val position =
            positionOf(fund, listOf(buy(1, 100_000, day1), buy(2, 50_000, day3)), listOf(value(1, 110_000, day2)))

        assertEquals(eur(160_000), position.value)
        assertEquals(eur(150_000), position.cost)
    }

    @Test
    fun `a part sale takes cost in the share of the worth it sold`() {
        // Worth 120 000 on cost 100 000; selling 30 000 sells a quarter, so a quarter of the cost.
        val position =
            positionOf(fund, listOf(buy(1, 100_000, day1), sell(2, 30_000, day3)), listOf(value(1, 120_000, day2)))

        val sale = position.sales.single()
        assertEquals(eur(25_000), sale.cost)
        assertEquals(eur(5_000), sale.result)
        assertEquals(eur(90_000), position.value)
        assertEquals(eur(75_000), position.cost)
        assertEquals(eur(5_000), position.realised)
        assertTrue(position.isOpen)
    }

    @Test
    fun `selling everything takes all the cost whatever the last value said`() {
        val position =
            positionOf(
                fund,
                listOf(buy(1, 100_000, day1), sell(2, 95_000, day3, all = true)),
                listOf(value(1, 120_000, day2)),
            )

        assertEquals(eur(-5_000), position.sales.single().result)
        assertTrue(position.value.isZero)
        assertTrue(position.cost.isZero)
        assertFalse(position.isOpen)
    }

    @Test
    fun `selling as much as it is worth empties it too`() {
        val position = positionOf(fund, listOf(buy(1, 100_000, day1), sell(2, 130_000, day2)), emptyList())

        assertEquals(eur(30_000), position.sales.single().result)
        assertFalse(position.isOpen)
    }

    @Test
    fun `on one day, the purchase comes first, then the value, then the sale`() {
        // Bought 100 000 and valued 200 000 the same day: the value already has the purchase in
        // it. A sale that day is measured against that value, so half the worth is half the cost.
        val position =
            positionOf(fund, listOf(sell(2, 100_000, day1), buy(1, 100_000, day1)), listOf(value(1, 200_000, day1)))

        assertEquals(eur(50_000), position.sales.single().cost)
        assertEquals(eur(100_000), position.value)
    }

    @Test
    fun `a second value on the same day replaces the first`() {
        val position =
            positionOf(fund, listOf(buy(1, 100_000, day1)), listOf(value(1, 105_000, day2), value(2, 107_000, day2)))

        assertEquals(eur(107_000), position.value)
    }

    @Test
    fun `an amount in another currency is left out rather than added`() {
        val dollars = Trade(9, fund.id, TradeSide.BUY, Money(5_000, "USD"), day2, null)
        val position = positionOf(fund, listOf(buy(1, 100_000, day1), dollars), emptyList())

        assertEquals(eur(100_000), position.value)
    }

    private fun buy(
        id: Long,
        minor: Long,
        date: LocalDate,
    ) = Trade(id, fund.id, TradeSide.BUY, eur(minor), date, null)

    private fun sell(
        id: Long,
        minor: Long,
        date: LocalDate,
        all: Boolean = false,
    ) = Trade(id, fund.id, TradeSide.SELL, eur(minor), date, null, sellsAll = all)

    private fun value(
        id: Long,
        minor: Long,
        date: LocalDate,
    ) = HoldingValue(id, fund.id, date, eur(minor))

    private fun eur(minor: Long) = Money(minor, "EUR")
}
