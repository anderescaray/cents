package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.dao.RecurringRuleDao
import io.github.anderescaray.cents.data.local.dao.RuleReminderDao
import io.github.anderescaray.cents.data.local.entity.RecurringRuleEntity
import io.github.anderescaray.cents.data.local.entity.RulePostponementEntity
import io.github.anderescaray.cents.data.local.entity.RuleReminderEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.DueReminder
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PendingCharge
import io.github.anderescaray.cents.domain.model.Postponement
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.Reminder
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.usecase.canPostpone
import io.github.anderescaray.cents.domain.usecase.dueOccurrences
import io.github.anderescaray.cents.domain.usecase.occursOn
import io.github.anderescaray.cents.domain.usecase.stillFits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rules, and the movements they produce.
 *
 * **Generating is idempotent, and that is the property everything else rests on.** It runs on
 * every launch and will run again from WorkManager, so writing the same charge twice is the
 * failure to design against — not a missed one. Two things prevent it: each rule records how far
 * it has got, and that record is written **inside the same database transaction** as the
 * movements it describes. A catch-up killed halfway therefore leaves nothing behind rather than
 * leaving a rule claiming a period it never wrote.
 *
 * Over detekt's limit for the same reason its interface is: answering a bill the generator
 * refused to guess needs the same DAO, the same watermark and the same "one occurrence as a
 * movement" as generating one, and a second class would share all three.
 */
