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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
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

    val shape = RoundedCornerShape(CentsDimens.primaryButtonRadius)
    // The filled styles stand on a glow of their own colour and are lit a touch from above: the
    // one button a sheet ends in should look like the thing to press.
    val filled = style == ButtonStyle.PRIMARY || style == ButtonStyle.DESTRUCTIVE
    val fill = buttonFill(background, filled = filled, enabled = enabled)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .scale(scale)
                .defaultMinSize(minHeight = CentsDimens.primaryButtonHeight)
                .then(
                    if (filled && enabled && !colors.isDark) {
                        Modifier.dropShadow(
                            shape,
                            Shadow(
                                radius = 16.dp,
                                color = background.copy(alpha = FILLED_GLOW),
                                offset = DpOffset(0.dp, 6.dp),
                            ),
                        )
                    } else {
                        Modifier
                    },
                ).clip(shape)
                .background(fill)
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

/** A filled button lit a touch from above; anything else, or a disabled one, flat. */
private fun buttonFill(
    background: Color,
    filled: Boolean,
    enabled: Boolean,
): Brush =
    when {
        !enabled -> SolidColor(background.copy(alpha = background.alpha * DISABLED_ALPHA))
        filled -> Brush.verticalGradient(listOf(lerp(background, Color.White, FILLED_HIGHLIGHT), background))
        else -> SolidColor(background)
    }

private const val DISABLED_ALPHA = 0.4f
private const val FILLED_HIGHLIGHT = 0.12f
private const val FILLED_GLOW = 0.32f
private const val DISABLED_TEXT_ALPHA = 0.7f
