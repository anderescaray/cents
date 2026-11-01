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
    val holding =
        Holding(
            id = legacy.id,
            accountId = legacy.accountId,
            assetClass = AssetClass.entries.firstOrNull { it.name == legacy.assetClass } ?: AssetClass.STOCK,
            name = legacy.name.ifBlank { legacy.symbol },
            currency = legacy.currency,
            note = legacy.note,
        )
    val converter = HoldingConverter(legacy.id, legacy.currency)
    legacyTrades.sortedWith(compareBy({ it.date }, { it.id })).forEach(converter::add)
    if (quote != null && quote.currency == legacy.currency) converter.lastPrice(quote)
    return ConvertedHolding(holding, converter.trades, converter.values)
}

/** One holding's trades, in order, turned into amounts, with the units still held carried along. */
private class HoldingConverter(
    private val holdingId: Long,
    private val currency: String,
) {
    val trades = mutableListOf<Trade>()
    val values = mutableListOf<HoldingValue>()
    private var held = 0L

    fun add(trade: LegacyTrade) {
        val gross = unitsTimesPrice(trade.quantityScaled, trade.pricePerUnitMinor)
        when {
            // Kept, so the movement that points at it still has something to point at; left out
            // of every sum, as `positionOf` leaves out an amount in another currency (rule 2).
            trade.currency != currency -> {
                trades +=
                    trade.toTrade(if (trade.side == "SELL") TradeSide.SELL else TradeSide.BUY, gross, sellsAll = false)
            }

            trade.side == "BUY" -> {
                held += trade.quantityScaled
                trades += trade.toTrade(TradeSide.BUY, gross + trade.feeMinor, sellsAll = false)
            }

            trade.side == "SELL" -> {
                sell(trade)
            }

            else -> {
                held = maxOf(trade.quantityScaled, 0L)
            }
        }
    }

    private fun sell(trade: LegacyTrade) {
        val sold = minOf(trade.quantityScaled, held)
        val received = maxOf(unitsTimesPrice(sold, trade.pricePerUnitMinor) - trade.feeMinor, 0L)
        val before = held
        held -= sold
        // Only the day's first part sale leaves a value: a second one the same day is then
        // measured against what the first left, which at one price is the same share. A sale
        // whose fee swallowed it returned nothing, and says nothing about worth.
        val leavesValue = held > 0 && received > 0 && values.none { it.date == trade.date }
        if (leavesValue) {
            // What the whole holding was worth at this sale's price: the share of it this sale
            // returned is then the share of units it sold.
            val worth =
                divideRoundingHalfUp(
                    BigInteger.valueOf(received) * BigInteger.valueOf(before),
                    BigInteger.valueOf(sold),
                )
            values += HoldingValue(id = 0, holdingId = holdingId, date = trade.date, value = Money(worth, currency))
        }
        trades += trade.toTrade(TradeSide.SELL, received, sellsAll = held <= 0)
    }

    /** The last price, as the value of what is still held. */
    fun lastPrice(quote: LegacyQuote) {
        if (held <= 0) return
        val lastTrade = trades.maxOfOrNull { it.date }
        val latest = if (lastTrade != null && lastTrade.isAfter(quote.asOf)) lastTrade else quote.asOf
        // A day's value comes before that day's sales, so a price no newer than a sale is dated
        // the day after it: it is what was left, not what was there to sell.
        val date = if (trades.any { it.side == TradeSide.SELL && it.date == latest }) latest.plusDays(1) else latest
        values.removeAll { it.date == date }
        values +=
            HoldingValue(
                id = 0,
                holdingId = holdingId,
                date = date,
                value = Money(unitsTimesPrice(held, quote.priceMinor), currency),
            )
    }
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
