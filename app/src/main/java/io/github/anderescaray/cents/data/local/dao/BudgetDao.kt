package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.anderescaray.cents.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id")
    fun observeAll(): Flow<List<BudgetEntity>>

    /** For a backup, which reads everything at one moment rather than watching it. */
    @Query("SELECT * FROM budgets ORDER BY id")
    suspend fun all(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun findById(id: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun findByCategory(categoryId: Long): BudgetEntity?

    /** The general budget, over every expense of the month, if there is one. */
    @Query("SELECT * FROM budgets WHERE categoryId IS NULL ORDER BY id LIMIT 1")
    suspend fun findGeneral(): BudgetEntity?

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    /**
     * Sets the general budget: replaces the limit of the one there is, or creates it. The unique
     * index cannot keep a null to one row, so the look and the write are one transaction, and two
     * saves cannot both find none and both insert.
     */
    @Transaction
    suspend fun saveGeneral(budget: BudgetEntity): Long {
        val existing = findGeneral() ?: return insert(budget.copy(id = 0, categoryId = null))
        update(budget.copy(id = existing.id, categoryId = null, createdAt = existing.createdAt))
        return existing.id
    }

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Only for a restore, which replaces everything (`DECISIONS.md` D21). */
    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
