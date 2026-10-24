package com.manhunt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.ui.components.AppleCard
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.components.AppleSecondaryButton
import com.manhunt.app.ui.theme.AppleAccentBlue
import com.manhunt.app.ui.theme.AppleHiderGreen
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.util.BatteryOptimizationHelper
import com.manhunt.app.viewmodel.GameUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: GameUiState,
    onSaveProfile: (name: String, emoji: String) -> Unit,
    onToggleDarkMode: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(state.myPlayer.name) }
    var selectedEmoji by remember { mutableStateOf(state.myPlayer.avatarEmoji) }

    val emojiChoices = listOf("🏃", "⚡", "🦅", "🐺", "🕶", "🕵️", "🥷", "🦊", "🐆", "🎯", "🔥", "🚀")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Profile", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Avatar Preview Card
            AppleCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = selectedEmoji, fontSize = 36.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Device UUID: ${state.myPlayer.id.take(8)}...",
                        fontSize = 11.sp,
                        color = AppleTextSecondary
                    )
                }
            }

            // Dark Mode Theme Toggle Card
            AppleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = AppleAccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "OLED Dark Mode",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "High contrast night palette for stealth",
                                fontSize = 12.sp,
                                color = AppleTextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = state.isDarkMode,
                        onCheckedChange = { onToggleDarkMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleAccentBlue
                        )
                    )
                }
            }

            // Battery Optimization Whitelist Card (Critical for Background GPS)
            AppleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = AppleHiderGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Background GPS Protection",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "Prevent OS from killing GPS tracking",
                                fontSize = 12.sp,
                                color = AppleTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Android and aggressive task killers (Xiaomi MIUI, Samsung OneUI) sleep apps with screen off. Whitelist MANHNT to guarantee uninterrupted tracking.",
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                AppleSecondaryButton(
                    text = "Open Battery Whitelist Settings",
                    onClick = {
                        try {
                            val intent = BatteryOptimizationHelper.createOptimizationExemptionIntent(context)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            BatteryOptimizationHelper.openOemBackgroundSettings(context)
                        }
                    }
                )
            }

            // Display Name Input
            AppleCard {
                Text(
                    text = "Agent Codename",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Enter your name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppleAccentBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // Emoji Grid
            AppleCard {
                Text(
                    text = "Pick Avatar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(emojiChoices) { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AppleAccentBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            ApplePrimaryButton(
                text = "Save Profile",
                enabled = name.isNotBlank(),
                onClick = {
                    onSaveProfile(name.trim(), selectedEmoji)
                    onClose()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
