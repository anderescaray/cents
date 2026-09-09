package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.anderescaray.cents.domain.model.TradeSide
import java.time.Instant
import java.time.LocalDate

/**
 * One purchase or sale of a holding.
 *
 * [quantityScaled] is an exact integer of hundred-millionths of a unit and [pricePerUnitMinor]
 * an exact integer of cents — the same rule as every other number in this app, applied to the
 * one place where fractions are genuinely part of the domain rather than a formatting choice.
 *
 * Both are positive: the direction lives in [side], the way a transaction's direction lives in
 * its kind. The foreign key is `RESTRICT`, so a holding with trades cannot vanish sideways;
 * deleting one takes its trades with it explicitly, in one database transaction.
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
    val quantityScaled: Long,
    val pricePerUnitMinor: Long,
    val feeMinor: Long,
    /** ISO-4217 code. Denormalized from the holding so history survives a change to it. */
    val currency: String,
    val date: LocalDate,
    val note: String? = null,
    val createdAt: Instant,
)
