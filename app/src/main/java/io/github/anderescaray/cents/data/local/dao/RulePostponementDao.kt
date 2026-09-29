package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.anderescaray.cents.data.local.entity.RulePostponementEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RulePostponementDao {
    @Query("SELECT * FROM rule_postponements")
    suspend fun all(): List<RulePostponementEntity>

    /** One rule's postponements, to check against the rule once it is edited. */
    @Query("SELECT * FROM rule_postponements WHERE ruleId = :ruleId")
    suspend fun forRule(ruleId: Long): List<RulePostponementEntity>

    /** Every postponement, for the screens that have to hide or re-date what is waiting. */
    @Query("SELECT * FROM rule_postponements")
    fun observeAll(): Flow<List<RulePostponementEntity>>

    /** Postponing the same occurrence again moves it again, rather than adding a second day. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(postponement: RulePostponementEntity)

    @Query("DELETE FROM rule_postponements WHERE ruleId = :ruleId AND occurrence = :occurrence")
    suspend fun delete(
        ruleId: Long,
        occurrence: LocalDate,
    )

    /** Once an occurrence is written or skipped, its postponement has nothing left to say. */
    @Query("DELETE FROM rule_postponements WHERE ruleId = :ruleId AND occurrence <= :through")
    suspend fun deleteThrough(
        ruleId: Long,
        through: LocalDate,
    )

    /** Only for a restore, which replaces everything (`DECISIONS.md` D21). */
    @Query("DELETE FROM rule_postponements")
    suspend fun deleteAll()
}
