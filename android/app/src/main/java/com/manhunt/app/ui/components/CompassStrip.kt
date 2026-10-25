package com.manhunt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.ui.theme.AppleAccentBlue
import com.manhunt.app.ui.theme.AppleSeekerOrange
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.util.GeoUtils
import com.manhunt.app.ui.theme.*

@Composable
fun CompassStrip(
    deviceHeading: Float,
    targetBearing: Float,
    distanceMeters: Double,
    hasHeading: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Sensor data is already smoothed; avoid a second animation that makes the arrow lag.
    val relativeAngle = if (hasHeading) {
        GeoUtils.normalizeAngleDiff(targetBearing - deviceHeading)
    } else {
        0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x1F000000)),
        shape = RoundedCornerShape(20.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Direction Arrow in soft rounded circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF7ED)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Seeker Direction",
                        tint = if (hasHeading) AppleSeekerOrange else AppleTextSecondary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(relativeAngle)
                    )
                }

                Column {
                    Text(
                        text = if (hasHeading) "Nearest Seeker" else "Compass unavailable",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleTextSecondary
                    )
                    Text(
                        text = if (distanceMeters > 0) "${distanceMeters.toInt()}m away" else "Scanning...",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )
                }
            }

            // Proximity indicator pill
            val isDanger = distanceMeters in 1.0..30.0
            val pillBg = if (isDanger) AppleDangerRed.copy(alpha = 0.18f) else AppleFill
            val pillText = if (isDanger) Color(0xFFFF3B30) else AppleTextSecondary
            val pillLabel = when {
                !hasHeading -> "NO COMPASS"
                distanceMeters <= 0.0 -> "Waiting"
                distanceMeters < 15.0 -> "CRITICAL"
                distanceMeters < 50.0 -> "CLOSE"
                else -> "SAFE"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(pillBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = pillLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = pillText
                )
            }
        }
    }
}
