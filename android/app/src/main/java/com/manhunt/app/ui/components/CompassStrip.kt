package com.manhunt.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.ui.theme.AppleDangerRed
import com.manhunt.app.ui.theme.AppleSeekerOrange
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.util.GeoUtils
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassStrip(
    deviceHeading: Float,
    targetBearing: Float,
    distanceMeters: Double,
    hasHeading: Boolean = true,
    modifier: Modifier = Modifier
) {
    val targetAngle = if (hasHeading && distanceMeters > 0.0) {
        GeoUtils.normalizeAngleDiff(targetBearing - deviceHeading)
    } else {
        0f
    }
    val relativeAngle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nearestHunterCompass"
    )
    val colors = MaterialTheme.colorScheme
    val proximityColor = when {
        distanceMeters in 1.0..30.0 -> AppleDangerRed
        distanceMeters > 0.0 -> AppleSeekerOrange
        else -> colors.onSurfaceVariant
    }
    val proximityLabel = when {
        distanceMeters <= 0.0 -> "WAIT"
        distanceMeters < 15.0 -> "NEAR"
        distanceMeters < 50.0 -> "CLOSE"
        else -> "CLEAR"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val radius = minOf(centerX, centerY) - 1.dp.toPx()
                    drawCircle(
                        color = colors.outline.copy(alpha = 0.7f),
                        radius = radius,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    repeat(32) { index ->
                        val angle = Math.toRadians(index * 11.25 - 90.0)
                        val majorTick = index % 8 == 0
                        val innerRadius = radius * if (majorTick) 0.73f else 0.82f
                        val outerRadius = radius * 0.92f
                        drawLine(
                            color = colors.onSurfaceVariant.copy(alpha = if (majorTick) 0.9f else 0.55f),
                            start = androidx.compose.ui.geometry.Offset(
                                centerX + cos(angle).toFloat() * innerRadius,
                                centerY + sin(angle).toFloat() * innerRadius
                            ),
                            end = androidx.compose.ui.geometry.Offset(
                                centerX + cos(angle).toFloat() * outerRadius,
                                centerY + sin(angle).toFloat() * outerRadius
                            ),
                            strokeWidth = if (majorTick) 1.6.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                Text(
                    "N",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant
                )
                Text(
                    "S",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant
                )
                Text(
                    "W",
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant
                )
                Text(
                    "E",
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = if (hasHeading && distanceMeters > 0.0) "Direction to nearest Hunter" else "Hunter direction unavailable",
                    tint = if (hasHeading && distanceMeters > 0.0) proximityColor else colors.onSurfaceVariant,
                    modifier = Modifier.size(23.dp).rotate(relativeAngle)
                )
            }

            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "NEAREST HUNTER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = colors.onSurfaceVariant
                )
                Text(
                    if (distanceMeters > 0.0) distanceMeters.toInt().toString() + "m away" else "Waiting for live location",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
                if (!hasHeading) {
                    Text("Compass unavailable", fontSize = 11.sp, color = AppleTextSecondary)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = proximityColor.copy(alpha = 0.13f)
            ) {
                Text(
                    proximityLabel,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = proximityColor
                )
            }
        }
    }
}
