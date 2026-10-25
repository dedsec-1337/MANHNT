package com.manhunt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.manhunt.app.ui.components.RoleBadge
import com.manhunt.app.ui.theme.*
import com.manhunt.app.viewmodel.GameUiState

@Composable
fun ResultsScreen(
    state: GameUiState,
    onBackToHome: () -> Unit
) {
    val session = state.session ?: return
    val winner = session.winnerRole ?: Role.HIDER
    val isHiderWin = winner == Role.HIDER

    val winTitle = if (isHiderWin) "HIDERS ESCAPED!" else "SEEKERS TRIUMPH!"
    val winSubtitle = if (isHiderWin) {
        "Hiders successfully survived until the timer expired."
    } else {
        "All hiders were hunted down and tagged."
    }
    val winColor = if (isHiderWin) AppleHiderPurple else AppleSeekerOrange

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                // Victory Banner Card
                AppleCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(winColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isHiderWin) "🏆" else "🎯",
                                fontSize = 32.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = winTitle,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = AppleTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = winSubtitle,
                            fontSize = 13.sp,
                            color = AppleTextSecondary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Stats Summary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Duration",
                                    fontSize = 12.sp,
                                    color = AppleTextSecondary
                                )
                                Text(
                                    text = "${session.durationMinutes}m",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleTextPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Total Catches",
                                    fontSize = 12.sp,
                                    color = AppleTextSecondary
                                )
                                Text(
                                    text = "${session.totalCatches}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleTextPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Zone Radius",
                                    fontSize = 12.sp,
                                    color = AppleTextSecondary
                                )
                                Text(
                                    text = "${session.zoneRadiusMeters.toInt()}m",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Player List with Outcome
            item {
                Text(
                    text = "Player Standings",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
            }

            items(state.players) { player ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppleCardBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = player.avatarEmoji, fontSize = 22.sp)
                            Column {
                                Text(
                                    text = player.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppleTextPrimary
                                )
                                Text(
                                    text = if (player.isCaught) "Caught" else "Survived / Hunted",
                                    fontSize = 12.sp,
                                    color = if (player.isCaught) AppleDangerRed else AppleTextSecondary
                                )
                            }
                        }

                        RoleBadge(role = player.role)
                    }
                }
            }

            // Return Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                ApplePrimaryButton(
                    text = "Return to Lobby / Home",
                    onClick = onBackToHome
                )
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
