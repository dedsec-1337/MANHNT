package com.manhunt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.data.model.GameStatus
import com.manhunt.app.data.model.NetworkSyncStatus
import com.manhunt.app.data.model.Player
import com.manhunt.app.data.model.Role
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.components.CatchBottomSheet
import com.manhunt.app.ui.components.OSMMapView
import com.manhunt.app.ui.components.RoleBadge
import com.manhunt.app.ui.theme.*
import com.manhunt.app.viewmodel.GameUiState
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameUiState,
    headingFlow: StateFlow<Float>,
    onToggleSafetyPause: () -> Unit,
    onAttemptCatch: (targetHiderId: String, code: String) -> Unit,
    onActivateStealth: () -> Unit,
    onStartJailbreak: () -> Unit,
    onSendQuickSignal: (String) -> Unit,
    onLeaveGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = state.session ?: return
    val heading by headingFlow.collectAsState()
    var showCatchSheetForHider by remember { mutableStateOf<Player?>(null) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var isCatchCodeRevealed by remember(state.myPlayer.id) { mutableStateOf(false) }

    val remaining = session.remainingSeconds
    val timerText = String.format("%02d:%02d", remaining / 60, remaining % 60)

    Box(modifier = modifier.fillMaxSize()) {
        OSMMapView(
            playerLat = state.myPlayer.latitude,
            playerLng = state.myPlayer.longitude,
            userRole = state.myPlayer.role,
            zoneCenterLat = session.zoneCenterLat,
            zoneCenterLng = session.zoneCenterLng,
            zoneRadiusMeters = state.currentDynamicRadiusMeters,
            isUnlimitedZone = session.isUnlimitedZone,
            safeZones = session.safeZones,
            isAutoPingActive = state.isAutoPingActive,
            otherPlayers = state.players.filter { it.id != state.myPlayer.id },
            jailLat = session.jailLat,
            jailLng = session.jailLng,
            jailRadiusMeters = session.jailRadiusMeters,
            jailEnabled = session.jailEnabled,
            headingDegrees = heading,
            hasHeading = state.hasCompassHeading,
            playerLocationUpdatedAtEpochMs = state.myPlayer.lastUpdatedEpochMs,
            showOtherPlayerLocations = true,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (state.myPlayer.role == Role.HIDER) "HIDE & SURVIVE" else "LIVE HUNT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AppleCardBackground.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(session.roomName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppleTextPrimary)
                            RoleBadge(role = state.myPlayer.role)
                            val dotColor = when (state.networkStatus) {
                                NetworkSyncStatus.ONLINE -> AppleHiderGreen
                                NetworkSyncStatus.SYNCING -> AppleSeekerOrange
                                NetworkSyncStatus.OFFLINE -> AppleDangerRed
                            }
                            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                        }
                        Text(
                            text = if (session.isUnlimitedZone) "Zone: Unlimited" else "Zone: ${state.currentDynamicRadiusMeters.toInt()}m",
                            fontSize = 11.sp,
                            color = AppleTextSecondary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = timerText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (remaining < 180) AppleDangerRed else AppleTextPrimary
                        )
                        if (state.isOrganizer) {
                            IconButton(
                                onClick = onToggleSafetyPause,
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppleFill)
                            ) {
                                Icon(
                                    imageVector = if (session.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Safety Pause",
                                    tint = AppleTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (state.isOutOfZone) {
                Surface(shape = RoundedCornerShape(12.dp), color = AppleDangerRed, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("OUT OF BOUNDS — return to the zone", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            if (session.isPaused) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF9500), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "SAFETY PAUSE ACTIVE",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.myPlayer.role == Role.HIDER) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AppleCardBackground.copy(alpha = 0.96f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Your catch code", fontSize = 12.sp, color = AppleTextSecondary)
                                Text(
                                    if (isCatchCodeRevealed) state.myPlayer.catchCode.ifBlank { "----" } else "••••",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    color = AppleTextPrimary
                                )
                            }
                            TextButton(onClick = { isCatchCodeRevealed = !isCatchCodeRevealed }) {
                                Text(if (isCatchCodeRevealed) "Hide" else "Reveal")
                            }
                        }
                        if (state.nearestSeekerDistance >= 0) {
                            Text("Nearest seeker ~ ${state.nearestSeekerDistance.toInt()}m", fontSize = 12.sp, color = AppleTextSecondary)
                        }
                        ApplePrimaryButton(
                            text = when {
                                state.isStealthActive -> "Stealth active"
                                state.stealthCooldownSeconds > 0 -> "Stealth cooldown ${state.stealthCooldownSeconds}s"
                                else -> "Activate Stealth"
                            },
                            enabled = !state.isStealthActive && state.stealthCooldownSeconds <= 0 && !session.isPaused,
                            backgroundColor = AppleHiderPurple,
                            onClick = onActivateStealth
                        )
                    }
                }
            }

            if (state.myPlayer.role == Role.SEEKER) {
                val catchable = state.nearbyCatchableHider
                val inSafe = catchable?.isInSafeZone == true
                val anyHider = state.players.any { it.id != state.myPlayer.id && it.role == Role.HIDER && !it.isCaught }
                val buttonText = when {
                    state.isSeekerFrozen -> "Frozen: Waiting for Head Start"
                    inSafe -> "Target in Safe Zone"
                    catchable != null -> "⚡ Tag ${catchable.name}"
                    anyHider -> "Tag a Hider"
                    else -> "No Hiders available"
                }
                ApplePrimaryButton(
                    text = buttonText,
                    enabled = catchable != null && !session.isPaused && !state.isSeekerFrozen && !inSafe,
                    backgroundColor = if (inSafe) Color(0xFF64748B) else AppleSeekerOrange,
                    onClick = { if (catchable != null && !inSafe) showCatchSheetForHider = catchable }
                )
            }

            TextButton(
                onClick = { showLeaveConfirmDialog = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Leave", color = AppleDangerRed, fontWeight = FontWeight.SemiBold)
            }
        }

        showCatchSheetForHider?.let { hider ->
            CatchBottomSheet(
                targetHider = hider,
                onDismiss = { showCatchSheetForHider = null },
                onSubmitCatchCode = { code ->
                    onAttemptCatch(hider.id, code)
                    showCatchSheetForHider = null
                }
            )
        }

        if (showLeaveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirmDialog = false },
                title = { Text("Leave match?") },
                text = { Text("You can rejoin with the room code if the match is still running.") },
                confirmButton = {
                    TextButton(onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveGame()
                    }) { Text("Leave", color = AppleDangerRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirmDialog = false }) { Text("Stay") }
                }
            )
        }
    }
}
