package io.github.anderescaray.cents.ui.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.MoneyFigure
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwitch

/**
 * Which money figures the top of Home shows (`DECISIONS.md` D34): cash and bank, invested, and
 * their total, each its own switch. Wallet always shows all three (`DECISIONS.md` D37).
 */
@Composable
internal fun MoneyFiguresSection(
    figures: MoneyFigures,
    onChange: (MoneyFigure, Boolean) -> Unit,
) {
    CentsListSection(
        header = stringResource(R.string.settings_figures_header),
        footer = stringResource(R.string.settings_figures_footer),
    ) {
        MoneyFigure.entries.forEach { figure ->
            FigureRow(
                figure = figure,
                on = figures.isOn(figure),
                last = figure == MoneyFigure.entries.last(),
                onChange = { onChange(figure, it) },
            )
        }
    }
}

@Composable
private fun FigureRow(
    figure: MoneyFigure,
    on: Boolean,
    last: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val label = stringResource(figure.label)
    CentsListRow(
        title = label,
        onClick = { onChange(!on) },
        modifier = Modifier.heightIn(min = 48.dp),
        showSeparator = !last,
        trailing = {
            CentsSwitch(
                checked = on,
                onCheckedChange = onChange,
                modifier =
                    Modifier
                        .testTag(figure.switchTag)
                        .semantics {
                            contentDescription = label
                            toggleableState = ToggleableState(on)
                        },
            )
        },
    )
}

/** What a figure is called on its switch: the same words as on Wallet and Home. */
@get:StringRes
private val MoneyFigure.label: Int
    get() =
        when (this) {
            MoneyFigure.NORMAL -> R.string.money_normal
            MoneyFigure.INVESTED -> R.string.net_worth_invested
            MoneyFigure.TOTAL -> R.string.net_worth_total
        }

internal val MoneyFigure.switchTag: String get() = "figuresSwitch$name"
