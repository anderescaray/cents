package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * A text field shaped like a row of an inset grouped list: no box, no floating label and
 * no underline — the surrounding [CentsListSection] already provides the container, which
 * is how iOS forms are built.
 */
@Composable
fun CentsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val accent = CentsTheme.colors.accent
    val selectionColors =
        remember(accent) {
            TextSelectionColors(
                handleColor = accent,
                backgroundColor = accent.copy(alpha = SELECTION_ALPHA),
            )
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CentsTheme.colors.groupedCard)
                .defaultMinSize(minHeight = CentsDimens.minRowHeight)
                .padding(
                    horizontal = CentsDimens.rowHorizontalPadding,
                    vertical = CentsDimens.rowVerticalPadding,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = CentsTheme.typography.body,
                color = CentsTheme.colors.tertiaryLabel,
            )
        }
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = CentsTheme.typography.body.copy(color = CentsTheme.colors.label),
                cursorBrush = SolidColor(accent),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                // The caller's modifier lands on the field itself, which is the node that
                // carries the text semantics.
                modifier =
                    modifier
                        .fillMaxWidth()
                        .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            )
        }
    }
}

private const val SELECTION_ALPHA = 0.3f
