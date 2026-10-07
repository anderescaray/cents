package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/** Scroll distance over which the large title hands over to the compact bar. */
private const val COLLAPSE_THRESHOLD_PX = 60

/**
 * A screen with an iOS large title that collapses into a compact bar on scroll.
 *
 * The large title lives inside the scrolling content, exactly as on iOS, so it scrolls
 * away naturally; the bar above it fades in only once the title has left. The collapse
 * state is derived with derivedStateOf, so scrolling does not recompose the whole screen
 * on every pixel.
 */
@Composable
fun CentsLargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomPadding: Dp = 0.dp,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > COLLAPSE_THRESHOLD_PX
        }
    }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "navBarAlpha")

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground),
    ) {
        LazyColumn(
            state = listState,
            contentPadding =
                PaddingValues(
                    top = topInset + CentsDimens.barHeight,
                    bottom = bottomPadding + CentsDimens.sectionSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.sectionSpacing),
        ) {
            item(key = "largeTitle") { LargeTitle(title) }
            content()
        }

        CollapsingNavBar(
            title = title,
            alpha = barAlpha,
            topInset = topInset,
            leading = leading,
            trailing = trailing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun LargeTitle(title: String) {
    Text(
        text = title,
        style = CentsTheme.typography.largeTitle,
        color = CentsTheme.colors.label,
        modifier =
            Modifier.semantics { heading() }.padding(
                start = CentsDimens.screenMargin,
                end = CentsDimens.screenMargin,
                bottom = CentsDimens.spacingS,
            ),
    )
}

/** The compact bar that takes over once the large title has scrolled away. */
@Composable
private fun CollapsingNavBar(
    title: String,
    alpha: Float,
    topInset: Dp,
    leading: (@Composable RowScope.() -> Unit)?,
    trailing: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CentsTheme.colors.barBackground.copy(alpha = alpha)),
    ) {
        Box(modifier = Modifier.height(topInset))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = CentsDimens.barHeight)
                    .padding(horizontal = CentsDimens.screenMargin),
            verticalAlignment = Alignment.CenterVertically,
            // A gutter between the buttons and the compact title, so neither touches the other.
            horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
        ) {
            // The back affordance stays legible while the large title is still on screen;
            // only the compact title itself fades in.
            if (leading != null) {
                leading()
            }
            Text(
                text = title,
                style = CentsTheme.typography.headline,
                color = CentsTheme.colors.label,
                // One line in a bar: a long title in Spanish, or at a large text size, ends in an
                // ellipsis rather than pushing the buttons out or growing the bar.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .alpha(alpha)
                        // Faded out it is not there, and TalkBack would read the title twice:
                        // once here and once as the large one in the list.
                        .then(if (alpha > 0f) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics {}),
            )
            if (trailing != null) {
                trailing()
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CentsDimens.separatorThickness)
                    .background(CentsTheme.colors.barSeparator.copy(alpha = alpha)),
        )
    }
}
