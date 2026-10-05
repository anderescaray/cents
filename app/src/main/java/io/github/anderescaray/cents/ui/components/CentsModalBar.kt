package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The bar at the top of a modally presented screen: cancelling on the left, the title in
 * the middle and the confirming action on the right, which is the iOS convention for a
 * form you either commit or throw away.
 */
@Composable
fun CentsModalBar(
    title: String,
    cancelLabel: String,
    onCancel: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CentsDimens.screenMargin, vertical = CentsDimens.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = cancelLabel,
            style = CentsTheme.typography.body,
            color = CentsTheme.colors.accentText,
            modifier = Modifier.tappable(onClick = onCancel),
        )
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CentsTheme.typography.headline,
                color = CentsTheme.colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
        }
        Text(
            text = confirmLabel,
            style = CentsTheme.typography.headline,
            color = if (confirmEnabled) CentsTheme.colors.accent else CentsTheme.colors.tertiaryLabel,
            modifier = Modifier.tappable(onClick = onConfirm, enabled = confirmEnabled),
        )
    }
}
