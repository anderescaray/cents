package io.github.anderescaray.cents.ui.lock

import java.time.Duration
import java.time.Instant

/**
 * Whether the app is showing itself or asking who you are.
 *
 * Kept as a small object with no Android in it, because *when* an app locks is the part that is
 * easy to get subtly wrong and impossible to check by tapping: too eager and it asks every time
 * you glance at a calculator, too lazy and it is not a lock. The prompt itself is a thin shell
 * around `BiometricPrompt` and is the part a test cannot say anything useful about.
 */
enum class LockState {
    /** The lock is off, or the owner has just proved who they are. */
    OPEN,

    /** Waiting to be asked. Nothing of the app's contents is on screen. */
    LOCKED,

    /**
     * The prompt is up.
     *
     * Its own state rather than a flag on [LOCKED], because showing the prompt **backgrounds the
     * activity on some devices** — and a lock that re-locks on backgrounding would then fight
     * its own prompt, re-arming while the owner is looking at it and asking again the moment
     * they succeed.
     */
    ASKING,

    /**
     * Shut, and **waiting to be asked to ask again**.
     *
     * The difference between this and [LOCKED] is the whole of what stops the prompt looping.
     * The gate raises the prompt whenever the app is [LOCKED]; if a dismissal went back there,
     * dismissing would raise it again immediately and the owner could never reach the screen
     * underneath — not even to leave the app. So a dismissal lands here, and only the owner
     * asking puts it back to [LOCKED].
     */
    DISMISSED,
}

/**
 * The rule for when the app locks itself again.
 *
 * **On a cold start, always. On coming back from the background, only after a pause.** Locking
 * on every return would mean authenticating after two seconds in the calculator, which is the
 * fastest way to make an owner turn the lock off — and a lock that is off protects nothing. The
 * pause is deliberately short: long enough to cover switching apps to check something, far too
 * short to cover handing the phone to somebody.
 *
 * [GRACE] is a number chosen rather than derived, and it is one line to change.
 */
class AppLock(
    private val grace: Duration = GRACE,
) {
    var state: LockState = LockState.LOCKED
        private set

    /** When the app last went to the background, or null if it has not since being unlocked. */
    private var leftAt: Instant? = null

    /**
     * Called when the lock setting is read or changed.
     *
     * Turning it **off** opens immediately: the owner has just said, from inside the app, that
     * they do not want to be asked. Turning it **on** does not lock the screen they are looking
     * at — they are already here, and demanding a fingerprint the instant the switch moves would
     * read as the app not believing them.
     */
    fun onEnabledChanged(enabled: Boolean) {
        if (!enabled) {
            state = LockState.OPEN
            leftAt = null
        }
    }

    /** The owner proved who they are. */
    fun onUnlocked() {
        state = LockState.OPEN
        leftAt = null
    }

    /** The prompt is on screen, so backgrounding is the prompt's own doing. */
    fun onAsking() {
        if (state == LockState.LOCKED) state = LockState.ASKING
    }

    /**
     * The prompt closed without an answer — cancelled, or given up on. The app stays shut, and
     * waits to be asked again rather than asking again on its own.
     */
    fun onAskingFinished() {
        if (state == LockState.ASKING) state = LockState.DISMISSED
    }

    /** The owner asked for the prompt back, from the lock screen. */
    fun onRetry() {
        if (state == LockState.DISMISSED) state = LockState.LOCKED
    }

    /**
     * The app went to the background. Noted rather than acted on: whether it matters depends on
     * how long it lasts, which is only known on the way back.
     */
    fun onBackgrounded(now: Instant) {
        if (state == LockState.ASKING) return
        if (leftAt == null) leftAt = now
    }

    /**
     * Whether the gate should put the prompt up.
     *
     * [LOCKED] and nothing else: [ASKING] is already showing one, [DISMISSED] is waiting to be
     * asked, and [LockState.OPEN] has nothing to ask about.
     */
    val shouldAsk: Boolean get() = state == LockState.LOCKED

    /** Whether the app's contents must not be drawn. */
    val isShut: Boolean get() = state != LockState.OPEN

    /**
     * The app came back. Locks if the lock is on and the pause outlasted [grace].
     *
     * The clock is passed in rather than read, so a test can put the app away for an hour
     * without waiting for one.
     */
    fun onForegrounded(
        enabled: Boolean,
        now: Instant,
    ) {
        if (!enabled) {
            state = LockState.OPEN
            leftAt = null
            return
        }
        val away = leftAt
        leftAt = null
        if (away == null) return
        if (Duration.between(away, now) >= grace) state = LockState.LOCKED
    }

    companion object {
        /**
         * Long enough to switch to a calculator and back; nowhere near long enough to hand the
         * phone over. Thirty seconds is a judgement, not a finding.
         */
        val GRACE: Duration = Duration.ofSeconds(30)
    }
}
