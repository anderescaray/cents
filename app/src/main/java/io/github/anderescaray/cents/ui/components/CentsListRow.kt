package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * One row inside a [CentsListSection].
 *
 * Tappable rows show a chevron and fill with a flat pressed colour — iOS does not ripple.
 * The separator is inset to the start of the text, which is what makes a stack of rows
 * read as one grouped card instead of a table.
 */
@Composable
fun CentsListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /**
     * How many lines the subtitle may take before it ends in an ellipsis. Unlimited by default;
     * a row whose subtitle is the owner's own prose — a note — caps it, or one long note makes
     * the row as tall as the note.
     */
    subtitleMaxLines: Int = Int.MAX_VALUE,
    value: String? = null,
    valueColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    showSeparator: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val rowModifier =
        if (onClick != null) {
            modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
        } else {
            modifier
        }

    Column(
        modifier =
            rowModifier
                .fillMaxWidth()
                .background(if (pressed) CentsTheme.colors.rowPressed else CentsTheme.colors.groupedCard),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = CentsDimens.minRowHeight)
                    .padding(
                        horizontal = CentsDimens.rowHorizontalPadding,
                        vertical = CentsDimens.rowVerticalPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
        ) {
            if (leading != null) {
                leading()
            }
            RowLabels(
                title = title,
                subtitle = subtitle,
                subtitleMaxLines = subtitleMaxLines,
                modifier = Modifier.weight(1f),
            )
            RowAccessories(
                value = value,
                valueColor = valueColor,
                trailing = trailing,
                showChevron = showChevron,
            )
        }

        if (showSeparator) {
            RowSeparator()
        }
    }
}

@Composable
private fun RowLabels(
    title: String,
    subtitle: String?,
    subtitleMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = CentsTheme.typography.body,
            color = CentsTheme.colors.label,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.RowAccessories(
    value: String?,
    valueColor: Color?,
    trailing: (@Composable () -> Unit)?,
    showChevron: Boolean,
) {
    if (value != null) {
        Text(
            text = value,
            style = CentsTheme.typography.body,
            color = valueColor ?: CentsTheme.colors.secondaryLabel,
        )
    }
    if (trailing != null) {
        trailing()
    }
    if (showChevron) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CentsTheme.colors.tertiaryLabel,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ColumnScope.RowSeparator() {
    Box(
        modifier =
            Modifier
                .padding(start = CentsDimens.rowHorizontalPadding)
                .fillMaxWidth()
                .height(CentsDimens.separatorThickness)
                .background(CentsTheme.colors.separator),
    )
}

/** Lines a note may take as a row's subtitle: enough to recognise it, not enough to read it all. */
const val NOTE_MAX_LINES = 2
