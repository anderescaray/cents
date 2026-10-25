package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.entity.RulePostponementEntity
import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.model.BackupAccount
import io.github.anderescaray.cents.domain.model.BackupAdjustment
import io.github.anderescaray.cents.domain.model.BackupBudget
import io.github.anderescaray.cents.domain.model.BackupCaptureMatch
import io.github.anderescaray.cents.domain.model.BackupCategory
import io.github.anderescaray.cents.domain.model.BackupGroup
import io.github.anderescaray.cents.domain.model.BackupHolding
import io.github.anderescaray.cents.domain.model.BackupPostponement
import io.github.anderescaray.cents.domain.model.BackupQuote
import io.github.anderescaray.cents.domain.model.BackupReminder
import io.github.anderescaray.cents.domain.model.BackupRule
import io.github.anderescaray.cents.domain.model.BackupTrade
import io.github.anderescaray.cents.domain.model.BackupTransaction
import io.github.anderescaray.cents.domain.repository.BackupRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything, read at one moment.
 *
 * **Inside one `withTransaction`**, so the file is a picture of the app rather than of six
 * separate moments. Nothing about this app makes a write likely while an export is running —
 * one owner, one phone, one thread of attention — but a backup whose transactions mention an
 * account that its accounts list does not is a special kind of useless, and the consistency
 * costs one line.
 *
 * The tables are read through the flows every screen already uses, taking their first emission.
 * That is six queries and no new DAO surface: an export is not a new way of reading the
 * database, it is the same reading written down.
 *
 * @suppress TooManyFunctions — one small reader per table, the shape `BackupWriter` has for the
 * same reason: each says what one table becomes in the file, and a new table is a new function.
 */
