package com.manhunt.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.data.model.AccentMode
import com.manhunt.app.data.model.AppearanceMode
import com.manhunt.app.data.model.AppearanceSettings
import com.manhunt.app.util.BatteryOptimizationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    settings: AppearanceSettings,
    highRefreshRateAvailable: Boolean,
    isBatteryOptimizationExempt: Boolean,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onAccentModeChange: (AccentMode) -> Unit,
    onHighRefreshRateChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", fontWeight = FontWeight.Bold)
                        Text("Shape your field guide", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Appearance", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = colors.primary)
                        Column {
                            Text("Color mode", fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            Text("Choose the field’s canvas", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppearanceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.mode == mode,
                                onClick = { onAppearanceModeChange(mode) },
                                label = { Text(mode.label()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.primary.copy(alpha = 0.14f),
                                    selectedLabelColor = colors.primary
                                )
                            )
                        }
                    }
                    Text("OLED keeps the main canvas true black. Follow device matches your system appearance.", fontSize = 12.sp, color = colors.onSurfaceVariant, lineHeight = 17.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Accent", fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    AccentMode.entries.forEach { accent ->
                        FilterChip(
                            selected = settings.accent == accent,
                            onClick = { onAccentModeChange(accent) },
                            label = { Text(accent.label()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary.copy(alpha = 0.14f),
                                selectedLabelColor = colors.primary
                            )
                        )
                    }
                    if (settings.accent == AccentMode.SYSTEM_COLORS) {
                        Text(
                            text = "System colors use your device palette on Android 12 and newer; older versions use MANHNT’s neutral colors.",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.primary)
                            Column {
                                Text("Enable High Refresh Rate", fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                Text("Request your display’s highest rate", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = settings.highRefreshRateEnabled,
                            onCheckedChange = onHighRefreshRateChange,
                            enabled = highRefreshRateAvailable || settings.highRefreshRateEnabled
                        )
                    }
                    val refreshMessage = when {
                        !highRefreshRateAvailable -> "A higher refresh mode is not available on this display."
                        settings.highRefreshRateEnabled -> "MANHNT requests the highest supported rate while open. Android, battery saver, thermal limits, or your device may still choose a lower rate. Higher rates may use more battery and generate more heat."
                        else -> "Higher rates may use more battery and generate more heat. This preference is off until you enable it."
                    }
                    Text(refreshMessage, fontSize = 12.sp, color = colors.onSurfaceVariant, lineHeight = 17.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = colors.secondary)
                        Column {
                            Text("Background GPS protection", fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            Text(
                                if (isBatteryOptimizationExempt) "Battery optimization is disabled for MANHNT." else "Allow MANHNT to keep location updates alive during a chase.",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = {
                            try {
                                context.startActivity(BatteryOptimizationHelper.createOptimizationExemptionIntent(context))
                            } catch (_: Exception) {
                                BatteryOptimizationHelper.openOemBackgroundSettings(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant, contentColor = colors.onSurface)
                    ) {
                        Text("Open battery settings")
                    }
                }
            }
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("About", fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Text("MANHNT source code", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Button(
                        onClick = {
                            try {
                                uriHandler.openUri("https://github.com/dedsec-1337/MANHNT")
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not open the GitHub repository.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.onSurface
                        )
                    ) {
                        Text("Source code")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun AppearanceMode.label(): String = when (this) {
    AppearanceMode.LIGHT -> "Light"
    AppearanceMode.OLED -> "OLED"
    AppearanceMode.FOLLOW_DEVICE -> "Follow device"
}

private fun AccentMode.label(): String = when (this) {
    AccentMode.DEFAULT -> "Default"
    AccentMode.BLUE -> "Blue"
    AccentMode.SYSTEM_COLORS -> "System colors"
}
