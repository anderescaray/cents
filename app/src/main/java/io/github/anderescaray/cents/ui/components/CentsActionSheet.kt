package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The iOS action sheet: a question with more than two answers, asked from the bottom of
 * the screen with the way out kept separate underneath.
 *
 * [CentsAlert] is the right shape for a yes/no. Once there are two different things a
 * destructive button could mean, cramming them into two buttons is how someone taps the
 * wrong one — so they become a list of full-width choices, each saying what it does.
 */
@Composable
fun CentsActionSheet(
    title: String,
    message: String,
    actions: List<CentsSheetAction>,
    cancelLabel: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SHEET_RADIUS.dp))
                        .background(CentsTheme.colors.groupedCard),
            ) {
                SheetHeader(title = title, message = message)
                actions.forEach { action ->
                    Separator()
                    SheetAction(action)
                }
            }

            // Cancel is its own card, the way iOS separates "do nothing" from the choices.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SHEET_RADIUS.dp))
                        .background(CentsTheme.colors.groupedCard)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .defaultMinSize(minHeight = SHEET_ROW_HEIGHT.dp)
                        .padding(CentsDimens.spacingM),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cancelLabel,
                    style = CentsTheme.typography.headline,
                    color = CentsTheme.colors.accentText,
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier.padding(CentsDimens.spacingL),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = CentsTheme.typography.headline,
            color = CentsTheme.colors.label,
            textAlign = TextAlign.Center,
        )
        if (message.isNotBlank()) {
            Text(
                text = message,
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SheetAction(action: CentsSheetAction) {
    val color =
        when {
            !action.enabled -> CentsTheme.colors.tertiaryLabel
            action.destructive -> CentsTheme.colors.destructiveText
            action.quiet -> CentsTheme.colors.secondaryLabel
            else -> CentsTheme.colors.accentText
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
                .defaultMinSize(minHeight = if (action.quiet) QUIET_ROW_HEIGHT.dp else SHEET_ROW_HEIGHT.dp)
                .padding(
                    horizontal = CentsDimens.spacingL,
                    vertical = if (action.quiet) CentsDimens.spacingS else CentsDimens.spacingM,
                ),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = action.label,
            style = if (action.quiet) CentsTheme.typography.footnote else CentsTheme.typography.headline,
            color = color,
            textAlign = TextAlign.Center,
        )
        if (action.reason != null) {
            Text(
                text = action.reason,
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Separator() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CentsDimens.separatorThickness)
                .background(CentsTheme.colors.separator),
    )
}

private const val SHEET_RADIUS = 22
private const val SHEET_ROW_HEIGHT = 57

/** Small, but still the 48 dp a finger needs. */
private const val QUIET_ROW_HEIGHT = 48
