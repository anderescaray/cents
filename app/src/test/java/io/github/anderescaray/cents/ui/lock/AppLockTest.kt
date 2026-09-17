package io.github.anderescaray.cents.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * When the app locks itself, which is the half of a lock that cannot be checked by tapping.
 *
 * Every case below is one somebody would otherwise find on their own phone, weeks later, and
 * either be annoyed by or not notice at all. The prompt is a thin shell around the system's own
 * and has nothing here: what is worth pinning is the *rule*.
 */
class AppLockTest {
    private val start = Instant.parse("2026-09-17T12:00:00Z")

    @Test
    fun `it starts locked, because a cold start is the case a lock is for`() {
        assertEquals(LockState.LOCKED, AppLock().state)
    }

    @Test
    fun `turning the lock off opens it there and then`() {
        val lock = AppLock()

        lock.onEnabledChanged(enabled = false)

        // The owner has just said, from inside the app, that they do not want to be asked.
        assertEquals(LockState.OPEN, lock.state)
    }

    @Test
    fun `turning the lock on does not shut the door on the person who turned it on`() {
        val lock = unlocked()

        lock.onEnabledChanged(enabled = true)

        // They are already here. Demanding a fingerprint the instant the switch moves would
        // read as the app not believing them.
        assertEquals(LockState.OPEN, lock.state)
    }

    @Test
    fun `a glance at another app does not cost a fingerprint`() {
        val lock = unlocked()

        lock.onBackgrounded(start)
        lock.onForegrounded(enabled = true, now = start.plusSeconds(5))

        // Five seconds in a calculator. Locking here is the fastest way to make somebody turn
        // the lock off, and a lock that is off protects nothing.
        assertEquals(LockState.OPEN, lock.state)
    }

    @Test
    fun `a real absence locks it`() {
        val lock = unlocked()

        lock.onBackgrounded(start)
        lock.onForegrounded(enabled = true, now = start.plusSeconds(60))

        assertEquals(LockState.LOCKED, lock.state)
    }

    @Test
    fun `the pause is measured from leaving, not from the last look`() {
        val lock = unlocked()

        lock.onBackgrounded(start)
        // Android can report backgrounding more than once on the way out; the first one is when
        // the owner actually left, and taking the last would restart the clock on its own.
        lock.onBackgrounded(start.plusSeconds(25))
        lock.onForegrounded(enabled = true, now = start.plusSeconds(31))

        assertEquals(LockState.LOCKED, lock.state)
    }

    @Test
    fun `the prompt backgrounding the app does not re-arm the lock behind it`() {
        val lock = AppLock()

        lock.onAsking()
        // Showing `BiometricPrompt` backgrounds the activity on some devices. Without this, the
        // lock would re-arm while the owner is looking at the prompt and ask again the instant
        // they succeeded — the loop that makes a lock unusable.
        lock.onBackgrounded(start)
        lock.onUnlocked()

        assertEquals(LockState.OPEN, lock.state)
    }

    @Test
    fun `a cancelled prompt leaves the app shut, and waiting to be asked`() {
        val lock = AppLock()

        lock.onAsking()
        lock.onAskingFinished()

        // Backing out of the prompt is not a way in — and it must not land back on LOCKED,
        // which is the state the gate raises a prompt from: dismissing would raise it again
        // immediately and the owner could never reach the screen underneath, or leave.
        assertEquals(LockState.DISMISSED, lock.state)
        assertFalse(lock.shouldAsk)
        assertTrue(lock.isShut)
    }

    @Test
    fun `asking again is something the owner does`() {
        val lock = AppLock()

        lock.onAsking()
        lock.onAskingFinished()
        lock.onRetry()

        assertEquals(LockState.LOCKED, lock.state)
        assertTrue(lock.shouldAsk)
    }

    @Test
    fun `a dismissed prompt still hides everything`() {
        val lock = AppLock()

        lock.onAsking()
        lock.onAskingFinished()

        // The way out of a dismissal is the prompt or the home button, never the ledger.
        assertTrue(lock.isShut)
    }

    @Test
    fun `coming back with the lock switched off opens it`() {
        val lock = unlocked()

        lock.onBackgrounded(start)
        lock.onForegrounded(enabled = false, now = start.plusSeconds(600))

        // Turned off while away — from a restore, most plausibly. Ten minutes is long past the
        // pause, and it still must not ask.
        assertEquals(LockState.OPEN, lock.state)
    }

    @Test
    fun `coming back without having left changes nothing`() {
        val lock = unlocked()

        lock.onForegrounded(enabled = true, now = start.plusSeconds(600))

        // A foreground callback with no backgrounding before it — a configuration change, say —
        // must not be read as an absence, however long ago the app started.
        assertEquals(LockState.OPEN, lock.state)
    }

    @Test
    fun `unlocking clears the absence, so the next glance is judged on its own`() {
        val lock = unlocked()

        lock.onBackgrounded(start)
        lock.onForegrounded(enabled = true, now = start.plusSeconds(60))
        lock.onUnlocked()
        lock.onBackgrounded(start.plusSeconds(61))
        lock.onForegrounded(enabled = true, now = start.plusSeconds(63))

        assertEquals(LockState.OPEN, lock.state)
    }

    @Test
    fun `the pause is exactly as long as it says`() {
        val lock = AppLock(grace = Duration.ofSeconds(10))

        lock.onUnlocked()
        lock.onBackgrounded(start)
        lock.onForegrounded(enabled = true, now = start.plusSeconds(10))

        // On the boundary it locks. A lock is the side to round towards.
        assertEquals(LockState.LOCKED, lock.state)
    }

    private fun unlocked() = AppLock().also { it.onUnlocked() }
}
