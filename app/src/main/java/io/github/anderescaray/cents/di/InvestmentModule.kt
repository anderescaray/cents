package io.github.anderescaray.cents.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.data.repository.RoomHoldingRepository
import io.github.anderescaray.cents.data.repository.RoomPortfolioRepository
import io.github.anderescaray.cents.data.repository.RoomPriceQuoteRepository
import io.github.anderescaray.cents.data.repository.RoomTradeRepository
import io.github.anderescaray.cents.domain.repository.HoldingRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.repository.PriceQuoteRepository
import io.github.anderescaray.cents.domain.repository.TradeRepository

/**
 * The investments repositories, bound in their own module.
 *
 * A module per area rather than one growing `RepositoryModule`, the same reasoning
 * `PreferencesModule` was split out with: a feature that adds four bindings should add a file,
 * not edit the one file every other feature also edits. Hilt does not care how many modules
 * there are.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InvestmentModule {
    @Binds
    abstract fun bindHoldingRepository(impl: RoomHoldingRepository): HoldingRepository

    @Binds
    abstract fun bindTradeRepository(impl: RoomTradeRepository): TradeRepository

    @Binds
    abstract fun bindPriceQuoteRepository(impl: RoomPriceQuoteRepository): PriceQuoteRepository

    @Binds
    abstract fun bindPortfolioRepository(impl: RoomPortfolioRepository): PortfolioRepository
}
