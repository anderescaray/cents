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

    for (trade in trades.sortedWith(compareBy({ it.date }, { it.id }))) {
        when (trade.side) {
            TradeSide.BUY -> {
                quantity += trade.quantity
                basisMinor += trade.net.minorUnits
            }

            TradeSide.SELL -> {
                // More sold than held is not something the form allows, but a database can hold
                // it: an edited buy can shrink under a sale recorded against it. Selling what is
                // there and no more keeps the quantity at zero instead of going negative, which
                // would make every number below it meaningless rather than merely wrong.
                val sold = if (trade.quantity > quantity) quantity else trade.quantity
                val costOut = proportionOf(basisMinor, sold, quantity)
                realisedMinor += trade.net.minorUnits - costOut
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
    )
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
