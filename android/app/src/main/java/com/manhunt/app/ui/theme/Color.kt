package com.manhunt.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Light Palette (Apple-inspired Clean Light)
val AppleBackgroundLight = Color(0xFFF8F9FA)
val AppleSurfaceLight = Color(0xFFFFFFFF)
val AppleCardBackgroundLight = Color(0xFFFFFFFF)
val AppleTextPrimaryLight = Color(0xFF1C1C1E)
val AppleTextSecondaryLight = Color(0xFF8E8E93)
val AppleDividerLight = Color(0xFFE5E5EA)

// Dark Palette (Apple-inspired OLED Dark)
val AppleBackgroundDark = Color(0xFF000000)
val AppleSurfaceDark = Color(0xFF1C1C1E)
val AppleCardBackgroundDark = Color(0xFF1C1C1E)
val AppleTextPrimaryDark = Color(0xFFF5F5F7)
val AppleTextSecondaryDark = Color(0xFF98989D)
val AppleDividerDark = Color(0xFF2C2C2E)

// Theme-aware aliases (follow light/dark ColorScheme from ManhuntTheme)
val AppleTextPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val AppleTextSecondary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val AppleCardBackground: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val AppleDivider: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

// Shared Accents
val AppleAccentBlue = Color(0xFF007AFF)
val AppleAccentBlueDark = Color(0xFF0A84FF)
val AppleSeekerOrange = Color(0xFFFF9500)
val AppleHiderGreen = Color(0xFF34C759)
val AppleDangerRed = Color(0xFFFF3B30)

// Geofence Zone Colors
val ZoneFillColor = Color(0x22007AFF)
val ZoneBorderColor = Color(0x88007AFF)
val ZoneWarningBorderColor = Color(0xCCFF9500)
val OutOfZoneColor = Color(0x33FF3B30)
