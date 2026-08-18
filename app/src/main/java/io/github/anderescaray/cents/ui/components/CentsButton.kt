package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The prominent action button: a filled, fully rounded capsule in the tint colour.
 * Pressing dims it rather than rippling, as iOS does.
 */
@Composable
fun CentsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = if (destructive) CentsTheme.colors.destructive else CentsTheme.colors.accent

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        !enabled -> background.copy(alpha = 0.4f)
                        pressed -> background.copy(alpha = 0.7f)
                        else -> background
                    },
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ).padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CentsTheme.typography.headline,
            color = Color.White,
        )
    }
}
