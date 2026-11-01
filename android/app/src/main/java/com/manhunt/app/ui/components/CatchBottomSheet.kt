package com.manhunt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.manhunt.app.data.model.Player
import com.manhunt.app.ui.theme.AppleAccentBlue
import com.manhunt.app.ui.theme.AppleCardBackground
import com.manhunt.app.ui.theme.AppleSeekerOrange
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchBottomSheet(
    targetHider: Player,
    candidates: List<Player> = listOf(targetHider),
    onDismiss: () -> Unit,
    onSubmitCatchCode: (hiderId: String, code: String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Every Hider has their own code. With more than one Hider in play the seeker has to say which
    // one they tagged, otherwise a correct code for Hider B is checked against Hider A and fails.
    var selectedId by remember(targetHider.id) { mutableStateOf(targetHider.id) }
    val selected = candidates.firstOrNull { it.id == selectedId } ?: targetHider

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppleCardBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Catch ${selected.name}!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ask ${selected.name} for the 4-digit code shown on their screen.",
                fontSize = 14.sp,
                color = AppleTextSecondary,
                textAlign = TextAlign.Center
            )

            if (candidates.size > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Who did you tag?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    candidates.forEach { candidate ->
                        val isSelected = candidate.id == selected.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedId = candidate.id
                                    code = ""
                                    errorMessage = null
                                },
                            color = if (isSelected) AppleSeekerOrange.copy(alpha = 0.22f) else AppleFill,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${candidate.avatarEmoji}  ${candidate.name}",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = AppleTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Code Display (4 Digit Boxes)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (i in 0 until 4) {
                    val digit = code.getOrNull(i)?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppleFill),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleTextPrimary
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage!!,
                    fontSize = 13.sp,
                    color = Color(0xFFFF3B30),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Custom Apple-style Number Pad
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val padRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫")
                )

                for (row in padRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (key in row) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        errorMessage = null
                                        when (key) {
                                            "C" -> code = ""
                                            "⌫" -> if (code.isNotEmpty()) code = code.dropLast(1)
                                            else -> if (code.length < 4) code += key
                                        }
                                    },
                                color = AppleFill,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppleTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Catch Code Button
            ApplePrimaryButton(
                text = "Submit Catch Code",
                enabled = code.length == 4,
                backgroundColor = AppleSeekerOrange,
                onClick = {
                    if (code.length == 4) {
                        onSubmitCatchCode(selected.id, code)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
