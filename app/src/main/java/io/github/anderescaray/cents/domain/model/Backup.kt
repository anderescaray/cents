package io.github.anderescaray.cents.domain.model

import java.time.Instant

/**
 * Everything the app holds, at one moment, in a shape that can be written to a file.
 *
 * **This is the "I am never locked in" guarantee** (PRD §4.7, `DECISIONS.md` D15): a file the
 * owner keeps, with no account behind it and no third party in it. It is deliberately the
 * *storage* shape rather than the domain shape — amounts in minor units, dates as epoch days,
 * ids as they are — because a backup's job is to be readable by whatever reads it next,
 * including a version of this app that has moved on, and a number that has been through a
 * formatter on the way out is a number somebody has to unformat on the way in.
 *
 * [schemaVersion] is the one field that makes it survivable. A file says which database version
 * wrote it, so an import can refuse what it cannot read instead of guessing — and the guessing
 * is what turns a backup into data loss.
 */
data class Backup(
    val schemaVersion: Int,
    val exportedAt: Instant,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    val holdings: List<BackupHolding>,
    val trades: List<BackupTrade>,
    val quotes: List<BackupQuote>,
    /**
     * The subscriptions, salaries and rent that record themselves. Empty in a file written before
     * a backup carried them, which a restore then reads as *there were none*.
     */
    val rules: List<BackupRule> = emptyList(),
    val reminders: List<BackupReminder> = emptyList(),
    /** Empty in a file written before budgets existed, which a restore reads as none. */
    val budgets: List<BackupBudget> = emptyList(),
    /** Empty in a file written before groups existed, which a restore reads as none. */
    val groups: List<BackupGroup> = emptyList(),
    /** Empty in a file written before a charge could be postponed, which a restore reads as none. */
    val postponements: List<BackupPostponement> = emptyList(),
    /**
     * Empty in a file written before an account's balance could be corrected, which a restore
     * reads as none — so every balance comes back as opening balance plus movements, exactly as
     * that version of the app showed it.
     */
    val adjustments: List<BackupAdjustment> = emptyList(),
) {
    /** How many rows are in it, for a screen that wants to say what it just wrote. */
    val rowCount: Int
        get() =
            accounts.size + categories.size + transactions.size +
                holdings.size + trades.size + quotes.size + rules.size + reminders.size + budgets.size + groups.size +
                postponements.size + adjustments.size
}

data class BackupAccount(
    val id: Long,
    val name: String,
    val type: String,
    val currency: String,
    val openingBalanceMinor: Long,
    val colorArgb: Int,
    val archived: Boolean,
    val sortOrder: Int,
)

data class BackupCategory(
    val id: Long,
    val name: String,
    val kind: String,
    val colorArgb: Int,
    val parentId: Long?,
    val archived: Boolean,
    val sortOrder: Int,
)

data class BackupTransaction(
    val id: Long,
    val accountId: Long,
    val categoryId: Long?,
    val kind: String,
    val amountMinor: Long,
    val currency: String,
    /** Epoch day, which is how the database stores it. */
    val date: Long,
    val note: String?,
    val transferId: Long?,
    val tradeId: Long?,
    /** The recurring rule that wrote it, so a restored rule can still say what it produced. */
    val ruleId: Long? = null,
    /** The occasion it belongs to (PRD §4.9); absent in a file written before groups existed. */
    val groupId: Long? = null,
)

data class BackupHolding(
    val id: Long,
    val accountId: Long,
    val assetClass: String,
    val symbol: String,
    val name: String,
    val currency: String,
    val note: String?,
)

data class BackupTrade(
    val id: Long,
    val holdingId: Long,
    val side: String,
    /** Hundred-millionths of a unit, exactly as [Quantity] keeps it. */
    val quantityScaled: Long,
    val pricePerUnitMinor: Long,
    val feeMinor: Long,
    val currency: String,
    val date: Long,
    val note: String?,
)

data class BackupQuote(
    val symbol: String,
    val priceMinor: Long,
    val currency: String,
    /** Epoch millis: a quote is an instant, not a day. */
    val asOf: Long,
    val source: String,
)

/**
 * A recurring rule as stored. [lastGenerated] is the part that must not be lost: it is the
 * watermark that stops a restored rule from writing every charge since [startDate] a second time.
 */
data class BackupRule(
    val id: Long,
    val name: String,
    val accountId: Long,
    val categoryId: Long?,
    val kind: String,
    val amountMinor: Long,
    val currency: String,
    val frequency: String,
    val intervalDays: Int?,
    /** Epoch days, like every other date in the file. */
    val startDate: Long,
    val endDate: Long?,
    val mode: String,
    val active: Boolean,
    val lastGenerated: Long?,
)

data class BackupReminder(
    val ruleId: Long,
    /** Signed: occurrence + offset = the day it fires. */
    val offsetDays: Int,
)

/** One occurrence of a rule moved to a later day. Both dates are epoch days. */
data class BackupPostponement(
    val ruleId: Long,
    val occurrence: Long,
    val postponedTo: Long,
)

/** A monthly limit on one expense category. Nothing points at it, so it needs no id of its own. */
data class BackupBudget(
    val categoryId: Long,
    val amountMinor: Long,
    val currency: String,
)

/**
 * A correction to one account's balance from [date] on. [amountMinor] is signed, unlike a
 * movement's, because there is no kind to carry the direction. Nothing points at it, so its id is
 * not kept.
 */
data class BackupAdjustment(
    val accountId: Long,
    val amountMinor: Long,
    val currency: String,
    /** Epoch day, like every other date in the file. */
    val date: Long,
)

/** An occasion that cuts across categories. Movements point at its id, so it keeps one. */
data class BackupGroup(
    val id: Long,
    val name: String,
    /** Epoch days, like every other date in the file. */
    val startDate: Long?,
    val endDate: Long?,
)
