package io.github.anderescaray.cents.ui.feature.categories

import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.emoji2.emojipicker.EmojiPickerView
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * Every emoji the phone has, by group, with the recent ones first and the skin tones a long press
 * away: Android's own picker (`androidx.emoji2:emoji2-emojipicker`), the one keyboards use, rather
 * than a list of our own that would always be missing the one wanted. Choosing one closes it.
 */
@Composable
internal fun EmojiPickerSheet(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(SHEET_HEIGHT)
                        .clip(RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp))
                        .background(CentsTheme.colors.groupedCard)
                        .navigationBarsPadding(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CentsDimens.screenMargin, vertical = CentsDimens.spacingM),
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = CentsTheme.typography.callout,
                        color = CentsTheme.colors.accentText,
                        modifier = Modifier.align(Alignment.CenterStart).tappable(onClick = onDismiss),
                    )
                    Text(
                        text = stringResource(R.string.category_emoji_all),
                        style = CentsTheme.typography.headline,
                        color = CentsTheme.colors.label,
                        modifier = Modifier.align(Alignment.Center).semantics { heading() },
                    )
                }
                AndroidView(
                    // The picker is a View and takes its colours from the theme it is built with,
                    // so it is given the dark one when the app is dark.
                    factory = { context ->
                        val theme = if (dark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light
                        EmojiPickerView(ContextThemeWrapper(context, theme)).apply {
                            setOnEmojiPickedListener { onPick(it.emoji) }
                        }
                    },
                    modifier = Modifier.fillMaxSize().testTag(EMOJI_PICKER_TAG),
                )
            }
        }
    }
}

private const val SHEET_HEIGHT = 0.7f
private const val SHEET_RADIUS = 30

internal const val EMOJI_PICKER_TAG = "emojiPicker"
