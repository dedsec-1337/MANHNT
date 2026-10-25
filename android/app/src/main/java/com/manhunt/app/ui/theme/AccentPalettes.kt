package com.manhunt.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.manhunt.app.data.model.AccentMode

data class AccentPalette(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color
)

object AccentPalettes {
    val DefaultLight = Color(0xFF343632)
    val DefaultDark = Color(0xFFD8D7CE)

    private val palettes = mapOf(
        AccentMode.BLUE to AccentPalette(
            lightPrimary = Color(0xFF1D4ED8),
            lightOnPrimary = Color.White,
            darkPrimary = Color(0xFF78AFFF),
            darkOnPrimary = Color(0xFF071A34)
        ),
        AccentMode.TEAL to AccentPalette(
            lightPrimary = Color(0xFF0F766E),
            lightOnPrimary = Color.White,
            darkPrimary = Color(0xFF6EE7D1),
            darkOnPrimary = Color(0xFF07352F)
        ),
        AccentMode.VIOLET to AccentPalette(
            lightPrimary = Color(0xFF6D28D9),
            lightOnPrimary = Color.White,
            darkPrimary = Color(0xFFC4A7FF),
            darkOnPrimary = Color(0xFF24143E)
        ),
        AccentMode.FOREST to AccentPalette(
            lightPrimary = Color(0xFF15803D),
            lightOnPrimary = Color.White,
            darkPrimary = Color(0xFF76DF99),
            darkOnPrimary = Color(0xFF10361D)
        ),
        AccentMode.ROSE to AccentPalette(
            lightPrimary = Color(0xFFBE123C),
            lightOnPrimary = Color.White,
            darkPrimary = Color(0xFFFF8CA4),
            darkOnPrimary = Color(0xFF3D0718)
        ),
        AccentMode.AMBER to AccentPalette(
            lightPrimary = Color(0xFFB45309),
            lightOnPrimary = Color.White,
            darkPrimary = Color(0xFFFFB454),
            darkOnPrimary = Color(0xFF321B08)
        )
    )

    fun palette(mode: AccentMode): AccentPalette? = palettes[mode]

    fun previewColor(mode: AccentMode, darkTheme: Boolean): Color {
        val palette = palette(mode)
        return when {
            palette != null && darkTheme -> palette.darkPrimary
            palette != null -> palette.lightPrimary
            darkTheme -> DefaultDark
            else -> DefaultLight
        }
    }
}
