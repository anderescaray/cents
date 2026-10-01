package io.github.anderescaray.cents.ui.feature.categories

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.components.CentsSwipeableRow
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.feature.accounts.DeletionFailedAlert
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import kotlin.math.roundToInt

/**
 * The owner's own categories: one list per kind, dragged into the order they prefer.
 *
 * The app never puts anything here — an empty list on a fresh install is the correct
 * state, not a missing feature (PRD §4.1).
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onNewCategory: (CategoryKind) -> Unit,
    onEditCategory: (Long, CategoryKind) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.state.collectAsStateWithLifecycle()

    CentsLargeTitleScaffold(
        title = stringResource(R.string.categories_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = {
            CentsBackButton(label = stringResource(R.string.tab_settings), onClick = onBack)
        },
        trailing = {
            val addLabel = stringResource(R.string.categories_add)
            RoundIconButton(
                iconRes = R.drawable.ic_plus,
                contentDescription = addLabel,
                onClick = { onNewCategory(state.kind) },
                modifier = Modifier.testTag(ADD_CATEGORY_TAG),
            )
        },
    ) {
        kindSelector(state.kind, viewModel::setKind)

        if (state.showEmptyState) {
            emptyState(state.kind)
        } else if (state.active.isNotEmpty()) {
            activeCategories(
                categories = state.active,
                onOpen = onEditCategory,
                onMove = viewModel::moveCategory,
                onDelete = viewModel.deletion::request,
            )
        }

        if (state.archived.isNotEmpty()) {
            archivedCategories(state.archived, onEditCategory)
        }
    }

    if (deletion.asking) {
        CategoryDeletionAlert(
            state = deletion,
            onConfirm = viewModel.deletion::confirm,
            onDismiss = viewModel.deletion::cancel,
        )
    }
    if (deletion.failed) {
        DeletionFailedAlert(onDismiss = viewModel.deletion::cancel)
    }
}

private fun LazyListScope.kindSelector(
    kind: CategoryKind,
    onSelect: (CategoryKind) -> Unit,
) {
    item(key = "kind") {
        val expenseLabel = stringResource(R.string.kind_expense)
        val incomeLabel = stringResource(R.string.kind_income)
        CentsSegmentedControl(
            options = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME),
            selected = kind,
            onSelect = onSelect,
            label = { option -> if (option == CategoryKind.EXPENSE) expenseLabel else incomeLabel },
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
        )
    }
}

private fun LazyListScope.emptyState(kind: CategoryKind) {
    item(key = "empty") {
        CentsListSection(footer = stringResource(R.string.categories_empty_footer)) {
            CentsListRow(
                title =
                    stringResource(
                        if (kind == CategoryKind.EXPENSE) {
                            R.string.categories_empty_expense
                        } else {
                            R.string.categories_empty_income
                        },
                    ),
                showSeparator = false,
            )
        }
    }
}

private fun LazyListScope.archivedCategories(
    categories: List<Category>,
    onOpen: (Long, CategoryKind) -> Unit,
) {
    item(key = "archived") {
        CentsListSection(
            header = stringResource(R.string.categories_archived_header),
            footer = stringResource(R.string.categories_archived_footer),
        ) {
            categories.forEachIndexed { index, category ->
                CentsListRow(
                    title = category.name,
                    leading = { ColorDot(Color(category.colorArgb)) },
                    showChevron = true,
                    showSeparator = index != categories.lastIndex,
                    onClick = { onOpen(category.id, category.kind) },
                )
            }
        }
    }
}

/**
 * The active list, with a grip on each row that reorders it and a sideways swipe on the
 * row itself that deletes it.
 *
 * Rows are all the same height, so the position a drag lands on is the start index plus
 * the offset divided by that height — no per-row geometry to keep in sync.
 *
 * Only the active list swipes. The archived section below it is where a category goes to
 * be restored, and "delete" there is either impossible — one with history behind it can
 * only stay archived (rule 8) — or the same button the editor already offers.
 */
