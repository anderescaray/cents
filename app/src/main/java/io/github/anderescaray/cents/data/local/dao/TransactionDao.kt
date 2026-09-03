package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.TransactionKind
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
     * The list, narrowed by whatever the owner asked for.
     *
     * Every parameter is optional, and each clause is written so that an unset one costs
     * nothing: `:x IS NULL OR column = :x`. The narrowing happens in SQLite, against the
     * indices on `accountId`, `categoryId` and `date`, rather than by pulling every movement
     * into Kotlin and walking it — the same reason chart aggregation belongs in `GROUP BY`.
     *
     * Two parameters describe the category, because one cannot. `categoryId IS NULL` already
     * means "never classified", so [uncategorized] carries the question that a null id would
     * otherwise swallow.
     *
     * [query] arrives already wrapped in `%` and with LIKE's own wildcards escaped, so a
     * search for "50%" looks for that text and not for "50 followed by anything".
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
        WHERE (:accountId IS NULL OR t.accountId = :accountId)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:uncategorized = 0 OR t.categoryId IS NULL)
          AND (:kind IS NULL OR t.kind = :kind)
          AND (:from IS NULL OR t.date >= :from)
          AND (:to IS NULL OR t.date <= :to)
          AND (:minAmountMinor IS NULL OR t.amountMinor >= :minAmountMinor)
          AND (:maxAmountMinor IS NULL OR t.amountMinor <= :maxAmountMinor)
          AND (
            :query IS NULL
            OR t.note LIKE :query ESCAPE '\'
            OR c.name LIKE :query ESCAPE '\'
            OR a.name LIKE :query ESCAPE '\'
          )
        ORDER BY t.date DESC, t.id DESC
        """,
    )
    @Suppress("LongParameterList")
    fun observeFilteredWithNames(
        accountId: Long?,
        categoryId: Long?,
        uncategorized: Boolean,
        kind: TransactionKind?,
        from: LocalDate?,
        to: LocalDate?,
        minAmountMinor: Long?,
        maxAmountMinor: Long?,
        query: String?,
    ): Flow<List<TransactionWithNames>>

    /** The same rows as [observeFilteredWithNames], for one account. */
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

    /**
     * What each category accounts for over a period, summed in SQL.
     *
     * This is the donut. A chart summarises every row rather than showing them, so pulling
     * the rows into Kotlin to add them up is the one shape that gets slower the longer the
     * app is used (`CLAUDE.md` §5): the work belongs in `GROUP BY`, against the index on
     * `date`.
     *
     * The join is a LEFT join and the grouping is on `t.categoryId`, so **uncategorized
     * movements survive as their own group** with a null id and a null name. They are money
     * that was spent, and dropping them would make the slices add up to less than the total
     * with nothing on screen saying why. Naming that group is the UI's job.
     *
     * A null [accountId] means every account, which is the same spelling `TransactionFilter`
     * uses for it. The predicate is written that way — `:accountId IS NULL OR ...` — rather
     * than as two queries, because the alternative is two `@Query`s per report that have to be
     * kept saying the same thing.
     *
     * **That form is not sargable**, and it is worth being honest about the cost: an `OR` over
     * a bound parameter stops SQLite using `index_transactions_accountId_date`, so all four
     * report queries fall back to the index on `date` and test the account row by row. It is
     * bounded by the length of the period rather than by anything the owner does, and no
     * device walk has ever noticed it. If one does, the fix is a second `@Query` per report,
     * not a new index — recorded in `PLAN.md` C4 rather than half-done here.
     */
    @Query(
        """
        SELECT t.categoryId AS categoryId,
               c.name AS categoryName,
               c.colorArgb AS categoryColorArgb,
               SUM(t.amountMinor) AS totalMinor,
               COUNT(*) AS transactionCount
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.kind = :kind
          AND t.date BETWEEN :from AND :to
          AND (:accountId IS NULL OR t.accountId = :accountId)
        GROUP BY t.categoryId
        ORDER BY totalMinor DESC, categoryName ASC
        """,
    )
    fun observeTotalsByCategory(
        kind: TransactionKind,
        from: LocalDate,
        to: LocalDate,
        accountId: Long?,
    ): Flow<List<CategoryTotal>>

    /**
     * Income and expenses per calendar month, summed in SQL.
     *
     * `date` is stored as an **epoch day** (see `Converters`), which is why the month key
     * is `date * 86400` read back as a Unix timestamp: seconds are what `strftime`
     * understands, and epoch days are UTC-anchored so the multiplication is exact rather
     * than a timezone guess.
     *
     * Months with no movements do not come back at all — SQL groups what exists. Filling
     * those gaps with zeros is done in the repository, where it costs one pass over the
     * months in the range rather than one over the rows.
     */
    @Query(
        """
        SELECT strftime('%Y-%m', t.date * 86400, 'unixepoch') AS month,
               COALESCE(SUM(CASE WHEN t.kind = 'INCOME' THEN t.amountMinor ELSE 0 END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN t.kind = 'EXPENSE' THEN t.amountMinor ELSE 0 END), 0) AS expenseMinor
        FROM transactions t
        WHERE t.date BETWEEN :from AND :to
          AND (:accountId IS NULL OR t.accountId = :accountId)
        GROUP BY month
        ORDER BY month
        """,
    )
    fun observeMonthlyTotals(
        from: LocalDate,
        to: LocalDate,
        accountId: Long?,
    ): Flow<List<MonthTotals>>

    /**
     * The money there was before a period started: the balance the line begins from.
     *
     * It is the same arithmetic as `AccountDao.observeBalances`, summed across every account
     * instead of grouped by one — opening balances plus every movement, income adding and
     * expense subtracting — but cut off at [from]. Starting the cumulative line anywhere else
     * would make it disagree with the number the Accounts tab shows for the same money.
     *
     * **Archived accounts count.** Archiving means "stop offering it", not "the money left",
     * and the two charts beside this one already count an archived account's movements. The
     * one legitimate difference from the Accounts tab total, which sums only active accounts,
     * is an archived account that still holds a balance.
     *
     * With an [accountId] given, **both halves narrow together**. Only that account's opening
     * balance is carried in, and only its movements are counted before [from] — narrowing one
     * and not the other is the mistake this query exists to make impossible, because it would
     * draw a line starting from everyone's money and moving with one account's.
     *
     * Two scalar sub-selects rather than a join: an account with no movements must still
     * contribute its opening balance, and a `LEFT JOIN` filtered on the transaction's date
     * gets that wrong in a way that is easy to write and hard to see.
     */
    @Query(
        """
        SELECT (
                   SELECT COALESCE(SUM(a.openingBalanceMinor), 0)
                   FROM accounts a
                   WHERE :accountId IS NULL OR a.id = :accountId
               ) + (
                   SELECT COALESCE(
                       SUM(
                           CASE t.kind
                               WHEN 'INCOME' THEN t.amountMinor
                               WHEN 'EXPENSE' THEN -t.amountMinor
                               ELSE 0
                           END
                       ),
                       0
                   )
                   FROM transactions t
                   WHERE t.date < :from
                     AND (:accountId IS NULL OR t.accountId = :accountId)
               )
        """,
    )
    fun observeBalanceBefore(
        from: LocalDate,
        accountId: Long?,
    ): Flow<Long>

    /**
     * What each day of a period did to the balance, summed in SQL.
     *
     * This is the balance line. Only days that have movements come back — a running total
     * changes on the days something happened, and the flat stretches between them are filled
     * in the repository, where the cost is the length of the period rather than the number of
     * movements ever recorded (`CLAUDE.md` §5).
     *
     * [DayNet.netMinor] is signed, because a cumulative balance adds and subtracts rather
     * than carrying two columns; [DayNet.movementCount] is what tells an empty period apart
     * from a period whose income and expenses happened to cancel out exactly.
     */
    @Query(
        """
        SELECT t.date AS date,
               COALESCE(
                   SUM(
                       CASE t.kind
                           WHEN 'INCOME' THEN t.amountMinor
                           WHEN 'EXPENSE' THEN -t.amountMinor
                           ELSE 0
                       END
                   ),
                   0
               ) AS netMinor,
               COUNT(*) AS movementCount
        FROM transactions t
        WHERE t.date BETWEEN :from AND :to
          AND (:accountId IS NULL OR t.accountId = :accountId)
        GROUP BY t.date
        ORDER BY t.date
        """,
    )
    fun observeDailyNet(
        from: LocalDate,
        to: LocalDate,
        accountId: Long?,
    ): Flow<List<DayNet>>

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

/**
 * One category's share of a period, as SQL returns it. A null [categoryId] is the
 * uncategorized group rather than a missing row.
 */
data class CategoryTotal(
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColorArgb: Int?,
    val totalMinor: Long,
    val transactionCount: Int,
)

/** One calendar month's totals. [month] is `YYYY-MM`, as produced by `strftime`. */
data class MonthTotals(
    val month: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

/**
 * One day's effect on the balance: [netMinor] is signed, income positive and expense
 * negative. [movementCount] separates "nothing happened" from "it cancelled out".
 */
data class DayNet(
    val date: LocalDate,
    val netMinor: Long,
    val movementCount: Int,
)
