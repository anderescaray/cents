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
val ALL_MIGRATIONS: Array<Migration>
    get() =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
        )

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

/**
 * v4 to v5 — the recurring rules, and the column that points back at them.
 *
 * One new table and one new column, still additive. Every movement recorded before this gets a
 * null `ruleId`, which is what it is: something the owner typed rather than something a template
 * produced.
 *
 * **The column is `SET NULL`, and that is rule 8 in the schema.** A rule that is genuinely
 * deleted leaves its movements standing with nothing pointing at the template any more. SQLite
 * only accepts a constraint on an existing table through `ALTER TABLE ... ADD COLUMN`, so the
 * reference is declared inline exactly as the exported `5.json` spells it.
 */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_rules` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, " +
                    "`categoryId` INTEGER, " +
                    "`kind` TEXT NOT NULL, " +
                    "`amountMinor` INTEGER NOT NULL, " +
                    "`currency` TEXT NOT NULL, " +
                    "`frequency` TEXT NOT NULL, " +
                    "`intervalDays` INTEGER, " +
                    "`startDate` INTEGER NOT NULL, " +
                    "`endDate` INTEGER, " +
                    "`mode` TEXT NOT NULL, " +
                    "`active` INTEGER NOT NULL, " +
                    "`lastGenerated` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_accountId` " +
                    "ON `recurring_rules` (`accountId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_categoryId` " +
                    "ON `recurring_rules` (`categoryId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_active` " +
                    "ON `recurring_rules` (`active`)",
            )

            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `ruleId` INTEGER " +
                    "REFERENCES `recurring_rules`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_ruleId` ON `transactions` (`ruleId`)",
            )
        }
    }

/**
 * v5 to v6 — the reminders a subscription can carry.
 *
 * One new table, additive like every migration before it. Every rule that existed before this
 * has no reminders, which is what it had: nothing has ever notified anybody.
 *
 * **`CASCADE` here, where every other key in this database is `RESTRICT` or `SET NULL`.** A
 * reminder is configuration rather than history — it records nothing that happened — so a rule
 * being deleted should take its reminders with it. Rule 8 is about movements, and no movement
 * points at this table.
 */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `rule_reminders` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`ruleId` INTEGER NOT NULL, " +
                    "`offsetDays` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`ruleId`) REFERENCES `recurring_rules`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_rule_reminders_ruleId` ON `rule_reminders` (`ruleId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_rule_reminders_ruleId_offsetDays` " +
                    "ON `rule_reminders` (`ruleId`, `offsetDays`)",
            )
        }
    }

/**
 * v6 to v7 — somewhere to keep a monthly limit per expense category (PRD §4.4).
 *
 * Purely additive, like every migration before it: one table and its index. An install made
 * before budgets existed opens with none, which is exactly what it has.
 */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `budgets` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`categoryId` INTEGER NOT NULL, " +
                    "`amountMinor` INTEGER NOT NULL, " +
                    "`currency` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)",
            )
        }
    }

/**
 * v7 to v8 — groups: what an occasion cost or earned, across categories (PRD §4.9).
 *
 * Additive, like every migration before it: one table, and one nullable column on `transactions`
 * with its index. Every movement recorded before this belongs to no group, which is what it did.
 *
 * The column arrives by `ALTER TABLE` with its foreign key, exactly as `ruleId` did in v5 —
 * SQLite accepts a `REFERENCES` clause on an added column whose default is null — and it is
 * `SET NULL` for the same reason: deleting a group must never take a movement with it (rule 8's
 * spirit, applied to something far less important than a subscription).
 */
val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `movement_groups` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`startDate` INTEGER, " +
                    "`endDate` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `groupId` INTEGER " +
                    "REFERENCES `movement_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_groupId` ON `transactions` (`groupId`)",
            )
        }
    }
