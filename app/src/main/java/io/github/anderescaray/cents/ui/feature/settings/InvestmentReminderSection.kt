package io.github.anderescaray.cents.ui.feature.settings

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.repository.InvestmentReminderRepository
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwitch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings › Investments: the Friday reminder to type the week's values (`DECISIONS.md` D40). */
@Composable
internal fun InvestmentReminderSection(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val label = stringResource(R.string.settings_investment_reminder)
    CentsListSection(
        header = stringResource(R.string.tab_investments),
        footer = stringResource(R.string.settings_investment_reminder_footer),
    ) {
        CentsListRow(
            title = label,
            onClick = { onChange(!enabled) },
            modifier = Modifier.heightIn(min = 48.dp),
            showSeparator = false,
            trailing = {
                CentsSwitch(
                    checked = enabled,
                    onCheckedChange = onChange,
                    modifier =
                        Modifier
                            .testTag(INVESTMENT_REMINDER_TAG)
                            .semantics {
                                contentDescription = label
                                toggleableState = ToggleableState(enabled)
                            },
                )
            },
        )
    }
}

/** The switch's state, from the preferences, and the way to change it. */
@HiltViewModel
class InvestmentReminderViewModel
    @Inject
    constructor(
        private val repository: InvestmentReminderRepository,
    ) : ViewModel() {
        val enabled: StateFlow<Boolean> =
            repository.observeEnabled().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

        fun set(enabled: Boolean) {
            viewModelScope.launch { repository.setEnabled(enabled) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

internal const val INVESTMENT_REMINDER_TAG = "investmentReminder"
