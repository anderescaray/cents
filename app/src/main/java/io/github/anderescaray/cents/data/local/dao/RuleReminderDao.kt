package io.github.anderescaray.cents.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.anderescaray.cents.data.local.entity.RuleReminderEntity

@Dao
interface RuleReminderDao {
    /** Soonest first, which is how they read in a list: three days before, then one. */
    @Query("SELECT * FROM rule_reminders WHERE ruleId = :ruleId ORDER BY offsetDays")
    suspend fun forRule(ruleId: Long): List<RuleReminderEntity>

    /**
     * Every reminder in the app, for the daily check.
     *
     * One query rather than one per rule: the worker is going to ask about all of them anyway,
     * and a handful of subscriptions is not worth a join.
     */
    @Query("SELECT * FROM rule_reminders")
    suspend fun all(): List<RuleReminderEntity>

    @Insert
    suspend fun insert(reminder: RuleReminderEntity): Long

    @Query("DELETE FROM rule_reminders WHERE ruleId = :ruleId")
    suspend fun deleteForRule(ruleId: Long)
}
