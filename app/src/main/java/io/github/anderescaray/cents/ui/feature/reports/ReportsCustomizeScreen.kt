package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwitch
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * Arranging the Reports tab: every card, in the order it is shown, with arrows to move it and a
 * switch to show it or hide it. Arrows rather than dragging, because they are as quick for seven
 * rows, work the same with TalkBack, and cannot drop a card in the wrong place by a slip.
 */
@Composable
fun ReportsCustomizeScreen(
    onBack: () -> Unit,
    viewModel: ReportLayoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportsCustomizeContent(
        state = state,
        onBack = onBack,
        onMove = viewModel::move,
        onSetVisible = viewModel::setVisible,
        onReset = viewModel::reset,
    )
}

@Composable
internal fun ReportsCustomizeContent(
    state: ReportLayoutUiState,
    onBack: () -> Unit,
    onMove: (ReportCard, Int) -> Unit,
    onSetVisible: (ReportCard, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.reports_customize_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.tab_reports), onClick = onBack) },
    ) {
        val choices = state.choices
        item(key = "cards") {
            CentsListSection(footer = stringResource(R.string.reports_customize_footer)) {
                choices.forEachIndexed { index, choice ->
                    CentsListRow(
                        title = stringResource(choice.card.labelRes),
                        leading = {
                            MoveButtons(
                                label = stringResource(choice.card.labelRes),
                                canMoveUp = index > 0,
                                canMoveDown = index < choices.lastIndex,
                                onMove = { by -> onMove(choice.card, by) },
                            )
                        },
                        trailing = {
                            CentsSwitch(
                                checked = choice.visible,
                                onCheckedChange = { onSetVisible(choice.card, it) },
                            )
                        },
                        showSeparator = index != choices.lastIndex,
                    )
                }
            }
        }
        item(key = "reset") {
            CentsListSection {
                CentsListRow(
                    title = stringResource(R.string.reports_customize_reset),
                    showSeparator = false,
                    onClick = onReset,
                )
            }
        }
    }
}

/** Up and down, each greyed out at its end of the list. */
@Composable
private fun MoveButtons(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
        Arrow(
            description = stringResource(R.string.reports_customize_up, label),
            enabled = canMoveUp,
            rotation = UP,
            onClick = { onMove(-1) },
        )
        Arrow(
            description = stringResource(R.string.reports_customize_down, label),
            enabled = canMoveDown,
            rotation = DOWN,
            onClick = { onMove(1) },
        )
    }
}

@Composable
private fun Arrow(
    description: String,
    enabled: Boolean,
    rotation: Float,
    onClick: () -> Unit,
) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = description,
        tint = if (enabled) CentsTheme.colors.accent else CentsTheme.colors.tertiaryLabel,
        modifier =
            Modifier
                .tappable(onClick = onClick, enabled = enabled)
                .padding(CentsDimens.spacingXs)
                .size(ARROW.dp)
                .rotate(rotation),
    )
}

private const val ARROW = 18
private const val UP = -90f
private const val DOWN = 90f
