package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.anderescaray.cents.domain.model.AssetClass
import java.time.Instant

/**
 * An asset held in an account: a type and the owner's own name for it (`DECISIONS.md` D40).
 *
 * Neither what it is worth nor what it cost is a column, for the reason an account's balance is
 * not one: both are what its trades and typed values add up to, and a stored copy would drift away
 * from them the first time one is edited.
 *
 * The name matches nothing, so two assets may share one: the owner decides what they are.
 */
@Entity(
    tableName = "holdings",
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
    ],
)
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val assetClass: AssetClass,
    val name: String,
    /** ISO-4217 code: the account's, taken when the asset is created. */
    val currency: String,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