@Suppress("TooManyFunctions")
@Singleton
class RoomBackupRepository
    @Inject
    constructor(
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : BackupRepository {
        override suspend fun snapshot(): Backup =
            database.withTransaction {
                Backup(
                    // What wrote the file, so whatever reads it next can refuse rather than
                    // guess. Guessing is what turns a backup into data loss.
                    schemaVersion = CentsDatabase.VERSION,
                    exportedAt = clock.instant(),
                    accounts = accounts(),
                    categories = categories(),
                    transactions = transactions(),
                    holdings = holdings(),
                    trades = trades(),
                    quotes = quotes(),
                    rules = rules(),
                    reminders = reminders(),
                    budgets = budgets(),
                    groups = groups(),
                    postponements = postponements(),
                    adjustments = adjustments(),
                    captureMatches = captureMatches(),
                )
            }

        private suspend fun accounts() =
            database.accountDao().observeAll().first().map {
                BackupAccount(
                    id = it.id,
                    name = it.name,
                    type = it.type.name,
                    currency = it.currency,
                    openingBalanceMinor = it.openingBalanceMinor,
                    colorArgb = it.colorArgb,
                    archived = it.archived,
                    sortOrder = it.sortOrder,
                )
            }

        private suspend fun categories() =
            database.categoryDao().observeAll().first().map {
                BackupCategory(
                    id = it.id,
                    name = it.name,
                    kind = it.kind.name,
                    colorArgb = it.colorArgb,
                    parentId = it.parentId,
                    archived = it.archived,
                    sortOrder = it.sortOrder,
                    emoji = it.iconKey,
                )
            }

        private suspend fun transactions() =
            database.transactionDao().observeAll().first().map {
                BackupTransaction(
                    id = it.id,
                    accountId = it.accountId,
                    categoryId = it.categoryId,
                    kind = it.kind.name,
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    date = it.date.toEpochDay(),
                    note = it.note,
                    transferId = it.transferId,
                    tradeId = it.tradeId,
                    ruleId = it.ruleId,
                    groupId = it.groupId,
                )
            }

        private suspend fun holdings() =
            database.holdingDao().observeAll().first().map {
                BackupHolding(
                    id = it.id,
                    accountId = it.accountId,
                    assetClass = it.assetClass.name,
                    symbol = it.symbol,
                    name = it.name,
                    currency = it.currency,
                    note = it.note,
                )
            }

        private suspend fun trades() =
            database.tradeDao().observeAll().first().map {
                BackupTrade(
                    id = it.id,
                    holdingId = it.holdingId,
                    side = it.side.name,
                    quantityScaled = it.quantityScaled,
                    pricePerUnitMinor = it.pricePerUnitMinor,
                    feeMinor = it.feeMinor,
                    currency = it.currency,
                    date = it.date.toEpochDay(),
                    note = it.note,
                )
            }

        /**
         * Everything out, everything in, in one transaction.
         *
         * **The order is the foreign keys' order, in both directions.** Movements point at
         * accounts, categories, trades and rules; trades point at holdings; rules point at
         * accounts and categories, reminders and postponements at rules, budgets at categories, and movements at
         * groups too; balance corrections point at accounts; capture matches at movements, rules and
         * accounts; so the emptying
         * goes from the leaves inward and the filling from the roots outward. Getting it wrong does not
         * corrupt anything — `RESTRICT` refuses and the whole transaction rolls back — but it
         * would turn every restore into a failure, so it is written down rather than discovered.
         *
         * Ids are kept exactly as the file has them. A backup is a graph, and a movement whose
         * account id was renumbered on the way in would point at a different account.
         */
        override suspend fun restore(backup: Backup) {
            database.withTransaction {
                database.captureMatchDao().deleteAll()
                database.balanceAdjustmentDao().deleteAll()
                database.transactionDao().deleteAll()
                database.groupDao().deleteAll()
                database.budgetDao().deleteAll()
                database.rulePostponementDao().deleteAll()
                database.ruleReminderDao().deleteAll()
                database.recurringRuleDao().deleteAll()
                database.tradeDao().deleteAll()
                database.holdingDao().deleteAll()
                database.priceQuoteDao().deleteAll()
                database.categoryDao().deleteAll()
                database.accountDao().deleteAll()

                val now = clock.instant()
                backup.accounts.forEach { database.accountDao().insert(it.toEntity(now)) }
                backup.categories.forEach { database.categoryDao().insert(it.toEntity(now)) }
                backup.adjustments.forEach { database.balanceAdjustmentDao().insert(it.toEntity(now)) }
                backup.rules.forEach { database.recurringRuleDao().insert(it.toEntity(now)) }
                backup.reminders.forEach { database.ruleReminderDao().insert(it.toEntity()) }
                backup.postponements.forEach {
                    database.rulePostponementDao().upsert(
                        RulePostponementEntity(
                            ruleId = it.ruleId,
                            occurrence = LocalDate.ofEpochDay(it.occurrence),
                            postponedTo = LocalDate.ofEpochDay(it.postponedTo),
                        ),
                    )
                }
                backup.budgets.forEach { database.budgetDao().insert(it.toEntity(now)) }
                backup.groups.forEach { database.groupDao().insert(it.toEntity(now)) }
                backup.holdings.forEach { database.holdingDao().insert(it.toEntity(now)) }
                backup.trades.forEach { database.tradeDao().insert(it.toEntity(now)) }
                backup.transactions.forEach { database.transactionDao().insert(it.toEntity(now)) }
                backup.quotes.forEach { database.priceQuoteDao().upsert(it.toEntity()) }
                backup.captureMatches.forEach { database.captureMatchDao().insert(it.toEntity(now)) }
            }
        }

        private suspend fun rules() =
            database.recurringRuleDao().observeAll().first().map {
                BackupRule(
                    id = it.id,
                    name = it.name,
                    accountId = it.accountId,
                    categoryId = it.categoryId,
                    kind = it.kind.name,
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    frequency = it.frequency.name,
                    intervalDays = it.intervalDays,
                    startDate = it.startDate.toEpochDay(),
                    endDate = it.endDate?.toEpochDay(),
                    mode = it.mode.name,
                    active = it.active,
                    lastGenerated = it.lastGenerated?.toEpochDay(),
                )
            }

        private suspend fun reminders() =
            database.ruleReminderDao().all().map { BackupReminder(ruleId = it.ruleId, offsetDays = it.offsetDays) }

        private suspend fun postponements() =
            database.rulePostponementDao().all().map {
                BackupPostponement(
                    ruleId = it.ruleId,
                    occurrence = it.occurrence.toEpochDay(),
                    postponedTo = it.postponedTo.toEpochDay(),
                )
            }

        private suspend fun captureMatches() =
            database.captureMatchDao().all().map {
                BackupCaptureMatch(
                    id = it.id,
                    chargeTransactionId = it.chargeTransactionId,
                    wroteCharge = it.wroteCharge,
                    ruleId = it.ruleId,
                    occurrence = it.occurrence?.toEpochDay(),
                    previousLastGenerated = it.previousLastGenerated?.toEpochDay(),
                    postponedTo = it.postponedTo?.toEpochDay(),
                    accountId = it.accountId,
                    kind = it.kind.name,
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    date = it.date.toEpochDay(),
                    note = it.note,
                )
            }

        private suspend fun adjustments() =
            database.balanceAdjustmentDao().all().map {
                BackupAdjustment(
                    accountId = it.accountId,
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    date = it.date.toEpochDay(),
                )
            }

        private suspend fun budgets() =
            database.budgetDao().all().map {
                BackupBudget(categoryId = it.categoryId, amountMinor = it.amountMinor, currency = it.currency)
            }

        private suspend fun groups() =
            database.groupDao().all().map {
                BackupGroup(
                    id = it.id,
                    name = it.name,
                    startDate = it.startDate?.toEpochDay(),
                    endDate = it.endDate?.toEpochDay(),
                    archived = it.archived,
                )
            }

        private suspend fun quotes() =
            database.priceQuoteDao().observeAll().first().map {
                BackupQuote(
                    symbol = it.symbol,
                    priceMinor = it.priceMinor,
                    currency = it.currency,
                    asOf = it.asOf.toEpochMilli(),
                    source = it.source.name,
                )
            }
    }
