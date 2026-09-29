package io.github.anderescaray.cents.ui.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.domain.repository.CaptureRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Hears every notification the phone shows, once the owner grants *Notification access*, and
 * hands the text of each one to [CaptureRecorder] (PRD §4.6, `DECISIONS.md` D29).
 *
 * **This is the only part of capture that needs Android, and it decides nothing.** Whether an app
 * was chosen, whether the text is a payment, and whether it was already written are all the
 * recorder's, where they are tested. Android hands this every app's notifications; the recorder
 * drops those from apps nobody chose before reading a word of them, and nothing of them is kept.
 *
 * Reached through an entry point rather than `@AndroidEntryPoint`, the same way the workers are,
 * so the graph is asked for one object when a notification arrives.
 */
class CaptureListenerService : NotificationListenerService() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun recorder(): CaptureRecorder
    }

    /** Off the main thread (rule 4), and one failure never stops the next notification. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seen = SeenNotifications()

    private val recorder: CaptureRecorder by lazy {
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).recorder()
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        val text = notification.textToRead()
        if (text != null && seen.isNew(notification.key, text)) {
            val postedAt = Instant.ofEpochMilli(notification.postTime)
            scope.launch {
                runCatching { recorder.record(notification.packageName, text, postedAt) }
                    // Never the text: it is the owner's money, and a log is the one place it must not go.
                    .onFailure { Log.w(TAG, "a notification could not be captured", it) }
            }
        }
    }

    /**
     * What there is to read, or null for the ones never worth reading: this app's own reminders,
     * which are not payments, and a group's summary, which repeats its children.
     */
    private fun StatusBarNotification.textToRead(): String? {
        val own = packageName == this@CaptureListenerService.packageName
        val summary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val extras = notification.extras
        return if (own || summary) {
            null
        } else {
            CaptureText.of(
                title = extras.getCharSequence(Notification.EXTRA_TITLE),
                text = extras.getCharSequence(Notification.EXTRA_TEXT),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Capture"
    }
}
