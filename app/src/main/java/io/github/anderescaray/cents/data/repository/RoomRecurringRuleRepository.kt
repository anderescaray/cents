package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.dao.RecurringRuleDao
import io.github.anderescaray.cents.data.local.entity.RecurringRuleEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PendingCharge
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.usecase.dueOccurrences
import kotlinx.coroutines.flow.Flow
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
        private val database: CentsDatabase,
        private val clock: Clock,
    ) : RecurringRuleRepository {
        override fun observeRules(): Flow<List<RecurringRule>> =
            ruleDao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override fun observeRule(id: Long): Flow<RecurringRule?> = ruleDao.observeById(id).map { it?.toDomain() }

        override suspend fun findById(id: Long): RecurringRule? = ruleDao.findById(id)?.toDomain()

        override suspend fun createRule(rule: RecurringRule): Long =
            ruleDao.insert(rule.toEntity(createdAt = clock.instant()))

        override suspend fun updateRule(rule: RecurringRule) {
            val existing = ruleDao.findById(rule.id) ?: return
            ruleDao.update(rule.toEntity(createdAt = existing.createdAt))
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
            var written = 0
            ruleDao.activeRules().forEach { entity ->
                if (entity.mode != RuleMode.AUTOMATIC) return@forEach
                val rule = entity.toDomain()
                val due = rule.dueOccurrences(today)
                if (due.isEmpty()) return@forEach

                // The movements and the watermark together, or neither. Separately, a crash in
                // between would either repeat a charge or skip a period for ever.
                database.withTransaction {
                    val now = clock.instant()
                    due.forEach { date ->
                        database.transactionDao().insert(entity.movementOn(date, now))
                    }
                    ruleDao.setLastGenerated(entity.id, due.last())
                }
                written += due.size
            }
            return written
        }

        override fun observePending(): Flow<List<PendingCharge>> =
            ruleDao.observeAll().map { rows ->
                val today = LocalDate.now(clock)
                rows
                    .map { it.toDomain() }
                    .filter { it.mode == RuleMode.CONFIRM && it.active }
                    .mapNotNull { rule -> rule.oldestPending(today) }
                    .sortedBy { it.dueDate }
            }

        override suspend fun recordPending(
            ruleId: Long,
            dueDate: LocalDate,
            amount: Money,
        ): Boolean {
            val entity = ruleDao.findById(ruleId)?.takeIf { it.accepts(dueDate, amount) } ?: return false

            // The movement and the watermark together, for the same reason the catch-up writes
            // them together: separately, a crash in between records a charge the rule will
            // happily record again tomorrow.
            database.withTransaction {
                val now = clock.instant()
                database.transactionDao().insert(entity.movementOn(dueDate, now, amount.minorUnits))
                ruleDao.setLastGenerated(ruleId, dueDate)
            }
            return true
        }

        override suspend fun skipPending(
            ruleId: Long,
            dueDate: LocalDate,
        ): Boolean {
            if (ruleDao.findById(ruleId)?.accepts(dueDate, amount = null) != true) return false
            ruleDao.setLastGenerated(ruleId, dueDate)
            return true
        }

        /**
         * Whether this rule will take that answer for that date.
         *
         * Two conditions, and both of them are about writing something nobody would ever notice
         * was wrong. The date has to be the rule's **oldest** waiting occurrence, because the
         * watermark is a single date and moving it to March would take January and February with
         * it in silence. And [amount], when there is one, has to be in the rule's own currency —
         * rule 2 at the one place a figure arrives from outside the app.
         */
        private fun RecurringRuleEntity.accepts(
            dueDate: LocalDate,
            amount: Money?,
        ): Boolean {
            if (amount != null && amount.currency != currency) return false
            return toDomain().oldestPending(LocalDate.now(clock))?.dueDate == dueDate
        }

        /**
         * The earliest occurrence this rule is still waiting on, and how many follow it.
         *
         * Null when it is up to date. Asked in three places — the list, and the guard on each of
         * the two ways of answering — so that "which one is being answered" has exactly one
         * definition rather than three that could drift apart.
         */
        private fun RecurringRule.oldestPending(today: LocalDate): PendingCharge? {
            val due = dueOccurrences(today)
            return due.firstOrNull()?.let { PendingCharge(this, it, laterCount = due.size - 1) }
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
