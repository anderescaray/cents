package io.github.anderescaray.cents.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomBackupRepository
import io.github.anderescaray.cents.data.repository.RoomBalanceRepository
import io.github.anderescaray.cents.data.repository.RoomBudgetRepository
import io.github.anderescaray.cents.data.repository.RoomCaptureRecorder
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomGroupRepository
import io.github.anderescaray.cents.data.repository.RoomRecurringRuleRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.data.repository.RoomTransferRepository
import io.github.anderescaray.cents.data.repository.reports.RoomBalanceOverTimeRepository
import io.github.anderescaray.cents.data.repository.reports.RoomCategoryTrendRepository
import io.github.anderescaray.cents.data.repository.reports.RoomMonthlyFlowRepository
import io.github.anderescaray.cents.data.repository.reports.RoomPeriodComparisonRepository
import io.github.anderescaray.cents.data.repository.reports.RoomSpendingByCategoryRepository
import io.github.anderescaray.cents.data.repository.reports.RoomSpendingByWeekdayRepository
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.BackupRepository
import io.github.anderescaray.cents.domain.repository.BalanceRepository
import io.github.anderescaray.cents.domain.repository.BudgetRepository
import io.github.anderescaray.cents.domain.repository.CaptureRecorder
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.GroupRepository
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.domain.repository.TransferRepository
import io.github.anderescaray.cents.domain.repository.reports.BalanceOverTimeRepository
import io.github.anderescaray.cents.domain.repository.reports.CategoryTrendRepository
import io.github.anderescaray.cents.domain.repository.reports.MonthlyFlowRepository
import io.github.anderescaray.cents.domain.repository.reports.PeriodComparisonRepository
import io.github.anderescaray.cents.domain.repository.reports.SpendingByCategoryRepository
import io.github.anderescaray.cents.domain.repository.reports.SpendingByWeekdayRepository
import java.time.Clock
import javax.inject.Singleton

/**
 * Which implementation answers each domain interface.
 *
 * Over detekt's function limit, and it will only grow: the file is one `@Binds` per repository
 * and nothing else, so splitting it would move lines between two files without making any of
 * them easier to read.
 */
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindAccountRepository(impl: RoomAccountRepository): AccountRepository

    @Binds
    abstract fun bindCaptureRecorder(impl: RoomCaptureRecorder): CaptureRecorder

    @Binds
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository

    @Binds
    abstract fun bindTransferRepository(impl: RoomTransferRepository): TransferRepository

    @Binds
    abstract fun bindBackupRepository(impl: RoomBackupRepository): BackupRepository

    @Binds
    abstract fun bindRecurringRuleRepository(impl: RoomRecurringRuleRepository): RecurringRuleRepository

    // One binding per view on the Reports tab: a new view adds one line here, and that is
    // the whole of its footprint on this file. See ui/feature/reports/ReportSection.kt.
    @Binds
    abstract fun bindSpendingByCategoryRepository(impl: RoomSpendingByCategoryRepository): SpendingByCategoryRepository

    @Binds
    abstract fun bindMonthlyFlowRepository(impl: RoomMonthlyFlowRepository): MonthlyFlowRepository

    @Binds
    abstract fun bindSpendingByWeekdayRepository(impl: RoomSpendingByWeekdayRepository): SpendingByWeekdayRepository

    @Binds
    abstract fun bindBalanceOverTimeRepository(impl: RoomBalanceOverTimeRepository): BalanceOverTimeRepository

    @Binds
    abstract fun bindPeriodComparisonRepository(impl: RoomPeriodComparisonRepository): PeriodComparisonRepository

    @Binds
    abstract fun bindCategoryTrendRepository(impl: RoomCategoryTrendRepository): CategoryTrendRepository

    @Binds
    abstract fun bindBudgetRepository(impl: RoomBudgetRepository): BudgetRepository

    @Binds
    abstract fun bindGroupRepository(impl: RoomGroupRepository): GroupRepository

    @Binds
    abstract fun bindBalanceRepository(impl: RoomBalanceRepository): BalanceRepository

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
