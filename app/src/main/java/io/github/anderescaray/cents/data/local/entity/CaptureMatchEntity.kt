package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.Instant
import java.time.LocalDate

/**
 * A captured payment taken to be a subscription's charge, so nothing new was saved for it
 * (`DECISIONS.md` D35).
 *
 * It answers two questions. **Has this charge been matched already?** One capture per charge: a
 * second payment inside the window is saved, because paying twice does happen. And **what does
 * Undo put back?** The payment as it would have been saved, and, when the match wrote the charge
 * itself, the rule as it stood before.
 *
 * Not money. The charge is an ordinary movement in `transactions`, counted like any other, so
 * nothing here holds anything up: deleting the rule leaves the payment to be undone into a plain
 * capture, and deleting the account takes the match with it. [chargeTransactionId] has no foreign
 * key on purpose. A movement deleted and put back by its Undo returns with the same id, and the
 * match has to still be pointing at it; a key that cleared the id on delete would let a second
 * payment match the same charge.
 */
@Entity(
    tableName = "capture_matches",
    foreignKeys = [
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chargeTransactionId"], unique = true),
        Index(value = ["ruleId"]),
        Index(value = ["accountId"]),
    ],
)
data class CaptureMatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** The subscription's movement the payment was taken to be, which may since have been deleted. */
    val chargeTransactionId: Long?,
    /**
     * Whether the match wrote that movement itself, for a charge the rule had not yet written.
     * Undo deletes only a movement the match wrote; one the rule wrote stays.
     */
    val wroteCharge: Boolean,
    val ruleId: Long?,
    /** The rule's occurrence the match wrote, or null when it found the charge already written. */
    val occurrence: LocalDate?,
    /** Where the rule's watermark stood before the match wrote the charge, for Undo to put back. */
    val previousLastGenerated: LocalDate?,
    /** The day [occurrence] had been moved to (D28), which writing it cleared. */
    val postponedTo: LocalDate?,
    /** The payment, as capture would have saved it: what Undo saves. */
    val accountId: Long,
    val kind: TransactionKind,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate,
    val note: String,
    val matchedAt: Instant,
)
