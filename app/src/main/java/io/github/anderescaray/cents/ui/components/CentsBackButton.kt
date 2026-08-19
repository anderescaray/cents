package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The iOS back affordance: a chevron followed by the name of the screen you came from,
 * not a bare arrow. Knowing where back goes is half the point of showing it.
 */
@Composable
fun CentsBackButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .padding(end = CentsDimens.spacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingXs),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_left),
            contentDescription = null,
            tint = CentsTheme.colors.accent,
            modifier = Modifier.size(CHEVRON_SIZE.dp),
        )
        Text(
            text = label,
            style = CentsTheme.typography.body,
            color = CentsTheme.colors.accent,
        )
    }
}

private const val CHEVRON_SIZE = 20
