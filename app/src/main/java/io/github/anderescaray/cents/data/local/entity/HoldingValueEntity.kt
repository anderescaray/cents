package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * What the owner said an asset was worth on a day: the whole of it, typed by hand, at least once a
 * week (`DECISIONS.md` D40). Kept rather than overwritten, because the history of these is the
 * chart.
 *
 * One per asset and day, which the unique index holds: typing a second value the same day
 * corrects the first rather than adding a rival to it.
 */
@Entity(
    tableName = "holding_values",
    foreignKeys = [
        ForeignKey(
            entity = HoldingEntity::class,
            parentColumns = ["id"],
            childColumns = ["holdingId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["holdingId", "date"], unique = true),
    ],
)
data class HoldingValueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val holdingId: Long,
    val date: LocalDate,
    val valueMinor: Long,
    /** ISO-4217 code, the asset's. */
    val currency: String,
    val createdAt: Instant,
)
