package io.github.anderescaray.cents.ui.feature.categories

import android.view.ContextThemeWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsSheet

/**
 * Every emoji the phone has, by group, with the recent ones first and the skin tones a long press
 * away: Android's own picker (`androidx.emoji2:emoji2-emojipicker`), rather than a list of our own
 * that would always be missing the one wanted. It sits in the app's usual sheet, and choosing an
 * emoji closes it.
 */
@Composable
internal fun EmojiPickerSheet(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pick by rememberUpdatedState(onPick)
    val dark = isSystemInDarkTheme()
    val height = LocalConfiguration.current.screenHeightDp * SHEET_SHARE
    CentsSheet(title = stringResource(R.string.category_emoji_all), onDismiss = onDismiss) {
        AndroidView(
            // The picker is a View and takes its colours from the theme it is built with, so it
            // is given the dark one when the app is dark.
            factory = { context ->
                val theme = if (dark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light
                EmojiPickerView(ContextThemeWrapper(context, theme)).apply {
                    setOnEmojiPickedListener { pick(it.emoji) }
                }
            },
            modifier = Modifier.fillMaxWidth().height(height.dp).testTag(EMOJI_PICKER_TAG),
        )
    }
}

/** Enough of the screen to browse a group without scrolling at once; the sheet caps the rest. */
private const val SHEET_SHARE = 0.65f

internal const val EMOJI_PICKER_TAG = "emojiPicker"
