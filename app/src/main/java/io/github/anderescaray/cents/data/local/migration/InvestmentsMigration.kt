package io.github.anderescaray.cents.data.local.migration

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.usecase.ConvertedPortfolio
import io.github.anderescaray.cents.domain.usecase.LegacyHolding
import io.github.anderescaray.cents.domain.usecase.LegacyQuote
import io.github.anderescaray.cents.domain.usecase.LegacyTrade
import io.github.anderescaray.cents.domain.usecase.TradeCash
import io.github.anderescaray.cents.domain.usecase.convertLegacyPortfolio
import io.github.anderescaray.cents.domain.usecase.positionOf
import io.github.anderescaray.cents.domain.usecase.tradeCashOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * v15 (2026-11-01): investments as amounts and typed values (`DECISIONS.md` D40).
 *
 * The one migration that is more than DDL, because what it moves has to be worked out: units at a
 * price become amounts, the last price becomes a value, and a sale's cash splits into the cost it
 * returned and the result it made. That working-out is `convertLegacyPortfolio` and
 * `tradeCashOf`, the same functions a restore of an older backup and every later write use, so
 * the three cannot disagree about what a holding was.
 *
 * - `holdings` loses `symbol` and its unique index; `trades` trades units, price and fee for one
 *   amount and `sellsAll`. Both are rebuilt, keeping every id, so a movement's `tradeId` still
 *   points at its trade. Foreign keys are not enforced during a migration; Room checks them all
 *   once it is done.
 * - `holding_values` is new, filled from each holding's last price.
 * - `price_quotes` goes: a value belongs to an asset now, not to a symbol.
 * - `transactions.tradeResult` is new, and every trade's cash is written again: a sale's as two
 *   rows, the cost it returned and its result, with no balance moving.
 *
 * The DDL is copied from the exported `15.json`, as every migration's is (`Migrations.kt`).
 */
