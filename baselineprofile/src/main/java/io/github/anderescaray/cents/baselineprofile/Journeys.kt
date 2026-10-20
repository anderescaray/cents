package io.github.anderescaray.cents.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/*
 * What the benchmarks and the profile generator do in the app, written once for both.
 *
 * Everything is found by resource id (the app exposes its test tags as ids) or by being
 * scrollable, never by text, so the journeys do not depend on the phone's language. None of them
 * writes anything: they open tabs and scroll.
 */

/** The app under test, as the baseline profile plugin passes it: the benchmark build's own id. */
val targetPackage: String =
    InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId is not set: run these through the baselineprofile Gradle tasks")

private const val SEED_ACTIVITY = "io.github.anderescaray.cents.benchmark.SeedActivity"
private const val SEED_TIMEOUT_MS = 30_000L
private const val SEED_POLL_MS = 250L
private const val UI_TIMEOUT_MS = 5_000L

/** A fifth of the screen width, kept clear on each side of a fling. */
private const val GESTURE_MARGIN_FRACTION = 5

private var seeded = false

/**
 * Fills the benchmark build with the invented ledger, once per run.
 *
 * `am start -W` returns when the activity is shown, not when the restore is done, so this waits
 * for the activity to close itself, which it does only once the rows are in.
 */
fun MacrobenchmarkScope.seedOnce() {
    if (seeded) return
    device.executeShellCommand("am start -W -n $targetPackage/$SEED_ACTIVITY")
    val deadline = System.currentTimeMillis() + SEED_TIMEOUT_MS
    while (device.executeShellCommand("dumpsys activity activities").contains(SEED_ACTIVITY)) {
        check(System.currentTimeMillis() < deadline) { "The benchmark ledger was not written in time" }
        Thread.sleep(SEED_POLL_MS)
    }
    seeded = true
}

/** Waits until the tab capsule is on screen, which is when Home has drawn. */
fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.res("tab_HOME")), UI_TIMEOUT_MS)
}

/** Opens a tab by its `TopLevelTab` name. */
fun MacrobenchmarkScope.openTab(key: String) {
    device.findObject(By.res("tab_$key"))?.click()
    device.waitForIdle()
}

/** Flings the screen's list down twice and back up once. */
fun MacrobenchmarkScope.scrollDownAndUp() {
    val list = device.wait(Until.findObject(By.scrollable(true)), UI_TIMEOUT_MS) ?: return
    // Keep clear of the system's gesture area at the edges.
    list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
    repeat(2) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
    list.fling(Direction.UP)
    device.waitForIdle()
}
