package io.github.anderescaray.cents.ui.feature.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What "delete" is actually able to do to this category, which is what the alert says. */
enum class DeletionKind {
    /** Nothing references it: it can go for good. */
    OUTRIGHT,

    /** It classifies transactions, so it can only leave the pickers (rule 8). */
    ARCHIVE,
}

data class CategoryEditUiState(
    val isNew: Boolean = true,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val name: String = "",
    val colorArgb: Int = CategoryPalette.first(),
    val archived: Boolean = false,
    val transactionCount: Int = 0,
    val nameTaken: Boolean = false,
    val confirmingDeletion: Boolean = false,
    val finished: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !nameTaken

    val deletionKind: DeletionKind
        get() = if (transactionCount == 0) DeletionKind.OUTRIGHT else DeletionKind.ARCHIVE
}

/**
 * Creating and editing one category.
 *
 * The delete button is deliberately not a single operation: a category nothing uses is
 * removed, while one that already classifies transactions can only be archived, because
 * deleting it would rewrite history that other screens still add up (rule 8). The state
 * carries which of the two applies, so the confirmation can say it before it happens.
 *
 * The state here is the form itself rather than something derived from a flow with
 * `WhileSubscribed`: what "delete" does depends on the transaction count, and reading a
 * stale count would be the difference between archiving and destroying history.
 */
@HiltViewModel
class CategoryEditViewModel
    @Inject
    constructor(
        private val categoryRepository: CategoryRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<Destination.CategoryEdit>()
        private val categoryId: Long? = route.categoryId.takeIf { it != Destination.CategoryEdit.NEW }

        private val form =
            MutableStateFlow(
                CategoryEditUiState(isNew = categoryId == null, kind = route.kind),
            )

        val uiState: StateFlow<CategoryEditUiState> = form.asStateFlow()

        /** Names already in use by the other categories of this kind, lowercased. */
        private var takenNames: Set<String> = emptySet()

        /** Set as soon as the owner types, so a slow load cannot overwrite their input. */
        private var edited = false

        init {
            viewModelScope.launch { load() }
            viewModelScope.launch {
                categoryRepository.observeAllCategories().collect { categories ->
                    takenNames =
                        categories
                            .filter { it.kind == form.value.kind && it.id != categoryId }
                            .map { it.name.lowercase() }
                            .toSet()
                    form.update { it.copy(nameTaken = isNameTaken(it.name)) }
                }
            }
        }

        private suspend fun load() {
            val id = categoryId
            if (id == null) {
                val suggested = nextPaletteColor(categoriesOfKind(route.kind).map { it.colorArgb })
                form.update { if (edited) it else it.copy(colorArgb = suggested) }
                return
            }

            val category = categoryRepository.findById(id) ?: return
            val count = categoryRepository.countTransactions(id)
            form.update { state ->
                state.copy(
                    isNew = false,
                    kind = category.kind,
                    // Whatever the owner has already typed wins over what was on disk.
                    name = if (edited) state.name else category.name,
                    colorArgb = if (edited) state.colorArgb else category.colorArgb,
                    archived = category.archived,
                    transactionCount = count,
                )
            }
        }

        fun setName(name: String) {
            edited = true
            form.update { it.copy(name = name, nameTaken = isNameTaken(name)) }
        }

        fun setColor(colorArgb: Int) {
            edited = true
            form.update { it.copy(colorArgb = colorArgb) }
        }

        fun save() {
            val state = form.value
            if (!state.canSave) return

            viewModelScope.launch {
                val id = categoryId
                if (id == null) {
                    categoryRepository.createCategory(
                        Category(
                            id = 0,
                            name = state.name.trim(),
                            kind = state.kind,
                            colorArgb = state.colorArgb,
                            parentId = null,
                            archived = false,
                            sortOrder = nextSortOrder(state.kind),
                        ),
                    )
                } else {
                    val existing = categoryRepository.findById(id) ?: return@launch
                    categoryRepository.updateCategory(
                        existing.copy(name = state.name.trim(), colorArgb = state.colorArgb),
                    )
                }
                form.update { it.copy(finished = true) }
            }
        }

        /** Opens the confirmation, after re-reading how many transactions are at stake. */
        fun requestDeletion() {
            val id = categoryId ?: return
            viewModelScope.launch {
                val count = categoryRepository.countTransactions(id)
                form.update { it.copy(transactionCount = count, confirmingDeletion = true) }
            }
        }

        fun cancelDeletion() = form.update { it.copy(confirmingDeletion = false) }

        /** Deletes an unused category, archives one that has history behind it. */
        fun confirmDeletion() {
            val id = categoryId ?: return
            viewModelScope.launch {
                when (form.value.deletionKind) {
                    DeletionKind.OUTRIGHT -> categoryRepository.deleteCategory(id)
                    DeletionKind.ARCHIVE -> categoryRepository.setArchived(id, archived = true)
                }
                form.update { it.copy(confirmingDeletion = false, finished = true) }
            }
        }

        fun restore() {
            val id = categoryId ?: return
            viewModelScope.launch {
                categoryRepository.setArchived(id, archived = false)
                form.update { it.copy(finished = true) }
            }
        }

        private fun isNameTaken(name: String): Boolean = name.trim().lowercase() in takenNames

        private suspend fun categoriesOfKind(kind: CategoryKind): List<Category> =
            categoryRepository.observeAllCategories().first().filter { it.kind == kind }

        /** New categories land at the end of the list they belong to. */
        private suspend fun nextSortOrder(kind: CategoryKind): Int =
            categoriesOfKind(kind).maxOfOrNull { it.sortOrder + 1 } ?: 0
    }
