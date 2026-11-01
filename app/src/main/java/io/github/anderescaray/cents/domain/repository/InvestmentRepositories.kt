package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.InvestmentAccount
import io.github.anderescaray.cents.domain.model.InvestmentOverview
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.Trade
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Assets (`DECISIONS.md` D40). Every write that changes what an asset holds also writes again the
 * movements its trades make in the account's cash, in the same database transaction, so the cash
 * and the assets cannot disagree.
 */
interface HoldingRepository {
    fun observeHolding(id: Long): Flow<Holding?>

    suspend fun findById(id: Long): Holding?

    /** Creates the asset together with its first purchase: an asset with nothing in it is not one. */
    suspend fun createHolding(
        holding: Holding,
        firstPurchase: Trade,
    ): Long

    suspend fun updateHolding(holding: Holding)

    /**
     * Deletes the asset, its trades, its values and the cash its trades moved, in one database
     * transaction. Reached only through a confirmation naming how many trades go, and nothing
     * undoes it — the bargain account deletion strikes (D7).
     */
    suspend fun deleteHolding(id: Long)

    suspend fun countTrades(id: Long): Int
}

/** Purchases and sales. */
interface TradeRepository {
    suspend fun findById(id: Long): Trade?

    suspend fun addTrade(trade: Trade): Long

    suspend fun updateTrade(trade: Trade)

    suspend fun deleteTrade(id: Long)
}

/** The values the owner types, at least weekly. */
interface HoldingValueRepository {
    suspend fun findById(id: Long): HoldingValue?

    /** The asset's worth on [date]; a second one the same day replaces the first. */
    suspend fun setValue(
        holdingId: Long,
        date: LocalDate,
        value: Money,
    ) = setValues(date, mapOf(holdingId to value))

    /** The week's values, by asset id, all written or none. */
    suspend fun setValues(
        date: LocalDate,
        values: Map<Long, Money>,
    )

    suspend fun deleteValue(id: Long)
}

/**
 * The read model: assets, trades, values and the broker accounts' cash, folded into what the
 * screens show. Nothing to write; the three above exist for that.
 */
interface PortfolioRepository {
    /** Every asset, open or not, wherever it is held. */
    fun observePositions(): Flow<List<Position>>

    fun observePosition(holdingId: Long): Flow<Position?>

    /**
     * Every active broker account with what is in it, and any other account still holding an open
     * asset, so none is out of reach: Invested's screen.
     */
    fun observeOverview(): Flow<InvestmentOverview>

    fun observeAccount(accountId: Long): Flow<InvestmentAccount?>
}
