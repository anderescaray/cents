package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.dao.AccountDayNet
import io.github.anderescaray.cents.data.local.dao.HoldingDao
import io.github.anderescaray.cents.data.local.dao.HoldingValueDao
import io.github.anderescaray.cents.data.local.dao.TradeDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.data.local.entity.HoldingValueEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.InvestmentAccount
import io.github.anderescaray.cents.domain.model.InvestmentOverview
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.ValueStep
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.HoldingRepository
import io.github.anderescaray.cents.domain.repository.HoldingValueRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.repository.TradeRepository
import io.github.anderescaray.cents.domain.usecase.positionOf
import io.github.anderescaray.cents.domain.usecase.tradeCashOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Writes again the movements an asset's trades make in its account's cash (`DECISIONS.md` D40).
 *
 * **Every write to an asset ends here**, inside its database transaction: a purchase edited,
 * a value typed, a sale deleted. Each can change what a later sale removed in cost, and so what it
 * made, so the whole asset's cash is worked out again from scratch rather than patched. That is
 * one fold over the owner's own record of one asset, which is short.
 *
 * Rows are matched to what they were by trade and part, and keep their ids and dates of creation,
 * so a movement written again does not move down the account's list.
 */
@Singleton
class TradeCashWriter
    @Inject
    constructor(
        private val holdingDao: HoldingDao,
        private val tradeDao: TradeDao,
        private val holdingValueDao: HoldingValueDao,
        private val transactionDao: TransactionDao,
        private val clock: Clock,
    ) {
        /** Call inside a database transaction. */
        suspend fun rewrite(holdingId: Long) {
            val holding = holdingDao.findById(holdingId)?.toDomain() ?: return
            val trades = tradeDao.forHolding(holdingId).map { it.toDomain() }
            val values = holdingValueDao.forHolding(holdingId).map { it.toDomain() }
            val wanted = tradeCashOf(trades, positionOf(holding, trades, values))
            val existing = transactionDao.cashForHolding(holdingId).associateBy { it.tradeId to it.tradeResult }
            val now = clock.instant()
            val kept = mutableSetOf<Long>()
            for (cash in wanted) {
                val old = existing[cash.trade.id to cash.isResult]
                val row =
                    TransactionEntity(
                        id = old?.id ?: 0,
                        accountId = holding.accountId,
                        categoryId = null,
                        kind = if (cash.signedMinor < 0) TransactionKind.EXPENSE else TransactionKind.INCOME,
                        // Positive, with the direction in the kind, as every movement is.
                        amountMinor = abs(cash.signedMinor),
                        currency = holding.currency,
                        date = cash.trade.date,
                        note = cash.trade.note,
                        tradeId = cash.trade.id,
                        tradeResult = cash.isResult,
                        createdAt = old?.createdAt ?: now,
                        updatedAt = now,
                    )
                if (old == null) {
                    kept += transactionDao.insert(row)
                } else {
                    transactionDao.update(row)
                    kept += old.id
                }
            }
            val stale = existing.values.map { it.id }.filterNot { it in kept }
            if (stale.isNotEmpty()) transactionDao.deleteByIds(stale)
        }
    }

