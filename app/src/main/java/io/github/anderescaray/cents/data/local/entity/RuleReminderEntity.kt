package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One nudge about one subscription, a number of days away from the day it comes round.
 *
 * **`CASCADE`, and that is not a contradiction of rule 8.** Rule 8 protects *movements* — money
 * that really moved — from a template disappearing. A reminder is not a record of anything: it
 * is part of the rule's own configuration, and keeping one pointed at a rule that no longer
 * exists would only produce notifications about nothing.
 *
 * [offsetDays] is signed and reads as **occurrence + offset = the day it fires**: `-3` is three
 * days before, `+1` is the day after, `0` is the morning of. Storing the direction as a sign
 * rather than as a second column means "three days either way" is one comparison and cannot end
 * up in the impossible state of a negative count pointing forwards.
 */
@Entity(
    tableName = "rule_reminders",
    foreignKeys = [
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ruleId"]),
        // The same reminder twice is the same notification twice, which is how somebody turns
        // notifications off altogether.
        Index(value = ["ruleId", "offsetDays"], unique = true),
    ],
)
data class RuleReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleId: Long,
    val offsetDays: Int,
)
