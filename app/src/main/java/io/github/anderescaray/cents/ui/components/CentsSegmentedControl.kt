package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * A segmented control: a fully rounded grey track with a raised white thumb marking the
 * selection. Used for choices with a few options that are always visible; each label stays on one
 * line, shrinking if it has to.
 */
@Composable
fun <T> CentsSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val largeFont = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = SEGMENT_HEIGHT.dp)
                .clip(RoundedCornerShape((SEGMENT_RADIUS + 3).dp))
                .background(CentsTheme.colors.fill)
                .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) CentsTheme.colors.groupedCard else CentsTheme.colors.fill,
                label = "segmentBackground",
            )

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .heightIn(min = SEGMENT_INNER_HEIGHT.dp)
                        .then(
                            if (isSelected) {
                                Modifier.cardSurface(RoundedCornerShape(SEGMENT_RADIUS.dp))
                            } else {
                                Modifier
                            },
                        ).clip(RoundedCornerShape(SEGMENT_RADIUS.dp))
                        .background(background)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(option) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                SegmentLabel(text = label(option), selected = isSelected, largeFont = largeFont)
            }
        }
    }
}

/**
 * One line at the size it was set: a label that does not fit shrinks a step at a time until it
 * does, rather than wrapping and being cut by the track. Spanish needs it most: *Trimestre* in a
 * fifth of the width. Should one still not fit at the smallest step, it ends in an ellipsis rather
 * than being cut without a sign. With a large system font the owner asked for big words, so there
 * it wraps and the control grows instead.
 */
@Composable
private fun SegmentLabel(
    text: String,
    selected: Boolean,
    largeFont: Boolean,
) {
    val style =
        CentsTheme.typography.subhead.copy(
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) CentsTheme.colors.label else CentsTheme.colors.secondaryLabel,
        )
    BasicText(
        text = text,
        style = style,
        maxLines = if (largeFont) Int.MAX_VALUE else 1,
        softWrap = largeFont,
        overflow = TextOverflow.Ellipsis,
        autoSize =
            if (largeFont || style.fontSize.isUnspecified) {
                null
            } else {
                TextAutoSize.StepBased(minFontSize = style.fontSize * MIN_LABEL_SHARE, maxFontSize = style.fontSize)
            },
        modifier = Modifier.padding(horizontal = LABEL_PADDING.dp),
    )
}

private const val SEGMENT_HEIGHT = 40

// A floor relative to the label's own size, so a large system font still gets large labels.
private const val MIN_LABEL_SHARE = 0.65f
private const val LARGE_FONT_SCALE = 1.3f
private const val LABEL_PADDING = 4
private const val SEGMENT_INNER_HEIGHT = 34
private const val SEGMENT_RADIUS = 20
