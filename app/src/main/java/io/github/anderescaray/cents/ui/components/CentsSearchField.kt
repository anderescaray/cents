package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The iOS search field: a rounded grey capsule with a magnifier at the front and, once
 * there is something to clear, a filled cross at the back.
 *
 * Unlike [CentsTextField] this one draws its own container, because it does not live inside
 * a grouped section — it sits above a list, which is where iOS puts it.
 *
 * The clear button is a real target rather than a decoration: emptying a search field by
 * holding backspace is the kind of thing that makes a screen feel like a form.
 */
@Composable
fun CentsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val accent = CentsTheme.colors.accent
    val selectionColors =
        remember(accent) {
            TextSelectionColors(
                handleColor = accent,
                backgroundColor = accent.copy(alpha = SELECTION_ALPHA),
            )
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FIELD_HEIGHT.dp)
                .clip(RoundedCornerShape(FIELD_RADIUS.dp))
                .background(CentsTheme.colors.fill)
                .padding(horizontal = CentsDimens.spacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = CentsTheme.colors.secondaryLabel,
            modifier = Modifier.size(GLYPH_SIZE.dp),
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = CentsTheme.typography.body,
                    color = CentsTheme.colors.secondaryLabel,
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
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Search,
                        ),
                    modifier = Modifier.fillMaxWidth().testTag(SEARCH_FIELD_TAG),
                )
            }
        }

        if (value.isNotEmpty()) {
            ClearButton(onClick = { onValueChange("") })
        }
    }
}

/**
 * An 18 dp dot with a 44 dp target around it.
 *
 * The glyph is the size iOS draws it; the thing a finger has to hit is not. This is the only
 * way to empty the field short of holding backspace, which is exactly what it exists to save
 * anyone from.
 */
@Composable
private fun ClearButton(onClick: () -> Unit) {
    val label = stringResource(R.string.search_clear)
    Box(
        modifier =
            Modifier
                .semantics { contentDescription = label }
                .testTag(SEARCH_CLEAR_TAG)
                .size(CentsDimens.minRowHeight)
                .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(GLYPH_SIZE.dp)
                    .clip(CircleShape)
                    .background(CentsTheme.colors.tertiaryLabel),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = CLEAR_MARK,
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.groupedCard,
            )
        }
    }
}

private const val FIELD_HEIGHT = 36
private const val FIELD_RADIUS = 14
private const val GLYPH_SIZE = 18
private const val SELECTION_ALPHA = 0.3f
private const val CLEAR_MARK = "✕"

internal const val SEARCH_FIELD_TAG = "searchField"
internal const val SEARCH_CLEAR_TAG = "searchClear"
