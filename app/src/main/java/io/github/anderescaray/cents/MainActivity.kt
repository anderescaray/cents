package io.github.anderescaray.cents

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.anderescaray.cents.ui.CentsApp
import io.github.anderescaray.cents.ui.lock.AppLockGate
import io.github.anderescaray.cents.ui.notifications.ReminderNotifier
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.Today
import javax.inject.Inject

/**
 * A `FragmentActivity` rather than a plain `ComponentActivity`, and only because of the lock:
 * `BiometricPrompt` puts up a fragment of its own and will take nothing less. It is a superclass
 * of what was here before, so nothing else changes.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    /**
     * Whether we were opened by a notification asking for an amount.
     *
     * Read as state so that a tap arriving while the app is already open lands somewhere too:
     * the activity is `singleTop`-ish through the intent flags, so a second tap comes through
     * [onNewIntent] rather than building a new activity, and without this it would do nothing
     * at all.
     */
    private var openPending by mutableStateOf(false)

    @Inject
    lateinit var today: Today

    /**
     * Tells [today] to look at the clock again whenever the system says time moved.
     *
     * `TIME_TICK` arrives every minute and is the one that carries midnight: `DATE_CHANGED` alone
     * is not sent when the phone was asleep across it. The other two are the owner changing the
     * clock or crossing a time zone. Registered only while the activity is visible, which is the
     * only time a stale *Today* can be seen.
     */
    private val timeChanges =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                today.refresh()
            }
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPending = intent.wantsPending
    }

    override fun onStart() {
        super.onStart()
        today.refresh()
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        // Not exported: these are system broadcasts, which reach a receiver that is not.
        ContextCompat.registerReceiver(this, timeChanges, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(timeChanges)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPending = intent.wantsPending
        enableEdgeToEdge()
        setContent {
            CentsTheme {
                // Outside the app rather than over it: while the lock is shut, nothing of the
                // app is composed at all.
                AppLockGate {
                    CentsApp(openPending = openPending)
                }
            }
        }
    }
}

/** The flag [ReminderNotifier] sets when the notification was asking for a figure. */
private val Intent.wantsPending: Boolean
    get() = getBooleanExtra(ReminderNotifier.EXTRA_OPEN_PENDING, false)
