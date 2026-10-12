package io.github.anderescaray.cents.data.repository

import androidx.room.withTransaction
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.entity.CaptureMatchEntity
import io.github.anderescaray.cents.data.local.entity.RecurringRuleEntity
import io.github.anderescaray.cents.data.local.entity.RulePostponementEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.usecase.nextUnwritten
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** A payment capture read from a notification, as it would be saved. */
internal data class CapturedPayment(
    val accountId: Long,
    val kind: TransactionKind,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate,
    val note: String,
) {
    /** An ordinary captured movement: uncategorized, so it waits under *To categorize*. */
    fun toMovement(now: Instant) =
        TransactionEntity(
            accountId = accountId,
            categoryId = null,
            kind = kind,
            amountMinor = amountMinor,
            currency = currency,
            date = date,
            note = note,
            createdAt = now,
            updatedAt = now,
        )
}

/**
 * Recognises a captured payment as a subscription's charge, so it is not counted twice
 * (`DECISIONS.md` D35; its open details are in `docs/next-improvements.md`).
 *
 * **What matches:** a charge of an active subscription in the same account, of the same kind, in
 * the same currency, dated within [WINDOW_DAYS] days of the payment either way. A postponed charge
 * is dated the day it was moved to (D28). Of several, the nearest in date wins.
 * - **A fixed subscription** matches only at its own amount, whether its charge is already written
 *   or still to come. Nothing new is saved.
 * - **A variable subscription** matches only while its bill is waiting, at any amount, and the bill
 *   takes the payment's amount on its own date.
 * - **One capture per charge.** Once a charge has been matched, another payment is saved as usual.
 *
 * **Every charge that has fallen due is written first**, as opening the app would, so one the
 * rule has not caught up with is matched as written. **A charge still to come is written by the
 * match**, dated its own day, whichever kind of rule it is. Only the oldest unwritten occurrence can
 * be: the watermark is a single date. Writing it at once keeps the balance true, since the money
 * has gone, and means nothing done to the rule later (stopped, moved to another day) can leave a
 * payment recorded nowhere.
 *
 * Everything runs in one transaction, so a catch-up or an answered bill cannot write the same
 * charge in between; both read the rule again inside their own.
 */
