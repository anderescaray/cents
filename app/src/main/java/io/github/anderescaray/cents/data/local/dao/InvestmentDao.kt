package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.anderescaray.cents.data.local.entity.HoldingEntity
import io.github.anderescaray.cents.data.local.entity.HoldingValueEntity
import io.github.anderescaray.cents.data.local.entity.TradeEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Assets, their trades and their typed values.
 *
 * There is no query here that computes what an asset is worth or cost. A sale removes cost in
 * proportion to what the asset was worth just before it, which is a fold over everything in order
 * (`positionOf`); what SQLite does here is what it is good at: return the rows, as flows.
 */
@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings WHERE id = :id")
    fun observeById(id: Long): Flow<HoldingEntity?>

    @Query("SELECT * FROM holdings WHERE id = :id")
    suspend fun findById(id: Long): HoldingEntity?

    @Query("SELECT * FROM holdings ORDER BY id")
    suspend fun all(): List<HoldingEntity>

    @Query("SELECT COUNT(*) FROM trades WHERE holdingId = :id")
    suspend fun countTrades(id: Long): Int

    @Insert
    suspend fun insert(holding: HoldingEntity): Long

    @Update
    suspend fun update(holding: HoldingEntity)

    /**
     * Deletes the asset with its values and trades, in one database transaction.
     *
     * The foreign keys are `RESTRICT`, so this is the only way an asset with history goes: what
     * points at it is removed first, deliberately, and the deletions succeed or fail together. The
     * cash its trades wrote has to go before this, because it points at the trades.
     */
    @Transaction
    suspend fun deleteWithHistory(id: Long) {
        deleteValuesOf(id)
        deleteTradesOf(id)
        deleteById(id)
    }

    @Query("DELETE FROM holding_values WHERE holdingId = :id")
    suspend fun deleteValuesOf(id: Long)

    @Query("DELETE FROM trades WHERE holdingId = :id")
    suspend fun deleteTradesOf(id: Long)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Every asset. Only a restore does this, and only after what points at them. */
    @Query("DELETE FROM holdings")
    suspend fun deleteAll()
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY date, id")
    fun observeAll(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE holdingId = :holdingId ORDER BY date, id")
    suspend fun forHolding(holdingId: Long): List<TradeEntity>

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
interface HoldingValueDao {
    @Query("SELECT * FROM holding_values ORDER BY date, id")
    fun observeAll(): Flow<List<HoldingValueEntity>>

    @Query("SELECT * FROM holding_values WHERE holdingId = :holdingId ORDER BY date, id")
    suspend fun forHolding(holdingId: Long): List<HoldingValueEntity>

    @Query("SELECT * FROM holding_values ORDER BY holdingId, date")
    suspend fun all(): List<HoldingValueEntity>

    @Query("SELECT * FROM holding_values WHERE id = :id")
    suspend fun findById(id: Long): HoldingValueEntity?

    @Query("SELECT * FROM holding_values WHERE holdingId = :holdingId AND date = :date")
    suspend fun findOn(
        holdingId: Long,
        date: LocalDate,
    ): HoldingValueEntity?

    @Insert
    suspend fun insert(value: HoldingValueEntity): Long

    @Update
    suspend fun update(value: HoldingValueEntity)

    /**
     * The value for its asset and day: a second one the same day replaces the first, keeping its
     * id, which is what the unique index asks and what the owner means by typing it again.
     */
    @Transaction
    suspend fun save(value: HoldingValueEntity) {
        val existing = findOn(value.holdingId, value.date)
        if (existing == null) insert(value) else update(value.copy(id = existing.id, createdAt = existing.createdAt))
    }

    @Query("DELETE FROM holding_values WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Every value. Nothing points at these, so they can go whenever. */
    @Query("DELETE FROM holding_values")
    suspend fun deleteAll()
}
