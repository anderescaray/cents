package io.github.anderescaray.cents.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change this database has ever had, in order.
 *
 * **The first migration in the project**, and the pattern the rest follow. Three rules, all of
 * them learned from other people's data loss rather than from this app's:
 *
 * 1. **The statements are written out, not generated.** Room compares the migrated database
 *    against the schema it expects and refuses to open one that differs by a single index, so
 *    the DDL below is copied from the exported `2.json` rather than typed from the entity
 *    classes. `CentsDatabaseMigrationTest` is what proves the copy is faithful.
 * 2. **A migration is never edited once it has shipped.** It has already run on the owner's
 *    phone; changing it changes only what *new* installs get, and the two silently diverge.
 *    A mistake is corrected by the next migration, in the open.
 * 3. **Nothing here is destructive.** `fallbackToDestructiveMigration` is deliberately absent
 *    from `DatabaseModule`, so a missing migration is a crash in development rather than an
 *    erased financial history on a real device.
 */
val ALL_MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2)

/**
 * v1 to v2 — the investments tables.
 *
 * Purely additive: three new tables and their indices, nothing touched on `accounts`,
 * `categories` or `transactions`. An install made before investments existed opens with an
 * empty portfolio, which is exactly what it has.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `holdings` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, " +
                    "`assetClass` TEXT NOT NULL, " +
                    "`symbol` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`currency` TEXT NOT NULL, " +
                    "`note` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_holdings_accountId` ON `holdings` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_holdings_symbol` ON `holdings` (`symbol`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_holdings_accountId_symbol` " +
                    "ON `holdings` (`accountId`, `symbol`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trades` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`holdingId` INTEGER NOT NULL, " +
                    "`side` TEXT NOT NULL, " +
                    "`quantityScaled` INTEGER NOT NULL, " +
                    "`pricePerUnitMinor` INTEGER NOT NULL, " +
                    "`feeMinor` INTEGER NOT NULL, " +
                    "`currency` TEXT NOT NULL, " +
                    "`date` INTEGER NOT NULL, " +
                    "`note` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`holdingId`) REFERENCES `holdings`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trades_holdingId` ON `trades` (`holdingId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trades_holdingId_date` ON `trades` (`holdingId`, `date`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `price_quotes` (" +
                    "`symbol` TEXT NOT NULL, " +
                    "`priceMinor` INTEGER NOT NULL, " +
                    "`currency` TEXT NOT NULL, " +
                    "`asOf` INTEGER NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "PRIMARY KEY(`symbol`))",
            )
        }
    }
