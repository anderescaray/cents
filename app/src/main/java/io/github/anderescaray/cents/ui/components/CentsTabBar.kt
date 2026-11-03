package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.rememberReduceMotion

/**
 * The floating tab capsule (ADR 0003): a rounded white bar standing clear of the screen's bottom
 * edge, the sections as icons with a small name under each, and — when [onAdd] is given — the
 * add button in the middle, the one action the app exists for.
 *
 * Each tab says *tab* and whether it is selected to TalkBack, which a plain bar gave for free and
 * a drawn one has to ask for.
 */
@Composable
fun CentsTabBar(
    items: List<CentsTabItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
    addLabel: String = "",
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val half = (items.size + 1) / 2

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CentsTheme.colors.groupedBackground)
                .padding(horizontal = CAPSULE_MARGIN.dp)
                .padding(top = CentsDimens.spacingXs, bottom = bottomInset + CentsDimens.spacingS),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CentsDimens.tabBarHeight)
                    .cardSurface(CircleShape)
                    .padding(horizontal = CentsDimens.spacingS),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Every slot, the add button's included, has the same width; the pill slides between
            // the tabs' slots and skips the add button's.
            val slots = items.size + if (onAdd != null) 1 else 0
            val slotWidth = maxWidth / slots.coerceAtLeast(1)
            val selected = items.indexOfFirst { it.key == selectedKey }
            if (selected >= 0) {
                val slot = if (onAdd != null && selected >= half) selected + 1 else selected
                SelectionPill(offset = slotWidth * slot, width = slotWidth)
            }
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, item ->
                    if (onAdd != null && index == half) {
                        AddButton(onClick = onAdd, label = addLabel, modifier = Modifier.weight(1f))
                    }
                    TabButton(
                        item = item,
                        selected = item.key == selectedKey,
                        onClick = { onSelect(item.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * The soft accent pill behind the selected tab. It glides to the next one rather than jumping, so
 * a change of tab is seen in the capsule as well as on the screen.
 */
@Composable
private fun SelectionPill(
    offset: Dp,
    width: Dp,
) {
    val reduce = rememberReduceMotion()
    val x by animateDpAsState(
        targetValue = offset,
        animationSpec =
            if (reduce) {
                snap()
            } else {
                spring(
                    dampingRatio = PILL_DAMPING,
                    stiffness = Spring.StiffnessMediumLow,
                )
            },
        label = "tabPill",
    )
    Box(
        modifier =
            Modifier
                .offset(x = x + PILL_INSET.dp)
                .size(width = width - (PILL_INSET * 2).dp, height = CentsDimens.tabBarHeight - (PILL_INSET * 2 + 4).dp)
                .clip(CircleShape)
                .background(CentsTheme.colors.accent.copy(alpha = PILL_ALPHA)),
    )
}

private const val PILL_INSET = 4
private const val PILL_ALPHA = 0.1f
private const val PILL_DAMPING = 0.82f

@Composable
private fun TabButton(
    item: CentsTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) CentsTheme.colors.accent else CentsTheme.colors.inactiveIcon,
        label = "tabTint",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .testTag(tabTag(item.key))
                .semantics { this.selected = selected }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                ).scale(rememberPressScale(pressed)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(TAB_ICON.dp),
        )
        Text(
            text = item.label,
            style = CentsTheme.typography.caption2,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val accent = CentsTheme.colors.accent
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .scale(rememberPressScale(pressed))
                    .size(width = ADD_WIDTH.dp, height = ADD_HEIGHT.dp)
                    .dropShadow(
                        CircleShape,
                        Shadow(radius = 14.dp, color = accent.copy(alpha = 0.35f), offset = DpOffset(0.dp, 6.dp)),
                    ).clip(CircleShape)
                    .background(accent)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    ).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = null,
                tint = CentsTheme.colors.onAccent,
                modifier = Modifier.size(ADD_ICON.dp),
            )
        }
    }
}

private const val CAPSULE_MARGIN = 20
private const val TAB_ICON = 24
private const val ADD_WIDTH = 56
private const val ADD_HEIGHT = 48
private const val ADD_ICON = 26

/**
 * The test tag of one tab, by its key. The benchmarks in `:baselineprofile` find the tabs by it,
 * as a resource id, because a label changes with the phone's language.
 */
internal fun tabTag(key: String): String = "tab_$key"
