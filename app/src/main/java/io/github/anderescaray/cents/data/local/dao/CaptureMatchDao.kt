package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.anderescaray.cents.data.local.entity.CaptureMatchEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.LocalDate

/** What capture needs to recognise a subscription's charge (`DECISIONS.md` D35), and to undo it. */
@Dao
interface CaptureMatchDao {
    @Insert
    suspend fun insert(match: CaptureMatchEntity): Long

    @Query("SELECT * FROM capture_matches WHERE id = :id")
    suspend fun findById(id: Long): CaptureMatchEntity?

    @Query("DELETE FROM capture_matches WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * The movements rules wrote into [accountId] between two days that no capture has been matched
     * to yet: the charges a captured payment may already be.
     */
    @Query(
        "SELECT * FROM transactions WHERE ruleId IS NOT NULL AND accountId = :accountId " +
            "AND kind = :kind AND date BETWEEN :from AND :to AND id NOT IN " +
            "(SELECT chargeTransactionId FROM capture_matches WHERE chargeTransactionId IS NOT NULL)",
    )
    suspend fun unmatchedCharges(
        accountId: Long,
        kind: TransactionKind,
        from: LocalDate,
        to: LocalDate,
    ): List<TransactionEntity>

    @Query("SELECT * FROM capture_matches")
    suspend fun all(): List<CaptureMatchEntity>

    /** Only for a restore, which replaces everything (`DECISIONS.md` D21). */
    @Query("DELETE FROM capture_matches")
    suspend fun deleteAll()
}
