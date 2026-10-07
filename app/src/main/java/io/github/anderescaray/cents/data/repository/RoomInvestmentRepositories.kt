package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.dao.HoldingDao
import io.github.anderescaray.cents.data.local.dao.PriceQuoteDao
import io.github.anderescaray.cents.data.local.dao.TradeDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.data.local.entity.PriceQuoteEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Portfolio
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.PriceQuote
import io.github.anderescaray.cents.domain.model.QuoteSource
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.HoldingRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.repository.PriceQuoteRepository
import io.github.anderescaray.cents.domain.repository.TradeRepository
import io.github.anderescaray.cents.domain.usecase.positionFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class RoomHoldingRepository
    @Inject
    constructor(
        private val holdingDao: HoldingDao,
        private val transactionDao: TransactionDao,
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : HoldingRepository {
        override fun observeHoldings(): Flow<List<Holding>> =
            holdingDao.observeAll().map { rows ->
                rows.map { it.toDomain() }
            }

        override fun observeHolding(id: Long): Flow<Holding?> = holdingDao.observeById(id).map { it?.toDomain() }

        override suspend fun findById(id: Long): Holding? = holdingDao.findById(id)?.toDomain()

        override suspend fun findBySymbol(
            accountId: Long,
            symbol: String,
        ): Holding? = holdingDao.findBySymbol(accountId, symbol)?.toDomain()

        override suspend fun createHolding(holding: Holding): Long {
            val now = clock.instant()
            return holdingDao.insert(holding.toEntity(createdAt = now, updatedAt = now))
        }

        override suspend fun updateHolding(holding: Holding) {
            val existing = holdingDao.findById(holding.id) ?: return
            holdingDao.update(holding.toEntity(createdAt = existing.createdAt, updatedAt = clock.instant()))
        }

        override suspend fun deleteHolding(id: Long) {
            // The cash goes first, because `transactions.tradeId` is `RESTRICT` and the trades
            // are about to be deleted underneath it. It also has to go at all: those rows are
            // movements in a real account, and leaving them behind would leave that account's
            // balance short by money whose reason no longer exists.
            database.withTransaction {
                transactionDao.deleteCashForHolding(id)
                holdingDao.deleteWithTrades(id)
            }
        }

        override suspend fun countTrades(id: Long): Int = holdingDao.countTrades(id)
    }

/**
 * Trades, and the cash each one moves.
 *
 * **A purchase is two records, written together**: the trade, which says what was bought, and a
 * movement in the holding's account, which says the money left. They are separate facts rather
 * than one fact stored twice — the trade knows about units and the movement knows about a
 * balance — and keeping them together is what makes an investments account's balance read as
 * **the cash still in it** rather than as everything ever put there (D11).
 *
 * Every write here is one `withTransaction`, for the reason `RoomTransferRepository` gives about
 * its two legs: nothing enforces the pairing but this class, so a trade recorded without its
 * cash — or cash left behind by a deleted trade — must not be a state the app can reach.
 */
@Singleton
class RoomTradeRepository
    @Inject
    constructor(
        private val tradeDao: TradeDao,
        private val holdingDao: HoldingDao,
        private val transactionDao: TransactionDao,
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : TradeRepository {
        override fun observeTrades(holdingId: Long): Flow<List<Trade>> =
            tradeDao.observeForHolding(holdingId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun findById(id: Long): Trade? = tradeDao.findById(id)?.toDomain()

        override suspend fun addTrade(trade: Trade): Long =
            database.withTransaction {
                val id = tradeDao.insert(trade.toEntity(createdAt = clock.instant()))
                // With the id, because the movement has to point back at the trade it paid for.
                writeCash(trade.copy(id = id))
                id
            }

        override suspend fun updateTrade(trade: Trade) {
            val existing = tradeDao.findById(trade.id) ?: return
            database.withTransaction {
                tradeDao.update(trade.toEntity(createdAt = existing.createdAt))
                writeCash(trade)
            }
        }

        override suspend fun deleteTrade(id: Long) {
            database.withTransaction {
                // First, because `transactions.tradeId` is `RESTRICT`: the movement points at
                // the trade, so the pointer goes before the thing pointed at.
                transactionDao.deleteTradeCash(id)
                tradeDao.deleteById(id)
            }
        }

        /**
         * Puts the account's side of the trade in step with the trade itself.
         *
         * Written rather than patched, and **rewritten rather than added to**: editing a
         * purchase from 400 € to 300 € has to leave one movement of 300 €, not two totalling
         * 700 €. The row keeps the id it had, so a trade edited twice does not shuffle its way
         * down the account's list.
         *
         * A trade that moves no money has no row, and that is not only the correction case: a
         * sale whose fee exactly swallows its proceeds moves nothing either. If such an edit
         * lands on a trade that *did* move money, the row it had is removed rather than left
         * behind at its old amount.
         *
         * The currency is the holding's, which is the account's — a holding takes it from the
         * account it is created in (rule 2). A movement is denominated in what it was recorded
         * in, and a balance sums an account's movements under the account's own currency, so
         * these two agreeing is what keeps that sum meaningful.
         */
        private suspend fun writeCash(trade: Trade) {
            val holding = holdingDao.findById(trade.holdingId) ?: return
            val existing = transactionDao.findTradeCash(trade.id)
            val delta = trade.cashDelta
            if (delta.minorUnits == 0L) {
                if (existing != null) transactionDao.deleteTradeCash(trade.id)
                return
            }
            val now = clock.instant()
            val row =
                TransactionEntity(
                    id = existing?.id ?: 0,
                    accountId = holding.accountId,
                    categoryId = null,
                    kind = if (delta.minorUnits < 0) TransactionKind.EXPENSE else TransactionKind.INCOME,
                    // The amount is always positive and the direction lives in the kind, which
                    // is the same rule every other movement in this app obeys.
                    amountMinor = abs(delta.minorUnits),
                    currency = holding.currency,
                    date = trade.date,
                    // The trade's note travels with its cash, so searching the list for it finds
                    // the movement as well as the trade.
                    note = trade.note,
                    tradeId = trade.id,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            if (existing == null) transactionDao.insert(row) else transactionDao.update(row)
        }
    }

@Singleton
class RoomPriceQuoteRepository
    @Inject
    constructor(
        private val priceQuoteDao: PriceQuoteDao,
    ) : PriceQuoteRepository {
        override fun observeQuotes(): Flow<List<PriceQuote>> =
            priceQuoteDao.observeAll().map { rows ->
                rows.map { it.toDomain() }
            }

        override fun observeQuote(symbol: String): Flow<PriceQuote?> =
            priceQuoteDao.observeBySymbol(symbol).map {
                it?.toDomain()
            }

        override suspend fun setManualPrice(
            symbol: String,
            price: Money,
            asOf: Instant,
        ) {
            priceQuoteDao.upsert(
                PriceQuoteEntity(
                    symbol = symbol,
                    priceMinor = price.minorUnits,
                    currency = price.currency,
                    asOf = asOf,
                    source = QuoteSource.MANUAL,
                ),
            )
        }
    }

/**
 * The read model: holdings, every trade, and every price, folded into positions.
 *
 * **Every trade in one query rather than one query per holding.** A portfolio of twenty
 * holdings would otherwise be twenty flows to combine and twenty statements to re-run whenever
 * any of them changed. The rows are grouped in Kotlin, which is bounded by how many times the
 * owner has traded — the same argument `positionFrom` makes about why the fold is not SQL.
 *
 * **A price is matched by symbol, not by holding**, so the same instrument held in two accounts
 * is worth the same in both. A holding with no quote yet produces a position with a null market
 * value, which is what the screens render as "no price yet" rather than as zero.
 */
@Singleton
class RoomPortfolioRepository
    @Inject
    constructor(
        private val holdingDao: HoldingDao,
        private val tradeDao: TradeDao,
        private val priceQuoteDao: PriceQuoteDao,
    ) : PortfolioRepository {
        override fun observePortfolio(): Flow<Portfolio> =
            combine(
                holdingDao.observeAll(),
                tradeDao.observeAll(),
                priceQuoteDao.observeAll(),
            ) { holdings, trades, quotes ->
                val tradesByHolding = trades.groupBy { it.holdingId }
                val quotesBySymbol = quotes.associateBy { it.symbol }
                Portfolio(
                    positions =
                        holdings.map { entity ->
                            val holding = entity.toDomain()
                            positionFrom(
                                holding = holding,
                                trades = tradesByHolding[entity.id].orEmpty().map { it.toDomain() },
                                quote = quotesBySymbol[holding.symbol]?.toDomain(),
                            )
                        },
                )
            }

        override fun observePosition(holdingId: Long): Flow<Position?> =
            observePortfolio().map { portfolio -> portfolio.positions.firstOrNull { it.holding.id == holdingId } }
    }
