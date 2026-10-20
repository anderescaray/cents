package io.github.anderescaray.cents.baselineprofile

import android.graphics.Rect
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/*
 * What the benchmarks and the profile generator do in the app, written once for both.
 *
 * Everything is found by resource id (the app exposes its test tags as ids) or by being
 * scrollable, never by text, so the journeys do not depend on the phone's language. None of them
 * writes anything: they open tabs and scroll. A step that cannot find what it looks for fails the
 * run, because a benchmark that quietly measured another screen would look exactly like one that
 * measured the right one.
 */

/** The app under test, as the baseline profile plugin passes it: the benchmark build's own id. */
val targetPackage: String =
    InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId is not set: run these through the baselineprofile Gradle tasks")

// The same strings as SeedActivity and CentsTabBar's tabTag in :app.
private const val SEED_ACTIVITY = "io.github.anderescaray.cents.benchmark.SeedActivity"
private const val SEED_LOG_TAG = "CentsSeed"
private const val SEEDED = "seeded"
private const val FAILED = "failed"
private const val TAB_TAG_PREFIX = "tab_"

private const val SEED_TIMEOUT_MS = 30_000L
private const val SEED_POLL_MS = 250L
private const val UI_TIMEOUT_MS = 5_000L

/** A fifth of the list's height, kept clear at each end of a fling. */
private const val GESTURE_MARGIN_FRACTION = 5

/** How many times a list is looked up again before the journey gives up. */
private const val LOOKUP_ATTEMPTS = 5

/** Few steps make a fast swipe, which the list takes as a fling. */
private const val FLING_STEPS = 5

private var seeded = false

/**
 * Fills the benchmark build with the invented ledger, once per run, then stops the app.
 *
 * Called from a test's `@Before`, outside anything measured or recorded: the restore is not what
 * a startup runs, and it must not end up in the profile. It waits for SeedActivity to say in the
 * log that the rows are in, and fails if it says otherwise or says nothing. Stopping the app
 * afterwards leaves the first measured cold start genuinely cold.
 */
fun seedOnce() {
    if (seeded) return
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.executeShellCommand("logcat -c")
    device.executeShellCommand("am start -W -n $targetPackage/$SEED_ACTIVITY")
    val deadline = System.currentTimeMillis() + SEED_TIMEOUT_MS
    while (true) {
        val log = device.executeShellCommand("logcat -d -s $SEED_LOG_TAG")
        check(!log.contains(FAILED)) { "The benchmark ledger could not be written:\n$log" }
        if (log.contains(SEEDED)) break
        check(System.currentTimeMillis() < deadline) { "The benchmark ledger was not written in time" }
        Thread.sleep(SEED_POLL_MS)
    }
    device.executeShellCommand("am force-stop $targetPackage")
    seeded = true
}

/** Waits until the tab capsule is on screen, which is when Home has drawn. */
fun MacrobenchmarkScope.waitForHome() {
    check(device.wait(Until.hasObject(By.res(TAB_TAG_PREFIX + "HOME")), UI_TIMEOUT_MS)) {
        // Most often the phone locked itself: then this names the system UI rather than the app.
        "Home did not appear; ${device.currentPackageName} was in front"
    }
}

/** Opens a tab by its `TopLevelTab` name. */
fun MacrobenchmarkScope.openTab(key: String) {
    checkNotNull(device.findObject(By.res(TAB_TAG_PREFIX + key))) { "No tab $key" }.click()
    device.waitForIdle()
}

/**
 * Flings the screen's list down twice and back up once.
 *
 * By coordinates, from the list's bounds read once, rather than through `UiObject2.fling`, which
 * keeps reading a node that Compose replaces while the list moves and then fails as stale.
 */
fun MacrobenchmarkScope.scrollDownAndUp() {
    val bounds = screenListBounds()
    // Keep clear of the edges, where the system's own gestures are.
    val margin = bounds.height() / GESTURE_MARGIN_FRACTION
    val x = bounds.centerX()
    val top = bounds.top + margin
    val bottom = bounds.bottom - margin
    repeat(2) {
        device.swipe(x, bottom, x, top, FLING_STEPS)
        device.waitForIdle()
    }
    device.swipe(x, top, x, bottom, FLING_STEPS)
    device.waitForIdle()
}

/**
 * The bounds of the screen's main list: the largest thing on it that scrolls, rather than a row
 * of chips.
 *
 * Read again if the node goes stale on the way, which it does while a tab change is still fading
 * the old screen out.
 */
private fun MacrobenchmarkScope.screenListBounds(): Rect {
    repeat(LOOKUP_ATTEMPTS) {
        device.wait(Until.hasObject(By.scrollable(true)), UI_TIMEOUT_MS)
        try {
            val bounds = device.findObjects(By.scrollable(true)).map { it.visibleBounds }
            return checkNotNull(bounds.maxByOrNull { it.width() * it.height() }) { "Nothing to scroll" }
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
    error("The list kept changing under the lookup")
}
