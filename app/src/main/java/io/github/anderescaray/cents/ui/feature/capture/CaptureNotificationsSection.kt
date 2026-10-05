package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSwitch

@Composable
internal fun CaptureNotificationsSection(
    enabled: Boolean,
    allowed: Boolean?,
    onEnabledChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val title = stringResource(R.string.capture_notify)
    val blocked = enabled && allowed == false
    CentsListSection(footer = stringResource(R.string.capture_notify_footer)) {
        CentsListRow(
            title = title,
            onClick = { onEnabledChanged(!enabled) },
            modifier = Modifier.heightIn(min = 48.dp),
            showSeparator = blocked,
            trailing = {
                CentsSwitch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                    modifier =
                        Modifier
                            .testTag(CAPTURE_NOTIFY_SWITCH_TAG)
                            .semantics {
                                contentDescription = title
                                toggleableState = ToggleableState(enabled)
                            },
                )
            },
        )
        if (blocked) {
            CentsListRow(
                title = stringResource(R.string.capture_notify_blocked),
                subtitle = stringResource(R.string.capture_notify_settings),
                showChevron = true,
                showSeparator = false,
                onClick = onOpenSettings,
            )
        }
    }
}

internal const val CAPTURE_NOTIFY_SWITCH_TAG = "captureNotifyOnCapture"
