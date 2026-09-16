package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.Instant
import java.time.LocalDate

/**
 * One income or expense in one account.
 *
 * [amountMinor] is always **positive**: the direction lives in [kind]. Storing signed
 * amounts and a kind means two sources of truth that can disagree, and a single wrong
 * sign silently corrupts every total in the app.
 *
 * A row with a [transferId] is **one leg of a transfer** rather than a movement of its own:
 * money that left one of the owner's accounts and arrived in another. Both legs carry the
 * same [transferId], and every income-and-expense report excludes them — moving 500 € from
 * checking to savings is not spending 500 € (PRD §3). Balances count them, because the money
 * really did move.
 *
 * A row with a [tradeId] is the same idea one step along: **the cash a purchase took out of the
 * account, or a sale put back**. Buying 400 € of shares is not spending 400 €, it is holding the
 * same money in a different shape — so it is excluded from the reports exactly as a transfer leg
 * is, and counted by the balance exactly as one is, which is what makes an investments account's
 * balance read as the cash left in it.
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
        ForeignKey(
            entity = TradeEntity::class,
            parentColumns = ["id"],
            childColumns = ["tradeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["date"]),
        Index(value = ["accountId", "date"]),
        Index(value = ["transferId"]),
        Index(value = ["tradeId"]),
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
    /**
     * The transfer this row is one leg of, and null for an ordinary movement.
     *
     * It is the row id of the outgoing leg, which is where the value comes from and why no
     * counter or extra table is needed: a row id is already unique and already exists by the
     * time the second leg is written. The pair is written in one database transaction, so a
     * leg with nobody on the other end is not a state this app can produce.
     */
    val transferId: Long? = null,
    /**
     * The trade whose cash this row is, and null for an ordinary movement.
     *
     * `RESTRICT` rather than `CASCADE`, and deliberately: the two are written and removed
     * together by `RoomTradeRepository`, inside one database transaction, so the cascade would
     * never fire — and rule 8 is a standing instruction not to let a delete somewhere else
     * quietly rewrite recorded history here. A constraint violation is a bug reaching the
     * surface; a silent cascade is the same bug not reaching it.
     */
    val tradeId: Long? = null,
    val attachmentUri: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
