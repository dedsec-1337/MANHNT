package com.manhunt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.ui.components.AppleCard
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.ui.theme.AppleAccentBlue
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.viewmodel.GameUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: GameUiState,
    onSaveProfile: (name: String, emoji: String) -> Unit,
    onClose: () -> Unit
) {
    var name by remember { mutableStateOf(state.myPlayer.name) }
    var selectedEmoji by remember { mutableStateOf(state.myPlayer.avatarEmoji) }

    val emojiChoices = listOf("🏃", "⚡", "🦅", "🐺", "🕶", "🕵️", "🥷", "🦊", "🐆", "🎯", "🔥", "🚀")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player Profile", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                            .background(Color(0xFFF2F2F7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = selectedEmoji, fontSize = 36.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Device ID: ${state.myPlayer.id.take(8)}...",
                        fontSize = 11.sp,
                        color = AppleTextSecondary
                    )
                }
            }

            // Display Name Input
            AppleCard {
                Text(
                    text = "Display Name",
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
                        unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedContainerColor = Color(0xFFF8F9FA),
                        unfocusedContainerColor = Color(0xFFF8F9FA)
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
                    modifier = Modifier.height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(emojiChoices) { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AppleAccentBlue.copy(alpha = 0.2f) else Color(0xFFF2F2F7))
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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
