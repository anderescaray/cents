package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.capture.InstalledApp
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSearchField
import io.github.anderescaray.cents.ui.components.CentsSheetAction
import io.github.anderescaray.cents.ui.theme.CentsDimens

/** The phone's apps, to choose one more for capture to read (`DECISIONS.md` D29). */
@Composable
fun CaptureAppsScreen(
    onDone: () -> Unit,
    viewModel: CaptureAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Asked again on coming back: an app with a notification showing now is offered too.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    CaptureAppsContent(
        state = state,
        onBack = onDone,
        onQueryChange = viewModel::setQuery,
        onChoose = viewModel::choose,
        onAssign = viewModel::assign,
        onCancelChoosing = viewModel::cancelChoosing,
    )
}

/** The screen without its view model, so a test can draw it from a state. */
@Composable
internal fun CaptureAppsContent(
    state: CaptureAppsUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onChoose: (InstalledApp) -> Unit,
    onAssign: (Long) -> Unit,
    onCancelChoosing: () -> Unit,
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.capture_apps_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.capture_title), onClick = onBack) },
    ) {
        item(key = "search") {
            CentsSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.capture_apps_search),
                modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
            )
        }
        if (state.isLoading) return@CentsLargeTitleScaffold
        item(key = "apps") {
            CentsListSection(footer = stringResource(R.string.capture_apps_footer_picker)) {
                if (state.apps.isEmpty()) {
                    CentsListRow(title = stringResource(R.string.capture_apps_none), showSeparator = false)
                }
                state.apps.forEachIndexed { index, app ->
                    CentsListRow(
                        title = app.label,
                        subtitle = app.packageName,
                        subtitleMaxLines = 1,
                        showSeparator = index != state.apps.lastIndex,
                        onClick = { onChoose(app) },
                        modifier = Modifier.testTag(PICKER_ROW_TAG),
                    )
                }
            }
        }
    }

    state.choosing?.let { app ->
        CentsActionSheet(
            title = app.label,
            message = stringResource(R.string.capture_choose_account_message),
            actions = state.accounts.map { CentsSheetAction(label = it.name, onClick = { onAssign(it.id) }) },
            cancelLabel = stringResource(R.string.action_cancel),
            onDismiss = onCancelChoosing,
        )
    }
}

internal const val PICKER_ROW_TAG = "captureAppChoice"
