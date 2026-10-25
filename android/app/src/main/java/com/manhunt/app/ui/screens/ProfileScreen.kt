package com.manhunt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.ui.components.ApplePrimaryButton
import com.manhunt.app.viewmodel.GameUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: GameUiState,
    onSaveProfile: (name: String, emoji: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var name by remember(state.myPlayer.id) { mutableStateOf(state.myPlayer.name) }
    var selectedEmoji by remember(state.myPlayer.id) { mutableStateOf(state.myPlayer.avatarEmoji) }
    val emojiChoices = listOf("🏃", "⚡", "🦅", "🐺", "🕶", "🕵️", "🥷", "🦊", "🐆", "🎯", "🔥", "🚀")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("You", fontWeight = FontWeight.Bold)
                        Text("Your field identity", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.surfaceVariant)
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selectedEmoji, fontSize = 34.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(name.ifBlank { "Field runner" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Text("ID " + state.myPlayer.id.take(8), fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Agent codename", fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        placeholder = { Text("Enter your name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outline
                        )
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Pick your signal", fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.height(154.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(emojiChoices) { emoji ->
                            val selected = selectedEmoji == emoji
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceVariant)
                                    .clickable { selectedEmoji = emoji }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }

            ApplePrimaryButton(
                text = "Save profile",
                enabled = name.isNotBlank(),
                onClick = { onSaveProfile(name.trim(), selectedEmoji) }
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}
