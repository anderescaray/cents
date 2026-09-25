package io.github.anderescaray.cents.data.export

import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.model.BackupAccount
import io.github.anderescaray.cents.domain.model.BackupCategory
import io.github.anderescaray.cents.domain.model.BackupHolding
import io.github.anderescaray.cents.domain.model.BackupQuote
import io.github.anderescaray.cents.domain.model.BackupTrade
import io.github.anderescaray.cents.domain.model.BackupTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * What the file actually says.
 *
 * This is the part of an export that can be wrong for months without anybody noticing: a file
 * is written, it looks like a file, and the day it is needed a note turns out to have eaten a
 * column or an amount to be a hundred times too small. So the format is pinned here, value by
 * value, rather than being compared against another rendering of itself.
 */
class BackupWriterTest {
    @Test
    fun `the backup says which schema wrote it`() {
        val json = Json.parseToJsonElement(BackupWriter.toJson(backup())).jsonObject

        // The one field that makes the file survivable: whatever reads it next can refuse what
        // it cannot understand instead of guessing, and guessing is what loses money.
        assertEquals(
            4,
            json
                .getValue("schemaVersion")
                .jsonPrimitive.content
                .toInt(),
        )
        assertEquals("2026-09-16T18:00:00Z", json.getValue("exportedAt").jsonPrimitive.content)
    }

    @Test
    fun `every table is in it, holdings and trades included`() {
        val json = Json.parseToJsonElement(BackupWriter.toJson(backup())).jsonObject

        // "So I am never locked into the app" (PRD §4.7) is only true if nothing is left out.
        assertEquals(
            setOf(
                "schemaVersion",
                "exportedAt",
                "accounts",
                "categories",
                "transactions",
                "holdings",
                "trades",
                "quotes",
                "recurringRules",
                "ruleReminders",
                "budgets",
            ),
            json.keys,
        )
        assertEquals(1, json.getValue("accounts").jsonArray.size)
        assertEquals(1, json.getValue("holdings").jsonArray.size)
        assertEquals(1, json.getValue("trades").jsonArray.size)
        assertEquals(1, json.getValue("quotes").jsonArray.size)
    }

