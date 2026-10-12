package io.github.anderescaray.cents.ui.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.domain.repository.CaptureRecorder
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * **Undo** on a capture notification that matched a subscription (`DECISIONS.md` D35): the payment
 * is saved as an ordinary capture, and the notification becomes the one an ordinary capture shows.
 *
 * A broadcast rather than the activity, so it works with the app closed and without opening it.
 * Reached through an entry point, the same way the listener and the workers are.
 */
class CaptureUndoReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun recorder(): CaptureRecorder

        fun settings(): CaptureSettingsRepository

        fun captureNotifier(): CaptureNotifier
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val matchId = intent.getLongExtra(CaptureNotifier.EXTRA_MATCH_ID, 0L).takeIf { it > 0 } ?: return
        val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        scope.launch {
            try {
                undo(dependencies, matchId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "Capture"

        /** Off the main thread (rule 4); `goAsync` keeps the process alive until it finishes. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** What Undo does, apart from the broadcast that asks for it. */
        internal suspend fun undo(
            dependencies: Dependencies,
            matchId: Long,
        ) {
            val notifier = dependencies.captureNotifier()
            try {
                val saved = dependencies.recorder().undoMatch(matchId)
                // Gone once done, or once a second tap found nothing left to do. Kept when undoing
                // failed, so it can be tried again.
                notifier.cancelMatch(matchId)
                if (saved != null) {
                    val setup = dependencies.settings().observe().first()
                    notifier.notify(saved, enabled = setup.enabled && setup.notifyOnCapture)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                // Never the payment: it is the owner's money, and a log is the one place it must not go.
                Log.w(TAG, "a capture match could not be undone", failure)
            }
        }
    }
}
