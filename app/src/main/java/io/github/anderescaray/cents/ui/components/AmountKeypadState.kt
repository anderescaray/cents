package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Whether a form that holds an amount is currently offering [CentsKeypad].
 *
 * The keypad is pinned to the bottom of the screen rather than summoned by the field it
 * fills, which is what makes recording a movement three taps instead of four. The cost is
 * that something has to decide when it is in the way, and nothing did: it was mounted
 * unconditionally, so tapping an account's name answered a question about letters with a
 * number pad, and opening a movement to read it put a keypad up over the thing being read.
 *
 * The rule lives here rather than in each screen for the same reason the deletion
 * confirmation does: there are two forms with an amount today and the answer should not be
 * allowed to drift between them.
 */
@Stable
class AmountKeypadState internal constructor(
    shown: Boolean,
) {
    var isShown: Boolean by mutableStateOf(shown)
        private set

    /**
     * A text field taking focus raises the system keyboard, so the keypad steps aside. Losing
     * focus does *not* bring it back: dismissing the keyboard should leave the form as it is,
     * not make a keypad appear where the owner did not ask for one.
     */
    fun onTextFieldFocus(focused: Boolean) {
        if (focused) isShown = false
    }

    /** Tapping the amount is asking for digits. */
    fun showForAmount() {
        isShown = true
    }

    /**
     * Anything else answered on the form — a picker, a chip, a tap on its empty space — is the
     * owner done with the digits for now. What was typed stays; tapping the amount brings it back.
     */
    fun hide() {
        isShown = false
    }

    internal companion object {
        val Saver: Saver<AmountKeypadState, Boolean> =
            Saver(save = { it.isShown }, restore = { AmountKeypadState(it) })
    }
}

/**
 * @param shownInitially true when the form is creating a record rather than opening one that
 *   already exists. Recording a movement is the action this app exists for and the PRD asks
 *   for it in three taps, so a new form arrives ready to take digits. An existing one is
 *   usually opened to read it, and a keypad over the amount is in the way of doing that.
 */
@Composable
fun rememberAmountKeypadState(shownInitially: Boolean): AmountKeypadState =
    rememberSaveable(shownInitially, saver = AmountKeypadState.Saver) {
        AmountKeypadState(shownInitially)
    }
