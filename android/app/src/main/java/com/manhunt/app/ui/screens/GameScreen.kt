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
        // Both roles get the live map. OSMMapView already suppresses other players' markers for
        // non-Seekers (and while Stealth is active), so a Hider sees only their own position and
        // the zone geometry. Previously Hiders rendered an empty Box, which is pure black in OLED.
        GameMapLayer(
            state = state,
            headingFlow = headingFlow,
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
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Text(text = signalOption.emoji, fontSize = 19.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = signalSenderName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppleTextSecondary
                                )
                                Text(
                                    text = signalOption.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleTextPrimary
                                )
                            }
                        }
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // A. If Hider: Display Rotary Compass pointing to nearest Seeker + Display Catch Code
            if (state.myPlayer.role == Role.HIDER) {
                GameCompassLayer(
                    headingFlow = headingFlow,
                    targetBearing = state.nearestSeekerBearing,
                    distanceMeters = state.nearestSeekerDistance,
                    hasHeading = state.hasCompassHeading
                )

                if (state.myPlayer.isCaught) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = AppleCardBackground,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You have been caught",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleDangerRed
                        )
                    }
                } else {
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

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isCatchCodeRevealed) state.myPlayer.catchCode.ifEmpty { "----" } else "••••",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    color = AppleTextPrimary
                                )
                                IconButton(
                                    onClick = { isCatchCodeRevealed = !isCatchCodeRevealed },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCatchCodeRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isCatchCodeRevealed) "Hide catch code" else "Reveal catch code",
                                        tint = AppleTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
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
                    backgroundColor = AppleHiderPurple,
                    onClick = onActivateStealth
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

            // B. If Seeker: Catch Button activates within 25 meters of a Hider!
            if (state.myPlayer.role == Role.SEEKER) {
                val catchable = state.nearbyCatchableHider
                val isTargetInSafeZone = catchable?.isInSafeZone == true

                val buttonText = when {
                    state.isSeekerFrozen -> "Frozen: Waiting for Head Start"
                    isTargetInSafeZone -> "Target in Safe Zone (${catchable?.safeZoneName ?: "Base"})"
                    catchable != null -> "⚡ Tag ${catchable.name}"
                    else -> "Tag a Hider"
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

            // Compact quick reactions and preset chat share the existing footer row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showLeaveConfirmDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Leave Chase",
                        fontSize = 13.sp,
                        color = AppleTextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        IconButton(
                            onClick = { showReactionMenu = true },
                            enabled = canSendSignals,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AppleCardBackground.copy(alpha = 0.96f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = "Send a reaction",
                                tint = AppleTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showReactionMenu,
                            onDismissRequest = { showReactionMenu = false }
                        ) {
                            REACTION_OPTIONS.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("${option.emoji}  ${option.text}") },
                                    onClick = {
                                        showReactionMenu = false
                                        sendSignal(option)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showQuickChatSheet = true },
                        enabled = canSendSignals,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppleCardBackground.copy(alpha = 0.96f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Open preset chat",
                            tint = AppleAccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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

        if (showQuickChatSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQuickChatSheet = false },
                containerColor = AppleCardBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Quick chat",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "Preset messages only — typing is off.",
                        fontSize = 13.sp,
                        color = AppleTextSecondary
                    )
                    QUICK_CHAT_OPTIONS.forEach { option ->
                        Button(
                            onClick = {
                                showQuickChatSheet = false
                                sendSignal(option)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppleFill,
                                contentColor = AppleTextPrimary
                            )
                        ) {
                            Text(
                                text = "${option.emoji}  ${option.text}",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = recentSignalFeedback != null,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 92.dp),
            enter = fadeIn(MotionTokens.smoothTween(MotionTokens.FeedbackMillis)) + slideInVertically(MotionTokens.CardOffset) { it / 3 },
            exit = fadeOut(MotionTokens.smoothTween(MotionTokens.FeedbackMillis)) + slideOutVertically(MotionTokens.CardOffset) { it / 3 }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp
            ) {
                Text(
                    text = recentSignalFeedback.orEmpty(),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
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

@Composable
private fun GameMapLayer(
    state: GameUiState,
    headingFlow: StateFlow<Float>,
    modifier: Modifier
) {
    val headingDegrees by headingFlow.collectAsState()
    val session = state.session ?: return
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
        headingDegrees = headingDegrees,
        hasHeading = state.hasCompassHeading,
        playerLocationUpdatedAtEpochMs = state.myPlayer.lastUpdatedEpochMs,
        modifier = modifier
    )
}

@Composable
private fun GameCompassLayer(
    headingFlow: StateFlow<Float>,
    targetBearing: Float,
    distanceMeters: Double,
    hasHeading: Boolean
) {
    val headingDegrees by headingFlow.collectAsState()
    CompassStrip(
        deviceHeading = headingDegrees,
        targetBearing = targetBearing,
        distanceMeters = distanceMeters,
        hasHeading = hasHeading
    )
}
