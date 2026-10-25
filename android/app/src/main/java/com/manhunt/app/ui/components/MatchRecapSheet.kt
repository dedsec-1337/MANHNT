package com.manhunt.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhunt.app.data.local.MatchHistoryEntity
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchRecapSheet(
    match: MatchHistoryEntity,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("FIELD RUN RECAP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(match.rolePlayed.uppercase() + " FIELD RUN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(formatMatchDate(match.timestamp), fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecapStat(
                    label = "Result",
                    value = if (match.isWin) "Win" else "Run complete",
                    modifier = Modifier.weight(1f)
                )
                RecapStat(
                    label = "Duration",
                    value = formatDuration(match.durationSeconds),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecapStat(
                    label = "Catches",
                    value = match.catchesCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    modifier = Modifier.weight(1f),
                    color = colors.surfaceVariant,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = if (match.isWin) "A good run in the field." else "Every chase leaves a story.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        }
    }
}

fun formatMatchDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

fun formatDuration(durationSeconds: Int): String {
    val safeDuration = durationSeconds.coerceAtLeast(0)
    val hours = safeDuration / 3600
    val minutes = (safeDuration % 3600) / 60
    val seconds = safeDuration % 60
    return if (hours > 0) {
        hours.toString() + "h " + minutes.toString().padStart(2, '0') + "m"
    } else {
        minutes.toString() + "m " + seconds.toString().padStart(2, '0') + "s"
    }
}
