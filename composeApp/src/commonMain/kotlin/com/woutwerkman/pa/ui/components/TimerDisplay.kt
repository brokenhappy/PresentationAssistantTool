package com.woutwerkman.pa.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlin.time.Duration

@Composable
fun TimerDisplay(
    elapsed: Duration,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = formatTimer(elapsed),
        style = style,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun DeltaTimerDisplay(
    delta: Duration?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    if (delta == null) return
    val isNegative = delta.isNegative()
    val sign = if (isNegative) "-" else "+"
    val abs = delta.absoluteValue
    val color = if (!isNegative) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.secondary
    }
    Text(
        text = "$sign${formatTimer(abs)}",
        style = style,
        color = color,
        modifier = modifier,
    )
}

fun formatTimer(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun formatTimestamp(epochMs: Long): String {
    val totalSeconds = epochMs / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}
