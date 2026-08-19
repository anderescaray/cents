package io.github.anderescaray.cents.ui.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val active: List<Category> = emptyList(),
    val archived: List<Category> = emptyList(),
    val isLoading: Boolean = true,
) {
    /**
     * Nothing to list for this kind. False while loading, so opening the screen does not
     * flash "no categories yet" for the length of the push transition before the real
     * list arrives.
     */
    val showEmptyState: Boolean get() = !isLoading && active.isEmpty()
}

/**
 * The category list: which kind is on screen, and the order the owner dragged it into.
 *
 * Creating, renaming, archiving and deleting belong to the edit screen; this one only
 * lists and reorders.
 */
@HiltViewModel
class CategoriesViewModel
    @Inject
    constructor(
        private val categoryRepository: CategoryRepository,
    ) : ViewModel() {
        private val selectedKind = MutableStateFlow(CategoryKind.EXPENSE)

        val uiState: StateFlow<CategoriesUiState> =
            combine(
                selectedKind,
                categoryRepository.observeAllCategories(),
            ) { kind, categories ->
                val ofKind = categories.filter { it.kind == kind }
                CategoriesUiState(
                    kind = kind,
                    active = ofKind.filterNot { it.archived },
                    archived = ofKind.filter { it.archived },
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = CategoriesUiState(),
            )

        fun setKind(kind: CategoryKind) {
            selectedKind.value = kind
        }

        /**
         * Persists the list as it looks after dragging the item at [from] to [to]. The whole
         * order is rewritten rather than patched, so the stored positions stay 0..n-1 and no
         * gap accumulates over time.
         */
        fun moveCategory(
            from: Int,
            to: Int,
        ) {
            val current = uiState.value.active
            if (from == to || from !in current.indices || to !in current.indices) return

            val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
            viewModelScope.launch { categoryRepository.reorder(reordered.map { it.id }) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
