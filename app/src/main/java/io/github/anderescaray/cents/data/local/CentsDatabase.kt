package io.github.anderescaray.cents.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.anderescaray.cents.data.local.converter.Converters
import io.github.anderescaray.cents.data.local.converter.InvestmentConverters
import io.github.anderescaray.cents.data.local.dao.AccountDao
import io.github.anderescaray.cents.data.local.dao.CategoryDao
import io.github.anderescaray.cents.data.local.dao.HoldingDao
import io.github.anderescaray.cents.data.local.dao.PriceQuoteDao
import io.github.anderescaray.cents.data.local.dao.RecurringRuleDao
import io.github.anderescaray.cents.data.local.dao.TradeDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.data.local.entity.AccountEntity
import io.github.anderescaray.cents.data.local.entity.CategoryEntity
import io.github.anderescaray.cents.data.local.entity.HoldingEntity
import io.github.anderescaray.cents.data.local.entity.PriceQuoteEntity
import io.github.anderescaray.cents.data.local.entity.RecurringRuleEntity
import io.github.anderescaray.cents.data.local.entity.TradeEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        HoldingEntity::class,
        TradeEntity::class,
        PriceQuoteEntity::class,
        RecurringRuleEntity::class,
    ],
    version = CentsDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class, InvestmentConverters::class)
abstract class CentsDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun holdingDao(): HoldingDao

    abstract fun tradeDao(): TradeDao

    abstract fun priceQuoteDao(): PriceQuoteDao

    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        /**
         * v2 (2026-09-09) added holdings, trades and price quotes; v3 (2026-09-16) added
         * `transactions.transferId`, which pairs the two legs of a transfer; v4 (2026-09-16)
         * added `transactions.tradeId`, which ties a purchase to the cash it took out of the
         * account; v5 (2026-09-17) added `recurring_rules` and `transactions.ruleId`. See
         * `Migrations.kt`.
         */
        const val VERSION = 5
        const val NAME = "cents.db"
    }
}
