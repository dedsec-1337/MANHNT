package com.manhunt.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Field Play warm light palette.
val AppleBackgroundLight = Color(0xFFF3F1E9)
val AppleSurfaceLight = Color(0xFFFAF9F4)
val AppleCardBackgroundLight = Color(0xFFFAF9F4)
val AppleTextPrimaryLight = Color(0xFF242522)
val AppleTextSecondaryLight = Color(0xFF73746D)
val AppleDividerLight = Color(0xFFD7D4CB)

// Dark Palette (Apple-inspired OLED Dark)
val AppleBackgroundDark = Color(0xFF000000)
val AppleSurfaceDark = Color(0xFF121311)
val AppleCardBackgroundDark = Color(0xFF121311)
val AppleTextPrimaryDark = Color(0xFFF3F1E9)
val AppleTextSecondaryDark = Color(0xFFA6A59F)
val AppleDividerDark = Color(0xFF30312E)

// Theme-aware aliases (follow light/dark ColorScheme from ManhuntTheme)
val AppleTextPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val AppleTextSecondary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val AppleCardBackground: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val AppleDivider: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
val AppleFill: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val AppleInputBackground: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

// Shared Accents
val AppleAccentBlue: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val AppleAccentBlueDark = Color(0xFF9BB8D0)
val AppleSeekerOrange = Color(0xFFBC604A)
val AppleHiderPurple = Color(0xFF8474A3)
val AppleHiderGreen = Color(0xFF34C759)
val AppleDangerRed = Color(0xFFFF3B30)

// Geofence Zone Colors
val ZoneFillColor = Color(0x22007AFF)
val ZoneBorderColor = Color(0x88007AFF)
val ZoneWarningBorderColor = Color(0xCCFF9500)
val OutOfZoneColor = Color(0x33FF3B30)
