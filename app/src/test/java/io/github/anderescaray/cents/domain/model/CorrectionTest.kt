package io.github.anderescaray.cents.domain.model

import io.github.anderescaray.cents.domain.usecase.positionFrom
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Correcting what is held: the owner saying the app is wrong, and the app believing them without
 * losing what it recorded.
 *
 * The rules this pins, all of which follow from a correction moving **units and not money**:
 * finding more units costs nothing and lowers the average; finding fewer takes their share of the
 * cost with them and realises no profit, because no money came back; and the correction states
 * the quantity **on its date**, so fixing the real cause later cannot count it twice.
 */
class CorrectionTest {
    @Test
    fun `finding more units than the app thought costs nothing and lowers the average`() {
        // Bought 50 at 10,00. The broker says 60 — a split, a bonus, a purchase made elsewhere.
        val position =
            positionOf(
                buy(units = 50, priceMinor = 1_000),
                correctTo(units = 60),
            )

        assertEquals(Quantity.ofUnits(60), position.quantity)
        assertEquals("what was paid is what was paid", Money(50_000, EUR), position.costBasis)
        // 500,00 spread over 60 rather than 50.
        assertEquals(Money(833, EUR), position.averageCost)
        assertEquals("nothing was sold, so nothing was made", Money(0, EUR), position.realisedPnl)
    }

    @Test
    fun `finding fewer units takes their share of the cost with them, and realises nothing`() {
        val position =
            positionOf(
                buy(units = 60, priceMinor = 1_000),
                correctTo(units = 50),
            )

        assertEquals(Quantity.ofUnits(50), position.quantity)
        // 600,00 paid, a sixth of the units were never held, so a sixth of the cost goes.
        assertEquals(Money(50_000, EUR), position.costBasis)
        assertEquals(Money(1_000, EUR), position.averageCost)
        assertEquals(
            "discovering a number was wrong is not a profit or a loss",
            Money(0, EUR),
            position.realisedPnl,
        )
    }

    @Test
    fun `a correction states the quantity, so a later fix cannot count it twice`() {
        // The owner corrects 50 up to 60 in March. In April they realise why: a purchase in
        // February that was never recorded. Recording it must not leave them holding 70.
        val corrected =
            positionOf(
                buy(units = 50, priceMinor = 1_000, date = LocalDate.of(2026, 1, 5)),
                correctTo(units = 60, date = LocalDate.of(2026, 3, 5)),
            )
        val withTheRealCause =
            positionOf(
                buy(units = 50, priceMinor = 1_000, date = LocalDate.of(2026, 1, 5)),
                buy(units = 10, priceMinor = 1_200, date = LocalDate.of(2026, 2, 5)),
                correctTo(units = 60, date = LocalDate.of(2026, 3, 5)),
            )

        assertEquals(Quantity.ofUnits(60), corrected.quantity)
        assertEquals(Quantity.ofUnits(60), withTheRealCause.quantity)
        // And the cost is now the truth rather than the guess: the forgotten purchase is in it.
        assertEquals(Money(50_000, EUR), corrected.costBasis)
        assertEquals(Money(62_000, EUR), withTheRealCause.costBasis)
    }

    @Test
    fun `trades after a correction move from the corrected number`() {
        val position =
            positionOf(
                buy(units = 50, priceMinor = 1_000, date = LocalDate.of(2026, 1, 5)),
                correctTo(units = 60, date = LocalDate.of(2026, 2, 5)),
                sell(units = 10, priceMinor = 1_500, date = LocalDate.of(2026, 3, 5)),
            )

        assertEquals(Quantity.ofUnits(50), position.quantity)
        // Sold at 15,00 what cost 8,33 on average: 150,00 back, a sixth of 500,00 of cost gone.
        assertEquals(Money(15_000 - 8_333, EUR), position.realisedPnl)
    }

    @Test
    fun `correcting to nothing empties the position without inventing a loss`() {
        val position =
            positionOf(
                buy(units = 10, priceMinor = 1_000),
                correctTo(units = 0),
            )

        assertEquals(Quantity.ZERO, position.quantity)
        assertEquals(Money(0, EUR), position.costBasis)
        assertEquals(Money(0, EUR), position.realisedPnl)
    }

    @Test
    fun `a correction is worth nothing on its own, and says so`() {
        val correction = correctTo(units = 60)

        assertEquals(Money(0, EUR), correction.gross)
        assertEquals(Money(0, EUR), correction.net)
    }

    private fun positionOf(vararg trades: Trade): Position = positionFrom(HOLDING, trades.toList(), null)

    private fun buy(
        units: Long,
        priceMinor: Long,
        date: LocalDate = LocalDate.of(2026, 1, 1),
    ) = tradeOf(TradeSide.BUY, units, priceMinor, date)

    private fun sell(
        units: Long,
        priceMinor: Long,
        date: LocalDate = LocalDate.of(2026, 4, 1),
    ) = tradeOf(TradeSide.SELL, units, priceMinor, date)

    private fun correctTo(
        units: Long,
        date: LocalDate = LocalDate.of(2026, 2, 1),
    ) = tradeOf(TradeSide.CORRECTION, units, 0, date)

    private fun tradeOf(
        side: TradeSide,
        units: Long,
        priceMinor: Long,
        date: LocalDate,
    ) = Trade(
        id = nextId++,
        holdingId = 1,
        side = side,
        quantity = Quantity.ofUnits(units),
        pricePerUnit = Money(priceMinor, EUR),
        fee = Money(0, EUR),
        date = date,
        note = null,
    )

    private var nextId = 1L

    private companion object {
        const val EUR = "EUR"

        val HOLDING =
            Holding(
                id = 1,
                accountId = 1,
                assetClass = AssetClass.STOCK,
                symbol = "NVD.DE",
                name = "NVIDIA",
                currency = EUR,
                note = null,
            )
    }
}
