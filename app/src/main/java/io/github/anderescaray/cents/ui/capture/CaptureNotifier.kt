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
import io.github.anderescaray.cents.ui.language.AppLanguages
import io.github.anderescaray.cents.ui.notifications.notificationsAllowed
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
    ) {
        fun notify(
            outcome: CaptureOutcome,
            enabled: Boolean,
        ) {
            val announced = outcome is CaptureOutcome.Recorded || outcome is CaptureOutcome.Matched
            if (!enabled || !announced || !allowed(appContext)) return
            // In the language chosen in Settings, which before Android 13 the app applies itself.
            val context = AppLanguages.applyTo(appContext)
            when (outcome) {
                is CaptureOutcome.Recorded -> recorded(context, outcome)
                is CaptureOutcome.Matched -> matched(context, outcome)
                is CaptureOutcome.Duplicate, is CaptureOutcome.Ignored -> Unit
            }
        }

        /** Takes back a match's notification, once Undo has been tapped on it. */
        fun cancelMatch(matchId: Long) {
            NotificationManagerCompat.from(appContext).cancel(matchTag(matchId), 0)
        }

        private fun recorded(
            context: Context,
            outcome: CaptureOutcome.Recorded,
        ) {
            val amount = if (outcome.kind == TransactionKind.EXPENSE) -outcome.amount else outcome.amount
            val body =
                context.getString(
                    R.string.capture_notification_body,
                    MoneyFormatter.formatSigned(amount),
                    outcome.accountName,
                )
            val notification =
                builder(context, R.string.capture_notification_public)
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
                    .setContentIntent(intentFor(outcome.transactionId))
                    .build()
            NotificationManagerCompat.from(context).notify("capture:${outcome.transactionId}", 0, notification)
        }

        /**
         * A payment taken to be a subscription's charge (D35): which one, and **Undo**, the
         * notification's only action. Tapping it opens the subscription's movement.
         */
        private fun matched(
            context: Context,
            outcome: CaptureOutcome.Matched,
        ) {
            val amount = if (outcome.kind == TransactionKind.EXPENSE) -outcome.amount else outcome.amount
            val body =
                context.getString(
                    if (outcome.filledBill) {
                        R.string.capture_notification_filled_body
                    } else {
                        R.string.capture_notification_matched_body
                    },
                    MoneyFormatter.formatSigned(amount),
                    outcome.accountName,
                )
            val title =
                context.getString(
                    if (outcome.filledBill) {
                        R.string.capture_notification_filled
                    } else {
                        R.string.capture_notification_matched
                    },
                    outcome.subscription,
                )
            val notification =
                builder(context, R.string.capture_notification_matched_public)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(intentFor(outcome.chargeTransactionId))
                    .addAction(0, context.getString(R.string.capture_notification_undo), undoIntentFor(outcome.matchId))
                    .build()
            NotificationManagerCompat.from(context).notify(matchTag(outcome.matchId), 0, notification)
        }

        /** What every capture notification shares, with [publicText] for the lock screen. */
        private fun builder(
            context: Context,
            publicText: Int,
        ): NotificationCompat.Builder {
            ensureChannel(context)
            val publicVersion =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_tab_transactions)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(publicText))
                    .build()
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tab_transactions)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
        }

        private fun intentFor(transactionId: Long): PendingIntent {
            val intent =
                Intent(appContext, MainActivity::class.java).apply {
                    action = "$EXTRA_OPEN_CAPTURED.$transactionId"
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_CAPTURED, transactionId)
                }
            return PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** Undo, handled by [CaptureUndoReceiver] so it works with the app closed. */
        private fun undoIntentFor(matchId: Long): PendingIntent {
            val intent =
                Intent(appContext, CaptureUndoReceiver::class.java).apply {
                    // One action per match, so two notifications never share a PendingIntent.
                    action = "$ACTION_UNDO.$matchId"
                    putExtra(EXTRA_MATCH_ID, matchId)
                }
            return PendingIntent.getBroadcast(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ensureChannel(context: Context) {
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
            internal const val ACTION_UNDO = "io.github.anderescaray.cents.UNDO_CAPTURE_MATCH"
            internal const val EXTRA_MATCH_ID = "io.github.anderescaray.cents.CAPTURE_MATCH_ID"

            internal fun matchTag(matchId: Long) = "capture-match:$matchId"

            fun allowed(context: Context): Boolean =
                notificationsAllowed(context) &&
                    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                    context
                        .getSystemService(NotificationManager::class.java)
                        .getNotificationChannel(CHANNEL_ID)
                        ?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
