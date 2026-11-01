package io.github.anderescaray.cents

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.anderescaray.cents.ui.CentsApp
import io.github.anderescaray.cents.ui.capture.CaptureNotifier
import io.github.anderescaray.cents.ui.language.AppLanguages
import io.github.anderescaray.cents.ui.notifications.InvestmentReminderNotifier
import io.github.anderescaray.cents.ui.notifications.ReminderNotifier
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.Today
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * Whether we were opened by a notification asking for an amount.
     *
     * Read as state so that a tap arriving while the app is already open lands somewhere too:
     * the activity is `singleTop`-ish through the intent flags, so a second tap comes through
     * [onNewIntent] rather than building a new activity, and without this it would do nothing
     * at all.
     */
    private var openPending by mutableStateOf(false)

    /** The movement a capture notification was tapped for, the same way. */
    private var openCapturedTransactionId by mutableStateOf<Long?>(null)

    /** True when the Friday reminder was tapped: Invested opens, the same way. */
    private var openInvestments by mutableStateOf(false)

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

    /** The language chosen in Settings, put on the screen before it is drawn (Android 12 and older). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.applyTo(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPending = intent.wantsPending
        openCapturedTransactionId = intent.capturedTransactionId
        openInvestments = intent.wantsInvestments
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
        // A recreated activity gets back the intent it was launched with, extra and all, even
        // after `removeExtra`: reading it again would push that screen over the restored one. A
        // language change recreates it, so opening Settings from a reminder and changing the
        // language would otherwise land on the pending charges.
        if (savedInstanceState == null) {
            openPending = intent.wantsPending
            openCapturedTransactionId = intent.capturedTransactionId
            openInvestments = intent.wantsInvestments
        }
        enableEdgeToEdge()
        setContent {
            CentsTheme {
                CentsApp(
                    openPending = openPending,
                    openInvestments = openInvestments,
                    openCapturedTransactionId = openCapturedTransactionId,
                    onCapturedOpened = {
                        openCapturedTransactionId = null
                        // Or a recreated activity would read the old intent and open it again.
                        intent.removeExtra(CaptureNotifier.EXTRA_OPEN_CAPTURED)
                    },
                )
            }
        }
    }
}

/** The flag [ReminderNotifier] sets when the notification was asking for a figure. */
private val Intent.wantsPending: Boolean
    get() = getBooleanExtra(ReminderNotifier.EXTRA_OPEN_PENDING, false)

/** The flag [InvestmentReminderNotifier] sets on the Friday reminder. */
private val Intent.wantsInvestments: Boolean
    get() = getBooleanExtra(InvestmentReminderNotifier.EXTRA_OPEN_INVESTMENTS, false)

/** The movement [CaptureNotifier] names when its notification was tapped, if it was one of those. */
private val Intent.capturedTransactionId: Long?
    get() = getLongExtra(CaptureNotifier.EXTRA_OPEN_CAPTURED, 0L).takeIf { it > 0L }
