package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.anderescaray.cents.domain.model.TradeSide
import java.time.Instant
import java.time.LocalDate

/**
 * One purchase or sale of an asset, as an amount (`DECISIONS.md` D40).
 *
 * [amountMinor] is positive: the direction lives in [side], the way a movement's lives in its
 * kind. A sale's [sellsAll] says it emptied the asset. What cost a sale removed and what it made
 * are not stored: they follow from everything before it, and are worked out where they are read
 * (`positionOf`), so editing an old purchase corrects every sale after it.
 *
 * The foreign key is `RESTRICT`, so an asset with trades cannot vanish sideways; deleting one
 * takes its trades with it explicitly, in one database transaction.
 */
@Entity(
    tableName = "trades",
    foreignKeys = [
        ForeignKey(
            entity = HoldingEntity::class,
            parentColumns = ["id"],
            childColumns = ["holdingId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["holdingId"]),
        Index(value = ["holdingId", "date"]),
    ],
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val holdingId: Long,
    val side: TradeSide,
    val amountMinor: Long,
    val sellsAll: Boolean = false,
    /** ISO-4217 code. Denormalized from the asset so history survives a change to it. */
    val currency: String,
    val date: LocalDate,
    val note: String? = null,
    val createdAt: Instant,
)
