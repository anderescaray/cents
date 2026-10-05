package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.anderescaray.cents.data.local.entity.BalanceAdjustmentEntity
import kotlinx.coroutines.flow.Flow

/** The corrections behind an account's balance. Nothing here is an income or an expense. */
@Dao
interface BalanceAdjustmentDao {
    /** Newest first, the order the account's movements are listed in. */
    @Query("SELECT * FROM balance_adjustments WHERE accountId = :accountId ORDER BY date DESC, id DESC")
    fun observeForAccount(accountId: Long): Flow<List<BalanceAdjustmentEntity>>

    @Query("SELECT * FROM balance_adjustments ORDER BY id")
    suspend fun all(): List<BalanceAdjustmentEntity>

    @Query("SELECT * FROM balance_adjustments WHERE id = :id")
    suspend fun findById(id: Long): BalanceAdjustmentEntity?

    @Insert
    suspend fun insert(adjustment: BalanceAdjustmentEntity): Long

    @Query("DELETE FROM balance_adjustments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM balance_adjustments WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Long)

    /** The account's corrections follow its movements when they are moved to another account. */
    @Query("UPDATE balance_adjustments SET accountId = :toId WHERE accountId = :fromId")
    suspend fun reassignAccount(
        fromId: Long,
        toId: Long,
    )

    /** Every correction. Only a restore does this, before the accounts they point at. */
    @Query("DELETE FROM balance_adjustments")
    suspend fun deleteAll()
}
