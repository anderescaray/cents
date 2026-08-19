package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * A row whose whole point is its label: centred and tinted, with no chevron and no value.
 * This is how iOS presents "Delete Account" or "Sign Out" at the foot of a form.
 */
@Composable
fun CentsActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    showSeparator: Boolean = false,
) {
    Column(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(CentsTheme.colors.groupedCard)
                    .clickable(onClick = onClick)
                    .padding(vertical = CentsDimens.spacingM),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = CentsTheme.typography.body,
                color = if (destructive) CentsTheme.colors.destructive else CentsTheme.colors.accent,
            )
        }
        if (showSeparator) {
            Box(
                modifier =
                    Modifier
                        .padding(start = CentsDimens.rowHorizontalPadding)
                        .fillMaxWidth()
                        .height(CentsDimens.separatorThickness)
                        .background(CentsTheme.colors.separator),
            )
        }
    }
}
