package io.github.anderescaray.cents.data.export

import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.model.BackupAccount
import io.github.anderescaray.cents.domain.model.BackupAdjustment
import io.github.anderescaray.cents.domain.model.BackupBudget
import io.github.anderescaray.cents.domain.model.BackupCaptureMatch
import io.github.anderescaray.cents.domain.model.BackupCategory
import io.github.anderescaray.cents.domain.model.BackupGroup
import io.github.anderescaray.cents.domain.model.BackupHolding
import io.github.anderescaray.cents.domain.model.BackupHoldingValue
import io.github.anderescaray.cents.domain.model.BackupPostponement
import io.github.anderescaray.cents.domain.model.BackupReminder
import io.github.anderescaray.cents.domain.model.BackupRule
import io.github.anderescaray.cents.domain.model.BackupTrade
import io.github.anderescaray.cents.domain.model.BackupTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * What an export is written as.
 *
 * Two formats because they answer two different questions (PRD §4.7). **JSON** is the whole
 * app in one file, so nothing is locked in and `feat/backup` has something to send somewhere;
 * **CSV** is the movements, so they can be opened in a spreadsheet by somebody who does not
 * care what a holding is.
 */
enum class ExportFormat(
    val extension: String,
    val mimeType: String,
) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
}

/**
 * Turns the app's contents into the text of a file.
 *
 * **Pure, and that is the point.** Writing the bytes needs a `Uri`, a `ContentResolver` and a
 * file picker, none of which can be reasoned about in a test; deciding *what the file says* is
 * the part that can be wrong in ways nobody notices until they need the backup. So the decision
 * lives here, with no Android in it, and the shell around it only moves the string.
 *
 * @suppress TooManyFunctions — there is one small function per table, which is the shape that
 * makes the file format readable: each says what one row becomes and nothing else. Merging them
 * back into `toJson` is what the length rule objected to first, so the two rules pull opposite
 * ways here and this is the direction worth going.
 */
@Suppress("TooManyFunctions")
object BackupWriter {
    /**
     * The whole app as JSON, laid out so a person can read it.
     *
     * Pretty-printed on purpose: a backup is opened by a human far more often than by a parser
     * — usually a worried one, checking that their money is really in there — and the file is
     * small enough that the whitespace costs nothing worth having.
     *
     * The tree is built by hand rather than by annotating the model with `@Serializable`. That
     * keeps the file format a deliberate decision in one readable place instead of a side
     * effect of how the classes happen to be named today: renaming a field then breaks a test
     * here rather than silently writing a backup that last year's app cannot read.
     */
    fun toJson(backup: Backup): String {
        val root =
            JsonObject(
                mapOf(
                    "schemaVersion" to JsonPrimitive(backup.schemaVersion),
                    "exportedAt" to JsonPrimitive(backup.exportedAt.toString()),
                    "accounts" to backup.accounts.toArray(::accountJson),
                    "categories" to backup.categories.toArray(::categoryJson),
                    "transactions" to backup.transactions.toArray(::transactionJson),
                    "holdings" to backup.holdings.toArray(::holdingJson),
                    "trades" to backup.trades.toArray(::tradeJson),
                    "holdingValues" to backup.values.toArray(::valueJson),
                    "recurringRules" to backup.rules.toArray(::ruleJson),
                    "ruleReminders" to backup.reminders.toArray(::reminderJson),
                    "budgets" to backup.budgets.toArray(::budgetJson),
                    "groups" to backup.groups.toArray(::groupJson),
                    "rulePostponements" to backup.postponements.toArray(::postponementJson),
                    "balanceAdjustments" to backup.adjustments.toArray(::adjustmentJson),
                    "captureMatches" to backup.captureMatches.toArray(::captureMatchJson),
                ),
            )
        return PRETTY.encodeToString(JsonElement.serializer(), root)
    }

    private fun accountJson(row: BackupAccount) =
        mapOf(
            "id" to JsonPrimitive(row.id),
            "name" to JsonPrimitive(row.name),
            "type" to JsonPrimitive(row.type),
            "currency" to JsonPrimitive(row.currency),
            "openingBalanceMinor" to JsonPrimitive(row.openingBalanceMinor),
            "colorArgb" to JsonPrimitive(row.colorArgb),
            "archived" to JsonPrimitive(row.archived),
            "sortOrder" to JsonPrimitive(row.sortOrder),
        )

