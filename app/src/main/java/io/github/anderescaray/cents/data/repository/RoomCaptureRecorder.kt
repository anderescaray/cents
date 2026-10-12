package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.dao.AccountDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.data.local.entity.AccountEntity
import io.github.anderescaray.cents.domain.model.CaptureOutcome
import io.github.anderescaray.cents.domain.model.CaptureOutcome.IgnoreReason
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.CaptureRecorder
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import io.github.anderescaray.cents.domain.usecase.CaptureParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One notification in, one movement or nothing out (PRD §4.6, `DECISIONS.md` D29).
 *
 * In order: capture is on, the app is one the owner chose, its account still exists, the text
 * has a word and an amount, the amount is in the account's currency, the same payment was not
 * already captured in the same minute, and it is not a subscription's charge ([CaptureMatcher],
 * D35). Only then is anything kept, and it is an ordinary movement with **no category**, which puts
 * it under *To categorize* (`docs/PRD.md`). The text is only read once the first three pass: most
 * notifications are from apps nobody chose.
 *
 * **"The same minute" is remembered here, in memory, and only for captured notifications.** It
 * is not asked of the ledger, because a movement restored from a backup, written by a
 * subscription or typed by hand in that minute is not a notification, and would otherwise swallow
 * a real payment of the same amount. The memory lasts as long as the process, which is far longer
 * than a minute; a duplicate that arrives across a restart of the app is the one it can miss.
 */
