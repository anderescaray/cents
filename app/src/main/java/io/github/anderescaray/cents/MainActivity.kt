package io.github.anderescaray.cents

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.anderescaray.cents.ui.CentsApp
import io.github.anderescaray.cents.ui.lock.AppLockGate
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * A `FragmentActivity` rather than a plain `ComponentActivity`, and only because of the lock:
 * `BiometricPrompt` puts up a fragment of its own and will take nothing less. It is a superclass
 * of what was here before, so nothing else changes.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CentsTheme {
                // Outside the app rather than over it: while the lock is shut, nothing of the
                // app is composed at all.
                AppLockGate {
                    CentsApp()
                }
            }
        }
    }
}
