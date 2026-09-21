package com.astris.teleprompter.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = AmberOnDark,
    primaryContainer = AmberContainerDark,
    onPrimaryContainer = Amber,
    secondary = DimAmber,
    onSecondary = AmberOnDark,
    background = GraphiteBackground,
    onBackground = IvoryText,
    surface = GraphiteSurface,
    onSurface = IvoryText,
    surfaceVariant = GraphiteSurfaceVariant,
    onSurfaceVariant = MutedText,
    outline = GraphiteOutline,
    error = ErrorRed,
    onError = IvoryText
)

private val LightColorScheme = lightColorScheme(
    primary = DeepAmber,
    onPrimary = PaperSurface,
    primaryContainer = DeepAmberContainer,
    onPrimaryContainer = DeepAmber,
    secondary = DeepAmber,
    onSecondary = PaperSurface,
    background = PaperBackground,
    onBackground = InkText,
    surface = PaperSurface,
    onSurface = InkText,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = MutedInkText,
    outline = PaperOutline,
    error = ErrorRed,
    onError = PaperSurface
)

@Composable
fun TeleprompterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // The app's brand identity is deliberate (Cinematic Black & Amber); wallpaper-derived
    // Material You colors would silently override it, so dynamic color stays off by default.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