@Suppress("TooManyFunctions")
@Singleton
class RoomRecurringRuleRepository
    @Inject
    constructor(
        private val ruleDao: RecurringRuleDao,
        private val reminderDao: RuleReminderDao,
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : RecurringRuleRepository {
        override fun observeRules(): Flow<List<RecurringRule>> =
            ruleDao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override fun observeRule(id: Long): Flow<RecurringRule?> = ruleDao.observeById(id).map { it?.toDomain() }

        override suspend fun findById(id: Long): RecurringRule? = ruleDao.findById(id)?.toDomain()

        override suspend fun createRule(rule: RecurringRule): Long =
            ruleDao.insert(rule.toEntity(createdAt = clock.instant()))

        /**
         * Saves the edit, and drops any postponement the edited rule no longer fits.
         *
         * A move is made against the schedule as it was. Change the frequency or the start date
         * and the moved occurrence may no longer be one of the rule's days, or its new day may
         * now be past the next occurrence. Kept, it would still steer the generator, the budget
         * and *committed this month*, for a reason nothing on screen shows. In one transaction
         * with the edit, so the two are never seen apart.
         */
        override suspend fun updateRule(rule: RecurringRule) {
            val existing = ruleDao.findById(rule.id) ?: return
            database.withTransaction {
                ruleDao.update(rule.toEntity(createdAt = existing.createdAt))
                val saved = ruleDao.findById(rule.id)?.toDomain() ?: return@withTransaction
                database
                    .rulePostponementDao()
                    .forRule(rule.id)
                    .filterNot { saved.stillFits(it.occurrence, it.postponedTo) }
                    .forEach { database.rulePostponementDao().delete(rule.id, it.occurrence) }
            }
        }

        override suspend fun setActive(
            id: Long,
            active: Boolean,
        ) {
            ruleDao.setActive(id, active)
        }

        override suspend fun deleteRule(id: Long) {
            val rule = ruleDao.findById(id) ?: return
            ruleDao.delete(rule)
        }

        override suspend fun countGenerated(id: Long): Int = ruleDao.countGenerated(id)

        /**
         * Writes every charge that has fallen due and not yet been written.
         *
         * **Only [RuleMode.AUTOMATIC] rules.** A rule set to *confirm first* is one whose amount
         * varies, and the app is not entitled to assert a number the owner has not seen (PRD
         * §4.4) — those become proposals, which is a branch of their own. Until then they are
         * skipped rather than guessed at, and their `lastGenerated` is left alone so nothing is
         * lost when proposals arrive.
         *
         * @return how many movements were written, for a screen that wants to say so.
         */
        override suspend fun generateDue(): Int {
            val today = LocalDate.now(clock)
            val moved = movedByRule()
            var written = 0
            ruleDao.activeRules().forEach { entity ->
                if (entity.mode != RuleMode.AUTOMATIC) return@forEach
                val postponed = moved[entity.id].orEmpty()
                // A postponed occurrence waits for its new day, and the ones behind it wait too:
                // the watermark is a single date, so writing April past a March still to come
                // would take March with it.
                val due =
                    entity.toDomain().dueOccurrences(today).takeWhile { date ->
                        (postponed[date] ?: date) <= today
                    }
                if (due.isEmpty()) return@forEach

                // The movements and the watermark together, or neither. Separately, a crash in
                // between would either repeat a charge or skip a period for ever.
                database.withTransaction {
                    val now = clock.instant()
                    due.forEach { date ->
                        database.transactionDao().insert(entity.movementOn(postponed[date] ?: date, now))
                    }
                    advance(entity.id, due.last())
                }
                written += due.size
            }
            return written
        }

        /** Moves the watermark, and forgets any postponement it has now passed. */
        private suspend fun advance(
            ruleId: Long,
            through: LocalDate,
        ) {
            ruleDao.setLastGenerated(ruleId, through)
            database.rulePostponementDao().deleteThrough(ruleId, through)
        }

        /** Every postponement, as rule → (occurrence → the day it was moved to). */
        private suspend fun movedByRule(): Map<Long, Map<LocalDate, LocalDate>> =
            database.rulePostponementDao().all().byRule()

        private fun List<RulePostponementEntity>.byRule(): Map<Long, Map<LocalDate, LocalDate>> =
            groupBy { it.ruleId }.mapValues { (_, rows) -> rows.associate { it.occurrence to it.postponedTo } }

        override fun observePending(): Flow<List<PendingCharge>> =
            combine(ruleDao.observeAll(), database.rulePostponementDao().observeAll()) { rows, postponements ->
                val today = LocalDate.now(clock)
                val moved = postponements.byRule()
                rows
                    .map { it.toDomain() }
                    .filter { it.mode == RuleMode.CONFIRM && it.active }
                    .mapNotNull { rule -> rule.oldestPending(today, moved[rule.id].orEmpty()) }
                    .sortedBy { it.recordOn }
            }

        override suspend fun recordPending(
            ruleId: Long,
            dueDate: LocalDate,
            amount: Money,
        ): Boolean {
            val (entity, pending) =
                ruleDao.findById(ruleId)?.let { rule -> rule.waitingOn(dueDate, amount)?.let { rule to it } }
                    ?: return false

            // The movement and the watermark together, for the same reason the catch-up writes
            // them together: separately, a crash in between records a charge the rule will
            // happily record again tomorrow. Dated the day it was moved to, if it was.
            database.withTransaction {
                val now = clock.instant()
                database.transactionDao().insert(entity.movementOn(pending.recordOn, now, amount.minorUnits))
                advance(ruleId, dueDate)
            }
            return true
        }

        override suspend fun skipPending(
            ruleId: Long,
            dueDate: LocalDate,
        ): Boolean {
            if (ruleDao.findById(ruleId)?.waitingOn(dueDate, amount = null) == null) return false
            database.withTransaction { advance(ruleId, dueDate) }
            return true
        }

        override fun observePostponements(): Flow<List<Postponement>> =
            database.rulePostponementDao().observeAll().map { rows ->
                rows.map { Postponement(it.ruleId, it.occurrence, it.postponedTo) }
            }

        override suspend fun postpone(
            ruleId: Long,
            occurrence: LocalDate,
            to: LocalDate,
        ): Boolean {
            val rule = ruleDao.findById(ruleId)?.toDomain()
            val allowed = rule != null && rule.canPostpone(occurrence, to, LocalDate.now(clock))
            if (allowed) database.rulePostponementDao().upsert(RulePostponementEntity(ruleId, occurrence, to))
            return allowed
        }

        override suspend fun cancelPostponement(
            ruleId: Long,
            occurrence: LocalDate,
        ) {
            database.rulePostponementDao().delete(ruleId, occurrence)
        }

        override suspend fun remindersFor(ruleId: Long): List<Reminder> =
            reminderDao.forRule(ruleId).map { Reminder(id = it.id, offsetDays = it.offsetDays) }

        override suspend fun setReminders(
            ruleId: Long,
            offsets: List<Int>,
        ) {
            database.withTransaction {
                reminderDao.deleteForRule(ruleId)
                offsets.distinct().sorted().forEach { offset ->
                    reminderDao.insert(RuleReminderEntity(ruleId = ruleId, offsetDays = offset))
                }
            }
        }

        /**
         * What lands today.
         *
         * A reminder fires on `occurrence + offset`, so the occurrence it is about is
         * `today - offset` — for a reminder three days early that is a date three days from now,
         * which is exactly the point and the reason [occursOn] exists rather than a list.
         */
        override suspend fun dueReminders(today: LocalDate): List<DueReminder> {
            val reminders = reminderDao.all().groupBy { it.ruleId }
            val moved = movedByRule()
            if (reminders.isEmpty() && moved.isEmpty()) return emptyList()

            return ruleDao.activeRules().flatMap { entity ->
                val rule = entity.toDomain()
                val offsets = reminders[entity.id].orEmpty().map { it.offsetDays }
                val postponed = moved[entity.id].orEmpty()
                val onTheirOwnDay =
                    offsets.mapNotNull { offset ->
                        val occurrence = today.minusDays(offset.toLong())
                        DueReminder(rule, occurrence, offset).takeIf {
                            rule.occursOn(occurrence) && occurrence !in postponed && rule.stillWants(occurrence)
                        }
                    }
                onTheirOwnDay + rule.postponedReminders(today, offsets, postponed)
            }
        }

        /**
         * Reminders about occurrences moved to a later day, counted from that day.
         *
         * **Always on the new day itself**, whether or not the rule has an on-the-day reminder:
         * moving a bill is asking to be told when it comes round again. The rule's own reminders
         * follow it too, but only those landing after the original day — the ones before it have
         * already been sent about the date it used to have.
         */
        private fun RecurringRule.postponedReminders(
            today: LocalDate,
            offsets: List<Int>,
            postponed: Map<LocalDate, LocalDate>,
        ): List<DueReminder> =
            postponed.flatMap { (occurrence, to) ->
                (offsets + 0).distinct().mapNotNull { offset ->
                    val firesOn = to.plusDays(offset.toLong())
                    DueReminder(this, occurrence, offset, postponedTo = to).takeIf {
                        firesOn == today && firesOn > occurrence && stillWants(occurrence)
                    }
                }
            }

        /**
         * Whether a reminder about [occurrence] is still worth sending.
         *
         * Only variable rules can lose interest: once the bill has been answered the app knows
         * what it cost, and chasing somebody about a figure they have already typed is how
         * notifications get turned off altogether. A fixed rule always wants its heads-up — the
         * whole point is being told *before* the money goes.
         */
        private fun RecurringRule.stillWants(occurrence: LocalDate): Boolean =
            mode != RuleMode.CONFIRM || lastGenerated == null || occurrence > lastGenerated

        /**
         * The waiting occurrence this answer is for, or null if the rule will not take it.
         *
         * Two conditions, and both of them are about writing something nobody would ever notice
         * was wrong. The date has to be the rule's **oldest** waiting occurrence, because the
         * watermark is a single date and moving it to March would take January and February with
         * it in silence. And [amount], when there is one, has to be in the rule's own currency —
         * rule 2 at the one place a figure arrives from outside the app.
         */
        private suspend fun RecurringRuleEntity.waitingOn(
            dueDate: LocalDate,
            amount: Money?,
        ): PendingCharge? {
            if (amount != null && amount.currency != currency) return null
            return toDomain()
                .oldestPending(LocalDate.now(clock), movedByRule()[id].orEmpty())
                ?.takeIf { it.dueDate == dueDate }
        }

        /**
         * The earliest occurrence this rule is still waiting on, and how many follow it.
         *
         * Null when it is up to date. Asked in three places — the list, and the guard on each of
         * the two ways of answering — so that "which one is being answered" has exactly one
         * definition rather than three that could drift apart.
         */
        private fun RecurringRule.oldestPending(
            today: LocalDate,
            postponed: Map<LocalDate, LocalDate>,
        ): PendingCharge? {
            val due = dueOccurrences(today)
            val oldest = due.firstOrNull()
            val movedTo = oldest?.let { postponed[it] }
            // Moved to a day still to come: not waiting yet, and nothing behind it is either.
            return oldest
                ?.takeIf { movedTo == null || movedTo <= today }
                ?.let { PendingCharge(this, it, laterCount = due.size - 1, postponedTo = movedTo) }
        }

        /**
         * One occurrence as a real movement.
         *
         * It carries the rule's id and nothing else unusual: from here on it is an ordinary
         * movement, counted by every report, editable and deletable. That is the whole point of
         * a rule being a template — the charge it made is as real as one typed by hand, and
         * cancelling the rule later cannot reach back and remove it.
         */
        private fun RecurringRuleEntity.movementOn(
            date: LocalDate,
            now: java.time.Instant,
            /** What it actually came to, for a rule whose amount the owner has just read off a bill. */
            amountMinor: Long = this.amountMinor,
        ) = TransactionEntity(
            accountId = accountId,
            categoryId = categoryId,
            kind = kind,
            amountMinor = amountMinor,
            currency = currency,
            date = date,
            // The rule's name, so a generated row is recognisable in a list of movements
            // without having to look up what made it.
            note = name,
            ruleId = id,
            createdAt = now,
            updatedAt = now,
        )
    }
