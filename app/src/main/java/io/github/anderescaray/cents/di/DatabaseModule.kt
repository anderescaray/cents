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
import io.github.anderescaray.cents.data.local.dao.CategoryDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
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
    ): CentsDatabase = Room.databaseBuilder(context, CentsDatabase::class.java, CentsDatabase.NAME).build()

    @Provides
    fun provideAccountDao(database: CentsDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideCategoryDao(database: CentsDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTransactionDao(database: CentsDatabase): TransactionDao = database.transactionDao()
}
