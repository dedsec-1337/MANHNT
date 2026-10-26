package com.manhunt.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.manhunt.app.data.Constants
import com.manhunt.app.data.model.NetworkSyncStatus
import com.manhunt.app.data.model.Player
import com.manhunt.app.data.model.Role
import com.manhunt.app.ui.components.*
import com.manhunt.app.ui.theme.*
import com.manhunt.app.util.GeoUtils
import com.manhunt.app.viewmodel.GameUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow


private data class QuickSignalOption(
    val code: String,
    val emoji: String,
    val text: String
)

private val REACTION_OPTIONS = listOf(
    QuickSignalOption("R_LAUGH", "😂", "Laugh"),
    QuickSignalOption("R_EYES", "👀", "Eyes on you"),
    QuickSignalOption("R_CLAP", "👏", "Clap"),
    QuickSignalOption("R_FIRE", "🔥", "Let's go!")
)

private val QUICK_CHAT_OPTIONS = listOf(
    QuickSignalOption("P_CATCH_ME", "🏃", "Catch me if you can!"),
    QuickSignalOption("P_CLOSE_CALL", "😅", "That was close!"),
    QuickSignalOption("P_WARMER", "👀", "You're getting warmer…"),
    QuickSignalOption("P_NEED_RESCUE", "🆘", "Need a rescue!"),
    QuickSignalOption("P_GG", "🏁", "GG — nice chase!")
)

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
    var showCatchSheetForHider by remember { mutableStateOf<Player?>(null) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showReactionMenu by remember { mutableStateOf(false) }
    var showQuickChatSheet by remember { mutableStateOf(false) }
    var isCatchCodeRevealed by remember(state.myPlayer.id) { mutableStateOf(false) }
    var recentSignalFeedback by remember { mutableStateOf<String?>(null) }

    fun sendSignal(option: QuickSignalOption) {
        onSendQuickSignal(option.code)
        recentSignalFeedback = option.emoji + "  " + option.text
    }

    LaunchedEffect(recentSignalFeedback) {
        if (recentSignalFeedback != null) {
            delay(1600)
            recentSignalFeedback = null
        }
    }

    val latestSignal = state.roomSignals.maxByOrNull { it.value.timestampEpochMs }
    val signalAgeMs = latestSignal?.let { System.currentTimeMillis() - it.value.timestampEpochMs }
    val activeSignal = latestSignal?.takeIf {
        signalAgeMs != null && signalAgeMs in -Constants.GAME_SIGNAL_MAX_FUTURE_SKEW_MS..Constants.GAME_SIGNAL_MAX_AGE_MS
    }
    val signalOption = activeSignal?.value?.code?.let { code ->
        (REACTION_OPTIONS + QUICK_CHAT_OPTIONS).firstOrNull { it.code == code }
    }
    val signalSenderName = activeSignal?.key?.let { playerId ->
        state.players.firstOrNull { it.id == playerId }?.name
    } ?: "Player"
    val canSendSignals = session.status == com.manhunt.app.data.model.GameStatus.RUNNING && !session.isPaused

    val remaining = session.remainingSeconds
    val mins = remaining / 60
    val secs = remaining % 60
    val timerText = String.format("%02d:%02d", mins, secs)

    val headStartMins = state.headStartCountdownSeconds / 60
    val headStartSecs = state.headStartCountdownSeconds % 60
    val headStartTimerText = String.format("%02d:%02d", headStartMins, headStartSecs)

    Box(modifier = modifier.fillMaxSize()) {
        GameMapLayer(
            state = state,
            headingFlow = headingFlow,
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
                            Text(
                                text = session.roomName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleTextPrimary
                            )
                            RoleBadge(role = state.myPlayer.role)

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

            if (state.isAutoPingActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF6366F1),
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
                            text = state.safeZoneNotice!!,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

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

            if (activeSignal != null && signalOption != null) {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppleCardBackground.copy(alpha = 0.97f),
                        shadowElevation = 5.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(signalOption.emoji, fontSize = 20.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = signalSenderName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppleTextSecondary
                                )
                                Text(
                                    text = signalOption.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AppleTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            if (recentSignalFeedback != null) {
                Text(
                    text = "Sent $recentSignalFeedback",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }
        }

        // Bottom action column
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.myPlayer.role == Role.HIDER) {
                val isFree = !state.myPlayer.isCaught && !state.isInJail
                HiderActionPanel(
                    state = state,
                    isCatchCodeRevealed = isCatchCodeRevealed,
                    onRevealToggle = { isCatchCodeRevealed = !isCatchCodeRevealed },
                    onActivateStealth = onActivateStealth
                )

                if (session.jailEnabled && isFree && state.players.any { it.role == Role.HIDER && it.isInJail }) {
                    ApplePrimaryButton(
                        text = if (session.isJailbreakInProgress) "Jailbreak in progress (${session.jailbreakProgressSeconds}s)" else "Start Jailbreak",
                        enabled = !session.isPaused && !session.isJailbreakInProgress,
                        backgroundColor = AppleHiderPurple,
                        onClick = onStartJailbreak
                    )
                }
            }

            // Seeker catch button — active when a Hider is within CATCH_PROXIMITY_METERS.
            if (state.myPlayer.role == Role.SEEKER) {
                val catchable = state.nearbyCatchableHider
                val isTargetInSafeZone = catchable?.isInSafeZone == true
                val me = state.myPlayer
                val nearestHiderMeters = state.players
                    .filter { it.id != me.id && it.role == Role.HIDER && !it.isCaught }
                    .mapNotNull { hider ->
                        if (GeoUtils.hasValidCoordinates(me.latitude, me.longitude) &&
                            GeoUtils.hasValidCoordinates(hider.latitude, hider.longitude)
                        ) {
                            GeoUtils.distanceMeters(
                                me.latitude, me.longitude, hider.latitude, hider.longitude
                            )
                        } else null
                    }
                    .minOrNull()

                val rangeLabel = "${Constants.CATCH_PROXIMITY_METERS.toInt()}m"
                val buttonText = when {
                    state.isSeekerFrozen -> "Frozen: Waiting for Head Start"
                    isTargetInSafeZone -> "Target in Safe Zone (${catchable?.safeZoneName ?: "Base"})"
                    catchable != null -> "⚡ Tag ${catchable.name} (Within $rangeLabel!)"
                    nearestHiderMeters != null -> "Catch Button (Too Far: ${nearestHiderMeters.toInt()}m / $rangeLabel)"
                    else -> "Catch Button (Waiting for GPS…)"
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canSendSignals) {
                        Surface(
                            onClick = { showReactionMenu = true },
                            shape = RoundedCornerShape(14.dp),
                            color = AppleCardBackground.copy(alpha = 0.95f)
                        ) {
                            Text("Reactions", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Surface(
                            onClick = { showQuickChatSheet = true },
                            shape = RoundedCornerShape(14.dp),
                            color = AppleCardBackground.copy(alpha = 0.95f)
                        ) {
                            Text("Quick chat", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                TextButton(onClick = { showLeaveConfirmDialog = true }) {
                    Text("Leave", color = AppleDangerRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (showCatchSheetForHider != null) {
            CatchBottomSheet(
                hider = showCatchSheetForHider!!,
                onDismiss = { showCatchSheetForHider = null },
                onSubmitCode = { code ->
                    val target = showCatchSheetForHider
                    if (target != null) {
                        onAttemptCatch(target.id, code)
                    }
                    showCatchSheetForHider = null
                }
            )
        }

        if (showLeaveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirmDialog = false },
                title = { Text("Leave match?") },
                text = { Text("You will leave the live hunt. You can rejoin with the room code if the match is still running.") },
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

        if (showReactionMenu) {
            ModalBottomSheet(onDismissRequest = { showReactionMenu = false }) {
                Column(Modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick reactions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    REACTION_OPTIONS.forEach { option ->
                        TextButton(onClick = {
                            sendSignal(option)
                            showReactionMenu = false
                        }) {
                            Text("${option.emoji}  ${option.text}")
                        }
                    }
                    Spacer(Modifier = Modifier.height(24.dp))
                }
            }
        }

        if (showQuickChatSheet) {
            ModalBottomSheet(onDismissRequest = { showQuickChatSheet = false }) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick chat", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    QUICK_CHAT_OPTIONS.forEach { option ->
                        TextButton(onClick = {
                            sendSignal(option)
                            showQuickChatSheet = false
                        }) {
                            Text("${option.emoji}  ${option.text}")
                        }
                    }
                    Spacer(Modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun HiderActionPanel(
    state: GameUiState,
    isCatchCodeRevealed: Boolean,
    onRevealToggle: () -> Unit,
    onActivateStealth: () -> Unit
) {
    val me = state.myPlayer
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AppleCardBackground.copy(alpha = 0.96f),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Your catch code", fontSize = 12.sp, color = AppleTextSecondary)
                    Text(
                        text = if (isCatchCodeRevealed) me.catchCode.ifBlank { "----" } else "••••",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = AppleTextPrimary
                    )
                }
                TextButton(onClick = onRevealToggle) {
                    Text(if (isCatchCodeRevealed) "Hide" else "Reveal")
                }
            }

            if (state.nearestSeekerDistance >= 0) {
                Text(
                    text = "Nearest seeker ~ ${state.nearestSeekerDistance.toInt()}m",
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
            }

            val stealthReady = !state.isStealthActive && state.stealthCooldownSeconds <= 0 && !me.isCaught && !state.isInJail
            ApplePrimaryButton(
                text = when {
                    state.isStealthActive -> "Stealth active"
                    state.stealthCooldownSeconds > 0 -> "Stealth cooldown ${state.stealthCooldownSeconds}s"
                    else -> "Activate Stealth"
                },
                enabled = stealthReady && state.session?.isPaused != true,
                backgroundColor = AppleHiderPurple,
                onClick = onActivateStealth
            )
        }
    }
}

@Composable
private fun GameMapLayer(
    state: GameUiState,
    headingFlow: StateFlow<Float>,
    modifier: Modifier = Modifier
) {
    val heading by headingFlow.collectAsState()
    val me = state.myPlayer
    val session = state.session
    val hasHeading = state.hasCompassHeading
    val players = state.players
    val distanceMeters = state.nearestSeekerDistance

    OSMMapView(
        modifier = modifier,
        myPlayer = me,
        players = players,
        session = session,
        headingDegrees = heading,
        distanceMeters = distanceMeters,
        hasHeading = hasHeading
    )
}
