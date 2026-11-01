package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The way to Settings, the same on every tab: the last round button in the header, at its right
 * edge. A tab's own buttons, such as the filters, sit to its left, so Settings is found in one
 * place whichever tab is open. A flat grey of its own, rather than the white of the buttons beside
 * it, so it is not mistaken for one of them (Reports' Customize, above all).
 */
@Composable
fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoundIconButton(
        iconRes = R.drawable.ic_tab_settings,
        contentDescription = stringResource(R.string.tab_settings),
        onClick = onClick,
        background = CentsTheme.colors.settingsButton,
        modifier = modifier.testTag(SETTINGS_BUTTON_TAG),
    )
}

/** Tags the Settings button, so a test can find it on any tab. */
const val SETTINGS_BUTTON_TAG = "settings_button"