    @Test
    fun `amounts are written as the exact integers they are stored as`() {
        val json = Json.parseToJsonElement(BackupWriter.toJson(backup())).jsonObject
        val movement =
            json
                .getValue("transactions")
                .jsonArray
                .first()
                .jsonObject

        // Minor units, not euros, and not a formatted string: a backup is read by whatever
        // reads it next, and a number that has been through a formatter is a number somebody
        // has to unformat (rule 1).
        assertEquals("2550", movement.getValue("amountMinor").jsonPrimitive.content)
        assertEquals(LocalDate.of(2026, 9, 16).toEpochDay().toString(), movement.getValue("date").jsonPrimitive.content)
        assertEquals(
            "250000000",
            json
                .getValue("trades")
                .jsonArray
                .first()
                .jsonObject
                .getValue("quantityScaled")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `what is absent is null rather than missing or empty`() {
        val json = Json.parseToJsonElement(BackupWriter.toJson(uncategorized())).jsonObject
        val movement =
            json
                .getValue("transactions")
                .jsonArray
                .first()
                .jsonObject

        // An uncategorized movement is not a movement in a category called "". The distinction
        // is the whole of what `categoryId IS NULL` means to every report in the app.
        assertEquals(JsonNull, movement.getValue("categoryId") as JsonElement)
        assertEquals(JsonNull, movement.getValue("note") as JsonElement)
        assertTrue(movement.containsKey("transferId"))
    }

    @Test
    fun `the csv has one row per movement, under a header`() {
        val lines = BackupWriter.toCsv(backup()).trim().lines()

        assertEquals("date,account,category,kind,amount,currency,note,transferId,tradeId,group", lines.first())
        assertEquals(2, lines.size)
        assertEquals(
            "2026-09-16,Checking,Groceries,EXPENSE,25.50,EUR,Weekly shop,,,",
            lines[1],
        )
    }

    @Test
    fun `csv amounts are euros with a dot, whatever the phone's language is`() {
        val csv = BackupWriter.toCsv(backup(amountMinor = 123_456))

        // A comma here would be a column separator in half of Europe, and cents would be
        // unreadable to the spreadsheet and to the person. It is the one number in this app
        // that deliberately ignores the locale, because it is being handed to another program.
        assertTrue(csv, csv.contains(",1234.56,"))
    }

    @Test
    fun `a rounded number keeps both its decimals`() {
        assertTrue(BackupWriter.toCsv(backup(amountMinor = 500)).contains(",5.00,"))
        assertTrue(BackupWriter.toCsv(backup(amountMinor = 5)).contains(",0.05,"))
        assertTrue(BackupWriter.toCsv(backup(amountMinor = -250)).contains(",-2.50,"))
    }

    @Test
    fun `a note with a comma, a quote or a newline survives the file`() {
        val csv = BackupWriter.toCsv(backup(note = "Food, \"drink\"\nand rent"))

        // Without this a category called "Food, drink" becomes two columns, and the file is
        // silently a different file from the one the owner thinks they have.
        assertTrue(csv, csv.contains("\"Food, \"\"drink\"\"\nand rent\""))
        val header = csv.lines().first()
        assertEquals(9, header.split(",").size)
    }

    @Test
    fun `an empty app still exports a file that says so`() {
        val empty =
            Backup(
                schemaVersion = 4,
                exportedAt = WHEN,
                accounts = emptyList(),
                categories = emptyList(),
                transactions = emptyList(),
                holdings = emptyList(),
                trades = emptyList(),
                quotes = emptyList(),
            )

        // A fresh install has nothing in it, and exporting it must produce a valid file rather
        // than an error: "there is nothing yet" is a true thing for a backup to say.
        val json = Json.parseToJsonElement(BackupWriter.toJson(empty)).jsonObject
        assertTrue(json.getValue("accounts").jsonArray.isEmpty())
        assertEquals(0, empty.rowCount)
        assertEquals(
            1,
            BackupWriter
                .toCsv(empty)
                .trim()
                .lines()
                .size,
        )
    }

    @Test
    fun `the suggested name carries the day and the format`() {
        assertEquals("cents-2026-09-16.json", BackupWriter.fileName(ExportFormat.JSON, WHEN))
        assertEquals("cents-2026-09-16.csv", BackupWriter.fileName(ExportFormat.CSV, WHEN))
    }

    @Test
    fun `the json is laid out for a person to read`() {
        val text = BackupWriter.toJson(backup())

        // A backup is opened by a worried human far more often than by a parser, and the file
        // is small enough that the whitespace costs nothing worth having.
        assertTrue(text.contains("\n"))
        assertFalse(text.startsWith("{\"schemaVersion\""))
    }

    private fun backup(
        amountMinor: Long = 2_550,
        note: String? = "Weekly shop",
    ) = Backup(
        schemaVersion = 4,
        exportedAt = WHEN,
        accounts = listOf(anAccount()),
        categories = listOf(aCategory()),
        transactions = listOf(aMovement(amountMinor, note)),
        holdings = listOf(aHolding()),
        trades = listOf(aTrade()),
        quotes = listOf(aQuote()),
    )

    private fun anAccount() =
        BackupAccount(
            id = 1,
            name = "Checking",
            type = "BANK",
            currency = "EUR",
            openingBalanceMinor = 100_000,
            colorArgb = 0,
            archived = false,
            sortOrder = 0,
        )

    private fun aCategory() =
        BackupCategory(
            id = 2,
            name = "Groceries",
            kind = "EXPENSE",
            colorArgb = 0,
            parentId = null,
            archived = false,
            sortOrder = 0,
        )

    private fun aMovement(
        amountMinor: Long,
        note: String?,
    ) = BackupTransaction(
        id = 3,
        accountId = 1,
        categoryId = 2,
        kind = "EXPENSE",
        amountMinor = amountMinor,
        currency = "EUR",
        date = LocalDate.of(2026, 9, 16).toEpochDay(),
        note = note,
        transferId = null,
        tradeId = null,
    )

    private fun aHolding() =
        BackupHolding(
            id = 4,
            accountId = 1,
            assetClass = "STOCK",
            symbol = "NVD.DE",
            name = "NVIDIA",
            currency = "EUR",
            note = null,
        )

    private fun aTrade() =
        BackupTrade(
            id = 5,
            holdingId = 4,
            side = "BUY",
            quantityScaled = 250_000_000,
            pricePerUnitMinor = 20_000,
            feeMinor = 0,
            currency = "EUR",
            date = LocalDate.of(2026, 9, 16).toEpochDay(),
            note = null,
        )

    private fun aQuote() =
        BackupQuote(
            symbol = "NVD.DE",
            priceMinor = 21_000,
            currency = "EUR",
            asOf = WHEN.toEpochMilli(),
            source = "MANUAL",
        )

    private fun uncategorized() =
        backup().let { base ->
            base.copy(
                transactions =
                    base.transactions.map { it.copy(categoryId = null, note = null) },
            )
        }

    private companion object {
        val WHEN: Instant = Instant.parse("2026-09-16T18:00:00Z")
    }
}
