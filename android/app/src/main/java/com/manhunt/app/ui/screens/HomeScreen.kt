package com.manhunt.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.data.local.MatchHistoryEntity
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.components.AppleSecondaryButton
import com.manhunt.app.ui.components.MatchRecapSheet
import com.manhunt.app.ui.components.formatDuration
import com.manhunt.app.ui.components.formatMatchDate
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.ui.theme.MotionTokens
import com.manhunt.app.viewmodel.GameUiState

@Composable
fun HomeScreen(
    state: GameUiState,
    onCreateRoomClick: () -> Unit,
    onJoinRoomClick: (code: String) -> Unit,
    onEditProfileClick: () -> Unit
) {
    var inputCode by remember { mutableStateOf("") }
    var showRecap by remember { mutableStateOf(false) }
    var showHero by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { showHero = true }

    Scaffold(containerColor = colors.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "MANHNT",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = AppleTextPrimary
                        )
                        Text("QUIET FIELD GUIDE", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, color = AppleTextSecondary)
                    }
                    Surface(
                        onClick = onEditProfileClick,
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(state.myPlayer.avatarEmoji, fontSize = 20.sp)
                            Text(state.myPlayer.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                            Icon(Icons.Default.Person, contentDescription = "Open profile", tint = colors.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = showHero,
                    enter = fadeIn(tween(MotionTokens.CardEntranceMillis)) +
                        slideInVertically(tween(MotionTokens.CardEntranceMillis)) { it / 12 }
                ) {
                    Card(
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Explore, contentDescription = null, tint = colors.primary)
                                Text("BASECAMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = colors.primary)
                            }
                            Text(
                                "The city is your playground.",
                                fontSize = 30.sp,
                                lineHeight = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Text(
                                "Find your crew. Pick your side. Let the field tell the story.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = colors.onSurfaceVariant
                            )
                            ApplePrimaryButton(
                                text = if (state.isCreatingRoom) "Preparing your hunt…" else "Create a hunt",
                                onClick = onCreateRoomClick,
                                enabled = !state.isCreatingRoom
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Join a crew", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Text("Enter the room code from your organizer.", fontSize = 13.sp, color = colors.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputCode,
                                onValueChange = { raw ->
                                    inputCode = raw.filter { it.isLetterOrDigit() }.take(6).uppercase()
                                },
                                placeholder = { Text("ROOM CODE") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    keyboardType = KeyboardType.Ascii
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.primary,
                                    unfocusedBorderColor = colors.outline
                                )
                            )
                            Surface(
                                onClick = { if (inputCode.length >= 4 && !state.isJoiningRoom) onJoinRoomClick(inputCode) },
                                enabled = inputCode.length >= 4 && !state.isJoiningRoom,
                                shape = CircleShape,
                                color = if (inputCode.length >= 4 && !state.isJoiningRoom) colors.primary else colors.surfaceVariant
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Join hunt",
                                    modifier = Modifier.padding(15.dp),
                                    tint = if (inputCode.length >= 4 && !state.isJoiningRoom) colors.onPrimary else colors.onSurfaceVariant
                                )
                            }
                        }
                        if (state.isJoiningRoom) {
                            Text("Connecting to the field…", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                LastFieldRunCard(
                    match = state.latestMatch,
                    onViewRecap = { showRecap = true }
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showRecap) {
        state.latestMatch?.let { match ->
            MatchRecapSheet(match = match, onDismiss = { showRecap = false })
        }
    }
}

@Composable
private fun LastFieldRunCard(
    match: MatchHistoryEntity?,
    onViewRecap: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = colors.primary)
                Text("LAST FIELD RUN", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = colors.primary)
            }
            if (match == null) {
                Text("Your first field run is waiting.", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Text("Your recent result will live here.", fontSize = 13.sp, color = colors.onSurfaceVariant)
            } else {
                Text(
                    match.rolePlayed.uppercase() + " · " + if (match.isWin) "Win" else "Run complete",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(formatMatchDate(match.timestamp), fontSize = 12.sp, color = colors.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(formatDuration(match.durationSeconds), fontSize = 13.sp, color = colors.onSurfaceVariant)
                    Text(match.catchesCount.toString() + " catches", fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
                AppleSecondaryButton(text = "View recap", onClick = onViewRecap)
            }
        }
    }
}