@Singleton
class CaptureMatcher
    @Inject
    constructor(
        private val database: CentsDatabase,
        private val rules: RecurringRuleRepository,
        private val clock: Clock,
    ) {
        /** What a match came to, for the notification that says so. */
        internal data class Match(
            val matchId: Long,
            val subscription: String,
            /** The subscription's movement: the one found, or the one the match wrote. */
            val chargeTransactionId: Long,
            /** A variable bill took the payment's amount, rather than a fixed charge covering it. */
            val filledBill: Boolean,
        )

        /** What Undo saved: the payment, as the movement with this id. */
        internal data class Undone(
            val transactionId: Long,
            val payment: CapturedPayment,
        )

        /** Takes [payment] to be a subscription's charge if one fits, or returns null to save it. */
        internal suspend fun match(payment: CapturedPayment): Match? =
            database.withTransaction {
                rules.generateDue()
                candidates(payment)
                    .minWithOrNull(
                        compareBy<Charge> { abs(ChronoUnit.DAYS.between(payment.date, it.day)) }
                            .thenBy { it.day }
                            .thenBy { it.rule.id },
                    )?.let { charge -> record(charge, payment) }
            }

        /**
         * Puts back what [matchId] did and saves the payment as an ordinary capture, as though
         * nothing had matched. Null when there is nothing to undo, as on a second tap.
         *
         * A charge the match wrote is deleted, and the rule goes back to waiting for it, but only
         * while the rule stands where the match left it. Once it has written a later occurrence, or
         * the charge has been deleted by hand, the charge stays as the rule's own: taking it then
         * would lose that occurrence for good, since the watermark cannot go back past the later one.
         */
        internal suspend fun undo(matchId: Long): Undone? =
            database.withTransaction {
                val match = database.captureMatchDao().findById(matchId) ?: return@withTransaction null
                if (match.wroteCharge) putBack(match)
                val payment = match.payment()
                val id = database.transactionDao().insert(payment.toMovement(clock.instant()))
                database.captureMatchDao().delete(matchId)
                Undone(id, payment)
            }

        private suspend fun candidates(payment: CapturedPayment): List<Charge> {
            val rules =
                database.recurringRuleDao().activeRules().filter {
                    it.accountId == payment.accountId && it.kind == payment.kind && it.currency == payment.currency
                }
            if (rules.isEmpty()) return emptyList()
            val from = payment.date.minusDays(WINDOW_DAYS)
            val to = payment.date.plusDays(WINDOW_DAYS)
            val byId = rules.associateBy { it.id }
            val written =
                database
                    .captureMatchDao()
                    .unmatchedCharges(payment.accountId, payment.kind, from, to)
                    .mapNotNull { movement ->
                        byId[movement.ruleId]
                            ?.takeIf { it.mode == RuleMode.AUTOMATIC && movement.amountMinor == payment.amountMinor }
                            ?.let { Charge.Written(it, movement) }
                    }
            val unwritten =
                rules.mapNotNull { rule ->
                    rule.unwrittenCharge()?.takeIf {
                        it.day in from..to && (rule.mode == RuleMode.CONFIRM || rule.amountMinor == payment.amountMinor)
                    }
                }
            return written + unwritten
        }

        /** The oldest occurrence the rule has not written, dated where it was moved to, if it was. */
        private suspend fun RecurringRuleEntity.unwrittenCharge(): Charge.Unwritten? {
            val occurrence = toDomain().nextUnwritten() ?: return null
            val movedTo = database.rulePostponementDao().forRule(id).firstOrNull { it.occurrence == occurrence }
            return Charge.Unwritten(this, occurrence, movedTo?.postponedTo)
        }

        private suspend fun record(
            charge: Charge,
            payment: CapturedPayment,
        ): Match {
            val now = clock.instant()
            val chargeId =
                when (charge) {
                    is Charge.Written -> {
                        charge.movement.id
                    }

                    is Charge.Unwritten -> {
                        // A variable bill takes what was paid; a fixed charge is its own amount, which is.
                        val id =
                            database.transactionDao().insert(
                                charge.rule.movementOn(charge.day, now, payment.amountMinor),
                            )
                        database.advanceRule(charge.rule.id, charge.occurrence)
                        id
                    }
                }
            val unwritten = charge as? Charge.Unwritten
            val matchId =
                database.captureMatchDao().insert(
                    CaptureMatchEntity(
                        chargeTransactionId = chargeId,
                        wroteCharge = unwritten != null,
                        ruleId = charge.rule.id,
                        occurrence = unwritten?.occurrence,
                        previousLastGenerated = unwritten?.rule?.lastGenerated,
                        postponedTo = unwritten?.postponedTo,
                        accountId = payment.accountId,
                        kind = payment.kind,
                        amountMinor = payment.amountMinor,
                        currency = payment.currency,
                        date = payment.date,
                        note = payment.note,
                        matchedAt = now,
                    ),
                )
            return Match(matchId, charge.rule.name, chargeId, filledBill = charge.rule.mode == RuleMode.CONFIRM)
        }

        private suspend fun putBack(match: CaptureMatchEntity) {
            val charge = match.chargeTransactionId?.let { database.transactionDao().findById(it) }
            // Where the match left it: the last occurrence the rule wrote is still the match's.
            val rule =
                match.ruleId
                    ?.let { database.recurringRuleDao().findById(it) }
                    ?.takeIf { it.lastGenerated == match.occurrence }
            val occurrence = match.occurrence
            if (charge == null || rule == null || occurrence == null) return
            database.transactionDao().deleteById(charge.id)
            database.recurringRuleDao().setLastGenerated(rule.id, match.previousLastGenerated)
            match.postponedTo?.let {
                database.rulePostponementDao().upsert(
                    RulePostponementEntity(rule.id, occurrence, it),
                )
            }
        }

        private fun CaptureMatchEntity.payment() = CapturedPayment(accountId, kind, amountMinor, currency, date, note)

        /** One charge of one subscription, and the day it is dated. */
        private sealed interface Charge {
            val rule: RecurringRuleEntity
            val day: LocalDate

            data class Written(
                override val rule: RecurringRuleEntity,
                val movement: TransactionEntity,
            ) : Charge {
                override val day: LocalDate get() = movement.date
            }

            data class Unwritten(
                override val rule: RecurringRuleEntity,
                val occurrence: LocalDate,
                val postponedTo: LocalDate?,
            ) : Charge {
                override val day: LocalDate get() = postponedTo ?: occurrence
            }
        }

        private companion object {
            /** How far either side of a charge's day its payment can be notified (D35). */
            const val WINDOW_DAYS = 3L
        }
    }
