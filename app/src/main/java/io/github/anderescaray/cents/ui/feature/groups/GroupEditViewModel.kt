package io.github.anderescaray.cents.ui.feature.groups

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Group
import io.github.anderescaray.cents.domain.repository.GroupRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Which of a group's two dates a calendar is open for. */
enum class GroupDate { START, END }

@Immutable
data class GroupEditUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val today: LocalDate = LocalDate.now(),
    val picking: GroupDate? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val confirmingDelete: Boolean = false,
    /** Set once the group is deleted, so the screen can leave its detail behind as well. */
    val deleted: Boolean = false,
    val finished: Boolean = false,
) {
    /** A trip that ends before it starts is a typo, and saying so beats saving it. */
    val datesInverted: Boolean
        get() = startDate != null && endDate != null && endDate < startDate

    val canSave: Boolean get() = loaded && !saving && name.isNotBlank() && !datesInverted
}

/**
 * Making or changing one group: its name and, optionally, the dates it covers (D27).
 *
 * The dates only ever **suggest**: while a movement's date falls between them, the movement form
 * offers this group first. So both are optional and either can be left open.
 */
@HiltViewModel
class GroupEditViewModel
    @Inject
    constructor(
        private val groups: GroupRepository,
        savedStateHandle: SavedStateHandle,
        clock: Clock,
    ) : ViewModel() {
        private val groupId: Long? =
            savedStateHandle
                .toRoute<Destination.GroupEdit>()
                .groupId
                .takeUnless { it == Destination.GroupEdit.NEW }

        private val form =
            MutableStateFlow(
                GroupEditUiState(isNew = groupId == null, today = LocalDate.now(clock), loaded = groupId == null),
            )
        val uiState: StateFlow<GroupEditUiState> = form.asStateFlow()

        init {
            val id = groupId
            if (id != null) {
                viewModelScope.launch {
                    val existing = groups.findById(id)
                    form.update { state ->
                        state.copy(
                            name = existing?.name.orEmpty(),
                            startDate = existing?.startDate,
                            endDate = existing?.endDate,
                            loaded = existing != null,
                        )
                    }
                }
            }
        }

        fun setName(name: String) = form.update { it.copy(name = name, writeFailed = false) }

        fun openPicker(which: GroupDate) = form.update { it.copy(picking = which) }

        fun dismissPicker() = form.update { it.copy(picking = null) }

        fun setDate(date: LocalDate) =
            form.update { state ->
                when (state.picking) {
                    GroupDate.START -> state.copy(startDate = date, picking = null)
                    GroupDate.END -> state.copy(endDate = date, picking = null)
                    null -> state
                }
            }

        fun clearDates() = form.update { it.copy(startDate = null, endDate = null) }

        fun save() {
            val state = form.value
            if (!state.canSave) return
            form.update { it.copy(saving = true, writeFailed = false) }
            viewModelScope.launch {
                val written =
                    write {
                        groups.saveGroup(
                            Group(
                                id = groupId ?: Group.NEW,
                                name = state.name,
                                startDate = state.startDate,
                                endDate = state.endDate,
                            ),
                        )
                    }
                form.update { it.copy(saving = false, writeFailed = !written, finished = written) }
            }
        }

        /** Rule 7: deleting asks first — and says the movements stay, because that is the worry. */
        fun requestDelete() {
            if (groupId == null) return
            form.update { it.copy(confirmingDelete = true) }
        }

        fun cancelDelete() = form.update { it.copy(confirmingDelete = false) }

        fun confirmDelete() {
            val id = groupId ?: return
            form.update { it.copy(confirmingDelete = false, saving = true) }
            viewModelScope.launch {
                val deleted = write { groups.deleteGroup(id) }
                form.update { it.copy(saving = false, writeFailed = !deleted, deleted = deleted, finished = deleted) }
            }
        }

        /** A refused write becomes a footer rather than an exception escaping the scope. */
        private suspend fun write(action: suspend () -> Unit): Boolean =
            try {
                action()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
    }
