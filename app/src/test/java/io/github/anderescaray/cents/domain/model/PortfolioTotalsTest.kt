package io.github.anderescaray.cents.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The totals card, which is the one place a wrong number would be read as the answer to
 * "how am I doing".
 *
 * Two failures it must not commit, both found in review: showing 0,00 for a portfolio nobody has
 * priced yet — a number, and numbers get believed — and hiding a real total because the value
 * happened to be zero. And one crash it must not cause: a quote stored in another currency
 * cannot be added to this one, and the refusal must not escape into the flow that feeds the tab.
 */
class PortfolioTotalsTest {
    @Test
    fun `nothing priced means no value at all, not a value of zero`() {
        val portfolio = Portfolio(listOf(position(costMinor = 20_000, quote = null)))

        val total = portfolio.totals().single()

        assertNull("a value nobody has priced is not zero", total.marketValue)
        assertNull(total.profit)
        assertNull(total.profitRatio)
        assertEquals("what it cost is known either way", Money(20_000, EUR), total.costBasis)
        assertEquals(1, total.unpricedCount)
    }

    @Test
    fun `a portfolio genuinely worth nothing still shows its total`() {
        // Priced at zero is a real answer — a 100% loss — and it used to make the whole card
        // disappear, cost basis and all, because the filter could not tell it from "no prices".
        val portfolio = Portfolio(listOf(position(costMinor = 20_000, quote = quoteOf(0))))

        val total = portfolio.totals().single()

        assertEquals(Money(0, EUR), total.marketValue)
        assertEquals(Money(-20_000, EUR), total.profit)
        assertEquals(0, total.unpricedCount)
    }

    @Test
    fun `profit compares the priced half against what that half cost`() {
        val portfolio =
            Portfolio(
                listOf(
                    position(costMinor = 20_000, quote = quoteOf(12_500)),
                    position(symbol = "VWCE", costMinor = 30_000, quote = null),
                ),
            )

        val total = portfolio.totals().single()

        // Two units at 125,00 against the 200,00 that half cost — not against the 500,00 that
        // everything cost, which would report a loss on money the value simply does not cover.
        assertEquals(Money(25_000, EUR), total.marketValue)
        assertEquals(Money(5_000, EUR), total.profit)
        assertEquals(Money(50_000, EUR), total.costBasis)
        assertEquals(1, total.unpricedCount)
    }

    @Test
    fun `a quote in another currency prices nothing, and throws nothing`() {
        // Quotes are shared by symbol, so one can arrive carrying a currency this holding does
        // not use. Money refuses to mix them - correctly - and that refusal used to be thrown
        // from inside the flow the whole tab is built on.
        val position = position(costMinor = 20_000, quote = quoteOf(12_500, currency = "USD"))

        assertNull(position.marketValue)
        assertNull(position.unrealisedPnl)

        val totals = Portfolio(listOf(position)).totals()

        assertTrue("it reads as unpriced rather than as a crash", totals.single().unpricedCount == 1)
        assertNull(totals.single().marketValue)
    }

    @Test
    fun `two currencies are two totals, never one`() {
        val portfolio =
            Portfolio(
                listOf(
                    position(costMinor = 20_000, quote = quoteOf(12_500)),
                    position(symbol = "AAPL", currency = "USD", costMinor = 10_000, quote = quoteOf(6_000, "USD")),
                ),
            )

        val totals = portfolio.totals()

        assertEquals(listOf("EUR", "USD"), totals.map { it.currency })
    }

    @Test
    fun `a closed position is listed apart from the total and adds nothing to it`() {
        val open = position(symbol = "VWCE", costMinor = 20_000, quote = quoteOf(12_500))
        val closedStock =
            position(
                symbol = "NVDA",
                units = 0,
                costMinor = 0,
                realisedPnlMinor = 2_000,
                realisedCostMinor = 10_000,
                quote = null,
            )
        val closedCrypto =
            position(
                symbol = "BTC",
                assetClass = AssetClass.CRYPTO,
                units = 0,
                costMinor = 0,
                realisedPnlMinor = 5_000,
                realisedCostMinor = 10_000,
                quote = null,
            )
        // Never bought. A quantity of zero is not, by itself, a closed position.
        val untouched = position(symbol = "EMPTY", units = 0, costMinor = 0, quote = null)
        val portfolio = Portfolio(listOf(closedCrypto, open, untouched, closedStock))

        assertEquals(listOf("NVDA", "BTC"), portfolio.closedPositions.map { it.holding.symbol })
        val total = portfolio.totals().single()
        // Two units at 125,00. The closed holdings have no price, and that must not become
        // "left out of the total": they are not held.
        assertEquals(Money(25_000, EUR), total.marketValue)
        assertEquals(Money(20_000, EUR), total.costBasis)
        assertEquals(0, total.unpricedCount)
    }

    private fun position(
        symbol: String = "NVDA",
        currency: String = EUR,
        assetClass: AssetClass = AssetClass.STOCK,
        units: Long = 2,
        costMinor: Long,
        realisedPnlMinor: Long = 0,
        realisedCostMinor: Long = 0,
        quote: PriceQuote?,
    ) = Position(
        holding =
            Holding(
                id = 1,
                accountId = 1,
                assetClass = assetClass,
                symbol = symbol,
                name = symbol,
                currency = currency,
                note = null,
            ),
        quantity = Quantity.ofUnits(units),
        costBasis = Money(costMinor, currency),
        realisedPnl = Money(realisedPnlMinor, currency),
        realisedCost = Money(realisedCostMinor, currency),
        quote = quote,
    )

    private fun quoteOf(
        priceMinor: Long,
        currency: String = EUR,
    ) = PriceQuote(
        symbol = "NVDA",
        price = Money(priceMinor, currency),
        asOf = Instant.parse("2026-09-09T10:00:00Z"),
        source = QuoteSource.MANUAL,
    )

    private companion object {
        const val EUR = "EUR"
    }
}
