package io.github.anderescaray.cents.ui.feature.transactions

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * Placeholder. The real screen arrives with the transaction repository; this exists so the
 * design system can be seen and judged on a device before screens are built on top of it.
 */
@Composable
fun TransactionsScreen() {
    CentsLargeTitleScaffold(title = stringResource(R.string.tab_transactions)) {
        item {
            CentsListSection(header = stringResource(R.string.placeholder_section_header)) {
                CentsListRow(
                    title = stringResource(R.string.placeholder_row_title),
                    subtitle = stringResource(R.string.placeholder_row_subtitle),
                    value = "−12,50 €",
                    valueColor = CentsTheme.colors.label,
                    showChevron = true,
                    onClick = {},
                )
                CentsListRow(
                    title = stringResource(R.string.placeholder_row_income),
                    value = "+1.850,00 €",
                    valueColor = CentsTheme.colors.positive,
                    showChevron = true,
                    showSeparator = false,
                    onClick = {},
                )
            }
        }
        item {
            CentsListSection(
                footer = stringResource(R.string.placeholder_empty_footer),
                modifier = Modifier.padding(bottom = CentsDimens.spacingL),
            ) {
                CentsListRow(
                    title = stringResource(R.string.placeholder_empty_title),
                    showSeparator = false,
                )
            }
        }
    }
}

@Preview
@Composable
private fun TransactionsScreenPreview() {
    CentsTheme {
        TransactionsScreen()
    }
}
