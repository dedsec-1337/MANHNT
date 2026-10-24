package com.manhunt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.data.model.Role
import com.manhunt.app.ui.components.AppleCard
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.components.AppleSecondaryButton
import com.manhunt.app.ui.components.RoleBadge
import com.manhunt.app.ui.theme.AppleAccentBlue
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.viewmodel.GameUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    state: GameUiState,
    onStartGameClick: () -> Unit,
    onToggleRole: (playerId: String) -> Unit,
    onAutoBalanceRoles: () -> Unit,
    onLeaveLobbyClick: () -> Unit
) {
    val session = state.session ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        session.roomName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = onLeaveLobbyClick) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Leave",
                            tint = AppleTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Join Code Hero Card
            item {
                AppleCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "JOIN CODE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleTextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = session.joinCode,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            color = AppleAccentBlue
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Share this code or connect via local Wi-Fi / Hotspot.",
                            fontSize = 12.sp,
                            color = AppleTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "⏱ ${session.durationMinutes} mins",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "📍 ${session.zoneRadiusMeters.toInt()}m zone",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppleTextPrimary
                            )
                        }
                    }
                }
            }

            // Players Roster Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val seekersCount = state.players.count { it.role == Role.SEEKER }
                    val hidersCount = state.players.count { it.role == Role.HIDER }

                    Text(
                        text = "Players (${state.players.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "$seekersCount Seekers",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD97706)
                        )
                        Text(text = "•", fontSize = 12.sp, color = AppleTextSecondary)
                        Text(
                            text = "$hidersCount Hiders",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            }

            // Player Cards
            items(state.players) { player ->
                val isMe = player.id == state.myPlayer.id

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF2F2F7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = player.avatarEmoji, fontSize = 20.sp)
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = player.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppleTextPrimary
                                    )
                                    if (isMe) {
                                        Text(
                                            text = "(You)",
                                            fontSize = 12.sp,
                                            color = AppleTextSecondary
                                        )
                                    }
                                }
                                if (player.id == session.hostPlayerId) {
                                    Text(
                                        text = "Organizer",
                                        fontSize = 11.sp,
                                        color = AppleAccentBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Role Badge & Tap to Switch (if organizer)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoleBadge(role = player.role)

                            if (state.isOrganizer) {
                                TextButton(
                                    onClick = { onToggleRole(player.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Swap", fontSize = 12.sp, color = AppleAccentBlue)
                                }
                            }
                        }
                    }
                }
            }

            // Organizer Controls
            if (state.isOrganizer) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    AppleSecondaryButton(
                        text = "Auto-Balance Roles",
                        onClick = onAutoBalanceRoles
                    )
                }

                item {
                    ApplePrimaryButton(
                        text = "Start Manhunt",
                        onClick = onStartGameClick
                    )
                }
            } else {
                item {
                    AppleCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AppleAccentBlue
                            )
                            Text(
                                text = "Waiting for organizer to start the chase...",
                                fontSize = 13.sp,
                                color = AppleTextSecondary
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }
}
