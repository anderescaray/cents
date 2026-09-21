package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.PriceQuote
import io.github.anderescaray.cents.domain.model.Quantity
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.divideRoundingHalfUp
import java.math.BigInteger

/**
 * The position a list of trades leaves behind: how much is held, what it cost, and what has
 * already been made or lost on the parts that were sold.
 *
 * **Why this is Kotlin and not a `GROUP BY`.** Every other aggregation in this app is pushed
 * into SQLite (`CLAUDE.md` 5), and this one cannot be. A cost basis is not a sum: a sale
 * removes cost *at the average paid so far*, not at the price it sold for, so each trade has to
 * be applied to the result of the one before it. That is a fold over an ordered list, and the
 * list is bounded by how many times the owner has traded one instrument — dozens, not
 * thousands. Pushing it down would mean a recursive CTE that nobody can read.
 *
 * **Why the basis is reduced proportionally rather than by an average unit cost.** Multiplying
 * a rounded average by a quantity re-rounds on every sale, and those errors accumulate in the
 * one direction. Taking the same *fraction* of the basis that was sold is exact, and it leaves
 * a fully-sold position at a cost basis of exactly zero rather than at four cents.
 *
 * **A correction is not a trade and is not treated as one.** It states the quantity held on its
 * date and moves no money: what the owner paid is what they paid, and no profit is realised by
 * discovering that a number was wrong. Units appearing costs nothing (a split, a bonus, a
 * purchase recorded elsewhere) and the average per unit falls; units disappearing takes their
 * share of the cost with them, because they were never held.
 *
 * **The cost a sale removes is remembered**, not only the profit. A fully sold position has a
 * cost basis of zero — that is the point of reducing it proportionally — and the percentage
 * beside that profit needs the cost that left.
 */
fun positionFrom(
    holding: Holding,
    trades: List<Trade>,
    quote: PriceQuote?,
): Position {
    val currency = holding.currency
    var quantity = Quantity.ZERO
    var basisMinor = 0L
    var realisedMinor = 0L
    var soldCostMinor = 0L

    for (trade in trades.sortedWith(compareBy({ it.date }, { it.id }))) {
        when (trade.side) {
            TradeSide.BUY -> {
                quantity += trade.quantity
                basisMinor += trade.net.minorUnits
            }

            TradeSide.CORRECTION -> {
                // The owner saying what they actually hold, which is an **absolute** quantity
                // rather than a difference: if the real cause turns up later and gets recorded —
                // the purchase that was forgotten, say — the correction must not then add its
                // units a second time. Everything traded after it still moves from here.
                val corrected = if (trade.quantity.isNegative) Quantity.ZERO else trade.quantity
                // Money does not move. Fewer units than the app thought means those units were
                // never really held, so their cost leaves with them — proportionally, the way a
                // sale takes cost out, but with no proceeds and therefore no profit or loss to
                // realise. More units means they arrived without being paid for (a split, a
                // bonus, a purchase already recorded elsewhere), so the cost stays where it is
                // and the average per unit falls, which is exactly what a split does.
                if (corrected < quantity) {
                    basisMinor -= proportionOf(basisMinor, quantity - corrected, quantity)
                }
                quantity = corrected
            }

            TradeSide.SELL -> {
                // More sold than held is not something the form allows, but a database can hold
                // it: an edited buy can shrink under a sale recorded against it. Selling what is
                // there and no more keeps the quantity at zero instead of going negative, which
                // would make every number below it meaningless rather than merely wrong.
                //
                // **The money is clamped with the quantity**, which is the part that is easy to
                // miss: booking the proceeds of five units against the cost of the one that was
                // actually held would report a profit on four units that never existed, and it
                // would report it as *realised* — the number that reads as money already made.
                val sold = if (trade.quantity > quantity) quantity else trade.quantity
                val costOut = proportionOf(basisMinor, sold, quantity)
                realisedMinor += proceedsOf(trade, sold) - costOut
                // The basis below is about to lose this cost. A closed position's percentage is
                // the profit over it, so it has to be kept somewhere the basis is not.
                soldCostMinor += costOut
                basisMinor -= costOut
                quantity -= sold
            }
        }
    }

    return Position(
        holding = holding,
        quantity = quantity,
        costBasis = Money(basisMinor, currency),
        realisedPnl = Money(realisedMinor, currency),
        quote = quote,
        realisedCost = Money(soldCostMinor, currency),
    )
}

/**
 * What a sale of [sold] units actually returned, fee included.
 *
 * The whole fee is taken off even when the quantity was clamped: a fee is charged once for the
 * order, not per unit, and the alternative — scaling it down with the quantity — would invent a
 * cheaper trade than the one that happened.
 */
private fun proceedsOf(
    trade: Trade,
    sold: Quantity,
): Long =
    if (sold == trade.quantity) {
        trade.net.minorUnits
    } else {
        (sold * trade.pricePerUnit).minorUnits - trade.fee.minorUnits
    }

/** The share of [basisMinor] that [sold] out of [held] represents, rounded half away from zero. */
private fun proportionOf(
    basisMinor: Long,
    sold: Quantity,
    held: Quantity,
): Long =
    if (held.isZero || sold.isZero) {
        0L
    } else {
        divideRoundingHalfUp(
            BigInteger.valueOf(basisMinor) * BigInteger.valueOf(sold.scaled),
            BigInteger.valueOf(held.scaled),
        )
    }
