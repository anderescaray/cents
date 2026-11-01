package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.divideRoundingHalfUp
import java.math.BigInteger
import java.time.LocalDate

/**
 * A holding as it was kept before D40 (Room v14 and earlier): a symbol, and trades in units at a
 * price per unit. Read by the v15 migration and by the restore of an older backup, which both turn
 * it into the amounts D40 keeps, the same way.
 */
data class LegacyHolding(
    val id: Long,
    val accountId: Long,
    val assetClass: String,
    val symbol: String,
    val name: String,
    val currency: String,
    val note: String?,
)

/** A trade before D40. [quantityScaled] is in hundred-millionths of a unit. */
data class LegacyTrade(
    val id: Long,
    val holdingId: Long,
    val side: String,
    val quantityScaled: Long,
    val pricePerUnitMinor: Long,
    val feeMinor: Long,
    val currency: String,
    val date: LocalDate,
    val note: String?,
)

/** The last price typed for a symbol before D40, and the day it was true. */
data class LegacyQuote(
    val symbol: String,
    val priceMinor: Long,
    val currency: String,
    val asOf: LocalDate,
)

/** What a legacy portfolio becomes. The values have no ids yet: they are new rows. */
data class ConvertedPortfolio(
    val holdings: List<Holding>,
    val trades: List<Trade>,
    val values: List<HoldingValue>,
)

/**
 * Turns units and prices into the amounts D40 keeps, keeping every id, so the movements that point
 * at a trade still point at it.
 *
 * - A holding keeps its name, or its symbol when it had no name, and drops the symbol.
 * - A **purchase** becomes what it cost, fee included: the amount that left the cash.
 * - A **sale** becomes what it returned, fee taken off, and empties the asset when it sold every
 *   unit. A part sale also leaves a **value on its day**: what the whole holding was worth at the
 *   price it sold for. Measured against that value, the sale removes the same share of cost the
 *   average-cost method did, so its result is the one the owner already saw.
 * - A **correction** moves no money and has no amount to become. The units it set count in the
 *   last value below, and nowhere else.
 * - The **last price** of a holding still held becomes its value, on the later of the price's day
 *   and the last trade's, so what it is worth now is what the old screen said.
 */
fun convertLegacyPortfolio(
    holdings: List<LegacyHolding>,
    trades: List<LegacyTrade>,
    quotes: List<LegacyQuote>,
): ConvertedPortfolio {
    val tradesByHolding = trades.groupBy { it.holdingId }
    val quoteBySymbol = quotes.associateBy { it.symbol }
    val converted =
        holdings.map { legacy ->
            convertHolding(legacy, tradesByHolding[legacy.id].orEmpty(), quoteBySymbol[legacy.symbol])
        }
    return ConvertedPortfolio(
        holdings = converted.map { it.holding },
        trades = converted.flatMap { it.trades },
        values = converted.flatMap { it.values },
    )
}

private class ConvertedHolding(
    val holding: Holding,
    val trades: List<Trade>,
    val values: List<HoldingValue>,
)

private fun convertHolding(
    legacy: LegacyHolding,
    legacyTrades: List<LegacyTrade>,
    quote: LegacyQuote?,
): ConvertedHolding {
    val currency = legacy.currency
    val holding =
        Holding(
            id = legacy.id,
            accountId = legacy.accountId,
            assetClass = AssetClass.entries.firstOrNull { it.name == legacy.assetClass } ?: AssetClass.STOCK,
            name = legacy.name.ifBlank { legacy.symbol },
            currency = currency,
            note = legacy.note,
        )
    var held = 0L
    val trades = mutableListOf<Trade>()
    val values = mutableListOf<HoldingValue>()
    for (trade in legacyTrades.filter { it.currency == currency }.sortedWith(compareBy({ it.date }, { it.id }))) {
        val gross = unitsTimesPrice(trade.quantityScaled, trade.pricePerUnitMinor)
        when (trade.side) {
            "BUY" -> {
                held += trade.quantityScaled
                trades += trade.toTrade(TradeSide.BUY, gross + trade.feeMinor, sellsAll = false)
            }

            "SELL" -> {
                val sold = minOf(trade.quantityScaled, held)
                val received = maxOf(unitsTimesPrice(sold, trade.pricePerUnitMinor) - trade.feeMinor, 0L)
                val before = held
                held -= sold
                if (held > 0 && sold > 0) {
                    // What the whole holding was worth at this sale's price: the share of it this
                    // sale returned is then the share of units it sold.
                    val worth =
                        divideRoundingHalfUp(
                            BigInteger.valueOf(received) * BigInteger.valueOf(before),
                            BigInteger.valueOf(sold),
                        )
                    values +=
                        HoldingValue(id = 0, holdingId = legacy.id, date = trade.date, value = Money(worth, currency))
                }
                trades += trade.toTrade(TradeSide.SELL, received, sellsAll = held <= 0)
            }

            else -> {
                held = maxOf(trade.quantityScaled, 0L)
            }
        }
    }
    if (held > 0 && quote != null && quote.currency == currency) {
        val lastTrade = trades.maxOfOrNull { it.date }
        val date = if (lastTrade != null && lastTrade.isAfter(quote.asOf)) lastTrade else quote.asOf
        values.removeAll { it.date == date }
        values +=
            HoldingValue(
                id = 0,
                holdingId = legacy.id,
                date = date,
                value = Money(unitsTimesPrice(held, quote.priceMinor), currency),
            )
    }
    return ConvertedHolding(holding, trades, values)
}

private fun LegacyTrade.toTrade(
    side: TradeSide,
    amountMinor: Long,
    sellsAll: Boolean,
) = Trade(
    id = id,
    holdingId = holdingId,
    side = side,
    amount = Money(amountMinor, currency),
    date = date,
    note = note,
    sellsAll = sellsAll,
)

/** Units in hundred-millionths times a price in cents, back in cents, rounded half up. */
private fun unitsTimesPrice(
    quantityScaled: Long,
    priceMinor: Long,
): Long = divideRoundingHalfUp(BigInteger.valueOf(quantityScaled) * BigInteger.valueOf(priceMinor), QUANTITY_SCALE)

private val QUANTITY_SCALE: BigInteger = BigInteger.valueOf(100_000_000L)
