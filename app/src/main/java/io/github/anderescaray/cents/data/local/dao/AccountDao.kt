package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.anderescaray.cents.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/** Balance of one account, computed in SQL rather than stored. */
data class AccountBalance(
    val accountId: Long,
    val currency: String,
    val balanceMinor: Long,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    /**
     * Opening balance plus every movement, per account. Income adds, expense subtracts;
     * the aggregation happens in SQLite so the app never walks thousands of rows in Kotlin.
     *
     * A transfer's legs are movements like any other here, and deliberately so: a balance is
     * where the money is, and a transfer moved it. Only the income-and-expense reports leave
     * them out — see `TransactionDao`.
     *
     * **Balance corrections count here too**, and only in queries like this one, that compute a
     * balance. They live in their own table so that nothing reading `transactions` for income or
     * expenses can count one by mistake (`BalanceAdjustmentEntity`).
     */
    @Query(
        """
        SELECT a.id AS accountId,
               a.currency AS currency,
               $BALANCE_OF_A AS balanceMinor
        FROM accounts a
        LEFT JOIN transactions t ON t.accountId = a.id
        GROUP BY a.id
        ORDER BY a.sortOrder, a.name
        """,
    )
    fun observeBalances(): Flow<List<AccountBalance>>

    @Query(
        """
        SELECT $BALANCE_OF_A
        FROM accounts a
        LEFT JOIN transactions t ON t.accountId = a.id
        WHERE a.id = :accountId
        GROUP BY a.id
        """,
    )
    fun observeBalance(accountId: Long): Flow<Long?>

    /**
     * The same balance, read once: what setting the balance measures its difference against,
     * inside the transaction that writes it, so a movement captured in between cannot be missed.
     */
    @Query(
        """
        SELECT $BALANCE_OF_A
        FROM accounts a
        LEFT JOIN transactions t ON t.accountId = a.id
        WHERE a.id = :accountId
        GROUP BY a.id
        """,
    )
    suspend fun balanceOf(accountId: Long): Long?

    /**
     * Moves the opening balance by [deltaMinor] in SQL, so the read and the write cannot be split
     * by another write. Every balance this account has ever had moves with it, which is the point
     * of rebasing rather than correcting from a day on.
     */
    @Query("UPDATE accounts SET openingBalanceMinor = openingBalanceMinor + :deltaMinor WHERE id = :id")
    suspend fun addToOpeningBalance(
        id: Long,
        deltaMinor: Long,
    )

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun countAll(): Int

    /** How many movements live in this account: zero means it is safe to hard-delete. */
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id")
    suspend fun countTransactions(id: Long): Int

    /**
     * How many investments are held in this account.
     *
     * `holdings.accountId` is `RESTRICT` too, so this is a second thing that can refuse a
     * deletion — and unlike movements, there is no version of "delete" that resolves it here:
     * a holding cannot be moved to another account by this flow and cannot be detached from
     * one. Counting it is what lets the question be asked honestly instead of ending in a
     * constraint violation.
     */
    @Query("SELECT COUNT(*) FROM holdings WHERE accountId = :id")
    suspend fun countHoldings(id: Long): Int

    /**
     * Recurring rules that record into this account.
     *
     * The same shape of obstacle as a holding, and for the same reason: `recurring_rules.
     * accountId` is `RESTRICT`, and a rule with nowhere to record cannot be repaired by moving
     * its movements somewhere else. Active or stopped both count — a stopped rule is still a
     * row pointing at this account, and the database does not care that it has retired.
     */
    @Query("SELECT COUNT(*) FROM recurring_rules WHERE accountId = :id")
    suspend fun countRules(id: Long): Int

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Every account. Only a restore does this, and only after the rows that point here. */
    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(
        id: Long,
        archived: Boolean,
    )
}

/**
 * One account's balance, for a query over `accounts a LEFT JOIN transactions t` grouped by
 * account: its opening balance, plus its movements, plus its balance corrections.
 *
 * The corrections are a scalar sub-select rather than a second join, which would repeat every
 * movement once per correction and multiply the sum.
 */
private const val BALANCE_OF_A =
    """
    a.openingBalanceMinor + COALESCE(
        SUM(
            CASE t.kind
                WHEN 'INCOME' THEN t.amountMinor
                WHEN 'EXPENSE' THEN -t.amountMinor
                ELSE 0
            END
        ),
        0
    ) + (
        SELECT COALESCE(SUM(b.amountMinor), 0)
        FROM balance_adjustments b
        WHERE b.accountId = a.id
    )
    """
