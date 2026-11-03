package io.github.anderescaray.cents.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anderescaray.cents.MainActivity
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.ui.language.AppLanguages
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Friday reminder (`DECISIONS.md` D40): which assets have no value yet this week. Tapping it
 * opens Invested, where each account's *Update values* is one tap further.
 *
 * A channel of its own, so the owner can silence it in the phone's settings without silencing the
 * subscriptions, and the same id every week, so an unread one is replaced rather than stacked.
 */
@Singleton
class InvestmentReminderNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
    ) {
        fun notify(waiting: List<Position>) {
            if (waiting.isEmpty() || !notificationsAllowed(appContext)) return
            val context = AppLanguages.applyTo(appContext)
            ensureChannel(context)
            val body =
                if (waiting.size == 1) {
                    context.getString(R.string.investment_reminder_one, waiting.single().holding.name)
                } else {
                    context.resources.getQuantityString(R.plurals.investment_reminder_many, waiting.size, waiting.size)
                }
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_trend_up)
                    .setContentTitle(context.getString(R.string.investment_reminder_title))
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(openInvestments())
                    .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        private fun openInvestments(): PendingIntent {
            val intent =
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_INVESTMENTS, true)
                }
            return PendingIntent.getActivity(
                appContext,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.investment_reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.investment_reminder_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        companion object {
            /** Set on the intent of the Friday reminder. */
            const val EXTRA_OPEN_INVESTMENTS = "io.github.anderescaray.cents.OPEN_INVESTMENTS"

            private const val CHANNEL_ID = "investment_reminders"

            /** Outside the range of the subscription reminders' ids, which come from rule ids. */
            private const val NOTIFICATION_ID = -40
        }
    }
