package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.dao.AccountDao
import io.github.anderescaray.cents.data.local.dao.CategoryDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("TooManyFunctions")
@Singleton
class RoomAccountRepository
    @Inject
    constructor(
        private val accountDao: AccountDao,
        private val transactionDao: TransactionDao,
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : AccountRepository {
        override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
            (if (includeArchived) accountDao.observeAll() else accountDao.observeActive())
                .map { accounts -> accounts.map { it.toDomain() } }

        override fun observeAccountsWithBalances(includeArchived: Boolean): Flow<List<AccountWithBalance>> =
            combine(
                if (includeArchived) accountDao.observeAll() else accountDao.observeActive(),
                accountDao.observeBalances(),
            ) { accounts, balances ->
                val byAccount = balances.associateBy { it.accountId }
                accounts.map { entity ->
                    val account = entity.toDomain()
                    account.withBalance(byAccount[entity.id]?.toMoney())
                }
            }

        override fun observeAccountWithBalance(id: Long): Flow<AccountWithBalance?> =
            combine(
                accountDao.observeById(id),
                accountDao.observeBalance(id),
            ) { entity, balanceMinor ->
                entity?.toDomain()?.let { account ->
                    account.withBalance(balanceMinor?.let { Money(it, account.currency) })
                }
            }

        override suspend fun findById(id: Long): Account? = accountDao.findById(id)?.toDomain()

        override suspend fun createAccount(account: Account): Long =
            accountDao.insert(account.toEntity(createdAt = clock.instant()))

        override suspend fun updateAccount(account: Account) {
            // Keep the original creation instant: an edit is not a re-creation.
            val createdAt = accountDao.findById(account.id)?.createdAt ?: clock.instant()
            accountDao.update(account.toEntity(createdAt = createdAt))
        }

        override suspend fun setArchived(
            id: Long,
            archived: Boolean,
        ) {
            accountDao.setArchived(id, archived)
        }

        override suspend fun deleteAccount(id: Long) {
            accountDao.deleteById(id)
        }

        override suspend fun reassignAndDelete(
            id: Long,
            targetId: Long,
        ) {
            require(id != targetId) { "An account cannot be reassigned to itself" }
            // One transaction: movements left pointing at a row that is already gone is
            // exactly the state the RESTRICT foreign key exists to make impossible.
            database.withTransaction {
                transactionDao.reassignAccount(fromId = id, toId = targetId, updatedAt = clock.instant())
                accountDao.deleteById(id)
            }
        }

        override suspend fun deleteAccountWithTransactions(id: Long) {
            // Same reason, opposite direction: the movements go first, because the foreign
            // key refuses to let the account go while any of them still points at it.
            database.withTransaction {
                transactionDao.deleteByAccount(id)
                accountDao.deleteById(id)
            }
        }

        override suspend fun countTransactions(id: Long): Int = accountDao.countTransactions(id)

        override suspend fun countHoldings(id: Long): Int = accountDao.countHoldings(id)
    }

@Singleton
class RoomCategoryRepository
    @Inject
    constructor(
        private val categoryDao: CategoryDao,
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : CategoryRepository {
        override fun observeCategories(kind: CategoryKind): Flow<List<Category>> =
            categoryDao.observeActiveByKind(kind).map { categories -> categories.map { it.toDomain() } }

        override fun observeAllCategories(): Flow<List<Category>> =
            categoryDao.observeAll().map { categories -> categories.map { it.toDomain() } }

        override suspend fun findById(id: Long): Category? = categoryDao.findById(id)?.toDomain()

        override suspend fun createCategory(category: Category): Long =
            categoryDao.insert(category.toEntity(createdAt = clock.instant()))

        override suspend fun updateCategory(category: Category) {
            val createdAt = categoryDao.findById(category.id)?.createdAt ?: clock.instant()
            categoryDao.update(category.toEntity(createdAt = createdAt))
        }

        override suspend fun setArchived(
            id: Long,
            archived: Boolean,
        ) {
            categoryDao.setArchived(id, archived)
        }

        override suspend fun deleteCategory(id: Long) {
            categoryDao.deleteById(id)
        }

        override suspend fun reorder(orderedIds: List<Long>) {
            // One transaction: a half-applied order would leave the list in a state the
            // user never asked for.
            database.withTransaction {
                orderedIds.forEachIndexed { index, id -> categoryDao.updateSortOrder(id, index) }
            }
        }

        override suspend fun countTransactions(id: Long): Int = categoryDao.countTransactions(id)
    }

@Singleton
class RoomTransactionRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
        private val clock: Clock,
    ) : TransactionRepository {
        override fun observeTransactions(filter: TransactionFilter): Flow<List<TransactionDetails>> =
            transactionDao
                .observeFilteredWithNames(
                    accountId = filter.accountId,
                    categoryId = (filter.category as? CategoryFilter.One)?.id,
                    uncategorized = filter.category == CategoryFilter.Uncategorized,
                    kind = filter.kind,
                    from = filter.from,
                    to = filter.to,
                    weekday = filter.weekday?.value,
                    minAmountMinor = filter.minAmountMinor,
                    maxAmountMinor = filter.maxAmountMinor,
                    query = filter.query.toLikeArgument(),
                ).map { rows -> rows.map { it.toDomain() } }

        override fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>> =
            transactionDao.observeByAccount(accountId).map { rows -> rows.map { it.toDomain() } }

        override fun observeAccountTransactionDetails(accountId: Long): Flow<List<TransactionDetails>> =
            transactionDao.observeByAccountWithNames(accountId).map { rows -> rows.map { it.toDomain() } }

        override fun observeSummary(
            from: LocalDate,
            to: LocalDate,
            currency: String,
        ): Flow<PeriodSummary> = transactionDao.observeTotalsBetween(from, to).map { it.toSummary(currency) }

        override fun observeAccountSummary(
            accountId: Long,
            from: LocalDate,
            to: LocalDate,
            currency: String,
        ): Flow<PeriodSummary> =
            transactionDao.observeAccountTotalsBetween(accountId, from, to).map { it.toSummary(currency) }

        override suspend fun findById(id: Long): Transaction? = transactionDao.findById(id)?.toDomain()

        override suspend fun addTransaction(transaction: Transaction): Long {
            val now = clock.instant()
            return transactionDao.insert(transaction.toEntity(createdAt = now, updatedAt = now))
        }

        override suspend fun updateTransaction(transaction: Transaction) {
            val createdAt = transactionDao.findById(transaction.id)?.createdAt ?: clock.instant()
            transactionDao.update(transaction.toEntity(createdAt = createdAt, updatedAt = clock.instant()))
        }

        override suspend fun deleteTransaction(id: Long) {
            transactionDao.deleteById(id)
        }
    }

/**
 * Turns typed text into a LIKE pattern, or null when there is nothing to search for.
 *
 * The wildcards are escaped before the surrounding ones are added, so a note containing
 * "50%" is found by searching for "50%" rather than matching every note starting with 50.
 * The backslash is escaped first, or it would go on to escape the escapes.
 */
private fun String.toLikeArgument(): String? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    val escaped =
        trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    return "%$escaped%"
}
