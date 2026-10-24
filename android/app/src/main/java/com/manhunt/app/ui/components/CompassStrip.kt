package com.manhunt.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
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
    modifier: Modifier = Modifier
) {
    // Relative rotation: how much to turn arrow relative to top of phone
    val relativeAngle = GeoUtils.normalizeAngleDiff(targetBearing - deviceHeading)
    val animatedRotation by animateFloatAsState(
        targetValue = relativeAngle,
        animationSpec = tween(durationMillis = 200),
        label = "compassRotation"
    )

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
                        tint = AppleSeekerOrange,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(animatedRotation)
                    )
                }

                Column {
                    Text(
                        text = "Nearest Seeker",
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
