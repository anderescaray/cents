package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * An inset grouped section: a rounded card of rows floating on the grouped background,
 * with an optional uppercase header above and an explanatory footer below.
 *
 * This is the backbone of iOS layout. Almost every screen in the app is a column of
 * these rather than a flat Material list.
 */
@Composable
fun CentsListSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                modifier =
                    Modifier.padding(
                        start = CentsDimens.screenMargin + CentsDimens.rowHorizontalPadding,
                        end = CentsDimens.screenMargin,
                        bottom = CentsDimens.sectionHeaderSpacing,
                    ),
            )
        }

        Column(
            modifier =
                Modifier
                    .padding(horizontal = CentsDimens.screenMargin)
                    .clip(RoundedCornerShape(CentsDimens.groupedCornerRadius))
                    .background(CentsTheme.colors.groupedCard)
                    .fillMaxWidth(),
            content = content,
        )

        if (footer != null) {
            Text(
                text = footer,
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                modifier =
                    Modifier.padding(
                        start = CentsDimens.screenMargin + CentsDimens.rowHorizontalPadding,
                        end = CentsDimens.screenMargin + CentsDimens.rowHorizontalPadding,
                        top = CentsDimens.sectionHeaderSpacing,
                    ),
            )
        }
    }
}

/** A column of sections with iOS spacing between them. */
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
