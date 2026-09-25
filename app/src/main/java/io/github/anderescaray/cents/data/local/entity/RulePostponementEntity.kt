package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDate

/**
 * One occurrence of a rule moved to a later day: the rent due on the 1st, paid on the 4th.
 *
 * Keyed by the rule and the **occurrence it moves**, not by the new day, because the occurrence
 * is what the rule's watermark counts. The movement is dated [postponedTo], and the rule still
 * records [occurrence] as done once it is written, so the next month is counted from the start
 * date exactly as before (`DECISIONS.md` D28).
 *
 * `CASCADE` for the same reason a reminder cascades: this is part of the rule's configuration,
 * not a record of money that moved.
 */
@Entity(
    tableName = "rule_postponements",
    primaryKeys = ["ruleId", "occurrence"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["ruleId"])],
)
data class RulePostponementEntity(
    val ruleId: Long,
    val occurrence: LocalDate,
    val postponedTo: LocalDate,
)
