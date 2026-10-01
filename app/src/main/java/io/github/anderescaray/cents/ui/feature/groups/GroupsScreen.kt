package io.github.anderescaray.cents.ui.feature.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    onNewGroup: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GroupsContent(state = state, onBack = onBack, onNewGroup = onNewGroup, onOpenGroup = onOpenGroup)
}

/** The screen without its view model, so a test can draw it from a state. */
@Composable
internal fun GroupsContent(
    state: GroupsUiState,
    onBack: () -> Unit,
    onNewGroup: () -> Unit,
    onOpenGroup: (Long) -> Unit,
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.groups_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.tab_settings), onClick = onBack) },
        trailing = { AddGroupButton(onClick = onNewGroup) },
    ) {
        if (state.isLoading) return@CentsLargeTitleScaffold

        if (state.isEmpty) {
            emptyState()
        } else {
            groupRows(state = state, onOpenGroup = onOpenGroup)
        }
    }
}

private fun LazyListScope.emptyState() {
    item(key = "empty") {
        CentsListSection(footer = stringResource(R.string.groups_empty_message)) {
            CentsListRow(title = stringResource(R.string.groups_empty_title), showSeparator = false)
        }
    }
}

/**
 * One row per group: its name, when it happened and how many movements, and what it came to as a
 * sentence rather than a signed number — the question this screen answers is *what did it cost*.
 */
private fun LazyListScope.groupRows(
    state: GroupsUiState,
    onOpenGroup: (Long) -> Unit,
) {
    item(key = "groups") {
        CentsListSection(footer = stringResource(R.string.groups_footer)) {
            state.groups.forEachIndexed { index, summary ->
                CentsListRow(
                    title = summary.group.name,
                    subtitle = summary.subtitle(),
                    value = if (summary.isEmpty) null else summary.netSentence(),
                    valueColor = summary.netColor(),
                    showChevron = true,
                    showSeparator = index != state.groups.lastIndex,
                    onClick = { onOpenGroup(summary.group.id) },
                    modifier = Modifier.testTag(GROUP_ROW_TAG),
                )
            }
        }
    }
}

@Composable
private fun AddGroupButton(onClick: () -> Unit) {
    val label = stringResource(R.string.groups_add)
    Box(
        modifier =
            Modifier
                .semantics { contentDescription = label }
                .testTag(ADD_GROUP_TAG)
                .size(CentsDimens.minRowHeight)
                .clip(CircleShape)
                .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.action_add),
            style = CentsTheme.typography.title2,
            color = CentsTheme.colors.accentText,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

internal const val ADD_GROUP_TAG = "addGroup"
internal const val GROUP_ROW_TAG = "groupRow"
