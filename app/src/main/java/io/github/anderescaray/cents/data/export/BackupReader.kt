package io.github.anderescaray.cents.data.export

import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.model.BackupAccount
import io.github.anderescaray.cents.domain.model.BackupAdjustment
import io.github.anderescaray.cents.domain.model.BackupBudget
import io.github.anderescaray.cents.domain.model.BackupCategory
import io.github.anderescaray.cents.domain.model.BackupGroup
import io.github.anderescaray.cents.domain.model.BackupHolding
import io.github.anderescaray.cents.domain.model.BackupPostponement
import io.github.anderescaray.cents.domain.model.BackupQuote
import io.github.anderescaray.cents.domain.model.BackupReminder
import io.github.anderescaray.cents.domain.model.BackupRule
import io.github.anderescaray.cents.domain.model.BackupTrade
import io.github.anderescaray.cents.domain.model.BackupTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * What came back when a file was opened.
 *
 * A file the owner chose can be anything at all — the wrong file, a file from a newer version of
 * this app, a file that was half written when the phone died. Each of those is a different
 * sentence to say, and saying the right one is most of what makes a restore trustworthy: the
 * failure mode to design against is not a crash, it is a restore that half works.
 */
sealed interface BackupParse {
    /** A file this app can read, with everything that is in it. */
    data class Restorable(
        val backup: Backup,
    ) : BackupParse

    /** Not a Cents backup at all — the wrong file, most likely. */
    data object NotABackup : BackupParse

    /**
     * Written by a later version of the app than this one.
     *
     * Refused rather than read as far as it goes. A newer file may hold tables and columns this
     * version has never heard of, and restoring the parts it recognises would produce something
     * that looks complete and has quietly lost the rest.
     */
    data class TooNew(
        val fileVersion: Int,
        val appVersion: Int,
    ) : BackupParse

    /** The right shape, but something inside it is not readable. */
    data class Damaged(
        val detail: String,
    ) : BackupParse
}

/**
 * Reads a backup file back into something the app can restore from.
 *
 * The mirror of [BackupWriter], and written by hand for the same reason: the file format is a
 * deliberate decision rather than whatever the class names happen to be, and the round trip is
 * pinned by a test that writes a backup and reads it back rather than by two annotations trusting
 * each other.
 *
 * **Older files are read; newer ones are refused.** A v2 backup has no `transferId` because
 * transfers did not exist when it was written, and a field that is absent reads as null — which
 * is exactly what it means. A v5 file might have a table this version cannot see, so it is turned
 * away rather than partly understood.
 *
 * @suppress TooManyFunctions — one small function per table, mirroring [BackupWriter] for the
 * same reason: each says what one row of the file becomes and nothing else, and merging them is
 * what the length rule objects to first.
 */
@Suppress("TooManyFunctions")
object BackupReader {
    /**
     * @param appSchemaVersion the version this app writes, which is the highest it can read.
     */
    fun parse(
        text: String,
        appSchemaVersion: Int,
    ): BackupParse {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        val version =
            root
                ?.get("schemaVersion")
                ?.jsonPrimitive
                ?.longOrNull
                ?.toInt()
        return when {
            // Both marks of a Cents backup have to be there. A stray JSON file that happens to
            // carry a `schemaVersion` is still not this.
            root == null || version == null || root["accounts"] !is JsonArray -> {
                BackupParse.NotABackup
            }

            version > appSchemaVersion -> {
                BackupParse.TooNew(version, appSchemaVersion)
            }

            else -> {
                runCatching { BackupParse.Restorable(read(root, version)) }
                    .getOrElse { failure -> BackupParse.Damaged(failure.message ?: "unreadable") }
            }
        }
    }

