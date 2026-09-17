package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.dao.AccountDao
import io.github.anderescaray.cents.data.local.dao.CategoryDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
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
import io.github.anderescaray.cents.domain.model.Transfer
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
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
                // Transfers go whole, far leg included. A leg whose counterpart has been
                // deleted is money that arrived from nowhere: it would sit in another
                // account's list saying "Transfer from" with nothing to name, and stay out
                // of every report for a reason that no longer exists.
                //
                // The ids are read into a list first rather than deleted through a subquery
                // over the same table, which SQLite is free to re-evaluate as the rows it is
                // reading disappear underneath it.
                val transfers = transactionDao.transferIdsInAccount(id)
                if (transfers.isNotEmpty()) transactionDao.deleteTransfers(transfers)
                transactionDao.deleteByAccount(id)
                accountDao.deleteById(id)
            }
        }

        override suspend fun countTransactions(id: Long): Int = accountDao.countTransactions(id)

        override suspend fun countTransactionsRemovedWith(id: Long): Int =
            accountDao.countTransactions(id) + transactionDao.countTransferLegsOutside(id)

        override suspend fun countHoldings(id: Long): Int = accountDao.countHoldings(id)

        override suspend fun countRules(id: Long): Int = accountDao.countRules(id)
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
            val stored = transactionDao.findById(transaction.id)
            transactionDao.update(
                transaction
                    .toEntity(
                        // Keep the original creation instant: an edit is not a re-creation.
                        createdAt = stored?.createdAt ?: clock.instant(),
                        updatedAt = clock.instant(),
                    )
                    // And keep whatever pairs it, for the same reason and a sharper one. Nothing
                    // routes a transfer leg into the movement form today — both lists send a leg
                    // to the transfer instead — so a domain object reaching here carries the
                    // default `transferId = null`. If one ever does arrive, dropping the pair id
                    // would not fail: it would quietly turn one leg into a real income or
                    // expense, counted by every report, and leave the other pointing at a
                    // transfer that no longer exists. The invariant is held by this repository
                    // rather than by a foreign key, so it is held here too.
                    .copy(transferId = stored?.transferId),
            )
        }

        override suspend fun deleteTransaction(id: Long) {
            transactionDao.deleteById(id)
        }
    }

/**
 * Transfers, written as two ordinary movements that always travel together.
 *
 * There is no transfers table. A transfer is a pair of rows sharing a `transferId`, so the
 * amount that left and the amount that arrived are the same facts the balances are made of
 * rather than a second copy free to disagree with them. What that costs is this class: the
 * pairing invariant is not enforced by a foreign key, it is enforced by every write here
 * happening inside one `withTransaction`.
 */
@Singleton
class RoomTransferRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : TransferRepository {
        override suspend fun findTransfer(transferId: Long): Transfer? {
            val legs = transactionDao.findTransferLegs(transferId)
            val out = legs.firstOrNull { it.kind == TransactionKind.EXPENSE }
            val income = legs.firstOrNull { it.kind == TransactionKind.INCOME }
            // Half a pair is not a transfer. It is not a state this class can produce, but it
            // is one a screen can ask about: a row id survives in a back stack entry longer
            // than the rows behind it survive a deletion.
            if (out == null || income == null) return null
            return Transfer(
                id = transferId,
                fromAccountId = out.accountId,
                toAccountId = income.accountId,
                // What arrived is the amount; what the move cost is the difference. Reading
                // it back this way is what keeps the fee from ever drifting from the balances.
                amount = Money(income.amountMinor, income.currency),
                fee = Money(out.amountMinor - income.amountMinor, out.currency),
                date = out.date,
                note = out.note,
            )
        }

        override suspend fun saveTransfer(transfer: Transfer): Long =
            database.withTransaction {
                val existing =
                    transfer.id
                        .takeIf { it != Transfer.NEW }
                        ?.let { transactionDao.findTransferLegs(it) }
                        .orEmpty()
                if (existing.isEmpty()) insert(transfer) else rewrite(transfer, existing)
            }

        /**
         * A new transfer: the outgoing leg first, because its row id becomes the pair id.
         *
         * That is why the outgoing leg is written twice — once to be given an id, once to
         * carry it. Both writes and the incoming leg are inside the caller's transaction, so
         * the moment where a leg exists without a `transferId` is never visible to a reader.
         */
        private suspend fun insert(transfer: Transfer): Long {
            val now = clock.instant()
            val outId = transactionDao.insert(transfer.leg(out = true, createdAt = now))
            transactionDao.setTransferId(id = outId, transferId = outId, updatedAt = now)
            transactionDao.insert(transfer.leg(out = false, createdAt = now, transferId = outId))
            return outId
        }

        /**
         * An edit: both legs rewritten in place, keeping the row ids they already have.
         *
         * Rewriting both rather than patching what changed is the point. The two accounts can
         * have been swapped, and an update that touched only the amount would leave the money
         * flowing the old way; the ids are kept so that a transfer edited twice does not
         * shuffle its way down the list.
         */
        private suspend fun rewrite(
            transfer: Transfer,
            existing: List<TransactionEntity>,
        ): Long {
            val now = clock.instant()
            val out = existing.first { it.kind == TransactionKind.EXPENSE }
            val income = existing.first { it.kind == TransactionKind.INCOME }
            transactionDao.update(
                transfer
                    .leg(out = true, createdAt = out.createdAt, transferId = transfer.id)
                    .copy(id = out.id, updatedAt = now),
            )
            transactionDao.update(
                transfer
                    .leg(out = false, createdAt = income.createdAt, transferId = transfer.id)
                    .copy(id = income.id, updatedAt = now),
            )
            return transfer.id
        }

        override suspend fun deleteTransfer(transferId: Long) {
            transactionDao.deleteTransfer(transferId)
        }

        /**
         * One side of the transfer as a row.
         *
         * The outgoing leg carries [Transfer.amountLeaving] — the amount plus whatever the
         * move cost — and the incoming one carries what actually arrived. Neither has a
         * category: a transfer is not classified, and a category on a leg would put it in a
         * slice of a chart it is excluded from.
         */
        private fun Transfer.leg(
            out: Boolean,
            createdAt: Instant,
            transferId: Long? = null,
        ): TransactionEntity =
            TransactionEntity(
                accountId = if (out) fromAccountId else toAccountId,
                categoryId = null,
                kind = if (out) TransactionKind.EXPENSE else TransactionKind.INCOME,
                amountMinor = if (out) amountLeaving.minorUnits else amount.minorUnits,
                currency = amount.currency,
                date = date,
                note = note,
                transferId = transferId,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
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
