package io.github.anderescaray.cents.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change this database has ever had, in order.
 *
 * Three rules, all of them learned from other people's data loss rather than from this app's:
 *
 * 1. **The statements are written out, not generated.** Room compares the migrated database
 *    against the schema it expects and refuses to open one that differs by a single index, so
 *    the DDL below is copied from the exported schema of the version each migration produces,
 *    rather than typed from the entity classes. `CentsDatabaseMigrationTest` is what proves
 *    the copy is faithful, and it starts each one from the schema that really shipped.
 * 2. **A migration is never edited once it has shipped.** It has already run on the owner's
 *    phone; changing it changes only what *new* installs get, and the two silently diverge.
 *    A mistake is corrected by the next migration, in the open.
 * 3. **Nothing here is destructive.** `fallbackToDestructiveMigration` is deliberately absent
 *    from `DatabaseModule`, so a missing migration is a crash in development rather than an
 *    erased financial history on a real device.
 */
val ALL_MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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

/**
 * v2 to v3 — the column that pairs the two legs of a transfer.
 *
 * Additive as well, and deliberately the smallest change that could carry transfers: one
 * nullable column and its index. Every movement recorded before transfers existed gets
 * `NULL`, which is exactly what it is — an ordinary income or expense, and not half of
 * anything.
 *
 * `ALTER TABLE ... ADD COLUMN` is the one schema change SQLite performs in place, so nothing
 * here copies the ledger into a new table. The index matters more than it looks: the list
 * resolves each transfer leg's counterpart account with a lookup by `transferId`, and without
 * it that is a scan of every movement ever recorded, once per visible row.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transferId` INTEGER")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_transferId` ON `transactions` (`transferId`)",
            )
        }
    }

/**
 * v3 to v4 — the column that ties a movement to the trade it paid for.
 *
 * Additive, like the two before it: one nullable column and its index. Every movement recorded
 * before this got `NULL`, which is what it is — money that really was spent or earned, rather
 * than money that changed shape.
 *
 * **The foreign key is not added here, and it is not missing either.** SQLite cannot add a
 * constraint to an existing table, so `ALTER TABLE ... ADD COLUMN ... REFERENCES` is the only
 * form it accepts, and Room compares what it finds against what the entity declares. The column
 * is therefore declared with its reference inline, exactly as the exported `4.json` spells it —
 * which is what `CentsDatabaseMigrationTest` proves by opening the result.
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `tradeId` INTEGER " +
                    "REFERENCES `trades`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_tradeId` ON `transactions` (`tradeId`)",
            )
        }
    }
