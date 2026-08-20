package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC, id DESC")
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE date BETWEEN :from AND :to
        ORDER BY date DESC, id DESC
        """,
    )
    fun observeBetween(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY date DESC, id DESC")
    fun observeByCategory(categoryId: Long): Flow<List<TransactionEntity>>

    /** The inbox: everything captured but not yet classified. */
    @Query("SELECT * FROM transactions WHERE categoryId IS NULL ORDER BY date DESC, id DESC")
    fun observeUncategorized(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * The list the transactions screen shows: every movement with its account and category
     * names resolved by the database, so the UI never issues a query per row.
     */
    @Query(
        """
        SELECT t.id AS id, t.accountId AS accountId, t.categoryId AS categoryId,
               t.kind AS kind, t.amountMinor AS amountMinor, t.currency AS currency,
               t.date AS date, t.note AS note,
               a.name AS accountName,
               c.name AS categoryName,
               c.colorArgb AS categoryColorArgb
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        ORDER BY t.date DESC, t.id DESC
        """,
    )
    fun observeAllWithNames(): Flow<List<TransactionWithNames>>

    /** The same rows as [observeAllWithNames], for one account. */
    @Query(
        """
        SELECT t.id AS id, t.accountId AS accountId, t.categoryId AS categoryId,
               t.kind AS kind, t.amountMinor AS amountMinor, t.currency AS currency,
               t.date AS date, t.note AS note,
               a.name AS accountName,
               c.name AS categoryName,
               c.colorArgb AS categoryColorArgb
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.accountId = :accountId
        ORDER BY t.date DESC, t.id DESC
        """,
    )
    fun observeByAccountWithNames(accountId: Long): Flow<List<TransactionWithNames>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amountMinor ELSE 0 END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amountMinor ELSE 0 END), 0) AS expenseMinor
        FROM transactions
        WHERE date BETWEEN :from AND :to
        """,
    )
    fun observeTotalsBetween(
        from: LocalDate,
        to: LocalDate,
    ): Flow<PeriodTotals>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amountMinor ELSE 0 END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amountMinor ELSE 0 END), 0) AS expenseMinor
        FROM transactions
        WHERE accountId = :accountId AND date BETWEEN :from AND :to
        """,
    )
    fun observeAccountTotalsBetween(
        accountId: Long,
        from: LocalDate,
        to: LocalDate,
    ): Flow<PeriodTotals>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /**
     * Moves every movement out of one account and into another, so the first one can then
     * be deleted without taking its history with it.
     *
     * The caller is responsible for the two accounts sharing a currency. A movement carries
     * its own [TransactionEntity.currency], denormalized from the account it was recorded
     * in, and the balance query sums the amounts and labels the result with the *account's*
     * currency — so moving euros into a dollar account would not convert anything, it would
     * quietly make that account's balance a sum of two different currencies.
     */
    @Query("UPDATE transactions SET accountId = :toId, updatedAt = :updatedAt WHERE accountId = :fromId")
    suspend fun reassignAccount(
        fromId: Long,
        toId: Long,
        updatedAt: Instant,
    ): Int

    /** Every movement in one account, for the deletion that is meant to take them along. */
    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Long): Int
}

/** A transaction joined with the names a list row needs, resolved in SQL. */
data class TransactionWithNames(
    val id: Long,
    val accountId: Long,
    val categoryId: Long?,
    val kind: io.github.anderescaray.cents.domain.model.TransactionKind,
    val amountMinor: Long,
    val currency: String,
    val date: java.time.LocalDate,
    val note: String?,
    val accountName: String,
    val categoryName: String?,
    val categoryColorArgb: Int?,
)

/** Income and expense totals for a period, summed in SQL. */
data class PeriodTotals(
    val incomeMinor: Long,
    val expenseMinor: Long,
)
