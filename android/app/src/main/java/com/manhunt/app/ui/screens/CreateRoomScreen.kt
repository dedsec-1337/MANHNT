package com.manhunt.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.data.model.SafeZone
import com.manhunt.app.ui.components.AppleCard
import com.manhunt.app.ui.components.AppleChip
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.theme.AppleAccentBlue
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(
    isCreatingRoom: Boolean = false,
    onBackClick: () -> Unit,
    onCreateRoomSubmit: (
        roomName: String,
        duration: Int,
        zonePreset: String,
        headStartMinutes: Int,
        isShrinkingZone: Boolean,
        autoPingIntervalMinutes: Int,
        safeZones: List<SafeZone>
    ) -> Unit
) {
    var roomName by remember { mutableStateOf("Sunset Park Chase") }
    var selectedDuration by remember { mutableIntStateOf(30) } // 15, 30, 45, 60
    var selectedZonePreset by remember { mutableStateOf("1000m") } // "1000m", "2000m", "UNLIMITED"
    var selectedHeadStartMinutes by remember { mutableIntStateOf(2) } // 0, 1, 2, 3, 4, 5, 10, 15
    var isShrinkingFinaleEnabled by remember { mutableStateOf(true) }
    var selectedAutoPingInterval by remember { mutableIntStateOf(5) } // 0 (Off), 2, 5
    var enableSafeZones by remember { mutableStateOf(true) }

    val zonePresets = listOf(
        Pair("1000m", "1 Kilometer"),
        Pair("2000m", "2 Kilometers"),
        Pair("UNLIMITED", "Unlimited (No Border)")
    )
    val headStartPresets = listOf(0, 1, 2, 3, 4, 5, 10, 15)
    val durationPresets = listOf(15, 30, 45, 60)
    val autoPingPresets = listOf(
        Pair(0, "Off"),
        Pair(2, "Every 2m"),
        Pair(5, "Every 5m")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New Match",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = AppleTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Room Name Input
            AppleCard {
                Text(
                    text = "Room Name",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    placeholder = { Text("Enter room name...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppleAccentBlue,
                        unfocusedBorderColor = AppleDivider,
                        focusedContainerColor = AppleInputBackground,
                        unfocusedContainerColor = AppleInputBackground
                    )
                )
            }

            // 1. Play Zone Size Selector (1km, 2km, Unlimited)
            AppleCard {
                Text(
                    text = "Play Zone Size",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (selectedZonePreset == "UNLIMITED") {
                        "No perimeter boundary circle. Players can roam freely across the entire city."
                    } else {
                        "Circular boundary centered at GPS start point. Crossing triggers a 30s return timer."
                    },
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((presetKey, label) in zonePresets) {
                        AppleChip(
                            label = label,
                            isSelected = selectedZonePreset == presetKey,
                            onSelect = { selectedZonePreset = presetKey },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 2. Head Start Timer (0 to 15 mins)
            AppleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Head Start Timer",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = if (selectedHeadStartMinutes == 0) "Immediate Start" else "${selectedHeadStartMinutes} Minutes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleAccentBlue
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Seekers are frozen in place with a countdown overlay while Hiders scatter.",
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (mins in headStartPresets.take(4)) {
                        AppleChip(
                            label = if (mins == 0) "0m" else "${mins}m",
                            isSelected = selectedHeadStartMinutes == mins,
                            onSelect = { selectedHeadStartMinutes = mins },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (mins in headStartPresets.drop(4)) {
                        AppleChip(
                            label = "${mins}m",
                            isSelected = selectedHeadStartMinutes == mins,
                            onSelect = { selectedHeadStartMinutes = mins },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Shrinking Zone ("Shrinking Finale")
            AppleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shrinking Finale",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Zone automatically shrinks during the final 25% of match time to force a confrontation.",
                            fontSize = 12.sp,
                            color = AppleTextSecondary
                        )
                    }
                    Switch(
                        checked = isShrinkingFinaleEnabled && selectedZonePreset != "UNLIMITED",
                        onCheckedChange = { isShrinkingFinaleEnabled = it },
                        enabled = selectedZonePreset != "UNLIMITED",
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppleAccentBlue)
                    )
                }
            }

            // 4. Safe Zones (Base / Building Shelters)
            AppleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Safe Zones (Bases & Shelters)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Places designated shelters where Hiders cannot be physically tagged.",
                            fontSize = 12.sp,
                            color = AppleTextSecondary
                        )
                    }
                    Switch(
                        checked = enableSafeZones,
                        onCheckedChange = { enableSafeZones = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppleAccentBlue)
                    )
                }
            }

            // 5. Auto-Ping Mechanic (Anti-Camping)
            AppleCard {
                Text(
                    text = "Auto-Ping (Anti-Camping)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Every X minutes, all Hiders' exact GPS coordinates are revealed to Seekers for 10 seconds.",
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((interval, label) in autoPingPresets) {
                        AppleChip(
                            label = label,
                            isSelected = selectedAutoPingInterval == interval,
                            onSelect = { selectedAutoPingInterval = interval },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 6. Match Duration
            AppleCard {
                Text(
                    text = "Match Duration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total game time. Hiders win if they evade until the timer reaches zero.",
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (mins in durationPresets) {
                        AppleChip(
                            label = "${mins}m",
                            isSelected = selectedDuration == mins,
                            onSelect = { selectedDuration = mins },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Create Button
            ApplePrimaryButton(
                text = if (isCreatingRoom) "Creating Room..." else "Create Room & Open Lobby",
                enabled = roomName.isNotBlank() && !isCreatingRoom,
                onClick = {
                    val safeZonesList = if (enableSafeZones) {
                        listOf(
                            SafeZone(
                                id = "sz-base",
                                name = "Base Camp (Pavilion)",
                                latitude = 40.7845,
                                longitude = -73.9665,
                                radiusMeters = 40.0
                            ),
                            SafeZone(
                                id = "sz-north",
                                name = "North Gazebo",
                                latitude = 40.7870,
                                longitude = -73.9640,
                                radiusMeters = 35.0
                            )
                        )
                    } else emptyList()

                    onCreateRoomSubmit(
                        roomName,
                        selectedDuration,
                        selectedZonePreset,
                        selectedHeadStartMinutes,
                        isShrinkingFinaleEnabled,
                        selectedAutoPingInterval,
                        safeZonesList
                    )
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
