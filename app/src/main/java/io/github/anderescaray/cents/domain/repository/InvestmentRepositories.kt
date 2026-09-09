package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Portfolio
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.PriceQuote
import io.github.anderescaray.cents.domain.model.Trade
import kotlinx.coroutines.flow.Flow

interface HoldingRepository {
    fun observeHoldings(): Flow<List<Holding>>

    fun observeHolding(id: Long): Flow<Holding?>

    suspend fun findById(id: Long): Holding?

    /**
     * The holding that already exists for this symbol in this account, if any. The unique index
     * would refuse the insert anyway; asking first is what lets the editor say *why* instead of
     * throwing a constraint violation out of a view model.
     */
    suspend fun findBySymbol(
        accountId: Long,
        symbol: String,
    ): Holding?

    suspend fun createHolding(holding: Holding): Long

    suspend fun updateHolding(holding: Holding)

    /**
     * Deletes the holding **and every trade recorded against it**, in one database transaction.
     *
     * There is no version that keeps the trades: a trade with no holding is not a record of
     * anything. It is reached only through a confirmation naming how many go, the same bargain
     * account deletion strikes (D7), and nothing undoes it.
     */
    suspend fun deleteHolding(id: Long)

    /** How many trades go with it, which is what the confirmation has to say out loud. */
    suspend fun countTrades(id: Long): Int
}

interface TradeRepository {
    fun observeTrades(holdingId: Long): Flow<List<Trade>>

    suspend fun findById(id: Long): Trade?

    suspend fun addTrade(trade: Trade): Long

    suspend fun updateTrade(trade: Trade)

    suspend fun deleteTrade(id: Long)
}

interface PriceQuoteRepository {
    fun observeQuotes(): Flow<List<PriceQuote>>

    fun observeQuote(symbol: String): Flow<PriceQuote?>

    /**
     * Records a price the owner typed, against the symbol rather than the holding.
     *
     * [asOf] is when the price was true, which is not always now: a price read off a broker's
     * app on Friday and entered on Sunday is Friday's. Everything that shows a quote shows its
     * age, so this is the field that decides whether the portfolio looks trustworthy.
     */
    suspend fun setManualPrice(
        symbol: String,
        price: Money,
        asOf: java.time.Instant,
    )
}

/**
 * The portfolio, assembled from holdings, their trades and the prices known for them.
 *
 * It is its own repository rather than a method on [HoldingRepository] because it is a *read
 * model*: three flows combined and folded into positions, with nothing to write. Screens ask
 * for this; the three above exist for editing.
 */
interface PortfolioRepository {
    fun observePortfolio(): Flow<Portfolio>

    fun observePosition(holdingId: Long): Flow<Position?>
}
