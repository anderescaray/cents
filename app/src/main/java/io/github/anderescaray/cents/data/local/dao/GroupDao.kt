package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.anderescaray.cents.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface GroupDao {
    @Query("SELECT * FROM movement_groups ORDER BY id")
    fun observeAll(): Flow<List<GroupEntity>>

    /** For a backup, which reads everything at one moment rather than watching it. */
    @Query("SELECT * FROM movement_groups ORDER BY id")
    suspend fun all(): List<GroupEntity>

    @Query("SELECT * FROM movement_groups WHERE id = :id")
    suspend fun findById(id: Long): GroupEntity?

    /**
     * Every group with what came in and went out under it, summed in SQL.
     *
     * A `LEFT JOIN`, so a group nothing has been added to yet is still listed, at zero. The
     * exclusions are the ones every report makes — a transfer leg or a trade's cash is neither
     * earned nor spent — written into the join rather than the `WHERE`, which would otherwise
     * turn the left join back into an inner one and drop every group holding only those.
     * The form never offers a group to either, so this is the belt to that pair of braces.
     */
    @Query(
        """
        SELECT g.id AS id, g.name AS name, g.startDate AS startDate, g.endDate AS endDate,
               g.createdAt AS createdAt,
               COALESCE(SUM(CASE WHEN t.kind = 'INCOME' THEN t.amountMinor ELSE 0 END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN t.kind = 'EXPENSE' THEN t.amountMinor ELSE 0 END), 0) AS expenseMinor,
               COUNT(t.id) AS movementCount,
               MIN(t.date) AS firstDate,
               MAX(t.date) AS lastDate
        FROM movement_groups g
        LEFT JOIN transactions t
          ON t.groupId = g.id AND t.transferId IS NULL AND t.tradeId IS NULL
        GROUP BY g.id
        """,
    )
    fun observeTotals(): Flow<List<GroupTotals>>

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    /** The movements stay, with no group: `transactions.groupId` is `SET NULL`. */
    @Query("DELETE FROM movement_groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Only for a restore, which replaces everything (`DECISIONS.md` D21). */
    @Query("DELETE FROM movement_groups")
    suspend fun deleteAll()
}

/** One group and its movements' totals, as SQL returns them. */
data class GroupTotals(
    val id: Long,
    val name: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val createdAt: Instant,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val movementCount: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
)
