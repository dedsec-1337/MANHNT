package com.manhunt.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AppleAccentBlue,
    secondary = AppleSeekerOrange,
    tertiary = AppleHiderGreen,
    background = AppleBackgroundLight,
    surface = AppleSurfaceLight,
    surfaceVariant = Color(0xFFF2F2F7),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppleTextPrimaryLight,
    onSurface = AppleTextPrimaryLight,
    onSurfaceVariant = AppleTextSecondaryLight,
    outline = AppleDividerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = AppleAccentBlueDark,
    secondary = AppleSeekerOrange,
    tertiary = AppleHiderGreen,
    background = AppleBackgroundDark,
    surface = AppleSurfaceDark,
    surfaceVariant = Color(0xFF2C2C2E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppleTextPrimaryDark,
    onSurface = AppleTextPrimaryDark,
    onSurfaceVariant = AppleTextSecondaryDark,
    outline = AppleDividerDark
)

@Composable
fun ManhuntTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppleTypography,
        content = content
    )
}
