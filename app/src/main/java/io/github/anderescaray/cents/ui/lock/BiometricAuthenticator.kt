package io.github.anderescaray.cents.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Asking the device who is holding it.
 *
 * **The app never sees a credential**, only whether the system was satisfied — no fingerprint, no
 * PIN, nothing to store and nothing to leak (rule 6). That is the whole reason PRD §4.7 names
 * `BiometricPrompt` rather than a passcode of the app's own.
 *
 * **A fingerprint or the device PIN**, either one. Insisting on a fingerprint would lock out a
 * phone that has none enrolled, or an owner whose finger is wet — and the PIN is what the device
 * already trusts to unlock itself, so refusing it here would be claiming to be more careful than
 * the lock screen. `BIOMETRIC_WEAK` rather than `STRONG` for the same reason: this guards a
 * screen, not a key, and D16 already settled that there is no key.
 */
object BiometricAuthenticator {
    /** Whether this phone can ask at all — hardware, or an enrolled PIN. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the prompt and waits for the answer.
     *
     * Every way it can end lands on exactly one `resume`: success, a failure the system gave up
     * on, or the owner dismissing it. Note that a *wrong* finger is not an answer — the system
     * says so on the prompt and keeps waiting — so it is deliberately not resumed here.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelLabel: String,
    ): Boolean =
        suspendCoroutine { continuation ->
            val prompt =
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            continuation.resume(true)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            // Cancelled, or given up on. Either way the app stays shut, and the
                            // lock screen behind offers to ask again.
                            continuation.resume(false)
                        }
                    },
                )
            prompt.authenticate(
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .apply {
                        // A negative button and DEVICE_CREDENTIAL cannot both be set: the system
                        // supplies its own way out when the PIN is an option. Where it is not,
                        // one has to be given, or there is no way to dismiss the prompt.
                        if (usesDeviceCredential(activity)) {
                            setAllowedAuthenticators(ALLOWED)
                        } else {
                            setAllowedAuthenticators(BIOMETRIC_WEAK)
                            setNegativeButtonText(cancelLabel)
                        }
                    }.build(),
            )
        }

    private fun usesDeviceCredential(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    private const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
}
