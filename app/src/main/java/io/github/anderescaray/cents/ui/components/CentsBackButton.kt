package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R

/**
 * Back: a round button with a chevron. [label] is the screen it returns to, and it is what TalkBack
 * says — "Back to Settings" — so knowing where back goes is not lost now the word is not drawn.
 */
@Composable
fun CentsBackButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoundIconButton(
        iconRes = R.drawable.ic_chevron_left,
        contentDescription = stringResource(R.string.action_back_to, label),
        onClick = onClick,
        modifier = modifier,
    )
}
