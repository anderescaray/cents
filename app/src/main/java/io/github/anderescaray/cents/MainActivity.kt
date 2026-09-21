package io.github.anderescaray.cents

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.anderescaray.cents.ui.CentsApp
import io.github.anderescaray.cents.ui.lock.AppLockGate
import io.github.anderescaray.cents.ui.notifications.ReminderNotifier
import io.github.anderescaray.cents.ui.theme.CentsTheme

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPending = intent.wantsPending
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
