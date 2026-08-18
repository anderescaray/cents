package io.github.anderescaray.cents.ui.feature.accounts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.theme.CentsTheme

/** Placeholder until the account repository lands. */
@Composable
fun AccountsScreen() {
    CentsLargeTitleScaffold(title = stringResource(R.string.tab_accounts)) {
        item {
            CentsListSection(header = stringResource(R.string.accounts_section_header)) {
                CentsListRow(
                    title = stringResource(R.string.placeholder_account_checking),
                    value = "2.431,08 €",
                    valueColor = CentsTheme.colors.label,
                    showChevron = true,
                    onClick = {},
                )
                CentsListRow(
                    title = stringResource(R.string.placeholder_account_cash),
                    value = "85,00 €",
                    valueColor = CentsTheme.colors.label,
                    showChevron = true,
                    showSeparator = false,
                    onClick = {},
                )
            }
        }
    }
}
