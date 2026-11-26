package io.github.anderescaray.cents.readme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import java.io.File

/**
 * Writes the app's screen, one PNG per frame, while the test clock moves by [frameMs] between
 * them, so every animation is recorded at the speed it runs on a phone.
 *
 * Next to the frames goes `events.txt`: where a finger touched and when the bank's notification
 * arrived, which `docs/media/make_gifs.py` draws on top, since neither is part of the app's own
 * window.
 */
internal class FrameRecorder(
    private val rule: ComposeTestRule,
    private val dir: File,
    private val frameMs: Long = 40,
) {
    private var index = 0
    private val events = StringBuilder()

    init {
        dir.deleteRecursively()
        dir.mkdirs()
        rule.mainClock.autoAdvance = false
    }

    /** Lets the clock run on its own until [condition] holds: data loading, not recorded. */
    fun settle(condition: () -> Boolean = { true }) {
        rule.mainClock.autoAdvance = true
        rule.waitUntil(timeoutMillis = 20_000) {
            Thread.sleep(20)
            condition()
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    /** Records [ms] of the screen as it is, animations included. */
    fun hold(ms: Long) {
        repeat((ms / frameMs).toInt()) {
            rule.mainClock.advanceTimeBy(frameMs)
            // Lets what Room sent from its own threads reach the screen, as it would on a phone.
            Thread.sleep(5)
            shot()
        }
    }

    /** A finger on [node]: shown pressing for a few frames, then the click. */
    fun tap(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        event("tap ${index} ${bounds.center.x.toInt()} ${bounds.center.y.toInt()}")
        hold(frameMs * 4)
        node.performClick()
    }

    /** A swipe drawn from [fromY] to [toY], for the overlay; the scroll itself is the caller's. */
    fun event(line: String) {
        events.appendLine(line)
    }

    val frame: Int get() = index

    fun finish() {
        File(dir, "events.txt").writeText("frameMs $frameMs\n$events")
    }

    private fun shot() {
        val bitmap = rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        File(dir, "%04d.png".format(index++)).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
