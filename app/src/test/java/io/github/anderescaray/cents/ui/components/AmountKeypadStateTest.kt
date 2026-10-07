package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app's own keypad is on screen, and when it gets out of the way.
 *
 * The bug this encodes was found on a phone, not by a test: the keypad was mounted
 * unconditionally, so it was up over an account's *name* field and up over a movement opened
 * only to be read. What a test can hold is the rule; which modifier is wired to which field
 * is what the device walk is for.
 */
class AmountKeypadStateTest {
    @Test
    fun `a new record opens ready to take digits`() {
        assertTrue(AmountKeypadState(shown = true).isShown)
    }

    @Test
    fun `a record that already exists opens without the keypad over it`() {
        assertFalse(AmountKeypadState(shown = false).isShown)
    }

    @Test
    fun `a text field taking focus puts the keypad away`() {
        val state = AmountKeypadState(shown = true)

        state.onTextFieldFocus(focused = true)

        assertFalse(state.isShown)
    }

    @Test
    fun `losing focus does not summon a keypad nobody asked for`() {
        val state = AmountKeypadState(shown = false)

        state.onTextFieldFocus(focused = true)
        state.onTextFieldFocus(focused = false)

        assertFalse(state.isShown)
    }

    @Test
    fun `tapping the amount asks for it back`() {
        val state = AmountKeypadState(shown = false)

        state.showForAmount()

        assertTrue(state.isShown)
    }

    @Test
    fun `anything else answered on the form puts it away, and the amount brings it back`() {
        val state = AmountKeypadState(shown = true)

        state.hide()
        assertFalse(state.isShown)

        state.showForAmount()
        assertTrue(state.isShown)
    }

    @Test
    fun `it survives being saved and restored`() {
        val state = AmountKeypadState(shown = false)
        state.showForAmount()

        val scope = SaverScope { true }
        val saved = with(AmountKeypadState.Saver) { scope.save(state) }
        val restored = AmountKeypadState.Saver.restore(saved!!)

        assertTrue(restored!!.isShown)
    }
}
