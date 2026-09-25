package io.github.anderescaray.cents.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.dao.AccountDao
import io.github.anderescaray.cents.data.local.dao.BudgetDao
import io.github.anderescaray.cents.data.local.dao.CategoryDao
import io.github.anderescaray.cents.data.local.dao.GroupDao
import io.github.anderescaray.cents.data.local.dao.HoldingDao
import io.github.anderescaray.cents.data.local.dao.PriceQuoteDao
import io.github.anderescaray.cents.data.local.dao.RecurringRuleDao
import io.github.anderescaray.cents.data.local.dao.RuleReminderDao
import io.github.anderescaray.cents.data.local.dao.TradeDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.data.local.migration.ALL_MIGRATIONS
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * No `fallbackToDestructiveMigration` on purpose: this database holds the only copy of
     * the user's financial history, so a missing migration must fail loudly in development
     * rather than quietly wipe the data on a real device.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CentsDatabase =
        Room
            .databaseBuilder(context, CentsDatabase::class.java, CentsDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideAccountDao(database: CentsDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideCategoryDao(database: CentsDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTransactionDao(database: CentsDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideHoldingDao(database: CentsDatabase): HoldingDao = database.holdingDao()

    @Provides
    fun provideTradeDao(database: CentsDatabase): TradeDao = database.tradeDao()

    @Provides
    fun providePriceQuoteDao(database: CentsDatabase): PriceQuoteDao = database.priceQuoteDao()

    @Provides
    fun provideRecurringRuleDao(database: CentsDatabase): RecurringRuleDao = database.recurringRuleDao()

    @Provides
    fun provideRuleReminderDao(database: CentsDatabase): RuleReminderDao = database.ruleReminderDao()

    @Provides
    fun provideBudgetDao(database: CentsDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideGroupDao(database: CentsDatabase): GroupDao = database.groupDao()
}
