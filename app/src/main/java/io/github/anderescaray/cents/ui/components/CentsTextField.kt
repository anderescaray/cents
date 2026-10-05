package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
    /**
     * An amount, never a sentence: the decimal pad to type it, and nothing but digits and a
     * decimal separator allowed through. What may follow that separator is the caller's
     * business — see [io.github.anderescaray.cents.ui.util.AmountEntry].
     */
    numeric: Boolean = false,
    /** Set when another row follows inside the same section, as [CentsListRow] does. */
    showSeparator: Boolean = false,
) {
    val accent = CentsTheme.colors.accent
    val selectionColors =
        remember(accent) {
            TextSelectionColors(
                handleColor = accent,
                backgroundColor = accent.copy(alpha = SELECTION_ALPHA),
            )
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
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
                Field(
                    value = value,
                    onValueChange = onValueChange,
                    numeric = numeric,
                    accent = accent,
                    focusRequester = focusRequester,
                    modifier = modifier,
                )
            }
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

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean,
    accent: Color,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        // Filtered here rather than trusted to the keyboard: a decimal pad still pastes, and
        // a physical keyboard types whatever it likes. Both separators come through, because
        // which one a keyboard offers does not always follow the locale.
        onValueChange = { typed ->
            onValueChange(if (numeric) typed.filter { it.isDigit() || it in NUMERIC_SEPARATORS } else typed)
        },
        singleLine = true,
        textStyle = CentsTheme.typography.body.copy(color = CentsTheme.colors.label),
        cursorBrush = SolidColor(accent),
        keyboardOptions =
            KeyboardOptions(
                capitalization =
                    if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
        // The caller's modifier lands on the field itself, which is the node that carries
        // the text semantics.
        modifier =
            modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
    )
}

private const val SELECTION_ALPHA = 0.3f

/** Both, so a keyboard that offers the "wrong" one for the locale is not a dead key. */
private const val NUMERIC_SEPARATORS = ".,"
