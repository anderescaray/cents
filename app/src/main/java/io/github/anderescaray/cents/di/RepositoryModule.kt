package io.github.anderescaray.cents.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomReportsRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.ReportsRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
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

    @Binds
    abstract fun bindReportsRepository(impl: RoomReportsRepository): ReportsRepository

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
