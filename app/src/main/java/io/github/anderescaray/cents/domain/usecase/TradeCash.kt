package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide

/**
 * One movement a trade writes in its account. [signedMinor] is negative when money leaves the
 * cash; [isResult] marks a sale's result, the one part of a trade that is shown as a movement.
 */
data class TradeCash(
    val trade: Trade,
    val isResult: Boolean,
    val signedMinor: Long,
)

/**
 * The movements a position's trades write in the account's cash (`DECISIONS.md` D40).
 *
 * - A **purchase** takes its amount out of the cash: the money moved into the asset.
 * - A **sale** puts back **the cost it removed**, which is money returning from the asset, and
 *   writes its **result** apart, as what was made or lost. The two add up to what the sale
 *   returned. Only the result is a movement the owner sees; it is kept out of Reports and budgets
 *   like every trade's cash, so a loss never eats a budget.
 *
 * A row that would be zero is not written.
 */
fun tradeCashOf(
    trades: List<Trade>,
    position: Position,
): List<TradeCash> {
    val costBySale = position.sales.associate { it.trade.id to it.cost.minorUnits }
    return trades
        .flatMap { trade ->
            when (trade.side) {
                TradeSide.BUY -> {
                    listOf(TradeCash(trade, isResult = false, signedMinor = -trade.amount.minorUnits))
                }

                TradeSide.SELL -> {
                    val cost = costBySale[trade.id] ?: 0L
                    listOf(
                        TradeCash(trade, isResult = false, signedMinor = cost),
                        TradeCash(trade, isResult = true, signedMinor = trade.amount.minorUnits - cost),
                    )
                }
            }
        }.filter { it.signedMinor != 0L }
}
