package io.github.anderescaray.cents.ui.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anderescaray.cents.MainActivity
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.DueReminder
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a subscription says when the app is not open.
 *
 * It lives in `ui` rather than in `data` on purpose: a notification is this app's interface at a
 * moment when no screen is showing, and it is made of the same two things every screen is — a
 * string from `strings.xml` and an amount formatted by [MoneyFormatter]. It imports nothing from
 * Room or Retrofit, which is what the dependency rule actually forbids.
 *
 * **Two different sentences, because they are two different requests.** A fixed subscription is
 * being announced — *Netflix, 12,99 €, in 3 days* — and there is nothing to do about it. A
 * variable one is being **asked about**, so it says so and its tap lands on the screen that takes
 * the figure. A notification you cannot act on and one you must act on should not look alike.
 */
@Singleton
class ReminderNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /**
         * Whether the system will actually show anything.
         *
         * Asked before the worker bothers, and asked again by the editor so it can say the
         * reminders are being written down and thrown away. On Android 13 and later this is a
         * permission the owner grants; before that it is the app-wide switch in system settings,
         * which they can also have turned off.
         */
        val canNotify: Boolean get() = notificationsAllowed(context)

        fun notify(due: DueReminder) {
            if (!canNotify) return
            ensureChannel()

            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_tab_transactions)
                    .setContentTitle(due.rule.name)
                    .setContentText(bodyFor(due))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(bodyFor(due)))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(intentFor(due))
                    .build()

            // Keyed by rule and occurrence, so the same subscription on two different months is
            // two notifications and the same one twice is one. A fixed id would let a second
            // rule quietly replace the first before it had been read.
            NotificationManagerCompat.from(context).notify(due.notificationId, notification)
        }

        private fun bodyFor(due: DueReminder): String {
            val whenIt = whenLabel(due)
            return if (due.asksForAmount) {
                context.getString(R.string.reminder_variable_body, whenIt)
            } else {
                context.getString(
                    R.string.reminder_fixed_body,
                    MoneyFormatter.format(due.rule.amount),
                    whenIt,
                )
            }
        }

        /**
         * *in 3 days*, *today*, *3 days ago* — the part that makes the sentence worth reading.
         *
         * Counted from the offset rather than from the dates, because that is what the reminder
         * is: the owner chose "three days before", and being told "three days" is the promise
         * being kept.
         */
        private fun whenLabel(due: DueReminder): String =
            when {
                due.offsetDays == 0 -> {
                    context.getString(R.string.reminder_when_today)
                }

                due.offsetDays < 0 -> {
                    context.resources.getQuantityString(
                        R.plurals.reminder_when_before,
                        -due.offsetDays,
                        -due.offsetDays,
                    )
                }

                else -> {
                    context.resources.getQuantityString(
                        R.plurals.reminder_when_after,
                        due.offsetDays,
                        due.offsetDays,
                    )
                }
            }

        /**
         * Where tapping it goes.
         *
         * A variable one lands on the screen that takes the figure, because sending somebody to
         * the front door of the app to hunt for the thing they were just asked about is most of
         * the way to not doing it. A fixed one opens the app as normal: there is nothing to do.
         */
        private fun intentFor(due: DueReminder): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (due.asksForAmount) putExtra(EXTRA_OPEN_PENDING, true)
                }
            return PendingIntent.getActivity(
                context,
                due.notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.reminder_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        companion object {
            /** Set on the intent when the notification was asking for an amount. */
            const val EXTRA_OPEN_PENDING = "io.github.anderescaray.cents.OPEN_PENDING"

            private const val CHANNEL_ID = "subscription_reminders"
        }
    }

/**
 * Whether the system will show anything this app posts.
 *
 * A free function as well as a property on the notifier, because the editor asks the same
 * question and has no reason to build a notifier to do it — and the answer must not be able to
 * differ between the screen that promises a reminder and the worker that would send it.
 */
fun notificationsAllowed(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

/** One notification per rule per occurrence, and the same one twice is still one. */
private val DueReminder.notificationId: Int
    get() = (rule.id * PRIME + occurrence.toEpochDay()).toInt()

private const val PRIME = 31L
