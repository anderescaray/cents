package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/** How strongly a badge's colour is laid under its glyph: the film's 15 %. */
private const val BADGE_TINT_ALPHA = 0.15f

/**
 * A category's mark: a circle in its colour at 15 %, with its [emoji] on it, or without one the
 * first letter of its name in the full colour. The colour is there either way, because it is what
 * the charts draw the category in, and the circle is what ties the two together.
 *
 * [solid] fills the circle and writes the letter in white, for the one thing a screen is about.
 * Null [colorArgb] is the uncategorized mark: grey, with a question mark.
 */
@Composable
fun CategoryBadge(
    name: String?,
    colorArgb: Int?,
    modifier: Modifier = Modifier,
    size: Dp = CentsDimens.badgeSize,
    solid: Boolean = false,
    emoji: String? = null,
) {
    val color = colorArgb?.let(::Color) ?: CentsTheme.colors.secondaryLabel
    val letter =
        name
            ?.trim()
            ?.firstOrNull()
            ?.uppercaseChar()
            ?.toString() ?: "?"
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(if (solid) color else color.copy(alpha = BADGE_TINT_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            Text(text = emoji, style = CentsTheme.typography.body.copy(fontSize = (size.value * EMOJI_SCALE).sp))
        } else {
            Text(
                text = letter,
                style =
                    CentsTheme.typography.subhead.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.42f).sp,
                    ),
                color = if (solid) Color.White else color,
            )
        }
    }
}

/** An emoji fills a little more of the circle than a letter does, being a picture, not a glyph. */
private const val EMOJI_SCALE = 0.5f

/**
 * A round mark holding one of the app's own glyphs, tinted like a [CategoryBadge]: for a row that
 * is about a thing rather than a category — an account, a section of Settings.
 */
@Composable
fun IconBadge(
    iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = CentsDimens.badgeSize,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(color.copy(alpha = BADGE_TINT_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * A one-tap choice: a category, a group, an account. Grey at rest; picked, it fills with its own
 * colour (the accent when it has none) and its text turns white, and it swells under the thumb.
 *
 * Drawn 36 dp tall and measured at least 48 dp, so it looks like a chip and answers like a button.
 */
@Composable
fun CentsChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    colorArgb: Int? = null,
    /** Shows the category's letter badge before the label. */
    showBadge: Boolean = colorArgb != null,
    iconRes: Int? = null,
    /** The category's emoji, in its badge instead of the letter. */
    emoji: String? = null,
) {
    val tint = colorArgb?.let(::Color) ?: CentsTheme.colors.accent
    val background by animateColorAsState(
        targetValue = if (selected) tint else CentsTheme.colors.fill,
        label = "chipBackground",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = rememberPressScale(pressed)

    Row(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .semantics { this.selected = selected }
                .scale(scale)
                .clip(CircleShape)
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).heightIn(min = CentsDimens.chipHeight)
                .padding(
                    start = if (showBadge || iconRes != null) CentsDimens.spacingXs + 1.dp else CentsDimens.spacingL,
                    end = CentsDimens.spacingL,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
    ) {
        ChipLeading(
            label = label,
            selected = selected,
            colorArgb = colorArgb,
            showBadge = showBadge,
            iconRes = iconRes,
            tint = tint,
            emoji = emoji,
        )
        Text(
            text = label,
            style = CentsTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else CentsTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What stands before a chip's label: its glyph, its category's letter, or nothing. */
@Composable
private fun ChipLeading(
    label: String,
    selected: Boolean,
    colorArgb: Int?,
    showBadge: Boolean,
    iconRes: Int?,
    tint: Color,
    emoji: String?,
) {
    when {
        iconRes != null -> {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (selected) Color.White else tint,
                modifier = Modifier.padding(start = CentsDimens.spacingS).size(18.dp),
            )
        }

        showBadge -> {
            if (selected) {
                Box(
                    Modifier
                        .size(CHIP_BADGE.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = SELECTED_BADGE_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            emoji ?: label
                                .trim()
                                .firstOrNull()
                                ?.uppercaseChar()
                                ?.toString()
                                .orEmpty(),
                        style = CentsTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            } else {
                CategoryBadge(name = label, colorArgb = colorArgb, size = CHIP_BADGE.dp, emoji = emoji)
            }
        }
    }
}

private const val CHIP_BADGE = 26
private const val SELECTED_BADGE_ALPHA = 0.25f
