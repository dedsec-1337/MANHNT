package com.manhunt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.data.model.NetworkSyncStatus
import com.manhunt.app.data.model.Role
import com.manhunt.app.ui.components.AppleCard
import com.manhunt.app.ui.components.OSMMapView
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.components.AppleSecondaryButton
import com.manhunt.app.ui.components.RoleBadge
import com.manhunt.app.ui.theme.*
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
    val session = state.session
    if (session == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AppleAccentBlue)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            session.roomName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                        // Cloud sync status indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val dotColor = when (state.networkStatus) {
                                NetworkSyncStatus.ONLINE -> AppleHiderGreen
                                NetworkSyncStatus.SYNCING -> AppleSeekerOrange
                                NetworkSyncStatus.OFFLINE -> AppleDangerRed
                            }
                            val statusLabel = when (state.networkStatus) {
                                NetworkSyncStatus.ONLINE -> "Cellular Cloud Sync Active"
                                NetworkSyncStatus.SYNCING -> "Syncing..."
                                NetworkSyncStatus.OFFLINE -> "No Connection"
                            }
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = statusLabel,
                                fontSize = 11.sp,
                                color = AppleTextSecondary
                            )
                        }
                    }
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
                            text = "CLOUD JOIN CODE",
                            fontSize = 11.sp,
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
                            text = "Players can join from anywhere using cellular data.",
                            fontSize = 12.sp,
                            color = AppleTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            val zoneText = if (session.isUnlimitedZone) "📍 Unlimited" else "📍 ${session.zoneRadiusMeters.toInt()}m"
                            val headStartText = if (session.headStartMinutes > 0) "⏳ ${session.headStartMinutes}m Start" else "⏳ 0m Start"

                            Text(text = "⏱ ${session.durationMinutes}m", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppleTextPrimary)
                            Text(text = "•", fontSize = 12.sp, color = AppleTextSecondary)
                            Text(text = zoneText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppleTextPrimary)
                            Text(text = "•", fontSize = 12.sp, color = AppleTextSecondary)
                            Text(text = headStartText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppleTextPrimary)
                        }

                        if (session.isShrinkingZoneEnabled || session.autoPingIntervalMinutes > 0 || session.safeZones.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (session.isShrinkingZoneEnabled) {
                                    Text(text = "⚡ Shrinking Zone", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AppleSeekerOrange)
                                }
                                if (session.autoPingIntervalMinutes > 0) {
                                    Text(text = "📡 Ping: ${session.autoPingIntervalMinutes}m", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6366F1))
                                }
                                if (session.safeZones.isNotEmpty()) {
                                    Text(text = "🛡️ ${session.safeZones.size} Safe Zones", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AppleHiderGreen)
                                }
                            }
                        }
                    }
                }
            }

            item {
                AppleCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Map Preview",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleTextPrimary
                        )
                        Text(
                            text = "The play area is shown here. Live player positions appear when the game starts.",
                            fontSize = 12.sp,
                            color = AppleTextSecondary,
                            lineHeight = 17.sp
                        )
                        OSMMapView(
                            playerLat = state.myPlayer.latitude,
                            playerLng = state.myPlayer.longitude,
                            userRole = state.myPlayer.role,
                            zoneCenterLat = session.zoneCenterLat,
                            zoneCenterLng = session.zoneCenterLng,
                            zoneRadiusMeters = session.zoneRadiusMeters,
                            isUnlimitedZone = session.isUnlimitedZone,
                            safeZones = session.safeZones,
                            otherPlayers = emptyList(),
                            headingDegrees = state.headingDegrees,
                            hasHeading = state.hasCompassHeading,
                            centerOnPlayer = false,
                            showOtherPlayerLocations = false,
                            initialZoom = when {
                                session.isUnlimitedZone -> 13.0
                                session.zoneRadiusMeters >= 2000.0 -> 13.0
                                else -> 14.0
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            // CRITICAL CELLULAR DATA REQUIREMENT WARNING NOTICE
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFFBEB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Cellular Data Required (5G / 4G / LTE)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                            Text(
                                text = "All players must have an active mobile data connection enabled. Long-range GPS tracking updates across miles via the cloud.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E),
                                lineHeight = 16.sp
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
                    color = AppleCardBackground,
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
                                    .background(AppleFill),
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
