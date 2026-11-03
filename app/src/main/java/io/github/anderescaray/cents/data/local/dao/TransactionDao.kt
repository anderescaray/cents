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

/**
 * The ledger.
 *
 * **One rule runs through every query here: an income-and-expense report counts only rows that
 * are money earned or money spent, and a balance counts every row.** Two kinds of movement are
 * neither earned nor spent, and both are real:
 *
 * - a **transfer leg** (`transferId`), because moving 500 € from checking to savings is not
 *   spending 500 € (PRD §3) — and the same money arriving on the other side is not income;
 * - the **cash behind a trade** (`tradeId`), because buying 400 € of shares is the same money in
 *   a different shape. Counting either as an expense inflates every statistic in the app.
 *
 * A balance is the opposite case: the money really did leave the account, so a balance that
 * skipped these rows would disagree with the bank — and for an investments account it is exactly
 * what makes the balance read as the cash left in it.
 *
 * Which is which, so a new query has somewhere to look:
 *
 * - **Excluded** — [observeTotalsBetween], [observeAccountTotalsBetween],
 *   [observeTotalsByCategory], [observeMonthlyTotals], [observeTotalsByWeekday],
 *   [observeCategoriesWithMovements], [observeCategoryDailyTotals]. The last two would
 *   exclude them anyway, since neither kind carries a category and they join on one; the
 *   clause is written out regardless, so the rule is "every report query has it" rather
 *   than "every report query except the two you have to reason about".
 * - **Included** — [observeBalanceBefore], [observeDailyNet] and `AccountDao`'s balances.
 * - **Shown** — the list queries. Both kinds are things that happened and have to be visible,
 *   findable and openable; it is the *totals* they must stay out of. Of a trade's cash, only a
 *   sale's result is listed: a purchase, and the cost a sale returns, move money between the
 *   account's cash and its assets, and are read on the investment screens (`DECISIONS.md` D40).
 */
