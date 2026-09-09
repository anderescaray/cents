package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.dao.HoldingDao
import io.github.anderescaray.cents.data.local.dao.PriceQuoteDao
import io.github.anderescaray.cents.data.local.dao.TradeDao
import io.github.anderescaray.cents.data.local.entity.PriceQuoteEntity
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Portfolio
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.PriceQuote
import io.github.anderescaray.cents.domain.model.QuoteSource
import io.github.anderescaray.cents.domain.model.Trade
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

@Singleton
class RoomHoldingRepository
    @Inject
    constructor(
        private val holdingDao: HoldingDao,
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
            holdingDao.deleteWithTrades(id)
        }

        override suspend fun countTrades(id: Long): Int = holdingDao.countTrades(id)
    }

@Singleton
class RoomTradeRepository
    @Inject
    constructor(
        private val tradeDao: TradeDao,
        private val clock: Clock,
    ) : TradeRepository {
        override fun observeTrades(holdingId: Long): Flow<List<Trade>> =
            tradeDao.observeForHolding(holdingId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun findById(id: Long): Trade? = tradeDao.findById(id)?.toDomain()

        override suspend fun addTrade(trade: Trade): Long = tradeDao.insert(trade.toEntity(createdAt = clock.instant()))

        override suspend fun updateTrade(trade: Trade) {
            val existing = tradeDao.findById(trade.id) ?: return
            tradeDao.update(trade.toEntity(createdAt = existing.createdAt))
        }

        override suspend fun deleteTrade(id: Long) {
            tradeDao.deleteById(id)
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
