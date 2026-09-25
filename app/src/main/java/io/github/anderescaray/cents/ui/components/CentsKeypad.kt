package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry

/**
 * The numeric keypad for entering an amount.
 *
 * A full-width keypad rather than the system keyboard: entering an expense is the action
 * this app exists for, and the PRD asks for it in under five seconds and three taps.
 *
 * The digits are **euros**, and the decimal key is how cents are asked for — `2` `5` is 25 €,
 * `2` `,` `5` is 2,50 €. The rules that key obeys live in [AmountEntry]; the keypad only
 * reports which key was pressed.
 *
 * The bottom row is separator, zero, delete, which turns the pad into the even three-by-four
 * grid of the iOS decimal pad — closer to the platform being imitated than the two stretched
 * keys it replaces.
 *
 * **It stands on its own surface, with a hairline above it.** Without that it shared the
 * grouped background with the form it sits under, and its keys are grouped cards like every
 * other card on that ground — so in the account editor there was no line at all between the
 * colour swatches and the top row of keys, and the owner could not see where one ended and
 * the other began. A bar background and a `barSeparator` are what iOS puts under an input
 * accessory, and they are the same two tokens the tab bar uses to sit above content.
 *
 * The bottom inset is the keypad's own business rather than each caller's: it is always the
 * thing against the bottom edge when it is on screen, and a caller that padded *outside* it
 * would leave a strip of the form's background below the surface.
 */
@Composable
fun CentsKeypad(
    onDigit: (Char) -> Unit,
    onDecimalSeparator: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    deleteLabel: String = "⌫",
    separatorLabel: String = AmountEntry.separatorFor().toString(),
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CentsTheme.colors.barBackground),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CentsDimens.separatorThickness)
                    .background(CentsTheme.colors.barSeparator),
        )
        KeypadKeys(
            onDigit = onDigit,
            onDecimalSeparator = onDecimalSeparator,
            onDelete = onDelete,
            deleteLabel = deleteLabel,
            separatorLabel = separatorLabel,
        )
    }
}

/** The three-by-four grid itself, on the surface [CentsKeypad] puts under it. */
@Composable
private fun KeypadKeys(
    onDigit: (Char) -> Unit,
    onDecimalSeparator: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: String,
    separatorLabel: String,
) {
    val separatorDescription = stringResource(R.string.keypad_decimal_separator)
    val deleteDescription = stringResource(R.string.keypad_delete)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CentsDimens.spacingS)
                .padding(top = CentsDimens.spacingS)
                .padding(
                    bottom =
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            CentsDimens.spacingS,
                ),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
    ) {
        ROWS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
            ) {
                row.forEach { key ->
                    KeypadKey(
                        label =
                            when (key) {
                                DELETE_KEY -> deleteLabel
                                SEPARATOR_KEY -> separatorLabel
                                else -> key.toString()
                            },
                        description =
                            when (key) {
                                DELETE_KEY -> deleteDescription
                                SEPARATOR_KEY -> separatorDescription
                                else -> key.toString()
                            },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                DELETE_KEY -> onDelete()
                                SEPARATOR_KEY -> onDecimalSeparator()
                                else -> onDigit(key)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier =
            modifier
                .heightIn(min = KEY_HEIGHT.dp)
                .clip(RoundedCornerShape(CentsDimens.groupedCornerRadius))
                .background(
                    if (pressed) CentsTheme.colors.rowPressed else CentsTheme.colors.groupedCard,
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CentsTheme.typography.title2,
            color = CentsTheme.colors.label,
        )
    }
}

private const val DELETE_KEY = '⌫'

/** The canonical separator stands for the key; what it *says* is the locale's business. */
private const val SEPARATOR_KEY = AmountEntry.SEPARATOR

private const val KEY_HEIGHT = 52

private val ROWS =
    listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf(SEPARATOR_KEY, '0', DELETE_KEY),
    )
