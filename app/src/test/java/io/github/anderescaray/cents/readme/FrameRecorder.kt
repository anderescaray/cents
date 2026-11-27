package io.github.anderescaray.cents.readme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
    private val window: () -> View,
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
        event("tap $index ${bounds.center.x.toInt()} ${bounds.center.y.toInt()}")
        hold(frameMs * 4)
        node.performClick()
    }

    /**
     * A finger dragging [node] (the whole screen by default) by [dy] or [dx] pixels over [ms],
     * frame by frame, then letting go, so the list's own fling and settle are recorded too.
     */
    fun scroll(
        dy: Float = 0f,
        dx: Float = 0f,
        ms: Long = 640,
        node: SemanticsNodeInteraction = rule.onAllNodes(isRoot()).onFirst(),
    ) {
        val steps = (ms / frameMs).toInt()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val x0 =
            if (dx <
                0
            ) {
                bounds.left + bounds.width * 0.8f
            } else if (dx > 0) {
                bounds.left + bounds.width * 0.2f
            } else {
                bounds.center.x
            }
        val y0 =
            if (dy <
                0
            ) {
                bounds.top + bounds.height * 0.72f
            } else if (dy >
                0
            ) {
                bounds.top + bounds.height * 0.28f
            } else {
                bounds.center.y
            }
        event("swipe $index $steps ${x0.toInt()} ${y0.toInt()} ${(x0 + dx).toInt()} ${(y0 + dy).toInt()}")
        // Coordinates in the node's own space, which is where touch input is given.
        val start = Offset(x0 - bounds.left, y0 - bounds.top)
        node.performTouchInput { down(start) }
        repeat(steps) { step ->
            val eased = easeInOut((step + 1f) / steps) - easeInOut(step.toFloat() / steps)
            // The move itself carries the clock forward by a frame, so the list sees a real speed.
            node.performTouchInput { moveBy(Offset(dx * eased, dy * eased), delayMillis = frameMs) }
            shot()
        }
        node.performTouchInput { up() }
    }

    private fun easeInOut(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2) * (-2 * t + 2) / 2

    /** A swipe drawn from [fromY] to [toY], for the overlay; the scroll itself is the caller's. */
    fun event(line: String) {
        events.appendLine(line)
    }

    val frame: Int get() = index

    fun finish() {
        File(dir, "events.txt").writeText("frameMs $frameMs\n$events")
    }

    private fun shot() {
        // Drawn straight from the window: `captureToImage` waits for a frame the paused clock
        // never sends.
        rule.waitForIdle()
        val view = window()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "%04d.png".format(index++)).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