@Singleton
class RoomCaptureRecorder
    @Inject
    constructor(
        private val settings: CaptureSettingsRepository,
        private val accountDao: AccountDao,
        private val transactionDao: TransactionDao,
        private val matcher: CaptureMatcher,
        private val clock: Clock,
    ) : CaptureRecorder {
        /**
         * One notification at a time. Wallet and the bank can announce the same payment in the
         * same instant, and without this both could check for the other before either is written.
         */
        private val oneAtATime = Mutex()

        /** What was captured lately, to recognise the same payment a second time. */
        private val recent = ArrayDeque<Seen>()

        override suspend fun record(
            packageName: String,
            text: String,
            postedAt: Instant,
            announced: Boolean,
        ): CaptureOutcome =
            when (val plan = plan(settings.observe().first(), packageName, text)) {
                is Plan.Skip -> CaptureOutcome.Ignored(plan.reason)
                is Plan.Write -> write(plan, text, postedAt, announced)
            }

        /** Whether it is on, the app is chosen and its account is still there, before any reading. */
        private suspend fun plan(
            setup: CaptureSettings,
            packageName: String,
            text: String,
        ): Plan {
            val source = setup.sourceFor(packageName)?.takeIf { setup.enabled }
            val account = source?.let { accountDao.findById(it.accountId) }?.takeUnless { it.archived }
            return when {
                !setup.enabled -> {
                    Plan.Skip(IgnoreReason.OFF)
                }

                source == null -> {
                    Plan.Skip(IgnoreReason.NOT_CHOSEN)
                }

                account == null -> {
                    Plan.Skip(IgnoreReason.NO_ACCOUNT)
                }

                else -> {
                    read(
                        CaptureParser.read(
                            text,
                            setup.wordsFor(packageName, TransactionKind.EXPENSE),
                            setup.wordsFor(packageName, TransactionKind.INCOME),
                            setup.ignoreWords,
                        ),
                        account,
                    )
                }
            }
        }

        /**
         * What the parser read, then its currency against the account's. The reading is
         * `CaptureParser.read`, the same one Settings › Capture's *Try a notification* shows.
         */
        private fun read(
            reading: CaptureParser.Reading,
            account: AccountEntity,
        ): Plan =
            when (reading) {
                CaptureParser.Reading.NoWord -> {
                    Plan.Skip(IgnoreReason.NO_WORD)
                }

                is CaptureParser.Reading.NoAmount -> {
                    Plan.Skip(IgnoreReason.NO_AMOUNT)
                }

                is CaptureParser.Reading.SeveralAmounts -> {
                    Plan.Skip(IgnoreReason.SEVERAL_AMOUNTS)
                }

                is CaptureParser.Reading.Movement -> {
                    if (reading.amount.currency != account.currency) {
                        Plan.Skip(IgnoreReason.OTHER_CURRENCY)
                    } else {
                        Plan.Write(account, reading.word.kind, reading.amount.minorUnits)
                    }
                }
            }

        private suspend fun write(
            plan: Plan.Write,
            text: String,
            postedAt: Instant,
            announced: Boolean,
        ): CaptureOutcome =
            oneAtATime.withLock {
                val seen = Seen(plan.kind, plan.amountMinor, plan.account.currency, postedAt)
                recent.removeAll { Duration.between(it.at, postedAt).abs() > FORGET_AFTER }
                if (recent.any { it.isSamePaymentAs(seen) }) {
                    CaptureOutcome.Duplicate
                } else {
                    val payment =
                        CapturedPayment(
                            accountId = plan.account.id,
                            kind = plan.kind,
                            amountMinor = plan.amountMinor,
                            currency = plan.account.currency,
                            date = postedAt.atZone(clock.zone).toLocalDate(),
                            // The whole text, so the merchant or the sender is in it (D29).
                            note =
                                CaptureParser
                                    .readable(text)
                                    .trim()
                                    .replace(WHITESPACE, " ")
                                    .clippedTo(MAX_NOTE_LENGTH),
                        )
                    val outcome =
                        (
                            if (announced) {
                                matcher.match(
                                    payment,
                                )
                            } else {
                                null
                            }
                        )?.let { matched(it, payment, plan.account.name) }
                            ?: recorded(
                                transactionDao.insert(payment.toMovement(clock.instant())),
                                payment,
                                plan.account.name,
                            )
                    // A matched payment counts too: the bank and Wallet announcing the same charge
                    // in the same minute is still one payment.
                    recent.addLast(seen)
                    outcome
                }
            }

        override suspend fun undoMatch(matchId: Long): CaptureOutcome.Recorded? =
            oneAtATime.withLock {
                matcher.undo(matchId)?.let { undone ->
                    val account = accountDao.findById(undone.payment.accountId)
                    recorded(undone.transactionId, undone.payment, account?.name.orEmpty())
                }
            }

        private fun recorded(
            transactionId: Long,
            payment: CapturedPayment,
            accountName: String,
        ) = CaptureOutcome.Recorded(
            transactionId = transactionId,
            amount = Money(payment.amountMinor, payment.currency),
            kind = payment.kind,
            accountName = accountName,
        )

        private fun matched(
            match: CaptureMatcher.Match,
            payment: CapturedPayment,
            accountName: String,
        ) = CaptureOutcome.Matched(
            matchId = match.matchId,
            subscription = match.subscription,
            amount = Money(payment.amountMinor, payment.currency),
            kind = payment.kind,
            accountName = accountName,
            filledBill = match.filledBill,
            chargeTransactionId = match.chargeTransactionId,
        )

        /** One captured payment, as much of it as "the same one again" needs. */
        private data class Seen(
            val kind: TransactionKind,
            val amountMinor: Long,
            val currency: String,
            val at: Instant,
        ) {
            /**
             * "The same minute" as the owner said it: within a minute of each other, either way,
             * so 10:00:59 and 10:01:02 are one payment rather than two clock minutes.
             */
            fun isSamePaymentAs(other: Seen): Boolean =
                kind == other.kind &&
                    amountMinor == other.amountMinor &&
                    currency == other.currency &&
                    Duration.between(at, other.at).abs() < SAME_MINUTE
        }

        private sealed interface Plan {
            data class Skip(
                val reason: IgnoreReason,
            ) : Plan

            data class Write(
                val account: AccountEntity,
                val kind: TransactionKind,
                val amountMinor: Long,
            ) : Plan
        }

        private companion object {
            val SAME_MINUTE: Duration = Duration.ofMinutes(1)

            /** Long after any duplicate could arrive, so the memory stays a handful of entries. */
            val FORGET_AFTER: Duration = Duration.ofMinutes(10)

            /** Long enough for any bank's sentence, short enough for a list row. */
            const val MAX_NOTE_LENGTH = 200
            val WHITESPACE = Regex("\\s+")

            /** Cut to [max] characters, never between the two halves of an emoji. */
            fun String.clippedTo(max: Int): String =
                when {
                    length <= max -> this
                    this[max - 1].isHighSurrogate() -> take(max - 1)
                    else -> take(max)
                }
        }
    }
