package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.entity.AccountEntity
import io.github.anderescaray.cents.data.local.entity.BudgetEntity
import io.github.anderescaray.cents.data.local.entity.CategoryEntity
import io.github.anderescaray.cents.data.local.entity.GroupEntity
import io.github.anderescaray.cents.data.local.entity.HoldingEntity
import io.github.anderescaray.cents.data.local.entity.PriceQuoteEntity
import io.github.anderescaray.cents.data.local.entity.RecurringRuleEntity
import io.github.anderescaray.cents.data.local.entity.RuleReminderEntity
import io.github.anderescaray.cents.data.local.entity.TradeEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.BackupAccount
import io.github.anderescaray.cents.domain.model.BackupBudget
import io.github.anderescaray.cents.domain.model.BackupCategory
import io.github.anderescaray.cents.domain.model.BackupGroup
import io.github.anderescaray.cents.domain.model.BackupHolding
import io.github.anderescaray.cents.domain.model.BackupQuote
import io.github.anderescaray.cents.domain.model.BackupReminder
import io.github.anderescaray.cents.domain.model.BackupRule
import io.github.anderescaray.cents.domain.model.BackupTrade
import io.github.anderescaray.cents.domain.model.BackupTransaction
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.QuoteSource
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.Instant
import java.time.LocalDate

/**
 * A backup row, back into the row it came from.
 *
 * **Ids are kept.** A backup is a graph — a movement names its account by id — so renumbering on
 * the way in would point it at somebody else. Room only generates an id when it is zero, which
 * is exactly the behaviour needed and exactly the behaviour to be careful of: an export whose
 * ids were dropped would restore into a heap of unrelated rows.
 *
 * **An enum the file does not recognise falls back rather than throwing.** A file written by a
 * later version could carry a `side` or an `assetClass` this one has never heard of, and a
 * restore that dies on it would leave the owner with no way back in at all. It cannot happen
 * through the front door, because a newer `schemaVersion` is refused before any of this runs —
 * this is the belt to that braces.
 *
 * [createdAt] is the moment of the restore rather than anything from the file. Those columns are
 * bookkeeping about the row rather than about the money, and the honest answer to "when did this
 * row come into being" is *now*: the backup did not carry them, and inventing one would be
 * worse than recording the truth.
 */

fun BackupAccount.toEntity(createdAt: Instant) =
    AccountEntity(
        id = id,
        name = name,
        type = enumOrDefault(type, AccountType.OTHER),
        currency = currency,
        openingBalanceMinor = openingBalanceMinor,
        colorArgb = colorArgb,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

fun BackupCategory.toEntity(createdAt: Instant) =
    CategoryEntity(
        id = id,
        name = name,
        kind = enumOrDefault(kind, CategoryKind.EXPENSE),
        colorArgb = colorArgb,
        parentId = parentId,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

fun BackupTransaction.toEntity(createdAt: Instant) =
    TransactionEntity(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        kind = enumOrDefault(kind, TransactionKind.EXPENSE),
        amountMinor = amountMinor,
        currency = currency,
        date = LocalDate.ofEpochDay(date),
        note = note,
        transferId = transferId,
        tradeId = tradeId,
        ruleId = ruleId,
        groupId = groupId,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

fun BackupHolding.toEntity(createdAt: Instant) =
    HoldingEntity(
        id = id,
        accountId = accountId,
        assetClass = enumOrDefault(assetClass, AssetClass.STOCK),
        symbol = symbol,
        name = name,
        currency = currency,
        note = note,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

fun BackupTrade.toEntity(createdAt: Instant) =
    TradeEntity(
        id = id,
        holdingId = holdingId,
        side = enumOrDefault(side, TradeSide.BUY),
        quantityScaled = quantityScaled,
        pricePerUnitMinor = pricePerUnitMinor,
        feeMinor = feeMinor,
        currency = currency,
        date = LocalDate.ofEpochDay(date),
        note = note,
        createdAt = createdAt,
    )

/**
 * An unknown frequency falls back to monthly and an unknown mode to *ask first*: of the wrong
 * guesses, those are the ones least likely to write money nobody expected.
 */
fun BackupRule.toEntity(createdAt: Instant) =
    RecurringRuleEntity(
        id = id,
        name = name,
        accountId = accountId,
        categoryId = categoryId,
        kind = enumOrDefault(kind, TransactionKind.EXPENSE),
        amountMinor = amountMinor,
        currency = currency,
        frequency = enumOrDefault(frequency, Frequency.MONTHLY),
        intervalDays = intervalDays,
        startDate = LocalDate.ofEpochDay(startDate),
        endDate = endDate?.let(LocalDate::ofEpochDay),
        mode = enumOrDefault(mode, RuleMode.CONFIRM),
        active = active,
        lastGenerated = lastGenerated?.let(LocalDate::ofEpochDay),
        createdAt = createdAt,
    )

fun BackupBudget.toEntity(createdAt: Instant) =
    BudgetEntity(categoryId = categoryId, amountMinor = amountMinor, currency = currency, createdAt = createdAt)

fun BackupGroup.toEntity(createdAt: Instant) =
    GroupEntity(
        id = id,
        name = name,
        startDate = startDate?.let(LocalDate::ofEpochDay),
        endDate = endDate?.let(LocalDate::ofEpochDay),
        createdAt = createdAt,
    )

fun BackupReminder.toEntity() = RuleReminderEntity(ruleId = ruleId, offsetDays = offsetDays)

fun BackupQuote.toEntity() =
    PriceQuoteEntity(
        symbol = symbol,
        priceMinor = priceMinor,
        currency = currency,
        asOf = Instant.ofEpochMilli(asOf),
        source = enumOrDefault(source, QuoteSource.MANUAL),
    )

private inline fun <reified T : Enum<T>> enumOrDefault(
    name: String,
    fallback: T,
): T = enumValues<T>().firstOrNull { it.name == name } ?: fallback
