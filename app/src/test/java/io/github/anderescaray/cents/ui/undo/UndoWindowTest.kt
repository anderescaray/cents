package io.github.anderescaray.cents.ui.undo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UndoWindowTest {
    @Test
    fun `an offer closes on its own when the window ends`() =
        runTest {
            val window = UndoWindow<String>(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            window.offer("correction")

            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS - 1)
            assertEquals("still open a moment before the end", "correction", window.offered.value)

            advanceTimeBy(2)
            assertNull(window.offered.value)
            assertNull("too late to put it back", window.take())
        }

    @Test
    fun `what is taken can only be put back once`() =
        runTest {
            val window = UndoWindow<String>(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            window.offer("correction")

            assertEquals("correction", window.take())
            assertNull(window.offered.value)
            assertNull(window.take())
        }

    @Test
    fun `a second offer gets a window of its own`() =
        runTest {
            val window = UndoWindow<String>(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            window.offer("first")
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2)

            window.offer("second")

            // The first offer's clock must not take the second one away with it.
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2 + 1)
            assertEquals("second", window.offered.value)
        }

    @Test
    fun `clearing closes the window at once, and its clock with it`() =
        runTest {
            val window = UndoWindow<String>(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            window.offer("correction")
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2)

            window.clear()

            assertNull(window.offered.value)
            assertNull(window.take())

            // The cleared offer's clock must not close the next one early.
            window.offer("another")
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2 + 1)
            assertEquals("another", window.offered.value)
        }

    @Test
    fun `a held offer waits, and gets a whole window once released`() =
        runTest {
            val window = UndoWindow<String>(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            window.offer("correction")
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2)

            window.hold()
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS * 2)
            assertEquals("its clock stopped while another banner showed", "correction", window.offered.value)

            window.release()
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS - 1)
            assertEquals("a whole window, not what was left of the first", "correction", window.offered.value)

            advanceTimeBy(2)
            assertNull(window.offered.value)
        }

    @Test
    fun `an offer made while held starts its clock only once released`() =
        runTest {
            val window = UndoWindow<String>(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            window.hold()

            window.offer("correction")
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS * 2)
            assertEquals("correction", window.offered.value)

            window.release()
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS + 1)
            assertNull(window.offered.value)
        }

    @Test
    fun `releasing a window that was not held leaves its clock alone`() =
        runTest {
            val window = UndoWindow<String>(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            window.offer("correction")
            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2)

            // What a screen does when it first learns that nothing else is showing.
            window.release()

            advanceTimeBy(UndoWindow.UNDO_WINDOW_MS / 2 + 1)
            assertNull("closed on its first clock, not a fresh one", window.offered.value)
        }
}
