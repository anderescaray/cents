package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * One income or expense in one account.
 *
 * [amountMinor] is always **positive**: the direction lives in [kind]. Storing signed
 * amounts and a kind means two sources of truth that can disagree, and a single wrong
 * sign silently corrupts every total in the app.
 *
 * Deleting an account or a category that still has transactions is refused by the
 * foreign keys ([ForeignKey.RESTRICT]); both are archived instead.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["date"]),
        Index(value = ["accountId", "date"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    /** Null means uncategorized: the inbox an auto-captured expense lands in. */
    val categoryId: Long? = null,
    val kind: TransactionKind,
    /** Always positive, in the smallest unit of [currency]. */
    val amountMinor: Long,
    /** ISO-4217 code. Denormalized from the account so history survives a currency change. */
    val currency: String,
    val date: LocalDate,
    val note: String? = null,
    val attachmentUri: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class TransactionKind {
    INCOME,
    EXPENSE,
}
