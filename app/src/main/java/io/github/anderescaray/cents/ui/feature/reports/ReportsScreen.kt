package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection

/** Placeholder until Phase 2 brings the charts. */
@Composable
fun ReportsScreen() {
    CentsLargeTitleScaffold(title = stringResource(R.string.tab_reports)) {
        item {
            CentsListSection(footer = stringResource(R.string.reports_placeholder_footer)) {
                CentsListRow(
                    title = stringResource(R.string.reports_placeholder_title),
                    showSeparator = false,
                )
            }
        }
    }
}
