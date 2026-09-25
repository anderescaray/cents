package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The iOS alert: a narrow centred card, the message under the title, and the actions side
 * by side under a hairline — never Material's text buttons pushed to the bottom right.
 *
 * The confirming action is the one on the right, in red when it destroys something.
 *
 * With no [dismissLabel] it becomes the one-button alert iOS uses to report something that
 * has already happened: there is nothing to decide, only something to acknowledge, and a
 * "Cancel" next to it would offer a choice that does not exist.
 */
@Composable
fun CentsAlert(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .width(ALERT_WIDTH.dp)
                    .clip(RoundedCornerShape(ALERT_RADIUS.dp))
                    .background(CentsTheme.colors.groupedCard),
        ) {
            AlertText(title = title, message = message)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(CentsDimens.separatorThickness)
                        .background(CentsTheme.colors.separator),
            )
            AlertActions(
                confirmLabel = confirmLabel,
                onConfirm = onConfirm,
                dismissLabel = dismissLabel,
                onDismiss = onDismiss,
                destructive = destructive,
            )
        }
    }
}

@Composable
private fun AlertText(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier.padding(horizontal = CentsDimens.spacingL, vertical = ALERT_TEXT_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = CentsTheme.typography.headline,
            color = CentsTheme.colors.label,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = CentsTheme.typography.footnote,
            color = CentsTheme.colors.label,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlertActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String?,
    onDismiss: () -> Unit,
    destructive: Boolean,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        if (dismissLabel != null) {
            AlertAction(
                label = dismissLabel,
                onClick = onDismiss,
                emphasised = false,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .width(CentsDimens.separatorThickness)
                        .fillMaxHeight()
                        .background(CentsTheme.colors.separator),
            )
        }
        AlertAction(
            label = confirmLabel,
            onClick = onConfirm,
            emphasised = true,
            destructive = destructive,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AlertAction(
    label: String,
    onClick: () -> Unit,
    emphasised: Boolean,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = CentsDimens.minRowHeight)
                .padding(horizontal = CentsDimens.spacingS),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CentsTheme.typography.body,
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
            color = if (destructive) CentsTheme.colors.destructive else CentsTheme.colors.accent,
            textAlign = TextAlign.Center,
        )
    }
}

private const val ALERT_WIDTH = 270
private const val ALERT_RADIUS = 14
private const val ALERT_TEXT_PADDING = 19