@Dao
@Suppress("TooManyFunctions")
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

    /** Every movement. Only a restore does this, and it goes first: everything points here. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /**
     * Both legs of one transfer, oldest row first — which is the outgoing one, because it is
     * written first and lends the pair its id.
     *
     * The caller does not rely on that order: it picks the legs apart by their [kind], since
     * an ordering that happens to be right is the kind of thing a later `ORDER BY` quietly
     * breaks. Returning both rather than a shaped object is deliberate — a transfer *is* two
     * movements, and the domain shape is assembled one layer up.
     */
    @Query("SELECT * FROM transactions WHERE transferId = :transferId ORDER BY id")
    suspend fun findTransferLegs(transferId: Long): List<TransactionEntity>

    /**
     * Stamps the pair id onto the leg that generated it.
     *
     * The outgoing leg is inserted before its id exists, so it is written twice: once to get
     * a row id, once to carry it. Both writes are inside the same database transaction as the
     * incoming leg, so a half-formed transfer is never visible to anything.
     */
    @Query("UPDATE transactions SET transferId = :transferId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTransferId(
        id: Long,
        transferId: Long,
        updatedAt: Instant,
    )

    /** Both legs at once: half a transfer is money that arrived from nowhere. */
    @Query("DELETE FROM transactions WHERE transferId = :transferId")
    suspend fun deleteTransfer(transferId: Long): Int

    /**
     * The cash a trade wrote: a purchase's one row, or a sale's two, the cost it returned and its
     * result ([TransactionEntity.tradeResult]).
     */
    @Query("SELECT * FROM transactions WHERE tradeId = :tradeId ORDER BY id")
    suspend fun tradeCash(tradeId: Long): List<TransactionEntity>

    /** The cash of every trade against one asset, so it can be written again in one go. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE tradeId IN (SELECT id FROM trades WHERE holdingId = :holdingId)
        ORDER BY id
        """,
    )
    suspend fun cashForHolding(holdingId: Long): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    /** Removes it, for a trade that is being deleted. */
    @Query("DELETE FROM transactions WHERE tradeId = :tradeId")
    suspend fun deleteTradeCash(tradeId: Long): Int

    /**
     * What each day did to the cash of each account investments live in — every broker, and any
     * other account holding an asset — signed, corrections included: the cash half of the
     * investment charts. The other half is the assets' own values.
     */
    @Query(
        """
        SELECT m.accountId AS accountId, m.date AS date, SUM(m.signedMinor) AS netMinor
        FROM (
            SELECT t.accountId AS accountId, t.date AS date,
                   CASE t.kind WHEN 'INCOME' THEN t.amountMinor WHEN 'EXPENSE' THEN -t.amountMinor ELSE 0 END
                       AS signedMinor
            FROM transactions t
            INNER JOIN accounts a ON a.id = t.accountId
            WHERE a.type = 'BROKER' OR a.id IN (SELECT accountId FROM holdings)
            UNION ALL
            SELECT b.accountId AS accountId, b.date AS date, b.amountMinor AS signedMinor
            FROM balance_adjustments b
            INNER JOIN accounts a ON a.id = b.accountId
            WHERE a.type = 'BROKER' OR a.id IN (SELECT accountId FROM holdings)
        ) m
        GROUP BY m.accountId, m.date
        ORDER BY m.date
        """,
    )
    fun observeInvestmentCashByDay(): Flow<List<AccountDayNet>>

    /**
     * The cash of every trade against one holding, for a holding being deleted whole.
     *
     * `transactions.tradeId` is `RESTRICT`, so these rows have to go before the trades they
     * point at — and they are movements in an account, so leaving them behind would leave that
     * account's balance short by money whose reason no longer exists.
     */
    @Query(
        """
        DELETE FROM transactions
        WHERE tradeId IN (SELECT id FROM trades WHERE holdingId = :holdingId)
        """,
    )
    suspend fun deleteCashForHolding(holdingId: Long): Int

    /** Every transfer this account is one side of, for a deletion that has to take them whole. */
    @Query("SELECT DISTINCT transferId FROM transactions WHERE accountId = :accountId AND transferId IS NOT NULL")
    suspend fun transferIdsInAccount(accountId: Long): List<Long>

    @Query("DELETE FROM transactions WHERE transferId IN (:transferIds)")
    suspend fun deleteTransfers(transferIds: List<Long>): Int

    /**
     * Legs living in *other* accounts that would go with this account's deletion.
     *
     * Deleting an account and its movements takes whole transfers with it, because a leg
     * whose counterpart is gone is money that arrived from nowhere. Those far legs are rows
     * in accounts the owner is not deleting, and their balances change — so the confirmation
     * has to count them rather than name only what lives in the account being removed.
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE accountId <> :accountId
          AND transferId IN (
              SELECT transferId FROM transactions
              WHERE accountId = :accountId AND transferId IS NOT NULL
          )
        """,
    )
    suspend fun countTransferLegsOutside(accountId: Long): Int

    /**
     * Transfers between these two accounts. Moving one account's movements into the other
     * leaves each of them with both legs in one account, so the sheet that offers the move
     * names them before it runs (`docs/known-limitations.md`).
     */
    @Query(
        """
        SELECT COUNT(DISTINCT here.transferId) FROM transactions AS here
        JOIN transactions AS there ON there.transferId = here.transferId AND there.id <> here.id
        WHERE here.accountId = :accountId AND there.accountId = :otherId
        """,
    )
    suspend fun countTransfersBetween(
        accountId: Long,
        otherId: Long,
    ): Int

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
     * **[uncategorized] is the one narrowing here that also excludes transfer legs and trade
     * cash**, which is not an exception to the rule at the top of this file but the same rule
     * reaching a filter. Neither carries a category because neither is classified, not because
     * nobody has got round to classifying them — and this filter is what the ring's
     * *Uncategorized* slice drills into. That slice counted neither, so a list that showed them
     * would not be the slice's movements: it would be the slice plus every transfer and every
     * purchase in the period, each one typically far larger than anything in it.
     *
     * [query], when given, is a `LIKE` pattern already wrapped in `%` with LIKE's own wildcards
     * escaped. **The list no longer passes one**: `LIKE` folds case for A–Z only, so the
     * repository matches the text itself, accents and all (see `searchKey`), and asks for null.
     *
     * **[kind] leaves transfer legs and trade cash out too**, for the same reason: *Income* is
     * a question about money earned, and the arriving half of a transfer from savings was not
     * earned — nor was the departing half spent. Every report already leaves them out, and a
     * slice or bar that drills in by kind has to land on the same movements it counted. With no
     * kind asked, they are listed as always: they happened, and the balances count them.
     *
     * [weekday] is an ISO day number, Monday 1 to Sunday 7 — `DayOfWeek.getValue()`. `date` is
     * an epoch day, so `date * 86400` is the same UTC-anchored Unix timestamp the monthly
     * query uses; `strftime('%w', …)` counts from Sunday 0, and `((… + 6) % 7) + 1` rotates
     * that onto the ISO numbering so the parameter needs no conversion on the way in.
     */
    @Query(
        """
        SELECT t.id AS id, t.accountId AS accountId, t.categoryId AS categoryId,
               t.kind AS kind, t.amountMinor AS amountMinor, t.currency AS currency,
               t.date AS date, t.note AS note,
               t.transferId AS transferId, t.tradeId AS tradeId, t.tradeResult AS tradeResult,
               t.groupId AS groupId, g.name AS groupName,
               a.name AS accountName,
               c.name AS categoryName,
               c.colorArgb AS categoryColorArgb,
               c.iconKey AS categoryEmoji,
               CASE
                   WHEN t.transferId IS NULL THEN NULL
                   ELSE (
                       SELECT a2.name
                       FROM transactions t2
                       INNER JOIN accounts a2 ON a2.id = t2.accountId
                       WHERE t2.transferId = t.transferId AND t2.id <> t.id
                       LIMIT 1
                   )
               END AS counterpartAccountName,
               CASE
                   WHEN t.tradeId IS NULL THEN NULL
                   ELSE (
                       SELECT h.name
                       FROM trades tr
                       INNER JOIN holdings h ON h.id = tr.holdingId
                       WHERE tr.id = t.tradeId
                   )
               END AS tradeAssetName,
               CASE
                   WHEN t.tradeId IS NULL THEN NULL
                   ELSE (SELECT tr.holdingId FROM trades tr WHERE tr.id = t.tradeId)
               END AS tradeHoldingId
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN movement_groups g ON g.id = t.groupId
        WHERE (:accountId IS NULL OR t.accountId = :accountId)
          AND (t.tradeId IS NULL OR t.tradeResult = 1)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (
            :uncategorized = 0
            OR (t.categoryId IS NULL AND t.transferId IS NULL AND t.tradeId IS NULL)
          )
          AND (:kind IS NULL OR (t.kind = :kind AND t.transferId IS NULL AND t.tradeId IS NULL))
          AND (:groupId IS NULL OR t.groupId = :groupId)
          AND (:from IS NULL OR t.date >= :from)
          AND (:to IS NULL OR t.date <= :to)
          AND (
            :weekday IS NULL
            OR ((CAST(strftime('%w', t.date * 86400, 'unixepoch') AS INTEGER) + 6) % 7) + 1 = :weekday
          )
          AND (:minAmountMinor IS NULL OR t.amountMinor >= :minAmountMinor)
          AND (:maxAmountMinor IS NULL OR t.amountMinor <= :maxAmountMinor)
          AND (
            :query IS NULL
            OR t.note LIKE :query ESCAPE '\'
            OR c.name LIKE :query ESCAPE '\'
            OR a.name LIKE :query ESCAPE '\'
            OR g.name LIKE :query ESCAPE '\'
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
        groupId: Long?,
        from: LocalDate?,
        to: LocalDate?,
        weekday: Int?,
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
               t.transferId AS transferId, t.tradeId AS tradeId, t.tradeResult AS tradeResult,
               t.groupId AS groupId, g.name AS groupName,
               a.name AS accountName,
               c.name AS categoryName,
               c.colorArgb AS categoryColorArgb,
               c.iconKey AS categoryEmoji,
               CASE
                   WHEN t.transferId IS NULL THEN NULL
                   ELSE (
                       SELECT a2.name
                       FROM transactions t2
                       INNER JOIN accounts a2 ON a2.id = t2.accountId
                       WHERE t2.transferId = t.transferId AND t2.id <> t.id
                       LIMIT 1
                   )
               END AS counterpartAccountName,
               CASE
                   WHEN t.tradeId IS NULL THEN NULL
                   ELSE (
                       SELECT h.name
                       FROM trades tr
                       INNER JOIN holdings h ON h.id = tr.holdingId
                       WHERE tr.id = t.tradeId
                   )
               END AS tradeAssetName,
               CASE
                   WHEN t.tradeId IS NULL THEN NULL
                   ELSE (SELECT tr.holdingId FROM trades tr WHERE tr.id = t.tradeId)
               END AS tradeHoldingId
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN movement_groups g ON g.id = t.groupId
        WHERE t.accountId = :accountId
          AND (t.tradeId IS NULL OR t.tradeResult = 1)
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
          AND transferId IS NULL
          AND tradeId IS NULL
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
          AND transferId IS NULL
          AND tradeId IS NULL
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
     * not a new index — recorded in `docs/known-limitations.md` rather than half-done here.
     */
    @Query(
        """
        SELECT t.categoryId AS categoryId,
               c.name AS categoryName,
               c.colorArgb AS categoryColorArgb,
               c.iconKey AS categoryEmoji,
               SUM(t.amountMinor) AS totalMinor,
               COUNT(*) AS transactionCount
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.kind = :kind
          AND t.date BETWEEN :from AND :to
          AND (:accountId IS NULL OR t.accountId = :accountId)
          AND t.transferId IS NULL
          AND t.tradeId IS NULL
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
     * What one group's movements of one [kind] came to, per category — the ring on a group's
     * screen (PRD §4.9).
     *
     * [observeTotalsByCategory] with a group where the period was: no dates, because a group is
     * its own span, and the same exclusions and the same surviving uncategorized slice.
     */
    @Query(
        """
        SELECT t.categoryId AS categoryId,
               c.name AS categoryName,
               c.colorArgb AS categoryColorArgb,
               c.iconKey AS categoryEmoji,
               SUM(t.amountMinor) AS totalMinor,
               COUNT(*) AS transactionCount
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.kind = :kind
          AND t.groupId = :groupId
          AND t.transferId IS NULL
          AND t.tradeId IS NULL
        GROUP BY t.categoryId
        ORDER BY totalMinor DESC, categoryName ASC
        """,
    )
    fun observeGroupTotalsByCategory(
        groupId: Long,
        kind: TransactionKind,
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
          AND t.transferId IS NULL
          AND t.tradeId IS NULL
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
     * What was spent on each day of the week over a period, summed in SQL.
     *
     * This is the weekday chart. Seven groups at most, so the work is bounded by the days of
     * the week rather than by the movements in the period — the same reason every other chart
     * on the tab aggregates in `GROUP BY` (`CLAUDE.md` §5).
     *
     * [WeekdayTotal.weekday] is an ISO day number, Monday 1 to Sunday 7, so it drops straight
     * into `DayOfWeek.of`. `strftime('%w')` counts from Sunday 0; `((… + 6) % 7) + 1` rotates
     * that onto the ISO numbering, exactly as the filter query does. A day with nothing spent
     * on it does not come back — SQL groups what exists — and the zero is filled in the
     * repository, where it costs one pass over seven days.
     */
    @Query(
        """
        SELECT (((CAST(strftime('%w', t.date * 86400, 'unixepoch') AS INTEGER) + 6) % 7) + 1) AS weekday,
               SUM(t.amountMinor) AS totalMinor,
               COUNT(*) AS transactionCount
        FROM transactions t
        WHERE t.kind = :kind
          AND t.date BETWEEN :from AND :to
          AND (:accountId IS NULL OR t.accountId = :accountId)
          AND t.transferId IS NULL
          AND t.tradeId IS NULL
        GROUP BY weekday
        ORDER BY weekday
        """,
    )
    fun observeTotalsByWeekday(
        kind: TransactionKind,
        from: LocalDate,
        to: LocalDate,
        accountId: Long?,
    ): Flow<List<WeekdayTotal>>

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
     * **Over every account, brokers are left out**: the line is cash and bank, the money Home and
     * Wallet call *Cash and bank*. A broker's cash is invested money (`DECISIONS.md` D34), and a
     * purchase only turns it into an asset (D40) — counted here, buying 300 € of shares drew a
     * 300 € fall in a month whose spending was 28 €. What is invested has its own screens. With a
     * broker chosen as [accountId], the line is that account's cash, as its own screen shows it.
     *
     * With an [accountId] given, **both halves narrow together**. Only that account's opening
     * balance is carried in, and only its movements are counted before [from] — narrowing one
     * and not the other is the mistake this query exists to make impossible, because it would
     * draw a line starting from everyone's money and moving with one account's.
     *
     * Two scalar sub-selects rather than a join: an account with no movements must still
     * contribute its opening balance, and a `LEFT JOIN` filtered on the transaction's date
     * gets that wrong in a way that is easy to write and hard to see.
     *
     * **Transfer legs count here**, unlike in every income-and-expense query on this DAO.
     * This is a balance, and the money really moved: leaving the legs out would start the
     * line from a number the Accounts tab disagrees with.
     *
     * **So do balance corrections dated before [from]**, for the same reason: the Accounts tab
     * counts them, and they are kept out of `transactions` precisely so that only balance queries
     * like this one see them.
     */
    @Query(
        """
        SELECT (
                   SELECT COALESCE(SUM(a.openingBalanceMinor), 0)
                   FROM accounts a
                   WHERE (:accountId IS NULL AND a.type != 'BROKER') OR a.id = :accountId
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
                     AND t.accountId IN (
                         SELECT a.id FROM accounts a
                         WHERE (:accountId IS NULL AND a.type != 'BROKER') OR a.id = :accountId
                     )
               ) + (
                   SELECT COALESCE(SUM(b.amountMinor), 0)
                   FROM balance_adjustments b
                   WHERE b.date < :from
                     AND b.accountId IN (
                         SELECT a.id FROM accounts a
                         WHERE (:accountId IS NULL AND a.type != 'BROKER') OR a.id = :accountId
                     )
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
     *
     * **The same accounts as [observeBalanceBefore]**: one chosen account, or every one but the
     * brokers.
     *
     * **Transfer legs count here too.** Between two cash and bank accounts a transfer nets to zero
     * and the line is flat through it, which is the truth: the money changed pockets. To a broker
     * it is a step down, because the money left *Cash and bank* to be invested. Narrowed to one
     * account it is a real step up or down, which is also the truth, and dropping it would
     * draw a balance that ends somewhere the account is not.
     *
     * **A balance correction is a step on its own day**, counted in [DayNet.movementCount] like a
     * movement, because a period whose only change was a correction is not an empty one.
     */
    @Query(
        """
        SELECT m.date AS date,
               COALESCE(SUM(m.signedMinor), 0) AS netMinor,
               COUNT(*) AS movementCount
        FROM (
            SELECT t.date AS date,
                   CASE t.kind
                       WHEN 'INCOME' THEN t.amountMinor
                       WHEN 'EXPENSE' THEN -t.amountMinor
                       ELSE 0
                   END AS signedMinor
            FROM transactions t
            WHERE t.date BETWEEN :from AND :to
              AND t.accountId IN (
                  SELECT a.id FROM accounts a
                  WHERE (:accountId IS NULL AND a.type != 'BROKER') OR a.id = :accountId
              )
            UNION ALL
            SELECT b.date AS date, b.amountMinor AS signedMinor
            FROM balance_adjustments b
            WHERE b.date BETWEEN :from AND :to
              AND b.accountId IN (
                  SELECT a.id FROM accounts a
                  WHERE (:accountId IS NULL AND a.type != 'BROKER') OR a.id = :accountId
              )
        ) m
        GROUP BY m.date
        ORDER BY m.date
        """,
    )
    fun observeDailyNet(
        from: LocalDate,
        to: LocalDate,
        accountId: Long?,
    ): Flow<List<DayNet>>

    /**
     * Every category with a movement over a span, largest first.
     *
     * This is the single-category trend's list of things it can be about, and where its
     * default comes from — the busiest category is usually the one worth watching. An INNER
     * JOIN rather than the LEFT one the donut uses, because a trend is about a named category
     * and "uncategorized" is not one: it is the absence of a label, and a line following it
     * over a year would be following several unrelated things at once.
     *
     * **Each category is summed in its own direction, and movements against it are netted.**
     * `amountMinor` is always positive and `kind` carries the sign, so adding both kinds
     * together makes a salary and a rent payment the same shape — which put an income category
     * at the top of a list documented as "what is most spent on". A movement whose kind matches
     * its category's counts up, and one that does not — a refund filed under Groceries — counts
     * down, so the total is what the category actually came to.
     */
    @Query(
        """
        SELECT t.categoryId AS categoryId,
               c.name AS categoryName,
               c.colorArgb AS categoryColorArgb,
               c.iconKey AS categoryEmoji,
               COALESCE(SUM(CASE WHEN t.kind = c.kind THEN t.amountMinor ELSE -t.amountMinor END), 0) AS totalMinor,
               COUNT(*) AS transactionCount
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE t.date BETWEEN :from AND :to
          AND (:accountId IS NULL OR t.accountId = :accountId)
          AND t.transferId IS NULL
          AND t.tradeId IS NULL
        GROUP BY t.categoryId
        ORDER BY totalMinor DESC, categoryName ASC
        """,
    )
    fun observeCategoriesWithMovements(
        from: LocalDate,
        to: LocalDate,
        accountId: Long?,
    ): Flow<List<CategoryTotal>>

    /**
     * One category's daily totals over a span, summed in SQL.
     *
     * Days rather than buckets, because a bucket is a calendar month or week and SQLite has no
     * idea which of those was selected. Folding days into buckets costs one pass over the days
     * of the span — a year is 365 steps at most — rather than one over every movement ever
     * recorded, which is the same shape the balance line uses (`CLAUDE.md` §5).
     *
     * Summed in the category's own direction and netted against it, exactly as
     * [observeCategoriesWithMovements] is, or the line would disagree with the list that chose
     * which line to draw.
     */
    @Query(
        """
        SELECT t.date AS date,
               COALESCE(SUM(CASE WHEN t.kind = c.kind THEN t.amountMinor ELSE -t.amountMinor END), 0) AS totalMinor
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE t.categoryId = :categoryId
          AND t.date BETWEEN :from AND :to
          AND (:accountId IS NULL OR t.accountId = :accountId)
          AND t.transferId IS NULL
          AND t.tradeId IS NULL
        GROUP BY t.date
        ORDER BY t.date
        """,
    )
    fun observeCategoryDailyTotals(
        categoryId: Long,
        from: LocalDate,
        to: LocalDate,
        accountId: Long?,
    ): Flow<List<DayTotal>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /**
     * How many movements are waiting in the inbox: no category, and not a transfer leg or a
     * trade's cash, which carry none because they are never classified (PRD §4.6). The same
     * predicate as the *uncategorized* filter, so the count and the list it opens agree.
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE categoryId IS NULL AND transferId IS NULL AND tradeId IS NULL
        """,
    )
    fun observeUncategorizedCount(): Flow<Int>

    /**
     * Files one movement under a category, and nothing else about it changes.
     *
     * Guarded four ways, each a way a stale screen could do harm: a transfer leg and a trade's
     * cash must stay unclassified or they would start counting as income and spending; a movement
     * already filed elsewhere — its form, a second tap — keeps the category the owner gave it; and
     * the category has to be of the movement's own kind, or an expense would land in the income
     * ring. Returns how many rows changed: 0 is any of those, or a movement that is gone.
     */
    @Query(
        """
        UPDATE transactions SET categoryId = :categoryId, updatedAt = :updatedAt
        WHERE id = :id AND transferId IS NULL AND tradeId IS NULL AND categoryId IS NULL
          AND kind = (SELECT c.kind FROM categories c WHERE c.id = :categoryId)
        """,
    )
    suspend fun setCategory(
        id: Long,
        categoryId: Long,
        updatedAt: Instant,
    ): Int

    /**
     * Moves every movement out of one account and into another, so the first one can then
     * be deleted without taking its history with it.
     *
     * The caller is responsible for the two accounts sharing a currency. A movement carries
     * its own [TransactionEntity.currency], denormalized from the account it was recorded
     * in, and the balance query sums the amounts and labels the result with the *account's*
     * currency — so moving euros into a dollar account would not convert anything, it would
     * quietly make that account's balance a sum of two different currencies.
     *
     * Transfer legs travel with everything else, which can leave a transfer whose two legs
     * are now in the same account. It is left standing rather than rewritten: the pair still
     * nets to zero, it is still out of every report, and the alternatives are worse — undoing
     * the `transferId` would turn it into a real income and a real expense and inflate both,
     * and deleting it would destroy history inside an operation whose whole promise is that
     * nothing is lost. `docs/known-limitations.md` records it.
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
    val transferId: Long?,
    val tradeId: Long?,
    /** The occasion it belongs to (PRD §4.9), and its name for the row, both null for most. */
    val groupId: Long?,
    val groupName: String?,
    val accountName: String,
    val categoryName: String?,
    val categoryColorArgb: Int?,
    /** The category's emoji, kept in its `iconKey` column; null shows its letter. */
    val categoryEmoji: String?,
    /**
     * The other end of the transfer, and null for an ordinary movement.
     *
     * A row reading *"Transfer"* with the account it is already filed under says nothing; the
     * one thing worth knowing about a leg is where the money went or came from. It is
     * resolved in SQL, in a subquery guarded by `transferId IS NULL` so that an ordinary
     * movement — which is nearly all of them — pays nothing for it, and indexed on
     * `transferId` so the leg that does pay looks up one row rather than scanning the ledger.
     */
    val counterpartAccountName: String?,
    /**
     * What was bought or sold, and where to find it — null for an ordinary movement.
     *
     * A purchase's row would otherwise read *Uncategorized* with an amount, which says nothing
     * about the one thing worth knowing. Both are resolved in SQL by subqueries guarded on
     * `tradeId IS NULL`, so the movements that are not trades — nearly all of them — pay
     * nothing, and the ones that are look up a row by primary key.
     */
    val tradeAssetName: String?,
    val tradeHoldingId: Long?,
    /** True when the row is a sale's result, the one part of a trade the lists show. */
    val tradeResult: Boolean = false,
)

/** One day's signed change to one account's cash, as SQL returns it. */
data class AccountDayNet(
    val accountId: Long,
    val date: LocalDate,
    val netMinor: Long,
)

/** Income and expense totals for a period, summed in SQL. */
data class PeriodTotals(
    val incomeMinor: Long,
    val expenseMinor: Long,
)

/** One day's total for a single category, as SQL returns it. */
data class DayTotal(
    val date: LocalDate,
    val totalMinor: Long,
)

/**
 * One category's share of a period, as SQL returns it. A null [categoryId] is the
 * uncategorized group rather than a missing row.
 */
data class CategoryTotal(
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColorArgb: Int?,
    val categoryEmoji: String?,
    val totalMinor: Long,
    val transactionCount: Int,
)

/**
 * One weekday's spending, as SQL returns it. [weekday] is an ISO day number, Monday 1 to
 * Sunday 7.
 */
data class WeekdayTotal(
    val weekday: Int,
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
