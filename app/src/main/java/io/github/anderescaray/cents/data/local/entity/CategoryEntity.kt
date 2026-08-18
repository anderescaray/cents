package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A user-defined label. Categories are archived rather than deleted once they have
 * transactions, so history and past charts stay intact.
 *
 * A category may have a parent, giving one level of subcategories (`Car > Fuel`).
 * The unique index does not constrain top-level names, because SQLite treats NULL
 * parents as distinct; uniqueness among root categories is enforced above this layer.
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["name", "kind", "parentId"], unique = true),
        Index(value = ["archived"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val colorArgb: Int,
    val iconKey: String? = null,
    val parentId: Long? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant,
)

enum class CategoryKind {
    INCOME,
    EXPENSE,
}
