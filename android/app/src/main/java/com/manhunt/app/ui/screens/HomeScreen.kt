package com.manhunt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.data.model.NetworkSyncStatus
import com.manhunt.app.ui.components.AppleCard
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.theme.AppleAccentBlue
import com.manhunt.app.ui.theme.AppleCardBackground
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.viewmodel.GameUiState
import com.manhunt.app.ui.theme.*

@Composable
fun HomeScreen(
    state: GameUiState,
    onCreateRoomClick: () -> Unit,
    onJoinRoomClick: (code: String) -> Unit,
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SignalCellularAlt,
                                contentDescription = null,
                                tint = AppleAccentBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Long-Range Cellular GPS Chase",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppleTextSecondary
                            )
                        }
                    }

                    // Profile Chip
                    Surface(
                        onClick = onEditProfileClick,
                        shape = RoundedCornerShape(20.dp),
                        color = AppleCardBackground
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

            // Cellular Data Advantage Banner
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppleAccentBlue.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = AppleAccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "City-Wide Cellular Tracking",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleAccentBlue
                            )
                            Text(
                                text = "Powered by Firebase Realtime REST API. Spread out across parks and miles with 5G/4G/LTE.",
                                fontSize = 11.sp,
                                color = AppleTextSecondary
                            )
                        }
                    }
                }
            }

            // Join with Code Card
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
                        text = "Enter the 6-character code created by the match organizer.",
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
                            unfocusedBorderColor = AppleDivider,
                            focusedContainerColor = AppleInputBackground,
                            unfocusedContainerColor = AppleInputBackground
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ApplePrimaryButton(
                        text = if (state.isJoiningRoom) "Connecting to Cloud..." else "Join Chase",
                        enabled = inputCode.length >= 4 && !state.isJoiningRoom,
                        onClick = { onJoinRoomClick(inputCode) }
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
                                text = "Set play zone boundary, timer, and generate a 6-character cloud room code.",
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

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }
}
