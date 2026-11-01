package io.github.anderescaray.cents.data.export

import io.github.anderescaray.cents.domain.model.BackupHolding
import io.github.anderescaray.cents.domain.model.BackupHoldingValue
import io.github.anderescaray.cents.domain.model.BackupTrade
import io.github.anderescaray.cents.domain.usecase.LegacyHolding
import io.github.anderescaray.cents.domain.usecase.LegacyQuote
import io.github.anderescaray.cents.domain.usecase.LegacyTrade
import io.github.anderescaray.cents.domain.usecase.convertLegacyPortfolio
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A backup's investments, in the shape D40 keeps them. */
internal data class BackupPortfolio(
    val holdings: List<BackupHolding>,
    val trades: List<BackupTrade>,
    val values: List<BackupHoldingValue>,
)

/**
 * The investments in a backup. A file from v15 on holds amounts and values; an older one holds
 * symbols, units and prices, which are turned into amounts the way the v15 migration turns them
 * (`convertLegacyPortfolio`), so a restored old backup and a migrated old database agree.
 */
internal fun JsonObject.portfolio(version: Int): BackupPortfolio =
    if (version >= INVESTMENT_AMOUNTS_SCHEMA) {
        BackupPortfolio(
            holdings = rows("holdings", ::holding),
            trades = rows("trades", ::trade),
            values = rows("holdingValues", ::holdingValue),
        )
    } else {
        legacyPortfolio()
    }

private fun holding(row: JsonObject) =
    BackupHolding(
        id = row.requireLong("id"),
        accountId = row.requireLong("accountId"),
        assetClass = row.requireString("assetClass"),
        name = row.requireString("name"),
        currency = row.requireString("currency"),
        note = row.string("note"),
    )

private fun trade(row: JsonObject) =
    BackupTrade(
        id = row.requireLong("id"),
        holdingId = row.requireLong("holdingId"),
        side = row.requireString("side"),
        amountMinor = row.requireLong("amountMinor"),
        sellsAll = row.boolean("sellsAll"),
        currency = row.requireString("currency"),
        date = row.requireLong("date"),
        note = row.string("note"),
    )

private fun holdingValue(row: JsonObject) =
    BackupHoldingValue(
        holdingId = row.requireLong("holdingId"),
        date = row.requireLong("date"),
        valueMinor = row.requireLong("valueMinor"),
        currency = row.requireString("currency"),
    )

private fun JsonObject.legacyPortfolio(): BackupPortfolio {
    val converted =
        convertLegacyPortfolio(
            holdings = rows("holdings", ::legacyHolding),
            trades = rows("trades", ::legacyTrade),
            quotes = rows("quotes", ::legacyQuote),
        )
    return BackupPortfolio(
        holdings =
            converted.holdings.map {
                BackupHolding(it.id, it.accountId, it.assetClass.name, it.name, it.currency, it.note)
            },
        trades =
            converted.trades.map {
                BackupTrade(
                    id = it.id,
                    holdingId = it.holdingId,
                    side = it.side.name,
                    amountMinor = it.amount.minorUnits,
                    sellsAll = it.sellsAll,
                    currency = it.amount.currency,
                    date = it.date.toEpochDay(),
                    note = it.note,
                )
            },
        values =
            converted.values.map {
                BackupHoldingValue(it.holdingId, it.date.toEpochDay(), it.value.minorUnits, it.value.currency)
            },
    )
}

private fun legacyHolding(row: JsonObject) =
    LegacyHolding(
        id = row.requireLong("id"),
        accountId = row.requireLong("accountId"),
        assetClass = row.requireString("assetClass"),
        symbol = row.requireString("symbol"),
        name = row.requireString("name"),
        currency = row.requireString("currency"),
        note = row.string("note"),
    )

private fun legacyTrade(row: JsonObject) =
    LegacyTrade(
        id = row.requireLong("id"),
        holdingId = row.requireLong("holdingId"),
        side = row.requireString("side"),
        quantityScaled = row.requireLong("quantityScaled"),
        pricePerUnitMinor = row.requireLong("pricePerUnitMinor"),
        feeMinor = row.requireLong("feeMinor"),
        currency = row.requireString("currency"),
        date = LocalDate.ofEpochDay(row.requireLong("date")),
        note = row.string("note"),
    )

/** A price's day is the phone's day it was true on, as the migration reads it. */
private fun legacyQuote(row: JsonObject) =
    LegacyQuote(
        symbol = row.requireString("symbol"),
        priceMinor = row.requireLong("priceMinor"),
        currency = row.requireString("currency"),
        asOf = Instant.ofEpochMilli(row.requireLong("asOf")).atZone(ZoneId.systemDefault()).toLocalDate(),
    )

/** The first schema whose investments are amounts and values (D40). */
private const val INVESTMENT_AMOUNTS_SCHEMA = 15
