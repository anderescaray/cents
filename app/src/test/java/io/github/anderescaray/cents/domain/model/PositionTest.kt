package io.github.anderescaray.cents.domain.model

import io.github.anderescaray.cents.domain.usecase.positionFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The arithmetic the whole investments section rests on.
 *
 * A cost basis is the number every profit on the screen is measured against, and it is the one
 * thing here that cannot be checked by eye once there is more than one trade. These pin the
 * rules: fees are part of what a purchase cost, a sale removes cost at the average paid rather
 * than at the price it sold for, and nothing rounds its way into a position that is worth a few
 * cents after everything has been sold.
 */
class PositionTest {
    @Test
    fun `a single purchase costs what was paid, fee included`() {
        val position = positionOf(buy(units = 10, priceMinor = 10_000, feeMinor = 500))

        assertEquals(Quantity.ofUnits(10), position.quantity)
        assertEquals(Money(100_500, EUR), position.costBasis)
        assertEquals(Money(10_050, EUR), position.averageCost)
    }

    @Test
    fun `two purchases at different prices average out`() {
        val position =
            positionOf(
                buy(units = 10, priceMinor = 10_000),
                buy(units = 10, priceMinor = 20_000),
            )

        assertEquals(Quantity.ofUnits(20), position.quantity)
        assertEquals(Money(300_000, EUR), position.costBasis)
        assertEquals(Money(15_000, EUR), position.averageCost)
    }

    @Test
    fun `a sale removes cost at the average paid, not at the price it sold for`() {
        val position =
            positionOf(
                buy(units = 10, priceMinor = 10_000),
                buy(units = 10, priceMinor = 20_000),
                // Half the position leaves at 300,00, having cost 150,00 on average.
                sell(units = 10, priceMinor = 30_000),
            )

        assertEquals(Quantity.ofUnits(10), position.quantity)
        // What is left still cost the average, not what the survivors happened to be bought at.
        assertEquals(Money(150_000, EUR), position.costBasis)
        assertEquals(Money(15_000, EUR), position.averageCost)
        // 300,00 out, 150,00 of cost gone with it.
        assertEquals(Money(150_000, EUR), position.realisedPnl)
    }

    @Test
    fun `selling everything leaves exactly nothing, not a rounding remainder`() {
        val position =
            positionOf(
                // A price and a quantity chosen so the average does not divide evenly.
                buy(quantityScaled = 3 * Quantity.SCALE, priceMinor = 10_001),
                sell(quantityScaled = 3 * Quantity.SCALE, priceMinor = 12_000),
            )

        assertEquals(Quantity.ZERO, position.quantity)
        assertEquals("a closed position must cost nothing", Money(0, EUR), position.costBasis)
        assertNull("there is no average cost of nothing", position.averageCost)
        assertEquals(Money(36_000 - 30_003, EUR), position.realisedPnl)
    }

    @Test
    fun `a fee makes a sale return less, and shows up in what was made`() {
        val position =
            positionOf(
                buy(units = 1, priceMinor = 10_000),
                sell(units = 1, priceMinor = 12_000, feeMinor = 300),
            )

        // 120,00 back, minus a 3,00 fee, minus the 100,00 it cost: 17,00.
        assertEquals(Money(1_700, EUR), position.realisedPnl)
    }

    @Test
    fun `trades are applied in date order, whatever order they arrive in`() {
        val early = buy(units = 10, priceMinor = 10_000, date = LocalDate.of(2026, 1, 5))
        val later = sell(units = 5, priceMinor = 20_000, date = LocalDate.of(2026, 3, 5))

        val asStored = positionOf(later, early)
        val inOrder = positionOf(early, later)

        assertEquals(inOrder.costBasis, asStored.costBasis)
        assertEquals(inOrder.realisedPnl, asStored.realisedPnl)
    }

    @Test
    fun `a sale bigger than the position takes what is there and stops at zero`() {
        // Not something the form allows, but an edited purchase can shrink under a sale
        // recorded against it. A negative quantity would make every number below it nonsense.
        val position =
            positionOf(
                buy(units = 1, priceMinor = 10_000),
                sell(units = 5, priceMinor = 12_000),
            )

        assertEquals(Quantity.ZERO, position.quantity)
        assertEquals(Money(0, EUR), position.costBasis)
    }

