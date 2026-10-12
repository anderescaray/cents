package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.anderescaray.cents.data.local.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurringRuleDao {
    /** Active first, then the ones that have been stopped: the order a list reads in. */
    @Query("SELECT * FROM recurring_rules ORDER BY active DESC, name")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE active = 1 ORDER BY name")
    fun observeActive(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    fun observeById(id: Long): Flow<RecurringRuleEntity?>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun findById(id: Long): RecurringRuleEntity?

    /** The ones a catch-up has to consider: stopped rules produce nothing. */
    @Query("SELECT * FROM recurring_rules WHERE active = 1")
    suspend fun activeRules(): List<RecurringRuleEntity>

    @Insert
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    /**
     * How many movements this rule has produced.
     *
     * Zero is what makes a real delete legitimate. With anything above zero the honest operation
     * is deactivating it, because deleting would cut history loose from the thing that explains
     * it — the same bargain a category strikes (PRD §4.4, rule 8).
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE ruleId = :id")
    suspend fun countGenerated(id: Long): Int

    /** Stops it generating without touching anything it has already made. */
    @Query("UPDATE recurring_rules SET active = :active WHERE id = :id")
    suspend fun setActive(
        id: Long,
        active: Boolean,
    )

    /**
     * Records how far a rule has got.
     *
     * Written in the same database transaction as the movements it describes, so a catch-up that
     * dies halfway cannot leave a rule claiming to have produced something it did not — which
     * would skip that period for ever. Null only when Undo puts back a rule that had written
     * nothing (D35).
     */
    @Query("UPDATE recurring_rules SET lastGenerated = :date WHERE id = :id")
    suspend fun setLastGenerated(
        id: Long,
        date: LocalDate?,
    )

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()
}
