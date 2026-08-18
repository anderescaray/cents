package io.github.anderescaray.cents.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The app theme.
 *
 * Material 3 is still configured underneath, because Compose's plumbing — ripple
 * defaults, text selection colours, insets — reads from it. What the screens actually use
 * is [CentsTheme.colors] and [CentsTheme.typography]; nothing is styled from the Material
 * scheme directly. There is no dynamic colour: see docs/decisions/0001-ios-flavored-ui.md.
 */
@Composable
fun CentsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalCentsColors provides colors,
        LocalCentsTypography provides DefaultTypography,
    ) {
        MaterialTheme(
            colorScheme =
                if (darkTheme) {
                    darkColorScheme(
                        primary = colors.accent,
                        background = colors.groupedBackground,
                        surface = colors.groupedCard,
                        error = colors.destructive,
                    )
                } else {
                    lightColorScheme(
                        primary = colors.accent,
                        background = colors.groupedBackground,
                        surface = colors.groupedCard,
                        error = colors.destructive,
                    )
                },
            content = content,
        )
    }
}

/** Entry point for the design tokens: `CentsTheme.colors.accent`. */
object CentsTheme {
    val colors: CentsColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCentsColors.current

    val typography: CentsTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalCentsTypography.current
}
