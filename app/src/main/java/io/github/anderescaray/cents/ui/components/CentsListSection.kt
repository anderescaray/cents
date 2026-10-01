package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * A card of rows floating on the canvas, with an optional small heading inside it and an
 * explanatory footer below it.
 *
 * Almost every screen in the app is a column of these rather than a flat list.
 */
@Composable
fun CentsListSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = CentsDimens.screenMargin)
                    .fillMaxWidth()
                    .cardSurface(),
        ) {
            if (header != null) {
                // Inside the card rather than above it (ADR 0003): one subject per card, and the
                // card says what it is about.
                Text(
                    text = header.uppercase(),
                    style = CentsTheme.typography.overline,
                    color = CentsTheme.colors.secondaryLabel,
                    // A heading to TalkBack, so a long screen can be crossed section by section.
                    modifier =
                        Modifier.semantics { heading() }.padding(
                            start = CentsDimens.rowHorizontalPadding,
                            end = CentsDimens.rowHorizontalPadding,
                            top = CentsDimens.spacingL,
                            bottom = CentsDimens.spacingXs,
                        ),
                )
            }
            content()
        }

        if (footer != null) {
            Text(
                text = footer,
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                modifier =
                    Modifier.padding(
                        start = CentsDimens.screenMargin + CentsDimens.rowHorizontalPadding,
                        end = CentsDimens.screenMargin + CentsDimens.rowHorizontalPadding,
                        top = CentsDimens.sectionHeaderSpacing + 2.dp,
                    ),
            )
        }
    }
}

/** A column of sections with the spacing between cards. */
@Composable
fun CentsListScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .background(CentsTheme.colors.groupedBackground)
                .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.sectionSpacing),
        content = content,
    )
}
