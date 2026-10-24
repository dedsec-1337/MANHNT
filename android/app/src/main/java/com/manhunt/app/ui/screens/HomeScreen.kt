package com.manhunt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.ui.components.AppleCard
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.theme.AppleAccentBlue
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.viewmodel.GameUiState

@Composable
fun HomeScreen(
    state: GameUiState,
    onCreateRoomClick: () -> Unit,
    onJoinRoomClick: (code: String, hostIp: String?) -> Unit,
    onEditProfileClick: () -> Unit
) {
    var inputCode by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                // Header with Profile Avatar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MANHUNT",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = AppleTextPrimary
                        )
                        Text(
                            text = "Local GPS Chase Game",
                            fontSize = 14.sp,
                            color = AppleTextSecondary
                        )
                    }

                    // Profile Chip
                    Surface(
                        onClick = onEditProfileClick,
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = state.myPlayer.avatarEmoji, fontSize = 20.sp)
                            Text(
                                text = state.myPlayer.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleTextPrimary
                            )
                        }
                    }
                }
            }

            // Quick Join Code Card
            item {
                AppleCard {
                    Text(
                        text = "Join with Game Code",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enter the 6-character code from the organizer.",
                        fontSize = 13.sp,
                        color = AppleTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { if (it.length <= 6) inputCode = it.uppercase() },
                        placeholder = { Text("e.g. HUNT42", color = AppleTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppleAccentBlue,
                            unfocusedBorderColor = Color(0xFFE5E5EA),
                            focusedContainerColor = Color(0xFFF8F9FA),
                            unfocusedContainerColor = Color(0xFFF8F9FA)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ApplePrimaryButton(
                        text = "Join Chase",
                        enabled = inputCode.length >= 4,
                        onClick = { onJoinRoomClick(inputCode, null) }
                    )
                }
            }

            // Create Room Card
            item {
                AppleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Organize New Chase",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Set play zone boundary, timer, and invite nearby friends.",
                                fontSize = 13.sp,
                                color = AppleTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AppleAccentBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create",
                                tint = AppleAccentBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ApplePrimaryButton(
                        text = "Create Room",
                        onClick = onCreateRoomClick
                    )
                }
            }

            // Discovered Nearby Rooms (UDP Local Discovery)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Nearby",
                            tint = AppleAccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Nearby Rooms (Local Wi-Fi)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                    }

                    Text(
                        text = "${state.discoveredRooms.size} found",
                        fontSize = 12.sp,
                        color = AppleTextSecondary
                    )
                }
            }

            if (state.discoveredRooms.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = AppleTextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Searching for nearby games...",
                                fontSize = 14.sp,
                                color = AppleTextSecondary
                            )
                            Text(
                                text = "Ensure both phones are on the same Wi-Fi or Hotspot.",
                                fontSize = 12.sp,
                                color = AppleTextSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                items(state.discoveredRooms) { room ->
                    Surface(
                        onClick = { onJoinRoomClick(room.joinCode, room.hostIp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = room.roomName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleTextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Code: ${room.joinCode} • ${room.zoneRadiusMeters.toInt()}m zone",
                                    fontSize = 13.sp,
                                    color = AppleTextSecondary
                                )
                            }

                            Button(
                                onClick = { onJoinRoomClick(room.joinCode, room.hostIp) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppleAccentBlue)
                            ) {
                                Text("Join", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }
}
