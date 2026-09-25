package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * What every small tappable thing in the app is made of: a word in a bar, a "+", a chip, a
 * link-styled "Edit".
 *
 * Two things a bare `clickable` leaves out, and both matter to someone who cannot see the screen
 * or has trouble aiming (PRD Phase 6, accessibility):
 * - **a role**, so TalkBack says "button" after the label rather than reading a word that might as
 *   well be decoration;
 * - **a 48 dp touch target**, Android's minimum, however small the text or glyph drawn inside it.
 *   `minimumInteractiveComponentSize` grows the layout around the visual rather than the visual
 *   itself, so an iOS-sized "Edit" keeps its look and gains a thumb-sized place to be pressed.
 *
 * [label] is what TalkBack says the action does, for the ones whose visible text says little on
 * its own — a "+" is read as "plus" otherwise.
 */
fun Modifier.tappable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    label: String? = null,
): Modifier =
    // The clickable goes outside the minimum size, so the whole grown area is what answers a tap
    // and what TalkBack outlines — not only the glyph in the middle of it.
    clickable(enabled = enabled, onClickLabel = label, role = role, onClick = onClick)
        .minimumInteractiveComponentSize()
