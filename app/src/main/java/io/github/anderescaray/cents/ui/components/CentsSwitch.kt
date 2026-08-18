package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsTheme

private val TrackWidth = 51.dp
private val TrackHeight = 31.dp
private val ThumbSize = 27.dp

/**
 * The iOS switch: 51x31 track, white thumb, green when on.
 *
 * Material's `Switch` is recognisably Material — different proportions, an icon inside the
 * thumb — so this one is drawn from scratch rather than restyled.
 */
@Composable
fun CentsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) CentsTheme.colors.positive else CentsTheme.colors.switchTrackOff,
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - 2.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "switchThumb",
    )

    Box(
        modifier =
            modifier
                .size(width = TrackWidth, height = TrackHeight)
                .clip(CircleShape)
                .background(if (enabled) trackColor else trackColor.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onClick = { onCheckedChange(!checked) },
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset)
                    .size(ThumbSize)
                    .shadow(elevation = 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
        )
    }
}
