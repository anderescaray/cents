package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface AccountRepository {
    fun observeAccounts(includeArchived: Boolean = false): Flow<List<Account>>

    fun observeAccountsWithBalances(includeArchived: Boolean = false): Flow<List<AccountWithBalance>>

    /** One account and its balance, for the screen that shows only that account. */
    fun observeAccountWithBalance(id: Long): Flow<AccountWithBalance?>

    suspend fun findById(id: Long): Account?

    suspend fun createAccount(account: Account): Long

    suspend fun updateAccount(account: Account)

    suspend fun setArchived(
        id: Long,
        archived: Boolean,
    )

    /**
     * Removes the account outright. Only legitimate while [countTransactions] is zero:
     * with movements in it, the two operations below are what "delete" can honestly mean.
     */
    suspend fun deleteAccount(id: Long)

    /**
     * Moves every movement into [targetId] and then deletes the account, in one database
     * transaction. Nothing is lost: the history survives under another account.
     *
     * [targetId] must be an account in the same currency. A movement keeps the currency it
     * was recorded in, and an account's balance is the sum of its movements labelled with
     * the account's own currency — so a cross-currency move would produce a balance that
     * adds euros to dollars without converting either.
     */
    suspend fun reassignAndDelete(
        id: Long,
        targetId: Long,
    )

    /**
     * Deletes the account **and every movement in it**, in one database transaction.
     *
     * This is the one operation in the app that removes recorded history on purpose. It
     * exists because the owner asked for it (`DECISIONS.md` D7): an account added by
     * mistake, and used before the mistake was noticed, otherwise haunts every picker
     * forever. It is never reached without an explicit confirmation that says how many
     * movements go with it, and there is no undo behind it.
     */
    suspend fun deleteAccountWithTransactions(id: Long)

    /** Zero means the account can be deleted outright instead of archived. */
    suspend fun countTransactions(id: Long): Int
}

interface CategoryRepository {
    fun observeCategories(kind: CategoryKind): Flow<List<Category>>

    fun observeAllCategories(): Flow<List<Category>>

    suspend fun findById(id: Long): Category?

    suspend fun createCategory(category: Category): Long

    suspend fun updateCategory(category: Category)

    suspend fun setArchived(
        id: Long,
        archived: Boolean,
    )

    /**
     * Removes the category outright. Only legitimate while [countTransactions] is zero:
     * with transactions attached, archiving is the operation that keeps history intact.
     */
    suspend fun deleteCategory(id: Long)

    /** Stores [orderedIds] as the order categories are listed and offered in. */
    suspend fun reorder(orderedIds: List<Long>)

    /** Zero means the category can be deleted outright instead of archived. */
    suspend fun countTransactions(id: Long): Int
}

interface TransactionRepository {
    /**
     * The list, narrowed by [filter] — and [TransactionFilter.None], the default, is every
     * movement. One method rather than two, so "the list" and "the filtered list" cannot
     * drift into disagreeing about what a movement's row looks like.
     */
    fun observeTransactions(filter: TransactionFilter = TransactionFilter.None): Flow<List<TransactionDetails>>

    fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>>

    /** One account's movements, carrying the names its rows have to show. */
    fun observeAccountTransactionDetails(accountId: Long): Flow<List<TransactionDetails>>

    /** Income and expense totals for a period, summed by the database. */
    fun observeSummary(
        from: LocalDate,
        to: LocalDate,
        currency: String,
    ): Flow<PeriodSummary>

    fun observeAccountSummary(
        accountId: Long,
        from: LocalDate,
        to: LocalDate,
        currency: String,
    ): Flow<PeriodSummary>

    suspend fun findById(id: Long): Transaction?

    suspend fun addTransaction(transaction: Transaction): Long

    suspend fun updateTransaction(transaction: Transaction)

    suspend fun deleteTransaction(id: Long)
}
