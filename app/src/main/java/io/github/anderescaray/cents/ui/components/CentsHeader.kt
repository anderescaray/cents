package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.rememberReduceMotion
import kotlin.math.roundToLong

/**
 * The head of a top-level screen: the title large, a line of context under it, and round buttons
 * on the right. It scrolls with the content rather than collapsing into a bar — the screen is one
 * column of cards, and its title is the first of them.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CentsDimens.screenMargin)
                .padding(top = CentsDimens.spacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CentsTheme.typography.largeTitle,
                color = CentsTheme.colors.label,
                modifier = Modifier.semantics { heading() },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = CentsTheme.typography.subhead,
                    color = CentsTheme.colors.secondaryLabel,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS), content = actions)
    }
}

/**
 * A round white button holding one glyph: settings, the calendar, back. It reads as a small card,
 * and gives under the thumb.
 */
@Composable
fun RoundIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CentsTheme.colors.label,
    filled: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = rememberPressScale(pressed)
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .semantics { this.contentDescription = contentDescription }
                .scale(scale)
                .size(ROUND_BUTTON.dp)
                .then(
                    if (filled) {
                        Modifier.clip(CircleShape).background(CentsTheme.colors.accent)
                    } else {
                        Modifier.cardSurface(CircleShape)
                    },
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (filled) CentsTheme.colors.onAccent else tint,
            modifier = Modifier.size(ROUND_ICON.dp),
        )
    }
}

private const val ROUND_BUTTON = 42
private const val ROUND_ICON = 20

private val CountEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private const val COUNT_MS = 900

/**
 * Money that counts from what it was to what it is when it changes, so a total visibly moves when
 * a movement lands. Money stays a `Long` throughout (rule 1): what animates is the fraction, and
 * each frame shows `old + (new − old) × fraction`, rounded to a whole cent.
 *
 * The first value is shown as it is; only changes count. TalkBack reads the final figure.
 */
@Composable
fun AnimatedMoney(
    money: Money,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = CentsTheme.colors.label,
    withSign: Boolean = false,
) {
    val reduce = rememberReduceMotion()
    var from by remember { mutableLongStateOf(money.minorUnits) }
    val progress = remember { Animatable(1f) }
    var target by remember { mutableLongStateOf(money.minorUnits) }

    LaunchedEffect(money.minorUnits) {
        if (money.minorUnits == target) return@LaunchedEffect
        val current = between(from, target, progress.value)
        from = current
        target = money.minorUnits
        if (reduce) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(COUNT_MS, easing = CountEasing))
        }
    }
    val shown = between(from, target, progress.value)
    val finalText = MoneyFormatter.format(money, withSign = withSign)
    Text(
        text = MoneyFormatter.format(money.copy(minorUnits = shown), withSign = withSign),
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier.semantics { contentDescription = finalText },
    )
}

/**
 * The figure [progress] of the way from [from] to [target], in cents.
 *
 * Through a `Double`, whose 53 bits hold any difference in cents exactly, never a `Float`: a Long
 * times a Float is a Float, and 24 bits turn a change of 20 000 001 cents into 20 000 000. At the
 * end it is [target] itself, so the figure at rest is the amount and not the arithmetic's version
 * of it (`CLAUDE.md` rule 1).
 */
internal fun between(
    from: Long,
    target: Long,
    progress: Float,
): Long = if (progress >= 1f) target else from + ((target - from).toDouble() * progress).roundToLong()