private fun LazyListScope.activeCategories(
    categories: List<Category>,
    onOpen: (Long, CategoryKind) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Category) -> Unit,
) {
    item(key = "active") {
        val reorder = remember { ReorderState() }

        CentsListSection(
            header = stringResource(R.string.categories_section_header),
            footer = stringResource(R.string.categories_reorder_footer),
        ) {
            categories.forEachIndexed { index, category ->
                // Keyed by identity, not by position: a row's swipe offset is remembered
                // against its key, and without one, dragging a row into this slot would
                // bring the open offset of whatever used to be here.
                key(category.id) {
                    CategoryRow(
                        category = category,
                        index = index,
                        count = categories.size,
                        reorder = reorder,
                        onOpen = onOpen,
                        onMove = onMove,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    index: Int,
    count: Int,
    reorder: ReorderState,
    onOpen: (Long, CategoryKind) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Category) -> Unit,
) {
    val isDragged = index == reorder.draggedIndex
    // Derived, not computed inline: a drag emits an offset per frame, but the displacement
    // of the rows around it only changes when the finger crosses into the next position.
    val shift by remember(index, count) {
        derivedStateOf {
            displacement(
                index = index,
                from = reorder.draggedIndex,
                to = reorder.targetIndex(count),
                rowHeight = reorder.rowHeight,
            )
        }
    }
    val animatedShift by animateFloatAsState(shift, label = "categoryShift")

    Box(
        modifier =
            Modifier
                .zIndex(if (isDragged) 1f else 0f)
                .onSizeChanged { reorder.rowHeight = it.height }
                .graphicsLayer { translationY = if (isDragged) reorder.offset else animatedShift },
    ) {
        CentsSwipeableRow(
            onDelete = { onDelete(category) },
            deleteLabel = stringResource(R.string.action_delete),
        ) { rowModifier ->
            CentsListRow(
                modifier = rowModifier,
                title = category.name,
                leading = { ColorDot(Color(category.colorArgb)) },
                trailing = {
                    ReorderHandle(
                        modifier =
                            Modifier.pointerInput(count) {
                                detectDragGestures(
                                    onDragStart = { reorder.start(index) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        reorder.offset += amount.y
                                    },
                                    onDragEnd = {
                                        val from = reorder.draggedIndex
                                        val to = reorder.targetIndex(count)
                                        if (from != null && to != null) onMove(from, to)
                                        reorder.stop()
                                    },
                                    onDragCancel = reorder::stop,
                                )
                            },
                    )
                },
                showSeparator = index != count - 1,
                onClick = { onOpen(category.id, category.kind) },
            )
        }
    }
}

/** Which row the finger is holding and how far it has travelled. */
private class ReorderState {
    var draggedIndex by mutableStateOf<Int?>(null)
    var offset by mutableFloatStateOf(0f)
    var rowHeight by mutableIntStateOf(0)

    /** The position the dragged row would take if the finger let go now. */
    fun targetIndex(count: Int): Int? {
        val from = draggedIndex ?: return null
        val movedRows = if (rowHeight == 0 || count == 0) 0 else (offset / rowHeight).roundToInt()
        return (from + movedRows).coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun start(index: Int) {
        draggedIndex = index
        offset = 0f
    }

    fun stop() {
        draggedIndex = null
        offset = 0f
    }
}

/**
 * How far a row that is not being dragged has to move so the gap follows the finger:
 * everything between the row's old and new position shifts by exactly one row.
 */
private fun displacement(
    index: Int,
    from: Int?,
    to: Int?,
    rowHeight: Int,
): Float {
    if (from == null || to == null || from == to) return 0f
    return when {
        from < to && index in (from + 1)..to -> -rowHeight.toFloat()
        from > to && index in to..<from -> rowHeight.toFloat()
        else -> 0f
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(DOT_SIZE.dp)
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
private fun ReorderHandle(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_reorder_handle),
        contentDescription = stringResource(R.string.categories_reorder),
        tint = CentsTheme.colors.tertiaryLabel,
        modifier = modifier.size(HANDLE_SIZE.dp),
    )
}

private const val DOT_SIZE = 12
private const val HANDLE_SIZE = 24
internal const val ADD_CATEGORY_TAG = "addCategory"
