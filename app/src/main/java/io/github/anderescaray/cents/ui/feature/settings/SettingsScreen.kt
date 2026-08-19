package io.github.anderescaray.cents.ui.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwitch
import io.github.anderescaray.cents.ui.theme.CentsDimens

/**
 * Placeholder that doubles as a shop window for the design system: a switch row, a value
 * row, a disclosure row and the prominent button, so all of it can be judged on a device.
 */
@Composable
fun SettingsScreen(onOpenCategories: () -> Unit) {
    var biometricLock by remember { mutableStateOf(false) }

    CentsLargeTitleScaffold(title = stringResource(R.string.tab_settings)) {
        item {
            CentsListSection(footer = stringResource(R.string.settings_categories_footer)) {
                CentsListRow(
                    title = stringResource(R.string.settings_categories),
                    showChevron = true,
                    showSeparator = false,
                    onClick = onOpenCategories,
                )
            }
        }
        item {
            CentsListSection(header = stringResource(R.string.settings_section_security)) {
                CentsListRow(
                    title = stringResource(R.string.settings_biometric_lock),
                    trailing = {
                        CentsSwitch(
                            checked = biometricLock,
                            onCheckedChange = { biometricLock = it },
                        )
                    },
                    showSeparator = false,
                )
            }
        }
        item {
            CentsListSection(
                header = stringResource(R.string.settings_section_data),
                footer = stringResource(R.string.settings_data_footer),
            ) {
                CentsListRow(
                    title = stringResource(R.string.settings_export),
                    showChevron = true,
                    onClick = {},
                )
                CentsListRow(
                    title = stringResource(R.string.settings_currency),
                    value = "EUR",
                    showChevron = true,
                    showSeparator = false,
                    onClick = {},
                )
            }
        }
        item {
            CentsButton(
                text = stringResource(R.string.settings_add_transaction),
                onClick = {},
                modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
            )
        }
    }
}