    private fun categoryJson(row: BackupCategory) =
        mapOf(
            "id" to JsonPrimitive(row.id),
            "name" to JsonPrimitive(row.name),
            "kind" to JsonPrimitive(row.kind),
            "colorArgb" to JsonPrimitive(row.colorArgb),
            "parentId" to row.parentId.orNull(),
            "archived" to JsonPrimitive(row.archived),
            "sortOrder" to JsonPrimitive(row.sortOrder),
            "emoji" to row.emoji.orNull(),
        )

    private fun transactionJson(row: BackupTransaction) =
        mapOf(
            "id" to JsonPrimitive(row.id),
            "accountId" to JsonPrimitive(row.accountId),
            "categoryId" to row.categoryId.orNull(),
            "kind" to JsonPrimitive(row.kind),
            "amountMinor" to JsonPrimitive(row.amountMinor),
            "currency" to JsonPrimitive(row.currency),
            "date" to JsonPrimitive(row.date),
            "note" to row.note.orNull(),
            // What ties a row to something bigger travels with it: without these, a restored
            // transfer is an unexplained expense beside an unexplained income.
            "transferId" to row.transferId.orNull(),
            "tradeId" to row.tradeId.orNull(),
            "ruleId" to row.ruleId.orNull(),
            "groupId" to row.groupId.orNull(),
            "tradeResult" to JsonPrimitive(row.tradeResult),
        )

    private fun holdingJson(row: BackupHolding) =
        mapOf(
            "id" to JsonPrimitive(row.id),
            "accountId" to JsonPrimitive(row.accountId),
            "assetClass" to JsonPrimitive(row.assetClass),
            "name" to JsonPrimitive(row.name),
            "currency" to JsonPrimitive(row.currency),
            "note" to row.note.orNull(),
        )

    private fun tradeJson(row: BackupTrade) =
        mapOf(
            "id" to JsonPrimitive(row.id),
            "holdingId" to JsonPrimitive(row.holdingId),
            "side" to JsonPrimitive(row.side),
            "amountMinor" to JsonPrimitive(row.amountMinor),
            "sellsAll" to JsonPrimitive(row.sellsAll),
            "currency" to JsonPrimitive(row.currency),
            "date" to JsonPrimitive(row.date),
            "note" to row.note.orNull(),
        )

    private fun ruleJson(row: BackupRule) =
        mapOf(
            "id" to JsonPrimitive(row.id),
            "name" to JsonPrimitive(row.name),
            "accountId" to JsonPrimitive(row.accountId),
            "categoryId" to row.categoryId.orNull(),
            "kind" to JsonPrimitive(row.kind),
            "amountMinor" to JsonPrimitive(row.amountMinor),
            "currency" to JsonPrimitive(row.currency),
            "frequency" to JsonPrimitive(row.frequency),
            "intervalDays" to row.intervalDays?.toLong().orNull(),
            "startDate" to JsonPrimitive(row.startDate),
            "endDate" to row.endDate.orNull(),
            "mode" to JsonPrimitive(row.mode),
            "active" to JsonPrimitive(row.active),
            "lastGenerated" to row.lastGenerated.orNull(),
        )

    private fun reminderJson(row: BackupReminder) =
        mapOf(
            "ruleId" to JsonPrimitive(row.ruleId),
            "offsetDays" to JsonPrimitive(row.offsetDays),
        )

    private fun postponementJson(row: BackupPostponement) =
        mapOf(
            "ruleId" to JsonPrimitive(row.ruleId),
            "occurrence" to JsonPrimitive(row.occurrence),
            "postponedTo" to JsonPrimitive(row.postponedTo),
        )

    private fun adjustmentJson(row: BackupAdjustment) =
        mapOf(
            "accountId" to JsonPrimitive(row.accountId),
            "amountMinor" to JsonPrimitive(row.amountMinor),
            "currency" to JsonPrimitive(row.currency),
            "date" to JsonPrimitive(row.date),
        )

    private fun captureMatchJson(row: BackupCaptureMatch) =
        mapOf(
            "id" to JsonPrimitive(row.id),
            "chargeTransactionId" to JsonPrimitive(row.chargeTransactionId),
            "wroteCharge" to JsonPrimitive(row.wroteCharge),
            "ruleId" to JsonPrimitive(row.ruleId),
            "occurrence" to JsonPrimitive(row.occurrence),
            "previousLastGenerated" to JsonPrimitive(row.previousLastGenerated),
            "postponedTo" to JsonPrimitive(row.postponedTo),
            "accountId" to JsonPrimitive(row.accountId),
            "kind" to JsonPrimitive(row.kind),
            "amountMinor" to JsonPrimitive(row.amountMinor),
            "currency" to JsonPrimitive(row.currency),
            "date" to JsonPrimitive(row.date),
            "note" to JsonPrimitive(row.note),
        )

