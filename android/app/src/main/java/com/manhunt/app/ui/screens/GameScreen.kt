package com.manhunt.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.data.model.NetworkSyncStatus
import com.manhunt.app.data.model.Player
import com.manhunt.app.data.model.Role
import com.manhunt.app.ui.components.*
import com.manhunt.app.ui.theme.*
import com.manhunt.app.viewmodel.GameUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameUiState,
    onToggleSafetyPause: () -> Unit,
    onAttemptCatch: (targetHiderId: String, code: String) -> Unit,
    onRespondToCatchConfirm: (accept: Boolean) -> Unit,
    onActivateStealth: () -> Unit,
    onStartJailbreak: () -> Unit,
    onLeaveGame: () -> Unit
) {
    val session = state.session ?: return
    var showCatchSheetForHider by remember { mutableStateOf<Player?>(null) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var dismissedCatchConfirmAt by remember { mutableStateOf(0L) }

    val remaining = session.remainingSeconds
    val mins = remaining / 60
    val secs = remaining % 60
    val timerText = String.format("%02d:%02d", mins, secs)

    val headStartMins = state.headStartCountdownSeconds / 60
    val headStartSecs = state.headStartCountdownSeconds % 60
    val headStartTimerText = String.format("%02d:%02d", headStartMins, headStartSecs)

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Background OSMDroid Map with Geofences, Buffer, Safe Zones, and Markers
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
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Header HUD Card & Notification Stack
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                            Text(
                                text = session.roomName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleTextPrimary
                            )
                            RoleBadge(role = state.myPlayer.role)

                            // Network Status indicator dot
                            val dotColor = when (state.networkStatus) {
                                NetworkSyncStatus.ONLINE -> AppleHiderGreen
                                NetworkSyncStatus.SYNCING -> AppleSeekerOrange
                                NetworkSyncStatus.OFFLINE -> AppleDangerRed
                            }
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }

                        // Play zone status summary
                        val zoneLabel = if (session.isUnlimitedZone) {
                            "Zone: Unlimited"
                        } else if (state.isShrinkingZoneActive) {
                            "Shrinking Zone: ${state.currentDynamicRadiusMeters.toInt()}m"
                        } else {
                            "Zone: ${session.zoneRadiusMeters.toInt()}m"
                        }

                        Text(
                            text = zoneLabel,
                            fontSize = 11.sp,
                            color = if (state.isShrinkingZoneActive) AppleSeekerOrange else AppleTextSecondary,
                            fontWeight = if (state.isShrinkingZoneActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // Countdown Timer
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = timerText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = if (remaining < 180) AppleDangerRed else AppleTextPrimary
                        )

                        // Organizer Safety Pause Button
                        if (state.isOrganizer) {
                            IconButton(
                                onClick = onToggleSafetyPause,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppleFill)
                            ) {
                                Icon(
                                    imageVector = if (session.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Safety Pause",
                                    tint = if (session.isPaused) AppleAccentBlue else AppleTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // A. Head Start Banner (for Hiders while running)
            if (state.headStartCountdownSeconds > 0 && state.myPlayer.role == Role.HIDER) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppleAccentBlue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("HEAD START ACTIVE — SCATTER!", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text(headStartTimerText, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }

            // B. Auto-Ping (Anti-Camping) Radar Reveal Alert
            if (state.isAutoPingActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF6366F1), // Indigo
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("📡 RADAR PING ACTIVE: Hiders revealed!", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("${state.autoPingCountdownSeconds}s", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }

            // C. Safe Zone Shield Alert (Hider inside Safe Zone)
            if (state.isInSafeZone) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppleHiderGreen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            text = "SAFE ZONE ACTIVE: Protected in ${state.currentSafeZoneName ?: "Base"} (Cannot be tagged!)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // D. Safe Zone Notice for Seekers
            if (state.myPlayer.role == Role.SEEKER && state.safeZoneNotice != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0284C7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            text = state.safeZoneNotice,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // E. Geofence Boundary Warning Alert
            if (state.isOutOfZone) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppleDangerRed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            text = "OUT OF BOUNDS: Return to play zone immediately!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // F. Cellular Network Disconnected Warning
            if (state.networkStatus == NetworkSyncStatus.OFFLINE) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppleDangerRed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularConnectedNoInternet0Bar,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "CELLULAR SYNC DISCONNECTED — CHECK MOBILE DATA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // G. Safety Pause Active
            if (session.isPaused) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF9500),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White)
                        Text(
                            text = "SAFETY PAUSE ACTIVE — FREEZE POSITIONS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 3. SEEKER FROZEN OVERLAY (During Head Start)
        AnimatedVisibility(
            visible = state.isSeekerFrozen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = AppleCardBackground,
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AppleAccentBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = AppleAccentBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "WAIT FOR HEAD START",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = AppleTextPrimary
                        )

                        Text(
                            text = headStartTimerText,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = AppleAccentBlue,
                            letterSpacing = 2.sp
                        )

                        Text(
                            text = "Hiders are currently scattering across the area.\nSeekers are frozen at base until countdown ends.",
                            fontSize = 13.sp,
                            color = AppleTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = AppleAccentBlue,
                            trackColor = AppleFill
                        )
                    }
                }
            }
        }

        // 4. Bottom Controls HUD
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // A. If Hider: Display Rotary Compass pointing to nearest Seeker + Display Catch Code
            if (state.myPlayer.role == Role.HIDER) {
                CompassStrip(
                    deviceHeading = state.headingDegrees,
                    targetBearing = state.nearestSeekerBearing,
                    distanceMeters = state.nearestSeekerDistance
                )

                // Hider's Personal Catch Code Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AppleCardBackground,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Your Catch Code",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleTextSecondary
                            )
                            Text(
                                text = if (state.isInSafeZone) "Protected in Safe Zone (Cannot be tagged)" else "Give only if physically tagged",
                                fontSize = 11.sp,
                                color = if (state.isInSafeZone) AppleHiderGreen else AppleTextSecondary,
                                fontWeight = if (state.isInSafeZone) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        Text(
                            text = state.myPlayer.catchCode.ifEmpty { "----" },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = AppleTextPrimary
                        )
                    }
                }
            }

            // Hider abilities: Stealth + Jailbreak
            if (state.myPlayer.role == Role.HIDER) {
                val me = state.myPlayer
                val isFree = !me.isCaught && !me.isInJail
                val stealthText = when {
                    !isFree -> "Stealth unavailable"
                    state.isStealthActive -> "Stealth Active"
                    state.stealthCooldownSeconds > 0 -> "Stealth Cooldown (${state.stealthCooldownSeconds}s)"
                    else -> "Activate Stealth"
                }
                ApplePrimaryButton(
                    text = stealthText,
                    enabled = isFree && !session.isPaused && !state.isStealthActive && state.stealthCooldownSeconds <= 0,
                    backgroundColor = Color(0xFF6366F1),
                    onClick = onActivateStealth
                )

                if (session.jailEnabled && isFree && state.players.any { it.role == Role.HIDER && it.isInJail }) {
                    ApplePrimaryButton(
                        text = if (session.isJailbreakInProgress) "Jailbreak in progress (${session.jailbreakProgressSeconds}s)" else "Start Jailbreak",
                        enabled = !session.isPaused && !session.isJailbreakInProgress,
                        backgroundColor = AppleHiderGreen,
                        onClick = onStartJailbreak
                    )
                }
            }

            // B. If Seeker: Catch Button activates within 10 meters of a Hider!
            if (state.myPlayer.role == Role.SEEKER) {
                val catchable = state.nearbyCatchableHider
                val isTargetInSafeZone = catchable?.isInSafeZone == true

                val buttonText = when {
                    state.isSeekerFrozen -> "Frozen: Waiting for Head Start"
                    isTargetInSafeZone -> "Target in Safe Zone (${catchable?.safeZoneName ?: "Base"})"
                    catchable != null -> "⚡ Tag ${catchable.name} (Within 10m!)"
                    else -> "Catch Button (Too Far)"
                }

                ApplePrimaryButton(
                    text = buttonText,
                    enabled = catchable != null && !session.isPaused && !state.isSeekerFrozen && !isTargetInSafeZone,
                    backgroundColor = if (isTargetInSafeZone) Color(0xFF64748B) else AppleSeekerOrange,
                    onClick = {
                        if (catchable != null && !isTargetInSafeZone) {
                            showCatchSheetForHider = catchable
                        }
                    }
                )
            }

            // Leave Game Button (Subtle)
            TextButton(
                onClick = { showLeaveConfirmDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Leave Chase",
                    fontSize = 13.sp,
                    color = AppleTextSecondary
                )
            }
        }

        // Catch Bottom Sheet for entering 4-digit code
        showCatchSheetForHider?.let { hider ->
            CatchBottomSheet(
                targetHider = hider,
                onDismiss = { showCatchSheetForHider = null },
                onSubmitCatchCode = { enteredPin ->
                    showCatchSheetForHider = null
                    onAttemptCatch(hider.id, enteredPin)
                }
            )
        }

        // A correct code creates a confirmation request for the Hider.
        val pendingCatch = state.pendingCatchConfirm
        if (pendingCatch != null &&
            pendingCatch.status == "PENDING" &&
            pendingCatch.targetHiderId == state.myPlayer.id &&
            pendingCatch.createdAtEpochMs != dismissedCatchConfirmAt
        ) {
            AlertDialog(
                onDismissRequest = { dismissedCatchConfirmAt = pendingCatch.createdAtEpochMs },
                title = { Text("Confirm catch") },
                text = {
                    val disputesLeft = state.myPlayer.disputesRemaining
                    Text(
                        if (disputesLeft > 0) {
                            "${pendingCatch.seekerName} entered your correct catch code. Confirm if you were tagged, or dispute the catch if you were not. Disputes left: $disputesLeft."
                        } else {
                            "${pendingCatch.seekerName} entered your correct catch code. Confirm the catch to continue."
                        }
                    )
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { onRespondToCatchConfirm(true) }) {
                            Text("Confirm catch")
                        }
                        if (state.myPlayer.disputesRemaining > 0) {
                            TextButton(onClick = { onRespondToCatchConfirm(false) }) {
                                Text("Dispute (${state.myPlayer.disputesRemaining} left)")
                            }
                        }
                        TextButton(onClick = {
                            dismissedCatchConfirmAt = pendingCatch.createdAtEpochMs
                        }) {
                            Text("Later")
                        }
                    }
                }
            )
        }

        // Leave Confirmation Dialog
        if (showLeaveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirmDialog = false },
                title = { Text("Leave Game?") },
                text = { Text("You will be removed from the active match roster.") },
                confirmButton = {
                    TextButton(onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveGame()
                    }) {
                        Text("Leave", color = AppleDangerRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirmDialog = false }) {
                        Text("Cancel", color = AppleTextPrimary)
                    }
                }
            )
        }
    }
}
