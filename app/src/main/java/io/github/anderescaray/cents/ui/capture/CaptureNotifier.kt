package io.github.anderescaray.cents.ui.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anderescaray.cents.MainActivity
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CaptureOutcome
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.notifications.notificationsAllowed
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun notify(
            outcome: CaptureOutcome,
            enabled: Boolean,
        ) {
            if (!enabled || outcome !is CaptureOutcome.Recorded || !allowed(context)) return
            ensureChannel()
            val amount = if (outcome.kind == TransactionKind.EXPENSE) -outcome.amount else outcome.amount
            val body =
                context.getString(
                    R.string.capture_notification_body,
                    MoneyFormatter.format(amount, withSign = true),
                    outcome.accountName,
                )
            val publicVersion =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_tab_transactions)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.capture_notification_public))
                    .build()
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_tab_transactions)
                    .setContentTitle(
                        context.getString(
                            if (outcome.kind == TransactionKind.INCOME) {
                                R.string.capture_notification_income
                            } else {
                                R.string.capture_notification_expense
                            },
                        ),
                    ).setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(intentFor(outcome.transactionId))
                    .build()
            NotificationManagerCompat.from(context).notify("capture:${outcome.transactionId}", 0, notification)
        }

        private fun intentFor(transactionId: Long): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = "$EXTRA_OPEN_CAPTURED.$transactionId"
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_CAPTURED, transactionId)
                }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ensureChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.capture_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.capture_notification_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        companion object {
            const val EXTRA_OPEN_CAPTURED = "io.github.anderescaray.cents.OPEN_CAPTURED_TRANSACTION"
            internal const val CHANNEL_ID = "captured_movements"

            fun allowed(context: Context): Boolean =
                notificationsAllowed(context) &&
                    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                    context
                        .getSystemService(NotificationManager::class.java)
                        .getNotificationChannel(CHANNEL_ID)
                        ?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
