package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.dao.RecurringRuleDao
import io.github.anderescaray.cents.data.local.entity.RecurringRuleEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
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
 */
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
