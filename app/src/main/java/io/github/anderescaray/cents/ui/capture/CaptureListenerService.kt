package io.github.anderescaray.cents.ui.capture

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.repository.CaptureRecorder
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Hears the notifications the phone shows, once the owner grants *Notification access*, and
 * hands the text of the ones from chosen apps to [CaptureRecorder] (PRD §4.6, `DECISIONS.md` D29).
 *
 * **It reads nothing from an app nobody chose.** Android hands a listener every app's
 * notifications; this one asks [CaptureText.worthReading] first, with capture's switch and chosen
 * apps kept current from settings, and only then looks at the text. Whether the text is a payment,
 * and whether it was already written, are the recorder's to decide, where they are tested.
 *
 * Reached through an entry point rather than `@AndroidEntryPoint`, the same way the workers are.
 */
class CaptureListenerService : NotificationListenerService() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun recorder(): CaptureRecorder

        fun settings(): CaptureSettingsRepository

        fun captureNotifier(): CaptureNotifier
    }

    /** Off the main thread (rule 4), and one failure never stops the next notification. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seen = SeenNotifications()

    /** Off until settings say otherwise, so nothing is read before they have been. */
    @Volatile
    private var setup = CaptureSettings()
    private var following: Job? = null

    private val dependencies: Dependencies by lazy {
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
    }

    override fun onListenerConnected() {
        following?.cancel()
        following = scope.launch { dependencies.settings().observe().collect { setup = it } }
    }

    /**
     * The system has let go of the listener, which aggressive battery managers do. Asked back
     * rather than left dead until the owner toggles access or restarts the phone.
     */
    override fun onListenerDisconnected() {
        following?.cancel()
        requestRebind(ComponentName(this, CaptureListenerService::class.java))
    }

    override fun onNotificationPosted(posted: StatusBarNotification) {
        val current = setup
        val worthReading =
            CaptureText.worthReading(
                packageName = posted.packageName,
                ownPackage = packageName,
                chosen = current.sources.mapTo(HashSet()) { it.packageName },
                enabled = current.enabled,
                ongoing = (posted.notification.flags and IGNORED_FLAGS) != 0,
                groupSummary = (posted.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            )
        val text = if (worthReading) posted.text() else null
        if (text != null && seen.isNew(posted.key, posted.notification.`when`, text)) {
            val postedAt = Instant.ofEpochMilli(posted.postTime)
            scope.launch { hand(posted, text, postedAt) }
        }
    }

    override fun onNotificationRemoved(removed: StatusBarNotification) {
        seen.forget(removed.key)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun hand(
        posted: StatusBarNotification,
        text: String,
        postedAt: Instant,
    ) {
        val outcome =
            try {
                dependencies.recorder().record(posted.packageName, text, postedAt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                // Forgotten, so the app's next repost of it is tried again rather than taken as seen.
                seen.forget(posted.key)
                // Never the text: it is the owner's money, and a log is the one place it must not go.
                Log.w(TAG, "a notification could not be captured", failure)
                return
            }
        try {
            val current = setup
            dependencies.captureNotifier().notify(outcome, enabled = current.enabled && current.notifyOnCapture)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            Log.w(TAG, "a captured movement could not be announced", failure)
        }
    }

    private fun StatusBarNotification.text(): String? {
        val extras = notification.extras
        return CaptureText.of(
            title = extras.getCharSequence(Notification.EXTRA_TITLE),
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.toList(),
        )
    }

    private companion object {
        const val TAG = "Capture"
        const val IGNORED_FLAGS = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE
    }
}
