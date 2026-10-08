package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.entity.BalanceAdjustmentEntity
import io.github.anderescaray.cents.domain.model.BalanceAdjustment
import io.github.anderescaray.cents.domain.model.BalanceChange
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBalanceRepository
    @Inject
    constructor(
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : BalanceRepository {
        private val accountDao get() = database.accountDao()
        private val adjustmentDao get() = database.balanceAdjustmentDao()

        override fun observeAdjustments(accountId: Long): Flow<List<BalanceAdjustment>> =
            adjustmentDao.observeForAccount(accountId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun setBalance(
            accountId: Long,
            target: Money,
            change: BalanceChange,
            on: LocalDate,
        ): Money? =
            // One transaction: the balance is read and the difference written while no other write
            // can land in between, which is what keeps a captured payment from being undone.
            database.withTransaction {
                val account = accountDao.findById(accountId) ?: return@withTransaction null
                require(target.currency == account.currency) {
                    "The balance of a ${account.currency} account cannot be set in ${target.currency}"
                }
                val current = accountDao.balanceOf(accountId) ?: return@withTransaction null
                val difference = Math.subtractExact(target.minorUnits, current)
                if (difference != 0L) {
                    when (change) {
                        BalanceChange.CORRECT_FROM_TODAY -> {
                            adjustmentDao.insert(
                                BalanceAdjustmentEntity(
                                    accountId = accountId,
                                    amountMinor = difference,
                                    currency = account.currency,
                                    date = on,
                                    createdAt = clock.instant(),
                                ),
                            )
                        }

                        BalanceChange.REBASE_OPENING -> {
                            // Refused rather than wrapped round: a balance that overflows is
                            // nonsense, and SQLite would store the wrapped number without a word.
                            Math.addExact(account.openingBalanceMinor, difference)
                            accountDao.addToOpeningBalance(accountId, difference)
                        }
                    }
                }
                Money(difference, account.currency)
            }

        override suspend fun deleteAdjustment(id: Long): BalanceAdjustment? =
            database.withTransaction {
                adjustmentDao.findById(id)?.also { adjustmentDao.deleteById(id) }?.toDomain()
            }

        override suspend fun restoreAdjustment(adjustment: BalanceAdjustment) {
            adjustmentDao.insert(
                BalanceAdjustmentEntity(
                    id = adjustment.id,
                    accountId = adjustment.accountId,
                    amountMinor = adjustment.amount.minorUnits,
                    currency = adjustment.amount.currency,
                    date = adjustment.date,
                    // When the row was made is bookkeeping the domain model does not carry, and
                    // nothing reads it back; a restore from a backup stamps it the same way.
                    createdAt = clock.instant(),
                ),
            )
        }
    }

internal fun BalanceAdjustmentEntity.toDomain(): BalanceAdjustment =
    BalanceAdjustment(
        id = id,
        accountId = accountId,
        amount = Money(amountMinor, currency),
        date = date,
    )
