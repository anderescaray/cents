package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.rememberReduceMotion
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/** One thing a [CentsPickerSheet] offers. */
@Immutable
data class PickerChoice(
    val id: Long,
    val label: String,
    /** A category's colour, drawn as its letter badge; or the tint of [iconRes] when that is given. */
    val colorArgb: Int? = null,
    /** A glyph instead of the letter, for a choice that is a thing rather than a category. */
    val iconRes: Int? = null,
)

/**
 * A row under the choices that does something else: *New category*, *No category*. Without an
 * [iconRes] it carries the grey question mark that stands for *uncategorized* everywhere else.
 */
@Immutable
data class PickerAction(
    val label: String,
    val iconRes: Int?,
    val onClick: () -> Unit,
)

/**
 * A sheet that rises over a form to choose one thing from a list that may be long: every category,
 * every account. One under the other, each name in full, the chosen one ticked, and a search field
 * once the list is long enough to need one — never a strip of chips scrolled sideways.
 *
 * Choosing calls [onChoose] and nothing else: the caller applies it and closes the sheet, so one
 * tap is the whole gesture. Tapping outside, dragging the grabber down, *Cancel* and back all
 * close it without a choice, leaving the one there was.
 */
@Composable
fun CentsPickerSheet(
    title: String,
    choices: List<PickerChoice>,
    selectedId: Long?,
    onChoose: (Long) -> Unit,
    onDismiss: () -> Unit,
    footer: String? = null,
    actions: List<PickerAction> = emptyList(),
) {
    Dialog(
        onDismissRequest = onDismiss,
        // The whole window, edge to edge, so the sheet can reach the bottom of the screen and the
        // dimmed area above it can be tapped; the insets are paid inside the sheet.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dismiss by rememberUpdatedState(onDismiss)
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { dismiss() } }
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = CentsDimens.spacingXl),
            contentAlignment = Alignment.BottomCenter,
        ) {
            SheetSurface(maxHeight = maxHeight, onDismiss = onDismiss) {
                SheetBar(title = title, onCancel = onDismiss)
                SheetChoices(
                    choices = choices,
                    selectedId = selectedId,
                    onChoose = onChoose,
                    footer = footer,
                    actions = actions,
                )
            }
        }
    }
}

/**
 * The white surface with its rounded top and grabber: it rises when it opens, and follows a drag
 * on its top edge down until, let go far enough, it closes.
 */
@Composable
private fun SheetSurface(
    maxHeight: Dp,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val rise = remember { Animatable(if (reduceMotion) 0f else 1f) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val closeAfter = with(LocalDensity.current) { DRAG_TO_CLOSE.dp.toPx() }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = RISE_DAMPING, stiffness = RISE_STIFFNESS)) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .graphicsLayer { translationY = rise.value * size.height + drag.value }
                .clip(RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp))
                .background(CentsTheme.colors.groupedCard)
                // A tap on the sheet is not a tap outside it.
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .imePadding(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state =
                            rememberDraggableState { delta ->
                                scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                            },
                        onDragStopped = {
                            if (drag.value > closeAfter) onDismiss() else drag.animateTo(0f)
                        },
                    ).padding(vertical = CentsDimens.spacingS),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = GRABBER_WIDTH.dp, height = GRABBER_HEIGHT.dp)
                    .clip(CircleShape)
                    .background(CentsTheme.colors.tertiaryLabel.copy(alpha = GRABBER_ALPHA)),
            )
        }
        content()
    }
}

/** *Cancel* on the left and what is being chosen in the middle, as the form under it does. */
@Composable
private fun SheetBar(
    title: String,
    onCancel: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CentsDimens.screenMargin),
    ) {
        Text(
            text = stringResource(R.string.action_cancel),
            style = CentsTheme.typography.callout,
            color = CentsTheme.colors.accentText,
            modifier = Modifier.align(Alignment.CenterStart).tappable(onClick = onCancel),
        )
        Text(
            text = title,
            style = CentsTheme.typography.headline,
            color = CentsTheme.colors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
    }
}

/** The search field when the list is long, the choices, the other actions and a closing word. */
@Composable
private fun SheetChoices(
    choices: List<PickerChoice>,
    selectedId: Long?,
    onChoose: (Long) -> Unit,
    footer: String?,
    actions: List<PickerAction>,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchable = choices.size > SEARCH_FROM
    val shown = remember(choices, query) { choices.matching(query) }

    if (searchable) {
        CentsSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.picker_search),
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin, vertical = CentsDimens.spacingS),
        )
    }
    LazyColumn(modifier = Modifier.testTag(PICKER_LIST_TAG)) {
        itemsIndexed(shown, key = { _, choice -> choice.id }) { index, choice ->
            CentsListRow(
                title = choice.label,
                leading = { ChoiceBadge(choice) },
                selected = choice.id == selectedId,
                onClick = { onChoose(choice.id) },
                showSeparator = index != shown.lastIndex || actions.isNotEmpty(),
            )
        }
        if (shown.isEmpty() && query.isNotBlank()) {
            item(key = "noMatches") {
                Text(
                    text = stringResource(R.string.picker_no_matches, query.trim()),
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(CentsDimens.screenMargin),
                )
            }
        }
        itemsIndexed(actions, key = { _, action -> "action:" + action.label }) { index, action ->
            CentsListRow(
                title = action.label,
                leading = {
                    if (action.iconRes != null) {
                        IconBadge(iconRes = action.iconRes, color = CentsTheme.colors.accent)
                    } else {
                        CategoryBadge(name = null, colorArgb = null)
                    }
                },
                onClick = action.onClick,
                showSeparator = index != actions.lastIndex,
            )
        }
        if (footer != null) {
            item(key = "footer") {
                Text(
                    text = footer,
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(CentsDimens.screenMargin),
                )
            }
        }
    }
}

@Composable
private fun ChoiceBadge(choice: PickerChoice) {
    if (choice.iconRes != null) {
        IconBadge(
            iconRes = choice.iconRes,
            color = choice.colorArgb?.let(::Color) ?: CentsTheme.colors.accent,
        )
    } else {
        CategoryBadge(name = choice.label, colorArgb = choice.colorArgb)
    }
}

/**
 * The choices whose name holds [query], compared the way the movement search compares: case and
 * accents set aside, so *cafe* finds *Café*.
 */
internal fun List<PickerChoice>.matching(query: String): List<PickerChoice> {
    val wanted = query.folded()
    return if (wanted.isEmpty()) this else filter { it.label.folded().contains(wanted) }
}

private fun String.folded(): String =
    Normalizer
        .normalize(trim(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** More choices than fit on a phone without scrolling is when a search field earns its place. */
private const val SEARCH_FROM = 7
private const val SHEET_RADIUS = 30
private const val GRABBER_WIDTH = 40
private const val GRABBER_HEIGHT = 5
private const val GRABBER_ALPHA = 0.6f
private const val DRAG_TO_CLOSE = 96
private const val RISE_DAMPING = 0.86f
private const val RISE_STIFFNESS = 174f

internal const val PICKER_LIST_TAG = "pickerList"
