package com.manhunt.app.data.model

enum class AppearanceMode {
    LIGHT,
    OLED,
    FOLLOW_DEVICE
}

enum class AccentMode {
    DEFAULT,
    BLUE,
    SYSTEM_COLORS
}

data class AppearanceSettings(
    val mode: AppearanceMode = AppearanceMode.FOLLOW_DEVICE,
    val accent: AccentMode = AccentMode.DEFAULT,
    val highRefreshRateEnabled: Boolean = false
)