    private fun read(
        root: JsonObject,
        version: Int,
    ): Backup =
        Backup(
            schemaVersion = version,
            exportedAt =
                root.string("exportedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?: Instant.EPOCH,
            accounts = root.rows("accounts", ::account),
            categories = root.rows("categories", ::category),
            transactions = root.rows("transactions", ::transaction),
            holdings = root.rows("holdings", ::holding),
            trades = root.rows("trades", ::trade),
            quotes = root.rows("quotes", ::quote),
            // Absent before a backup carried subscriptions, and read as none.
            rules = root.rows("recurringRules", ::rule),
            reminders = root.rows("ruleReminders", ::reminder),
            budgets = root.rows("budgets", ::budget),
            groups = root.rows("groups", ::group),
            postponements = root.rows("rulePostponements", ::postponement),
            // Absent before a balance could be corrected, and read as none.
            adjustments = root.rows("balanceAdjustments", ::adjustment),
        )

    private fun account(row: JsonObject) =
        BackupAccount(
            id = row.requireLong("id"),
            name = row.requireString("name"),
            type = row.requireString("type"),
            currency = row.requireString("currency"),
            openingBalanceMinor = row.requireLong("openingBalanceMinor"),
            colorArgb = row.requireLong("colorArgb").toInt(),
            archived = row.boolean("archived"),
            sortOrder = row.requireLong("sortOrder").toInt(),
        )

    private fun category(row: JsonObject) =
        BackupCategory(
            id = row.requireLong("id"),
            name = row.requireString("name"),
            kind = row.requireString("kind"),
            colorArgb = row.requireLong("colorArgb").toInt(),
            parentId = row.long("parentId"),
            archived = row.boolean("archived"),
            sortOrder = row.requireLong("sortOrder").toInt(),
        )

    private fun transaction(row: JsonObject) =
        BackupTransaction(
            id = row.requireLong("id"),
            accountId = row.requireLong("accountId"),
            categoryId = row.long("categoryId"),
            kind = row.requireString("kind"),
            amountMinor = row.requireLong("amountMinor"),
            currency = row.requireString("currency"),
            date = row.requireLong("date"),
            note = row.string("note"),
            // Absent in a file written before transfers or trades existed, which is what a
            // null means here anyway: this movement is half of nothing.
            transferId = row.long("transferId"),
            tradeId = row.long("tradeId"),
            ruleId = row.long("ruleId"),
            groupId = row.long("groupId"),
        )

    private fun holding(row: JsonObject) =
        BackupHolding(
            id = row.requireLong("id"),
            accountId = row.requireLong("accountId"),
            assetClass = row.requireString("assetClass"),
            symbol = row.requireString("symbol"),
            name = row.requireString("name"),
            currency = row.requireString("currency"),
            note = row.string("note"),
        )

    private fun trade(row: JsonObject) =
        BackupTrade(
            id = row.requireLong("id"),
            holdingId = row.requireLong("holdingId"),
            side = row.requireString("side"),
            quantityScaled = row.requireLong("quantityScaled"),
            pricePerUnitMinor = row.requireLong("pricePerUnitMinor"),
            feeMinor = row.requireLong("feeMinor"),
            currency = row.requireString("currency"),
            date = row.requireLong("date"),
            note = row.string("note"),
        )

    private fun rule(row: JsonObject) =
        BackupRule(
            id = row.requireLong("id"),
            name = row.requireString("name"),
            accountId = row.requireLong("accountId"),
            categoryId = row.long("categoryId"),
            kind = row.requireString("kind"),
            amountMinor = row.requireLong("amountMinor"),
            currency = row.requireString("currency"),
            frequency = row.requireString("frequency"),
            intervalDays = row.long("intervalDays")?.toInt(),
            startDate = row.requireLong("startDate"),
            endDate = row.long("endDate"),
            mode = row.requireString("mode"),
            active = row.boolean("active"),
            lastGenerated = row.long("lastGenerated"),
        )

    private fun reminder(row: JsonObject) =
        BackupReminder(
            ruleId = row.requireLong("ruleId"),
            offsetDays = row.requireLong("offsetDays").toInt(),
        )

    private fun postponement(row: JsonObject) =
        BackupPostponement(
            ruleId = row.requireLong("ruleId"),
            occurrence = row.requireLong("occurrence"),
            postponedTo = row.requireLong("postponedTo"),
        )

    private fun adjustment(row: JsonObject) =
        BackupAdjustment(
            accountId = row.requireLong("accountId"),
            amountMinor = row.requireLong("amountMinor"),
            currency = row.requireString("currency"),
            date = row.requireLong("date"),
        )

    private fun budget(row: JsonObject) =
        BackupBudget(
            categoryId = row.requireLong("categoryId"),
            amountMinor = row.requireLong("amountMinor"),
            currency = row.requireString("currency"),
        )

    private fun group(row: JsonObject) =
        BackupGroup(
            id = row.requireLong("id"),
            name = row.requireString("name"),
            startDate = row.long("startDate"),
            endDate = row.long("endDate"),
        )

    private fun quote(row: JsonObject) =
        BackupQuote(
            symbol = row.requireString("symbol"),
            priceMinor = row.requireLong("priceMinor"),
            currency = row.requireString("currency"),
            asOf = row.requireLong("asOf"),
            source = row.requireString("source"),
        )

    private fun <T> JsonObject.rows(
        name: String,
        row: (JsonObject) -> T,
    ): List<T> = (this[name] as? JsonArray).orEmpty().map { row(it as JsonObject) }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

    /**
     * A field that has to be there.
     *
     * It throws, and [parse] turns that into [BackupParse.Damaged] naming the field. A restore
     * that silently substituted a zero for a missing amount would be the failure this whole
     * class exists to prevent — money that is quietly wrong is worse than a file that is
     * honestly refused.
     */
    private fun JsonObject.requireLong(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull ?: error("$name is missing or is not a number")

    private fun JsonObject.requireString(name: String): String = string(name) ?: error("$name is missing")

    private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.takeUnless { it.toString() == "null" }?.content

    private fun JsonObject.boolean(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false
}