val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val converted = convertLegacyPortfolio(legacyHoldings(db), legacyTrades(db), legacyQuotes(db))
            createTables(db)
            writePortfolio(db, converted)
            db.execSQL("DROP TABLE `trades`")
            db.execSQL("DROP TABLE `holdings`")
            db.execSQL("DROP TABLE `price_quotes`")
            db.execSQL("ALTER TABLE `holdings_new` RENAME TO `holdings`")
            db.execSQL("ALTER TABLE `trades_new` RENAME TO `trades`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_holdings_accountId` ON `holdings` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trades_holdingId` ON `trades` (`holdingId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trades_holdingId_date` ON `trades` (`holdingId`, `date`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_holding_values_holdingId_date` " +
                    "ON `holding_values` (`holdingId`, `date`)",
            )
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tradeResult` INTEGER NOT NULL DEFAULT 0")
            rewriteTradeCash(db, converted)
        }
    }

private fun createTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `holdings_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`accountId` INTEGER NOT NULL, `assetClass` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`currency` TEXT NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `trades_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`holdingId` INTEGER NOT NULL, `side` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, " +
            "`sellsAll` INTEGER NOT NULL, `currency` TEXT NOT NULL, `date` INTEGER NOT NULL, `note` TEXT, " +
            "`createdAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`holdingId`) REFERENCES `holdings`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `holding_values` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`holdingId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `valueMinor` INTEGER NOT NULL, " +
            "`currency` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`holdingId`) REFERENCES `holdings`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
    )
}

private fun legacyHoldings(db: SupportSQLiteDatabase): List<LegacyHolding> =
    db.query("SELECT id, accountId, assetClass, symbol, name, currency, note FROM holdings ORDER BY id").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    LegacyHolding(
                        id = c.long("id"),
                        accountId = c.long("accountId"),
                        assetClass = c.string("assetClass"),
                        symbol = c.string("symbol"),
                        name = c.string("name"),
                        currency = c.string("currency"),
                        note = c.optionalString("note"),
                    ),
                )
            }
        }
    }

private fun legacyTrades(db: SupportSQLiteDatabase): List<LegacyTrade> =
    db
        .query(
            "SELECT id, holdingId, side, quantityScaled, pricePerUnitMinor, feeMinor, currency, date, note " +
                "FROM trades ORDER BY date, id",
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LegacyTrade(
                            id = c.long("id"),
                            holdingId = c.long("holdingId"),
                            side = c.string("side"),
                            quantityScaled = c.long("quantityScaled"),
                            pricePerUnitMinor = c.long("pricePerUnitMinor"),
                            feeMinor = c.long("feeMinor"),
                            currency = c.string("currency"),
                            date = LocalDate.ofEpochDay(c.long("date")),
                            note = c.optionalString("note"),
                        ),
                    )
                }
            }
        }

/** A quote's day is the phone's day it was true on, which is how the old screen showed it. */
private fun legacyQuotes(db: SupportSQLiteDatabase): List<LegacyQuote> =
    db.query("SELECT symbol, priceMinor, currency, asOf FROM price_quotes").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    LegacyQuote(
                        symbol = c.string("symbol"),
                        priceMinor = c.long("priceMinor"),
                        currency = c.string("currency"),
                        asOf = Instant.ofEpochMilli(c.long("asOf")).atZone(ZoneId.systemDefault()).toLocalDate(),
                    ),
                )
            }
        }
    }

private fun writePortfolio(
    db: SupportSQLiteDatabase,
    converted: ConvertedPortfolio,
) {
    val now = System.currentTimeMillis()
    converted.holdings.forEach { holding ->
        db.execSQL(
            "INSERT INTO `holdings_new` (id, accountId, assetClass, name, currency, note, createdAt, updatedAt) " +
                "SELECT id, accountId, ?, ?, currency, note, createdAt, updatedAt FROM holdings WHERE id = ?",
            arrayOf(holding.assetClass.name, holding.name, holding.id),
        )
    }
    converted.trades.forEach { trade ->
        db.execSQL(
            "INSERT INTO `trades_new` (id, holdingId, side, amountMinor, sellsAll, currency, date, note, createdAt) " +
                "SELECT id, holdingId, ?, ?, ?, currency, date, note, createdAt FROM trades WHERE id = ?",
            arrayOf(trade.side.name, trade.amount.minorUnits, if (trade.sellsAll) 1 else 0, trade.id),
        )
    }
    converted.values.forEach { value ->
        db.execSQL(
            "INSERT OR REPLACE INTO `holding_values` (holdingId, date, valueMinor, currency, createdAt) " +
                "VALUES (?, ?, ?, ?, ?)",
            arrayOf(value.holdingId, value.date.toEpochDay(), value.value.minorUnits, value.value.currency, now),
        )
    }
}

/**
 * Every trade's cash, written again the way `TradeCashWriter` writes it from now on: a purchase as
 * what it spent, a sale as the cost it returned and its result. The first row keeps the old row's
 * id and date of creation, so the movement does not move down the account's list.
 *
 * **No balance moves.** Where the new rows do not add up to what the old ones did — a purchase
 * recorded before v4, which never had a cash row, or a sale whose fee swallowed it — the account's
 * opening balance takes the difference, so its cash reads exactly what it read before.
 */
private fun rewriteTradeCash(
    db: SupportSQLiteDatabase,
    converted: ConvertedPortfolio,
) {
    val tradesByHolding = converted.trades.groupBy { it.holdingId }
    val valuesByHolding = converted.values.groupBy { it.holdingId }
    for (holding in converted.holdings) {
        val trades = tradesByHolding[holding.id].orEmpty()
        val cashByTrade =
            tradeCashOf(
                trades,
                positionOf(holding, trades, valuesByHolding[holding.id].orEmpty()),
            ).groupBy {
                it.trade.id
            }
        for (trade in trades.filter { it.amount.currency == holding.currency }) {
            val old = oldCash(db, trade.id)
            db.execSQL("DELETE FROM transactions WHERE tradeId = ?", arrayOf(trade.id))
            val rows = cashByTrade[trade.id].orEmpty()
            rows.forEachIndexed { index, cash ->
                db.insert(
                    "transactions",
                    SQLiteDatabase.CONFLICT_ABORT,
                    cashRow(
                        holding,
                        cash,
                        if (index ==
                            0
                        ) {
                            old
                        } else {
                            null
                        },
                        old?.createdAt,
                    ),
                )
            }
            val drift = (old?.signedSum ?: 0L) - rows.sumOf { it.signedMinor }
            if (drift != 0L) {
                db.execSQL(
                    "UPDATE accounts SET openingBalanceMinor = openingBalanceMinor + ? WHERE id = ?",
                    arrayOf(drift, holding.accountId),
                )
            }
        }
    }
}

private fun cashRow(
    holding: Holding,
    cash: TradeCash,
    keeping: OldCash?,
    createdAt: Long?,
) = ContentValues().apply {
    val at = createdAt ?: System.currentTimeMillis()
    if (keeping != null) put("id", keeping.id)
    put("accountId", holding.accountId)
    put("kind", if (cash.signedMinor < 0) "EXPENSE" else "INCOME")
    put("amountMinor", kotlin.math.abs(cash.signedMinor))
    put("currency", holding.currency)
    put("date", cash.trade.date.toEpochDay())
    put("note", cash.trade.note)
    put("tradeId", cash.trade.id)
    put("tradeResult", if (cash.isResult) 1 else 0)
    put("createdAt", at)
    put("updatedAt", at)
}

/** A trade's old cash: its first row, and what all its rows did to the balance. */
private class OldCash(
    val id: Long,
    val createdAt: Long,
    val signedSum: Long,
)

private fun oldCash(
    db: SupportSQLiteDatabase,
    tradeId: Long,
): OldCash? =
    db
        .query(
            "SELECT MIN(id) AS id, MIN(createdAt) AS createdAt, " +
                "SUM(CASE kind WHEN 'INCOME' THEN amountMinor ELSE -amountMinor END) AS signedSum, COUNT(*) AS n " +
                "FROM transactions WHERE tradeId = ?",
            arrayOf(tradeId),
        ).use { c ->
            if (c.moveToFirst() &&
                c.long("n") > 0
            ) {
                OldCash(c.long("id"), c.long("createdAt"), c.long("signedSum"))
            } else {
                null
            }
        }

private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

private fun Cursor.optionalString(column: String): String? =
    getColumnIndexOrThrow(column).let {
        if (isNull(it)) null else getString(it)
    }
