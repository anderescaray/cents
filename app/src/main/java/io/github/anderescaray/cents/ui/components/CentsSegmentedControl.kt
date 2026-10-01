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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * A segmented control: a fully rounded grey track with a raised white thumb marking the
 * selection. Used for choices with two or three options that are always visible.
 */
@Composable
fun <T> CentsSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
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
                Text(
                    text = label(option),
                    style =
                        CentsTheme.typography.subhead.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    color = if (isSelected) CentsTheme.colors.label else CentsTheme.colors.secondaryLabel,
                )
            }
        }
    }
}

private const val SEGMENT_HEIGHT = 40
private const val SEGMENT_INNER_HEIGHT = 34
private const val SEGMENT_RADIUS = 20
