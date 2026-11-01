package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * How much the owner means to spend in a month (PRD §4.4): in one expense category, or, with no
 * [categoryId], in all of them together, the **general budget** (the owner, 2026-10-31).
 *
 * **One per category**, which the unique index enforces: two limits on the same category would
 * leave the screen having to choose which one is being kept to. **At most one general budget**,
 * for the same reason, which the repository enforces: SQLite's unique index lets any number of
 * nulls through.
 *
 * **`CASCADE` from the category**, for the reason a reminder cascades from its rule: a budget
 * records nothing that happened. It is configuration about a category, and a category can only
 * be deleted while nothing uses it (PRD §3), at which point a limit on it limits nothing.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Null for the general budget, over every expense of the month. */
    val categoryId: Long?,
    /** The monthly limit, always positive, in the smallest unit of [currency] (rule 1). */
    val amountMinor: Long,
    val currency: String,
    val createdAt: Instant,
)
