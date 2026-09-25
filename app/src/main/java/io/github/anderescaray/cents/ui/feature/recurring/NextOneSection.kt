package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.usecase.nextUnwritten
import io.github.anderescaray.cents.domain.usecase.postponeChoices
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.Today
import io.github.anderescaray.cents.ui.util.relativeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The rule's next occurrence not yet written, and whether it has been moved. */
@Immutable
data class NextOneUiState(
    val name: String = "",
    /** Null while loading, for a rule that has stopped, or one past its end date. */
    val occurrence: LocalDate? = null,
    val postponedTo: LocalDate? = null,
    val choices: List<LocalDate> = emptyList(),
    val today: LocalDate = LocalDate.EPOCH,
    val picking: Boolean = false,
) {
    /** The day the movement will carry. */
    val dueOn: LocalDate? get() = postponedTo ?: occurrence
}

/**
 * Postponing a rule's next occurrence from its editor.
 *
 * **Applied at once rather than held until Save**, like stopping the rule: it acts on one
 * occurrence, not on the rule's definition, and a Cancel that quietly undid it would be a
 * postponement the owner saw happen and then did not get.
 *
 * Its own view model, reading the same route as the editor's, so the editor's form state does
 * not grow a second concern.
 */
@HiltViewModel
class NextOneViewModel
    @Inject
    constructor(
        private val repository: RecurringRuleRepository,
        savedStateHandle: SavedStateHandle,
        today: Today,
    ) : ViewModel() {
        private val ruleId: Long = savedStateHandle.toRoute<Destination.RuleForm>().ruleId
        private val picking = MutableStateFlow(false)

        val uiState: StateFlow<NextOneUiState> =
            combine(
                repository.observeRule(ruleId),
                repository.observePostponements(),
                today.date,
                picking,
            ) { rule, postponements, today, picking ->
                val occurrence = rule?.nextUnwritten()
                NextOneUiState(
                    name = rule?.name.orEmpty(),
                    occurrence = occurrence,
                    postponedTo =
                        postponements
                            .firstOrNull { it.ruleId == ruleId && it.occurrence == occurrence }
                            ?.postponedTo,
                    choices = occurrence?.let { day -> rule?.postponeChoices(day, today) }.orEmpty(),
                    today = today,
                    picking = picking,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = NextOneUiState(),
            )

        fun startPicking() {
            picking.value = true
        }

        fun stopPicking() {
            picking.value = false
        }

        fun postpone(to: LocalDate) {
            val occurrence = uiState.value.occurrence ?: return
            picking.value = false
            viewModelScope.launch { repository.postpone(ruleId, occurrence, to) }
        }

        fun restore() {
            val occurrence = uiState.value.occurrence ?: return
            viewModelScope.launch { repository.cancelPostponement(ruleId, occurrence) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** *Next one: 1 Oct*, and the way to move it. Nothing at all for a rule with no next one. */
@Composable
internal fun NextOneSection(viewModel: NextOneViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val occurrence = state.occurrence ?: return
    val postponedTo = state.postponedTo

    CentsListSection(
        header = stringResource(R.string.next_one_header),
        footer = stringResource(R.string.next_one_footer),
    ) {
        CentsListRow(
            title = stringResource(if (postponedTo != null) R.string.next_one_moved else R.string.next_one_due),
            subtitle =
                postponedTo?.let {
                    stringResource(R.string.next_one_moved_from, occurrence.relativeLabel(state.today))
                },
            value = state.dueOn?.relativeLabel(state.today),
            showSeparator = state.choices.isNotEmpty() || postponedTo != null,
        )
        if (state.choices.isNotEmpty()) {
            CentsActionRow(
                label = stringResource(R.string.next_one_postpone),
                onClick = viewModel::startPicking,
                showSeparator = postponedTo != null,
            )
        }
        if (postponedTo != null) {
            CentsActionRow(label = stringResource(R.string.next_one_restore), onClick = viewModel::restore)
        }
    }

    if (state.picking) {
        PostponeSheet(
            name = state.name,
            from = maxOf(occurrence, state.today),
            today = state.today,
            choices = state.choices,
            onChoose = viewModel::postpone,
            onDismiss = viewModel::stopPicking,
        )
    }
}
