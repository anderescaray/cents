package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The app's button: a rounded slab, 56 dp tall, that gives under the thumb rather than rippling.
 * One [ButtonStyle.PRIMARY] per screen at most; everything else is tinted, plain or text.
 */
@Composable
fun CentsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    style: ButtonStyle = if (destructive) ButtonStyle.DESTRUCTIVE else ButtonStyle.PRIMARY,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = rememberPressScale(pressed && enabled)
    val colors = CentsTheme.colors
    val (background, content) =
        when (style) {
            ButtonStyle.PRIMARY -> colors.accent to colors.onAccent
            ButtonStyle.DESTRUCTIVE -> colors.destructive to colors.onAccent
            ButtonStyle.TINTED -> colors.accent.copy(alpha = 0.12f) to colors.accentText
            ButtonStyle.PLAIN -> colors.fill to colors.label
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .scale(scale)
                .defaultMinSize(minHeight = CentsDimens.primaryButtonHeight)
                .clip(RoundedCornerShape(CentsDimens.primaryButtonRadius))
                .background(if (enabled) background else background.copy(alpha = background.alpha * DISABLED_ALPHA))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CentsTheme.typography.headline,
            color = if (enabled) content else content.copy(alpha = DISABLED_TEXT_ALPHA),
        )
    }
}

private const val DISABLED_ALPHA = 0.4f
private const val DISABLED_TEXT_ALPHA = 0.7f
