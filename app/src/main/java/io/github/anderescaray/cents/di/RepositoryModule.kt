package io.github.anderescaray.cents.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.data.repository.reports.RoomBalanceOverTimeRepository
import io.github.anderescaray.cents.data.repository.reports.RoomMonthlyFlowRepository
import io.github.anderescaray.cents.data.repository.reports.RoomSpendingByCategoryRepository
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.domain.repository.reports.BalanceOverTimeRepository
import io.github.anderescaray.cents.domain.repository.reports.MonthlyFlowRepository
import io.github.anderescaray.cents.domain.repository.reports.SpendingByCategoryRepository
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindAccountRepository(impl: RoomAccountRepository): AccountRepository

    @Binds
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository

    // One binding per view on the Reports tab: a new view adds one line here, and that is
    // the whole of its footprint on this file. See ui/feature/reports/ReportSection.kt.
    @Binds
    abstract fun bindSpendingByCategoryRepository(impl: RoomSpendingByCategoryRepository): SpendingByCategoryRepository

    @Binds
    abstract fun bindMonthlyFlowRepository(impl: RoomMonthlyFlowRepository): MonthlyFlowRepository

    @Binds
    abstract fun bindBalanceOverTimeRepository(impl: RoomBalanceOverTimeRepository): BalanceOverTimeRepository

    companion object {
        /**
         * Injected rather than calling Instant.now() directly, so tests can freeze time
         * instead of asserting against whatever the machine clock happened to say.
         */
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
