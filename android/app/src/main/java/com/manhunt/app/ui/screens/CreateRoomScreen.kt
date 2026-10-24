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
import com.manhunt.app.ui.components.AppleCard
import com.manhunt.app.ui.components.AppleChip
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.theme.AppleAccentBlue
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(
    onBackClick: () -> Unit,
    onCreateRoomSubmit: (roomName: String, duration: Int, zoneRadius: Double, ratio: Int) -> Unit
) {
    var roomName by remember { mutableStateOf("Sunset Park Chase") }
    var selectedDuration by remember { mutableIntStateOf(30) } // 15, 30, 45, 60
    var zoneRadiusMeters by remember { mutableFloatStateOf(500f) } // 200m to 2000m
    var selectedRatio by remember { mutableIntStateOf(3) } // 1 Hider per N Seekers (2, 3, 4)

    val durationPresets = listOf(15, 30, 45, 60)
    val ratioPresets = listOf(
        Pair(2, "1:2 (More Hiders)"),
        Pair(3, "1:3 (Standard)"),
        Pair(4, "1:4 (Challenging)")
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
                        unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedContainerColor = Color(0xFFF8F9FA),
                        unfocusedContainerColor = Color(0xFFF8F9FA)
                    )
                )
            }

            // Circular Play Zone Radius (200m - 2000m)
            AppleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Play Zone Boundary",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "${zoneRadiusMeters.roundToInt()}m radius",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleAccentBlue
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Circular zone centered at your current GPS location. Leaving triggers out-of-bounds warning.",
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                Slider(
                    value = zoneRadiusMeters,
                    onValueChange = { zoneRadiusMeters = it },
                    valueRange = 200f..2000f,
                    steps = 17, // ~100m steps
                    colors = SliderDefaults.colors(
                        thumbColor = AppleAccentBlue,
                        activeTrackColor = AppleAccentBlue,
                        inactiveTrackColor = Color(0xFFE5E5EA)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("200m", fontSize = 12.sp, color = AppleTextSecondary)
                    Text("1,000m", fontSize = 12.sp, color = AppleTextSecondary)
                    Text("2,000m", fontSize = 12.sp, color = AppleTextSecondary)
                }
            }

            // Duration Selection Chips
            AppleCard {
                Text(
                    text = "Match Duration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hiders win if they evade until the timer reaches zero.",
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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

            // Role Ratio Chips
            AppleCard {
                Text(
                    text = "Role Balance Ratio",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ratio of Hiders to Seekers when assigning teams.",
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((ratio, label) in ratioPresets) {
                        AppleChip(
                            label = label,
                            isSelected = selectedRatio == ratio,
                            onSelect = { selectedRatio = ratio },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Create Button
            ApplePrimaryButton(
                text = "Create Room & Open Lobby",
                enabled = roomName.isNotBlank(),
                onClick = {
                    onCreateRoomSubmit(
                        roomName,
                        selectedDuration,
                        zoneRadiusMeters.toDouble(),
                        selectedRatio
                    )
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
