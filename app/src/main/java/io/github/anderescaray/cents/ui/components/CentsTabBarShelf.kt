package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The tab bar and the grabber that puts it away.
 *
 * The canvas is the way into a section; the bar is how you move between sections once you are
 * in one. Some screens want the whole height, so the bar collapses to a grabber — and the
 * grabber stays, because a bar that can be hidden with no way back is a bar that is gone.
 *
 * Collapsing does not change what **back** does: from a section root it returns to the canvas
 * either way. A gesture that means two different things depending on a toggle is worse than a
 * toggle that only hides a bar.
 */
@Composable
fun CentsTabBarShelf(
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bar: @Composable () -> Unit,
) {
    val label =
        if (collapsed) {
            stringResource(R.string.tab_bar_show)
        } else {
            stringResource(R.string.tab_bar_hide)
        }

    Column(modifier = modifier.fillMaxWidth().background(CentsTheme.colors.barBackground)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(GrabberRowHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onCollapsedChange(!collapsed) },
                    ).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = GrabberWidth, height = GrabberHeight)
                        .background(
                            color = CentsTheme.colors.tertiaryLabel,
                            shape = RoundedCornerShape(GrabberHeight / 2),
                        ),
            )
        }

        AnimatedVisibility(visible = !collapsed, exit = shrinkVertically()) {
            bar()
        }

        // Collapsed, the shelf still has to stand clear of the gesture bar; expanded, the tab
        // bar carries that inset itself and this would double it.
        if (collapsed) {
            Box(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

private val GrabberRowHeight = 22.dp
private val GrabberWidth = 36.dp
private val GrabberHeight = 5.dp
