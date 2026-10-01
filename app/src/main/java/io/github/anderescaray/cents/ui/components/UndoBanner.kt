package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The one place a deletion can be taken back, for as long as it is on screen.
 *
 * Shown by every screen a movement can be deleted from and popped back to — the list and an
 * account's detail — because the offer belongs to wherever the owner lands after the delete.
 */
@Composable
fun UndoBanner(
    isTransfer: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(BANNER_RADIUS.dp))
                .background(CentsTheme.colors.label)
                .padding(horizontal = CentsDimens.spacingL, vertical = CentsDimens.spacingM),
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    if (isTransfer) R.string.transfer_deleted else R.string.transaction_deleted,
                ),
            style = CentsTheme.typography.subhead,
            // A dark pill: the inverse of the canvas, so it reads as something passing over the screen.
            color = CentsTheme.colors.groupedBackground,
        )
        Text(
            text = stringResource(R.string.action_undo),
            style = CentsTheme.typography.headline,
            color = if (CentsTheme.colors.isDark) CentsTheme.colors.accentText else AccentOnInk,
            modifier = Modifier.tappable(onClick = onUndo),
        )
    }
}

private const val BANNER_RADIUS = 24

/** The accent lightened until it reads on the ink pill in light mode. */
private val AccentOnInk = Color(0xFF5AA9FF)
