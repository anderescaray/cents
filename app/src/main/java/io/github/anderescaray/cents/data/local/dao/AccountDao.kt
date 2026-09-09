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
     */
    @Query(
        """
        SELECT a.id AS accountId,
               a.currency AS currency,
               a.openingBalanceMinor + COALESCE(
                   SUM(
                       CASE t.kind
                           WHEN 'INCOME' THEN t.amountMinor
                           WHEN 'EXPENSE' THEN -t.amountMinor
                           ELSE 0
                       END
                   ),
                   0
               ) AS balanceMinor
        FROM accounts a
        LEFT JOIN transactions t ON t.accountId = a.id
        GROUP BY a.id
        ORDER BY a.sortOrder, a.name
        """,
    )
    fun observeBalances(): Flow<List<AccountBalance>>

    @Query(
        """
        SELECT a.openingBalanceMinor + COALESCE(
                   SUM(
                       CASE t.kind
                           WHEN 'INCOME' THEN t.amountMinor
                           WHEN 'EXPENSE' THEN -t.amountMinor
                           ELSE 0
                       END
                   ),
                   0
               )
        FROM accounts a
        LEFT JOIN transactions t ON t.accountId = a.id
        WHERE a.id = :accountId
        """,
    )
    fun observeBalance(accountId: Long): Flow<Long?>

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

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

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
