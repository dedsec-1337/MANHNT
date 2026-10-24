package com.manhunt.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    onLeaveGame: () -> Unit
) {
    val session = state.session ?: return
    var showCatchSheetForHider by remember { mutableStateOf<Player?>(null) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    val remaining = session.remainingSeconds
    val mins = remaining / 60
    val secs = remaining % 60
    val timerText = String.format("%02d:%02d", mins, secs)

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Background OSMDroid Map
        OSMMapView(
            playerLat = state.myPlayer.latitude,
            playerLng = state.myPlayer.longitude,
            userRole = state.myPlayer.role,
            zoneCenterLat = session.zoneCenterLat,
            zoneCenterLng = session.zoneCenterLng,
            zoneRadiusMeters = session.zoneRadiusMeters,
            otherPlayers = state.players.filter { it.id != state.myPlayer.id },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Header HUD Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = session.roomName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleTextPrimary
                            )
                            RoleBadge(role = state.myPlayer.role)
                        }

                        // Out of zone alert badge
                        if (state.isOutOfZone) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AppleDangerRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "OUT OF BOUNDS!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleDangerRed
                                )
                            }
                        }
                    }

                    // Countdown Timer in prominent Apple typography
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = timerText,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = if (remaining < 180) AppleDangerRed else AppleTextPrimary
                        )

                        // Organizer Safety Pause Button
                        if (state.isOrganizer) {
                            IconButton(
                                onClick = onToggleSafetyPause,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF2F2F7))
                            ) {
                                Icon(
                                    imageVector = if (session.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Safety Pause",
                                    tint = if (session.isPaused) AppleAccentBlue else AppleTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Safety Pause Overlay Alert
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
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.White
                        )
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

        // 3. Bottom Controls HUD
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // A. If Hider: Display Compass Strip pointing to nearest Seeker + Display Catch Code
            if (state.myPlayer.role == Role.HIDER) {
                CompassStrip(
                    deviceHeading = state.headingDegrees,
                    targetBearing = state.nearestSeekerBearing,
                    distanceMeters = state.nearestSeekerDistance
                )

                // Hider's Personal Catch Code Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
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
                                text = "Provide this code only if physically tagged",
                                fontSize = 11.sp,
                                color = AppleTextSecondary
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

            // B. If Seeker: Catch Button activates within 10 meters of a Hider!
            if (state.myPlayer.role == Role.SEEKER) {
                val catchable = state.nearbyCatchableHider

                ApplePrimaryButton(
                    text = if (catchable != null) "⚡ Tag ${catchable.name} (Within 10m!)" else "Catch (Too Far)",
                    enabled = catchable != null && !session.isPaused,
                    backgroundColor = AppleSeekerOrange,
                    onClick = {
                        if (catchable != null) {
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
