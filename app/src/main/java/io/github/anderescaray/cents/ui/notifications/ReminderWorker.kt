package io.github.anderescaray.cents.ui.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.domain.repository.InvestmentReminderRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.usecase.withoutValueThisWeek
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The daily look at what is coming: the subscriptions, and on Fridays the investments.
 *
 * **It asks a question rather than keeping a schedule**, and that is the whole design. Nothing is
 * booked in advance for each reminder: once a day the worker asks *what lands today*, and the
 * answer is computed from the rules. A reminder edited, a rule stopped, a bill answered, the
 * clock moved, the phone rebooted — none of them need anything cancelled or rescheduled, because
 * there was never a queue of alarms to go stale. Booking a notification per occurrence would be
 * a second copy of when the rent is, and the two would drift.
 *
 * It is therefore also safe to run twice: the same rule on the same day is the same notification
 * id, so the system replaces it rather than stacking a second one.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    /**
     * Reached through an entry point rather than `@HiltWorker`.
     *
     * That annotation needs `androidx.hilt:hilt-work`, a compiler for it and a custom
     * `Configuration.Provider` on the application — three moving parts to inject two objects
     * into one class (rule 11). This is the same graph, asked for directly.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun repository(): RecurringRuleRepository

        fun notifier(): ReminderNotifier

        fun clock(): Clock

        fun portfolio(): PortfolioRepository

        fun investmentReminder(): InvestmentReminderRepository

        fun investmentNotifier(): InvestmentReminderNotifier
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val notifier = deps.notifier()
        // Nothing to post to, so nothing to compute. The editor is what tells the owner about
        // this, because a worker cannot.
        if (!notifier.canNotify) return Result.success()

        val today = LocalDate.now(deps.clock())
        val subscriptions = runCatching { deps.repository().dueReminders(today).forEach(notifier::notify) }
        val investments = runCatching { remindInvestments(deps, today) }
        return if (subscriptions.isSuccess && investments.isSuccess) Result.success() else Result.retry()
    }

    /**
     * On Fridays, the assets with no value yet this week (`DECISIONS.md` D40), unless the owner
     * turned the reminder off. Asked the same way the subscriptions are: from what is recorded,
     * with nothing booked ahead.
     */
    private suspend fun remindInvestments(
        deps: Dependencies,
        today: LocalDate,
    ) {
        if (today.dayOfWeek != DayOfWeek.FRIDAY) return
        if (!deps.investmentReminder().observeEnabled().first()) return
        deps.investmentNotifier().notify(withoutValueThisWeek(deps.portfolio().observePositions().first(), today))
    }

    companion object {
        private const val UNIQUE_NAME = "subscription-reminders"

        /** Early enough to be useful the same day, late enough not to be the alarm clock. */
        private val RUNS_AT: LocalTime = LocalTime.of(9, 0)

        /**
         * Books the daily check, or leaves the booked one alone.
         *
         * `UPDATE` rather than `REPLACE`: replacing resets the period, so an app opened every
         * morning would push the run later and later and eventually never reach it.
         */
        fun schedule(
            context: Context,
            clock: Clock,
        ) {
            val request =
                PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(untilNextRun(clock).toMinutes(), TimeUnit.MINUTES)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** How long until the next [RUNS_AT] in the phone's own time zone. */
        internal fun untilNextRun(clock: Clock): Duration {
            val now = ZonedDateTime.now(clock)
            val todaysRun = now.with(RUNS_AT)
            val next = if (todaysRun.isAfter(now)) todaysRun else todaysRun.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
