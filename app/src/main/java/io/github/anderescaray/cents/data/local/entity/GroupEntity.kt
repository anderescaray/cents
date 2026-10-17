package io.github.anderescaray.cents.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * An occasion that cuts across categories — a trip, a move, a freelance job (PRD §4.9,
 * `DECISIONS.md` D27).
 *
 * The table is `movement_groups` rather than `groups` because `GROUPS` is a keyword in SQLite
 * since 3.28 (window frames). Room quotes the names it writes, but every hand-written query would
 * have to remember to, and the one that forgot would fail only on the phone.
 *
 * [startDate] and [endDate] are **a hint, not a boundary**: while a movement's date falls between
 * them the form offers this group first, and that is all they do. A dinner recorded a day after
 * the trip ended still belongs to it if the owner says so.
 *
 * [archived] keeps a group out of the choices for new movements and leaves everything else as it
 * was: its movements, its totals and the reports that count them. Added in Room v13, every group
 * from before reads as not archived.
 */
@Entity(tableName = "movement_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val createdAt: Instant,
    @ColumnInfo(defaultValue = "0")
    val archived: Boolean = false,
)
