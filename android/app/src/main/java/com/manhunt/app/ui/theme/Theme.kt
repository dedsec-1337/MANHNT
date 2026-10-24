package com.manhunt.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AppleAccentBlue,
    secondary = AppleSeekerOrange,
    tertiary = AppleHiderGreen,
    background = AppleBackground,
    surface = AppleSurface,
    onPrimary = AppleSurface,
    onSecondary = AppleSurface,
    onBackground = AppleTextPrimary,
    onSurface = AppleTextPrimary,
    outline = AppleDivider
)

@Composable
fun ManhuntTheme(
    darkTheme: Boolean = false, // Keep consistent Apple clean light theme as specified
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppleTypography,
        content = content
    )
}
