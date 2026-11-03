package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.Sale
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.ValueStep
import io.github.anderescaray.cents.domain.model.divideRoundingHalfUp
import java.math.BigInteger

/**
 * What an asset's purchases, sales and typed values add up to (`DECISIONS.md` D40).
 *
 * A fold over the days something happened, in order, carrying two numbers: what the asset is
 * **worth** and what it **cost**.
 *
 * - A **purchase** adds its amount to both: money put in is worth what was paid until someone
 *   says otherwise.
 * - A **value** replaces the worth. It says nothing about cost.
 * - A **sale** takes its amount out of the worth, and takes cost out in the proportion of the
 *   worth it sold. Selling everything, or as much as the asset was worth, takes all of it.
 *   What the sale made is its amount minus the cost it took.
 *
 * **Within one day, purchases come first, then the value, then sales.** A value typed on the day
 * of a purchase is what the asset was worth with that purchase in it, and a sale on the day of a
 * value is measured against that value. One value per day is kept, the last one written.
 *
 * **Why Kotlin, not SQL** (`CLAUDE.md` §5): what a sale removes depends on everything before it,
 * which is a fold, and the list is as long as the owner's own record of one asset.
 *
 * Amounts in another currency than the asset's are left out rather than added (rule 2); only an
 * install that predates D2 could hold one.
 */
fun positionOf(
    holding: Holding,
    trades: List<Trade>,
    values: List<HoldingValue>,
): Position {
    val currency = holding.currency
    val ownTrades = trades.filter { it.amount.currency == currency }
    val ownValues = values.filter { it.value.currency == currency }
    val tradesByDay = ownTrades.groupBy { it.date }
    val valueByDay = ownValues.groupBy { it.date }.mapValues { (_, day) -> day.maxBy { it.id } }
    val days = (tradesByDay.keys + valueByDay.keys).sorted()

    var worth = 0L
    var cost = 0L
    val sales = mutableListOf<Sale>()
    val steps = mutableListOf<ValueStep>()
    for (day in days) {
        val onDay = tradesByDay[day].orEmpty().sortedBy { it.id }
        onDay.filter { it.side == TradeSide.BUY }.forEach { buy ->
            worth += buy.amount.minorUnits
            cost += buy.amount.minorUnits
        }
        valueByDay[day]?.let { worth = it.value.minorUnits }
        onDay.filter { it.side == TradeSide.SELL }.forEach { sale ->
            val received = sale.amount.minorUnits
            val emptied = sale.sellsAll || worth <= 0 || received >= worth
            val removed = if (emptied) cost else proportion(cost, received, worth)
            cost -= removed
            worth = if (emptied) 0 else worth - received
            sales += Sale(holding = holding, trade = sale, cost = Money(removed, currency))
        }
        steps += ValueStep(day, Money(worth, currency))
    }
    return Position(
        holding = holding,
        value = Money(worth, currency),
        cost = Money(cost, currency),
        sales = sales,
        steps = steps,
        lastValuedOn = ownValues.maxOfOrNull { it.date },
        trades = ownTrades.sortedWith(compareBy({ it.date }, { it.id })),
        values = ownValues.sortedWith(compareBy({ it.date }, { it.id })),
    )
}

/** [whole] × [part] / [of], rounded half up, without overflowing on the way. */
private fun proportion(
    whole: Long,
    part: Long,
    of: Long,
): Long = divideRoundingHalfUp(BigInteger.valueOf(whole) * BigInteger.valueOf(part), BigInteger.valueOf(of))
