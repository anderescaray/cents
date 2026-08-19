package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * A closed palette as rows of dots, the chosen one ringed. Meant to sit inside a
 * [CentsListSection], which supplies the card around it.
 *
 * Every colour in the app comes from a fixed set — twelve colours that look right together
 * beat sixteen million that do not, and picking one is a tap instead of a dialog.
 */
@Composable
fun CentsColorPicker(
    colors: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(CentsDimens.rowHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
    ) {
        colors.chunked(COLORS_PER_ROW).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                row.forEach { colorArgb ->
                    ColorSwatch(
                        colorArgb = colorArgb,
                        selected = colorArgb == selected,
                        onSelect = { onSelect(colorArgb) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    colorArgb: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(SWATCH_TAP_SIZE.dp)
                .clip(CircleShape)
                .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(SWATCH_SIZE.dp)
                    .clip(CircleShape)
                    .background(Color(colorArgb))
                    .then(
                        if (selected) {
                            Modifier.border(SWATCH_RING.dp, CentsTheme.colors.label, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
        )
    }
}

private const val COLORS_PER_ROW = 6
private const val SWATCH_SIZE = 30
private const val SWATCH_TAP_SIZE = 44
private const val SWATCH_RING = 2