    @Test
    fun `a clamped sale books proceeds only for the units that were really there`() {
        // One unit held, five recorded as sold at 120,00. Selling only what exists is half the
        // answer; the other half is the money. Booking 600,00 of proceeds against the cost of
        // one unit would report 500,00 of profit that never happened — as *realised*, which is
        // the number that reads as money already made.
        val position =
            positionOf(
                buy(units = 1, priceMinor = 10_000),
                sell(units = 5, priceMinor = 12_000),
            )

        assertEquals(Quantity.ZERO, position.quantity)
        assertEquals(Money(0, EUR), position.costBasis)
        assertEquals(Money(2_000, EUR), position.realisedPnl)
    }

    @Test
    fun `a clamped sale still pays its whole fee, because a fee is charged per order`() {
        val position =
            positionOf(
                buy(units = 1, priceMinor = 10_000),
                sell(units = 5, priceMinor = 12_000, feeMinor = 300),
            )

        // 120,00 for the one unit that existed, minus the 3,00 the order cost, minus 100,00.
        assertEquals(Money(1_700, EUR), position.realisedPnl)
    }

    @Test
    fun `what it is worth needs a price, and says nothing without one`() {
        val unpriced = positionOf(buy(units = 2, priceMinor = 10_000))

        assertNull(unpriced.marketValue)
        assertNull(unpriced.unrealisedPnl)
        assertNull(unpriced.unrealisedReturn)

        val priced = unpriced.copy(quote = quoteOf(12_500))

        assertEquals(Money(25_000, EUR), priced.marketValue)
        assertEquals(Money(5_000, EUR), priced.unrealisedPnl)
        assertEquals(0.25, priced.unrealisedReturn!!, 0.0001)
    }

    @Test
    fun `a fractional quantity is exact, to the eighth decimal, and rounds half away from zero`() {
        // The smallest quantity there is, at a price that makes it worth exactly half a cent.
        val half = positionOf(buy(quantityScaled = 1, priceMinor = 50_000_000))
        assertEquals(Money(1, EUR), half.costBasis)

        // A tenth of that is worth less than half a cent, and rounds to nothing rather than
        // being counted as a cent nobody spent.
        val sliver = positionOf(buy(quantityScaled = 1, priceMinor = 5_000_000))
        assertEquals(Money(0, EUR), sliver.costBasis)

        // And a real fractional purchase: half a share at 300,00 is 150,00.
        val third = positionOf(buy(quantityScaled = Quantity.SCALE / 2, priceMinor = 30_000))
        assertEquals(Money(15_000, EUR), third.costBasis)
    }

    private fun positionOf(vararg trades: Trade): Position = positionFrom(HOLDING, trades.toList(), null)

    private fun buy(
        units: Long = 1,
        quantityScaled: Long = units * Quantity.SCALE,
        priceMinor: Long,
        feeMinor: Long = 0,
        date: LocalDate = LocalDate.of(2026, 1, 1),
    ) = tradeOf(TradeSide.BUY, quantityScaled, priceMinor, feeMinor, date)

    private fun sell(
        units: Long = 1,
        quantityScaled: Long = units * Quantity.SCALE,
        priceMinor: Long,
        feeMinor: Long = 0,
        date: LocalDate = LocalDate.of(2026, 2, 1),
    ) = tradeOf(TradeSide.SELL, quantityScaled, priceMinor, feeMinor, date)

    private fun tradeOf(
        side: TradeSide,
        quantityScaled: Long,
        priceMinor: Long,
        feeMinor: Long,
        date: LocalDate,
    ) = Trade(
        id = nextId++,
        holdingId = 1,
        side = side,
        quantity = Quantity(quantityScaled),
        pricePerUnit = Money(priceMinor, EUR),
        fee = Money(feeMinor, EUR),
        date = date,
        note = null,
    )

    private fun quoteOf(priceMinor: Long) =
        PriceQuote(
            symbol = HOLDING.symbol,
            price = Money(priceMinor, EUR),
            asOf = java.time.Instant.parse("2026-09-09T10:00:00Z"),
            source = QuoteSource.MANUAL,
        )

    private var nextId = 1L

    private companion object {
        const val EUR = "EUR"

        val HOLDING =
            Holding(
                id = 1,
                accountId = 1,
                assetClass = AssetClass.STOCK,
                symbol = "NVDA",
                name = "NVIDIA",
                currency = EUR,
                note = null,
            )
    }
}
