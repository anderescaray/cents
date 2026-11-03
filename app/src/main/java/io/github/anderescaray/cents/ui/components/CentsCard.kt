package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * What every card stands on: a soft two-layer shadow in light, none in dark (where the lighter
 * fill is the elevation), then the shape and the card colour.
 *
 * `dropShadow` rather than `shadow`: the second is Material's elevation, which draws a hard grey
 * edge, and the film's cards float on a blur instead.
 */
@Composable
fun Modifier.cardSurface(shape: Shape = RoundedCornerShape(CentsDimens.cardCornerRadius)): Modifier {
    val colors = CentsTheme.colors
    val lifted =
        if (colors.isDark) {
            this
        } else {
            this
                .dropShadow(shape, Shadow(radius = 2.dp, color = colors.cardShadow, offset = DpOffset(0.dp, 1.dp)))
                .dropShadow(shape, Shadow(radius = 24.dp, color = colors.cardShadow, offset = DpOffset(0.dp, 8.dp)))
        }
    return lifted.clip(shape).background(colors.groupedCard)
}

/**
 * A white card on the canvas, holding one subject. Tappable when [onClick] is given, in which
 * case it gives slightly under the thumb rather than rippling.
 */
@Composable
fun CentsCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(CentsDimens.spacingL),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = rememberPressScale(pressed)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .scale(pressScale)
                .cardSurface()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = onClickLabel,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).padding(contentPadding),
        content = content,
    )
}

/**
 * The pale canvas a screen stands on, with a faint light of the accent falling from its top
 * corner: enough to give the page depth, never enough to read as a colour of its own.
 */
@Composable
fun Modifier.canvas(): Modifier {
    val colors = CentsTheme.colors
    val glow = colors.accent.copy(alpha = if (colors.isDark) CANVAS_GLOW_DARK else CANVAS_GLOW_LIGHT)
    return background(colors.groupedBackground).drawBehind {
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset(size.width * CANVAS_GLOW_X, 0f),
                radius = size.width * CANVAS_GLOW_REACH,
            ),
        )
    }
}

private const val CANVAS_GLOW_LIGHT = 0.07f
private const val CANVAS_GLOW_DARK = 0.14f
private const val CANVAS_GLOW_X = 0.85f
private const val CANVAS_GLOW_REACH = 1.1f

/** The card's corner shape, for the few places that draw their own surface. */
val CardShape = RoundedCornerShape(CentsDimens.cardCornerRadius)

/** A card inset by the screen margin, the way every card in a list sits. */
fun Modifier.screenMargin(): Modifier = padding(horizontal = CentsDimens.screenMargin)
