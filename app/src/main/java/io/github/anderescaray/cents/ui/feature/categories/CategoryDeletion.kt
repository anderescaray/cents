package io.github.anderescaray.cents.ui.feature.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.ui.components.CentsAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What "delete" is actually able to do to this category, which is what the alert says. */
enum class DeletionKind {
    /** Nothing references it: it can go for good. */
    OUTRIGHT,

    /** It classifies transactions, so it can only leave the pickers (rule 8). */
    ARCHIVE,
}

/** The category being asked about, and what deleting it would really do. */
data class CategoryDeletionState(
    val categoryId: Long? = null,
    val name: String = "",
    val transactionCount: Int = 0,
) {
    /** True while the owner is being asked. */
    val asking: Boolean get() = categoryId != null

    val kind: DeletionKind
        get() = if (transactionCount == 0) DeletionKind.OUTRIGHT else DeletionKind.ARCHIVE
}

/**
 * "Delete this category" as a question that can be asked from anywhere.
 *
 * It lives here rather than inside the editor because two screens ask it now — the editor
 * and a swipe on the list — and it is precisely the kind of thing that must not be written
 * twice: the difference between its two answers is the difference between removing a label
 * and rewriting the history it classified (rule 8).
 *
 * The count is read at the moment of asking, never carried over from a screen that loaded
 * it earlier. A category that was unused when the screen opened may not be by the time the
 * button is pressed, and the alert has to say what is about to happen, not what would have
 * happened a minute ago.
 */
class CategoryDeletion(
    private val repository: CategoryRepository,
    private val scope: CoroutineScope,
    private val onOutcome: (Result<Unit>) -> Unit = {},
) {
    private val question = MutableStateFlow(CategoryDeletionState())
    val state: StateFlow<CategoryDeletionState> = question.asStateFlow()

    /** Opens the confirmation for [category], after counting what depends on it. */
    fun request(
        id: Long,
        name: String,
    ) {
        scope.launch {
            val count = repository.countTransactions(id)
            question.value = CategoryDeletionState(categoryId = id, name = name, transactionCount = count)
        }
    }

    fun request(category: Category) = request(category.id, category.name)

    fun cancel() {
        question.value = CategoryDeletionState()
    }

    /**
     * Deletes an unused category, archives one that has history behind it.
     *
     * The delete is caught rather than trusted. `transactions.categoryId` is `RESTRICT`, so a
     * movement classified between counting and confirming turns a legitimate delete into a
     * constraint violation — and an exception escaping the scope here takes the app with it.
     */
    fun confirm() {
        val asked = question.value
        val id = asked.categoryId ?: return
        scope.launch {
            val done =
                runCatching {
                    when (asked.kind) {
                        DeletionKind.OUTRIGHT -> repository.deleteCategory(id)
                        DeletionKind.ARCHIVE -> repository.setArchived(id, archived = true)
                    }
                }
            question.value = CategoryDeletionState()
            onOutcome(done)
        }
    }
}

/**
 * Says which of the two things the button is about to do, and why, before it happens —
 * an unused category is removed, one with history behind it can only be archived.
 */
@Composable
internal fun CategoryDeletionAlert(
    state: CategoryDeletionState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val outright = state.kind == DeletionKind.OUTRIGHT
    CentsAlert(
        title =
            stringResource(
                if (outright) R.string.category_delete_title else R.string.category_archive_title,
                state.name,
            ),
        message =
            if (outright) {
                stringResource(R.string.category_delete_message)
            } else {
                pluralStringResource(
                    R.plurals.category_archive_message,
                    state.transactionCount,
                    state.transactionCount,
                )
            },
        confirmLabel =
            stringResource(if (outright) R.string.action_delete else R.string.action_archive),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        destructive = outright,
    )
}
