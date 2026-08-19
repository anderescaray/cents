package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
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
     * with movements in it, archiving is the operation that keeps the history.
     */
    suspend fun deleteAccount(id: Long)

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
    fun observeTransactions(): Flow<List<TransactionDetails>>

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
