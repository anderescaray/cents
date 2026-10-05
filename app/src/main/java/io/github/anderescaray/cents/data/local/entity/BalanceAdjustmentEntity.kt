package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * A correction to an account's balance from [date] on: the owner said what the account really
 * holds, and this is the difference between that and what the movements add up to.
 *
 * **Not a movement, and deliberately in a table of its own.** A correction is not money earned or
 * spent, so it must never reach a report, a budget, a group or the inbox. Keeping it out of
 * `transactions` makes that true by construction: every income-and-expense query reads only
 * `transactions`, and only the few queries that compute a *balance* read this table as well —
 * `AccountDao`'s two and the balance line's two in `TransactionDao`. A query that forgot it would
 * show a balance without the correction, which is visible; a correction filed as a movement and
 * forgotten by one report would inflate a statistic silently.
 *
 * [amountMinor] is **signed**, unlike a movement's: there is no kind to carry the direction,
 * because a correction is neither an income nor an expense.
 *
 * `RESTRICT` on the account, like a movement: the account's removal decides what becomes of its
 * corrections explicitly (`RoomAccountRepository`), rather than a cascade deciding it unseen.
 */
@Entity(
    tableName = "balance_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["date"]),
    ],
)
data class BalanceAdjustmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    /** Signed, in the smallest unit of [currency]: positive adds to the balance. */
    val amountMinor: Long,
    /** ISO-4217 code, the account's. */
    val currency: String,
    /** The first day the balance includes it. Every balance before it is left as it was. */
    val date: LocalDate,
    val createdAt: Instant,
)
