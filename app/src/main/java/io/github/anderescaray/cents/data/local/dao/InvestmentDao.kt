package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import io.github.anderescaray.cents.data.local.entity.HoldingEntity
import io.github.anderescaray.cents.data.local.entity.PriceQuoteEntity
import io.github.anderescaray.cents.data.local.entity.TradeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Holdings and the trades behind them.
 *
 * There is no query here that computes a position. A cost basis is a fold over the trades in
 * order rather than a sum — a sale removes cost at the average paid, not at the price it sold
 * for — so it lives in `positionFrom` in the domain layer, where the reasoning is written down.
 * What SQLite does here is what it is good at: return the rows, in order, as flows.
 */
@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings ORDER BY name")
    fun observeAll(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings WHERE id = :id")
    fun observeById(id: Long): Flow<HoldingEntity?>

    @Query("SELECT * FROM holdings WHERE id = :id")
    suspend fun findById(id: Long): HoldingEntity?

    /** The row that would collide with the unique index, so an editor can say so first. */
    @Query("SELECT * FROM holdings WHERE accountId = :accountId AND symbol = :symbol LIMIT 1")
    suspend fun findBySymbol(
        accountId: Long,
        symbol: String,
    ): HoldingEntity?

    @Query("SELECT COUNT(*) FROM trades WHERE holdingId = :id")
    suspend fun countTrades(id: Long): Int

    @Insert
    suspend fun insert(holding: HoldingEntity): Long

    @Update
    suspend fun update(holding: HoldingEntity)

    /**
     * Deletes the holding and every trade recorded against it, in one database transaction.
     *
     * The foreign key is `RESTRICT`, so this is the only way a holding with history goes: the
     * trades have to be removed first, deliberately, and the two deletions have to succeed or
     * fail together. It is reached only through a confirmation that names how many trades go
     * with it, and nothing undoes it — the same bargain account deletion strikes (D7).
     */
    @Transaction
    suspend fun deleteWithTrades(id: Long) {
        deleteTradesOf(id)
        deleteById(id)
    }

    @Query("DELETE FROM trades WHERE holdingId = :id")
    suspend fun deleteTradesOf(id: Long)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Every holding. Only a restore does this, and only after the trades against them. */
    @Query("DELETE FROM holdings")
    suspend fun deleteAll()
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY date, id")
    fun observeAll(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE holdingId = :holdingId ORDER BY date DESC, id DESC")
    fun observeForHolding(holdingId: Long): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun findById(id: Long): TradeEntity?

    @Insert
    suspend fun insert(trade: TradeEntity): Long

    @Update
    suspend fun update(trade: TradeEntity)

    @Query("DELETE FROM trades WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Every trade. Only a restore does this, and only after the cash rows that point at them. */
    @Query("DELETE FROM trades")
    suspend fun deleteAll()
}

@Dao
interface PriceQuoteDao {
    @Query("SELECT * FROM price_quotes")
    fun observeAll(): Flow<List<PriceQuoteEntity>>

    @Query("SELECT * FROM price_quotes WHERE symbol = :symbol")
    fun observeBySymbol(symbol: String): Flow<PriceQuoteEntity?>

    /** One price per symbol, replaced rather than appended: this is a cache, not a history. */
    @Upsert
    suspend fun upsert(quote: PriceQuoteEntity)

    /** Every quote. Nothing points at these, so they can go whenever. */
    @Query("DELETE FROM price_quotes")
    suspend fun deleteAll()

    @Query("DELETE FROM price_quotes WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