    private fun budgetJson(row: BackupBudget) =
        mapOf(
            "categoryId" to row.categoryId.orNull(),
            "amountMinor" to JsonPrimitive(row.amountMinor),
            "currency" to JsonPrimitive(row.currency),
        )

    private fun groupJson(row: BackupGroup) =
        mapOf(
            "id" to JsonPrimitive(row.id),
            "name" to JsonPrimitive(row.name),
            "startDate" to row.startDate.orNull(),
            "endDate" to row.endDate.orNull(),
            "archived" to JsonPrimitive(row.archived),
        )

    private fun valueJson(row: BackupHoldingValue) =
        mapOf(
            "holdingId" to JsonPrimitive(row.holdingId),
            "date" to JsonPrimitive(row.date),
            "valueMinor" to JsonPrimitive(row.valueMinor),
            "currency" to JsonPrimitive(row.currency),
        )

    /**
     * The movements as CSV, for a spreadsheet.
     *
     * **Amounts are written in major units with a dot**, not in cents and not in the device's
     * locale. Cents would be unreadable to the spreadsheet and to the person; a comma would be
     * a column separator in half of Europe. This is the one place in the app where a number
     * leaves without going through the locale, and it is deliberate: the file is being handed
     * to another program, not to a reader.
     *
     * The ids of transfers and trades travel with each row, so a movement that is half of
     * something bigger can still be told apart from money genuinely earned or spent — the same
     * distinction every report in the app makes. The group, when there is one, goes by name,
     * which is what a pivot table on a trip wants.
     */
    fun toCsv(backup: Backup): String {
        val accounts = backup.accounts.associate { it.id to it.name }
        val categories = backup.categories.associate { it.id to it.name }
        val groups = backup.groups.associate { it.id to it.name }
        return buildString {
            appendLine(CSV_HEADER)
            backup.transactions.forEach { row ->
                appendLine(
                    listOf(
                        LocalDate.ofEpochDay(row.date).format(DateTimeFormatter.ISO_LOCAL_DATE),
                        accounts[row.accountId].orEmpty(),
                        categories[row.categoryId].orEmpty(),
                        row.kind,
                        majorUnits(row.amountMinor),
                        row.currency,
                        row.note.orEmpty(),
                        row.transferId?.toString().orEmpty(),
                        row.tradeId?.toString().orEmpty(),
                        // Last, so a spreadsheet built on the columns before it still lines up.
                        groups[row.groupId].orEmpty(),
                    ).joinToString(",") { it.csvEscaped() },
                )
            }
        }
    }

    /** A suggested file name, which the picker offers and the owner may change. */
    fun fileName(
        format: ExportFormat,
        exportedAt: java.time.Instant,
    ): String {
        val day = exportedAt.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return "cents-$day.${format.extension}"
    }

    /**
     * Cents as a decimal string: 123456 becomes "1234.56", and a negative one keeps its sign.
     *
     * Done with integers rather than by dividing, because the moment this becomes a `Double`
     * the app has a rounding rule it did not choose (rule 1) — in the one file whose whole
     * purpose is to be exactly what was recorded.
     */
    private fun majorUnits(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val absolute = if (minor < 0) -minor else minor
        return "$sign${absolute / MINOR_UNITS}.${(absolute % MINOR_UNITS).toString().padStart(2, '0')}"
    }

    /**
     * RFC 4180, which is the whole reason this is not `joinToString` on its own: a note
     * containing a comma, a quote or a newline has to survive the round trip, and a category
     * called `Food, drink` must not become two columns.
     */
    private fun String.csvEscaped(): String = if (any { it in CSV_SPECIAL }) "\"${replace("\"", "\"\"")}\"" else this

    private fun <T> List<T>.toArray(row: (T) -> Map<String, JsonElement>): JsonArray =
        JsonArray(map { JsonObject(row(it)) })

    private fun Long?.orNull(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull

    private fun String?.orNull(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull

    /** The characters that make a field mean something other than itself. */
    private val CSV_SPECIAL = charArrayOf(',', '"', '\n', '\r')

    private const val MINOR_UNITS = 100

    private const val CSV_HEADER = "date,account,category,kind,amount,currency,note,transferId,tradeId,group"

    private val PRETTY = Json { prettyPrint = true }
}