@Singleton
class RoomHoldingRepository
    @Inject
    constructor(
        private val holdingDao: HoldingDao,
        private val tradeDao: TradeDao,
        private val transactionDao: TransactionDao,
        private val cashWriter: TradeCashWriter,
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : HoldingRepository {
        override fun observeHolding(id: Long): Flow<Holding?> = holdingDao.observeById(id).map { it?.toDomain() }

        override suspend fun findById(id: Long): Holding? = holdingDao.findById(id)?.toDomain()

        override suspend fun createHolding(
            holding: Holding,
            firstPurchase: Trade,
        ): Long =
            database.withTransaction {
                val now = clock.instant()
                val id = holdingDao.insert(holding.toEntity(createdAt = now, updatedAt = now))
                tradeDao.insert(firstPurchase.copy(id = 0, holdingId = id).toEntity(createdAt = now))
                cashWriter.rewrite(id)
                id
            }

        override suspend fun updateHolding(holding: Holding) {
            val existing = holdingDao.findById(holding.id) ?: return
            database.withTransaction {
                holdingDao.update(holding.toEntity(createdAt = existing.createdAt, updatedAt = clock.instant()))
                // The note and name travel with the cash's rows only through the trades, so
                // nothing about the cash changes; but the account cannot change either, so this
                // is enough.
            }
        }

        override suspend fun deleteHolding(id: Long) {
            // The cash goes first, because `transactions.tradeId` is `RESTRICT` and the trades
            // are about to be deleted underneath it. It also has to go at all: those rows are
            // movements in a real account, and leaving them would leave its cash short by money
            // whose reason no longer exists.
            database.withTransaction {
                transactionDao.deleteCashForHolding(id)
                holdingDao.deleteWithHistory(id)
            }
        }

        override suspend fun countTrades(id: Long): Int = holdingDao.countTrades(id)
    }

@Singleton
class RoomTradeRepository
    @Inject
    constructor(
        private val tradeDao: TradeDao,
        private val transactionDao: TransactionDao,
        private val cashWriter: TradeCashWriter,
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : TradeRepository {
        override suspend fun findById(id: Long): Trade? = tradeDao.findById(id)?.toDomain()

        override suspend fun addTrade(trade: Trade): Long =
            database.withTransaction {
                val id = tradeDao.insert(trade.toEntity(createdAt = clock.instant()))
                cashWriter.rewrite(trade.holdingId)
                id
            }

        override suspend fun updateTrade(trade: Trade) {
            val existing = tradeDao.findById(trade.id) ?: return
            database.withTransaction {
                tradeDao.update(trade.copy(holdingId = existing.holdingId).toEntity(createdAt = existing.createdAt))
                cashWriter.rewrite(existing.holdingId)
            }
        }

        override suspend fun deleteTrade(id: Long) {
            val existing = tradeDao.findById(id) ?: return
            database.withTransaction {
                // First, because `transactions.tradeId` is `RESTRICT`: the movement points at
                // the trade, so the pointer goes before the thing pointed at.
                transactionDao.deleteTradeCash(id)
                tradeDao.deleteById(id)
                cashWriter.rewrite(existing.holdingId)
            }
        }
    }

@Singleton
class RoomHoldingValueRepository
    @Inject
    constructor(
        private val holdingValueDao: HoldingValueDao,
        private val holdingDao: HoldingDao,
        private val cashWriter: TradeCashWriter,
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : HoldingValueRepository {
        override suspend fun findById(id: Long): HoldingValue? = holdingValueDao.findById(id)?.toDomain()

        override suspend fun setValue(
            holdingId: Long,
            date: LocalDate,
            value: Money,
        ) {
            val holding = holdingDao.findById(holdingId) ?: return
            database.withTransaction {
                holdingValueDao.save(
                    HoldingValueEntity(
                        holdingId = holdingId,
                        date = date,
                        valueMinor = value.minorUnits,
                        currency = holding.currency,
                        createdAt = clock.instant(),
                    ),
                )
                // A part sale after this day measures itself against it.
                cashWriter.rewrite(holdingId)
            }
        }

        override suspend fun deleteValue(id: Long) {
            val existing = holdingValueDao.findById(id) ?: return
            database.withTransaction {
                holdingValueDao.deleteById(id)
                cashWriter.rewrite(existing.holdingId)
            }
        }
    }

/**
 * The read model: assets, every trade, every value, and the broker accounts' cash, folded into
 * what the investment screens show.
 *
 * **Every trade and value in one query each**, grouped in Kotlin, rather than a flow per asset:
 * twenty assets would otherwise be sixty flows to combine. The rows are bounded by how much the
 * owner has recorded by hand.
 */
@Singleton
class RoomPortfolioRepository
    @Inject
    constructor(
        private val holdingDao: HoldingDao,
        private val tradeDao: TradeDao,
        private val holdingValueDao: HoldingValueDao,
        private val transactionDao: TransactionDao,
        private val accountRepository: AccountRepository,
    ) : PortfolioRepository {
        override fun observePositions(): Flow<List<Position>> =
            combine(
                holdingDao.observeAll(),
                tradeDao.observeAll(),
                holdingValueDao.observeAll(),
            ) { holdings, trades, values ->
                val tradesByHolding = trades.groupBy { it.holdingId }
                val valuesByHolding = values.groupBy { it.holdingId }
                holdings.map { entity ->
                    positionOf(
                        holding = entity.toDomain(),
                        trades = tradesByHolding[entity.id].orEmpty().map { it.toDomain() },
                        values = valuesByHolding[entity.id].orEmpty().map { it.toDomain() },
                    )
                }
            }

        override fun observePosition(holdingId: Long): Flow<Position?> =
            observePositions().map { positions -> positions.firstOrNull { it.holding.id == holdingId } }

        override fun observeOverview(): Flow<InvestmentOverview> =
            combine(
                accountRepository.observeAccountsWithBalances(includeArchived = false),
                observePositions(),
                transactionDao.observeBrokerCashByDay(),
            ) { accounts, positions, cashByDay ->
                val brokers = accounts.filter { it.account.type == AccountType.BROKER }
                val positionsByAccount = positions.groupBy { it.holding.accountId }
                val cashByAccount = cashByDay.groupBy { it.accountId }
                InvestmentOverview(
                    currency = brokers.map { it.account.currency }.distinct().singleOrNull(),
                    accounts =
                        brokers.map { item ->
                            investmentAccount(
                                item,
                                positionsByAccount[item.account.id].orEmpty(),
                                cashByAccount[item.account.id].orEmpty(),
                            )
                        },
                )
            }

        override fun observeAccount(accountId: Long): Flow<InvestmentAccount?> =
            observeOverview().map { overview -> overview.accounts.firstOrNull { it.account.id == accountId } }
    }

/** One broker account: its cash now, its cash through time from the opening balance, and its assets. */
private fun investmentAccount(
    item: AccountWithBalance,
    positions: List<Position>,
    cashByDay: List<AccountDayNet>,
): InvestmentAccount {
    val currency = item.account.currency
    var running = item.account.openingBalance.minorUnits
    val steps =
        cashByDay.sortedBy { it.date }.map { day ->
            running += day.netMinor
            ValueStep(day.date, Money(running, currency))
        }
    return InvestmentAccount(
        account = item.account,
        cash = item.balance,
        positions = positions,
        cashSteps = steps,
        cashBefore = item.account.openingBalance,
    )
}
