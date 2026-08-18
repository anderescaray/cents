package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.dao.AccountDao
import io.github.anderescaray.cents.data.local.dao.CategoryDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
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

@Singleton
class RoomAccountRepository
    @Inject
    constructor(
        private val accountDao: AccountDao,
        private val clock: Clock,
    ) : AccountRepository {
        override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
            (if (includeArchived) accountDao.observeAll() else accountDao.observeActive())
                .map { accounts -> accounts.map { it.toDomain() } }

        override fun observeAccountsWithBalances(): Flow<List<AccountWithBalance>> =
            combine(
                accountDao.observeActive(),
                accountDao.observeBalances(),
            ) { accounts, balances ->
                val byAccount = balances.associateBy { it.accountId }
                accounts.map { entity ->
                    val account = entity.toDomain()
                    account.withBalance(byAccount[entity.id]?.toMoney())
                }
            }

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
    }

@Singleton
class RoomCategoryRepository
    @Inject
    constructor(
        private val categoryDao: CategoryDao,
        private val clock: Clock,
    ) : CategoryRepository {
        override fun observeCategories(kind: CategoryKind): Flow<List<Category>> =
            categoryDao.observeActiveByKind(kind).map { categories -> categories.map { it.toDomain() } }

        override fun observeAllCategories(): Flow<List<Category>> =
            categoryDao.observeAll().map { categories -> categories.map { it.toDomain() } }

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

        override suspend fun countTransactions(id: Long): Int = categoryDao.countTransactions(id)
    }

@Singleton
class RoomTransactionRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
        private val clock: Clock,
    ) : TransactionRepository {
        override fun observeTransactions(): Flow<List<TransactionDetails>> =
            transactionDao.observeAllWithNames().map { rows -> rows.map { it.toDomain() } }

        override fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>> =
            transactionDao.observeByAccount(accountId).map { rows -> rows.map { it.toDomain() } }

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
