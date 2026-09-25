package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PendingCharge
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.usecase.postponeChoices
import io.github.anderescaray.cents.ui.util.AmountEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** One waiting bill, and what the owner is typing for it. */
@Immutable
data class PendingRow(
    val charge: PendingCharge,
    /** What is being typed, in [AmountEntry]'s canonical form. Empty means "not this one yet". */
    val input: String = AmountEntry.EMPTY,
    /** The days it can be moved to, soonest first. Empty when it cannot move at all. */
    val postponeChoices: List<LocalDate> = emptyList(),
) {
    val ruleId: Long get() = charge.rule.id

    val dueDate: LocalDate get() = charge.dueDate

    val name: String get() = charge.rule.name

    val currency: String get() = charge.rule.amount.currency

    val amount: Money get() = Money(AmountEntry.toMinorUnits(input), currency)

    val hasAmount: Boolean get() = input.isNotEmpty() && amount.minorUnits > 0
}

@Immutable
data class PendingChargesUiState(
    val rows: List<PendingRow> = emptyList(),
    val today: LocalDate = LocalDate.EPOCH,
    val loaded: Boolean = false,
    val skipping: PendingRow? = null,
    /** The row whose postpone sheet is open. */
    val postponing: PendingRow? = null,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val finished: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && rows.isEmpty()

    /** How many bills are about to be recorded, which is what the button says. */
    val answeredCount: Int get() = rows.count { it.hasAmount }

    val canSave: Boolean get() = answeredCount > 0 && !saving
}

/**
 * The bills the app refused to guess.
 *
 * A rule set to *ask me the amount* records nothing on its own (PRD §4.4), which leaves a question
 * with nowhere to be answered — this is that place. Several at once, the way the price screen
 * takes a whole broker page in one sitting: the electricity, the water and the gas all arrive in
 * the same week, and three separate forms would be three separate trips.
 *
 * **A row left empty is left alone.** Blank is how somebody says *I have not opened that one yet*,
 * and reading it as zero would write a bill that says the month cost nothing.
 *
 * The list is read once rather than observed. It is a form, and a list that re-emitted underneath
 * somebody halfway through typing would take the half-typed figure with it.
 */
@HiltViewModel
class PendingChargesViewModel
    @Inject
    constructor(
        private val repository: RecurringRuleRepository,
        clock: Clock,
    ) : ViewModel() {
        private val state = MutableStateFlow(PendingChargesUiState(today = LocalDate.now(clock)))
        val uiState: StateFlow<PendingChargesUiState> = state.asStateFlow()

        init {
            viewModelScope.launch { reload() }
        }

        /**
         * Reads the list again, keeping whatever was typed into the rows that are still there.
         *
         * Skipping or postponing one bill must not wipe the figures typed into the others.
         */
        private suspend fun reload() {
            val pending = repository.observePending().first()
            val today = state.value.today
            state.update { current ->
                val typed = current.rows.associate { (it.ruleId to it.dueDate) to it.input }
                val rows =
                    pending.map { charge ->
                        PendingRow(
                            charge = charge,
                            input = typed[charge.rule.id to charge.dueDate] ?: AmountEntry.EMPTY,
                            postponeChoices = charge.rule.postponeChoices(charge.dueDate, today),
                        )
                    }
                current.copy(rows = rows, loaded = true)
            }
        }

        fun onTyped(
            ruleId: Long,
            input: String,
        ) = state.update { current ->
            current.copy(
                rows =
                    current.rows.map { row ->
                        if (row.ruleId == ruleId) row.copy(input = AmountEntry.sanitize(input)) else row
                    },
            )
        }

        fun requestSkip(row: PendingRow) = state.update { it.copy(skipping = row) }

        fun cancelSkip() = state.update { it.copy(skipping = null) }

        /**
         * Moves past one occurrence without recording anything.
         *
         * *There was no bill that month* is a real answer, and it is not the same as zero: a zero
         * is a figure that would sit in the charts, and this is the absence of one. Nothing that
         * was already recorded is touched — only the rule's memory of how far it has got moves.
         */
        fun confirmSkip() {
            val row = state.value.skipping ?: return
            viewModelScope.launch {
                val done = runCatching { repository.skipPending(row.ruleId, row.dueDate) }
                state.update { it.copy(skipping = null, writeFailed = done.getOrDefault(false).not()) }
                reload()
                if (state.value.rows.isEmpty()) state.update { it.copy(finished = true) }
            }
        }

        fun requestPostpone(row: PendingRow) {
            if (row.postponeChoices.isNotEmpty()) state.update { it.copy(postponing = row) }
        }

        fun cancelPostpone() = state.update { it.copy(postponing = null) }

        /**
         * Moves one bill to a later day.
         *
         * It leaves this list until that day, is recorded on it once answered, and the owner is
         * reminded that morning. Nothing else about the rule changes.
         */
        fun postpone(to: LocalDate) {
            val row = state.value.postponing ?: return
            viewModelScope.launch {
                val done = runCatching { repository.postpone(row.ruleId, row.dueDate, to) }
                state.update { it.copy(postponing = null, writeFailed = done.getOrDefault(false).not()) }
                reload()
                if (state.value.rows.isEmpty()) state.update { it.copy(finished = true) }
            }
        }

        fun dismissFailure() = state.update { it.copy(writeFailed = false) }

        /**
         * Records every bill that was typed, and nothing else.
         *
         * Each one is written on **the day it fell due** — or the day it was moved to — not
         * today. A bill answered a week late still belongs to the period it was for, and dating
         * it by when somebody got round to it would move money between months in every chart in
         * the app.
         */
        fun save() {
            val current = state.value
            if (!current.canSave) return
            state.update { it.copy(saving = true) }
            viewModelScope.launch {
                val written =
                    runCatching {
                        current.rows.filter { it.hasAmount }.all { row ->
                            repository.recordPending(row.ruleId, row.dueDate, row.amount)
                        }
                    }
                state.update {
                    it.copy(
                        saving = false,
                        writeFailed = written.getOrDefault(false).not(),
                        finished = written.getOrDefault(false),
                    )
                }
            }
        }
    }
