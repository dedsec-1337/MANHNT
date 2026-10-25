package com.manhunt.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.manhunt.app.data.model.AccentMode
import com.manhunt.app.data.model.AppearanceMode
import com.manhunt.app.data.model.AppearanceSettings

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF343632),
    secondary = AppleSeekerOrange,
    tertiary = AppleHiderPurple,
    background = AppleBackgroundLight,
    surface = AppleSurfaceLight,
    surfaceVariant = Color(0xFFE8E5DC),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = AppleTextPrimaryLight,
    onSurface = AppleTextPrimaryLight,
    onSurfaceVariant = AppleTextSecondaryLight,
    outline = AppleDividerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF30312E),
    secondary = AppleSeekerOrange,
    tertiary = AppleHiderPurple,
    background = AppleBackgroundDark,
    surface = AppleSurfaceDark,
    surfaceVariant = Color(0xFF30312E),
    onPrimary = Color(0xFFF3F1E9),
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = AppleTextPrimaryDark,
    onSurface = AppleTextPrimaryDark,
    onSurfaceVariant = AppleTextSecondaryDark,
    outline = AppleDividerDark
)

@Composable
fun ManhuntTheme(
    settings: AppearanceSettings,
    content: @Composable () -> Unit
) {
    val darkTheme = when (settings.mode) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.OLED -> true
        AppearanceMode.FOLLOW_DEVICE -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val baseColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val systemColorScheme = if (settings.accent == AccentMode.SYSTEM_COLORS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dynamic.copy(
            background = if (darkTheme) AppleBackgroundDark else AppleBackgroundLight,
            surface = if (darkTheme) AppleSurfaceDark else AppleSurfaceLight,
            surfaceVariant = if (darkTheme) AppleDividerDark else Color(0xFFE8E5DC),
            secondary = AppleSeekerOrange,
            onSecondary = Color.White,
            tertiary = AppleHiderPurple,
            onTertiary = Color.White,
            outline = if (darkTheme) AppleDividerDark else AppleDividerLight
        )
    } else {
        baseColorScheme
    }
    val colorScheme: ColorScheme = when (settings.accent) {
        AccentMode.DEFAULT, AccentMode.SYSTEM_COLORS -> systemColorScheme
        AccentMode.BLUE -> systemColorScheme.copy(
            primary = if (darkTheme) Color(0xFF4A6A83) else Color(0xFF48677D),
            onPrimary = Color.White
        )
    }
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
