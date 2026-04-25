package com.woutwerkman.pa.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun TimerDisplay(
    elapsedMs: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = formatTimer(elapsedMs),
        style = style,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun DeltaTimerDisplay(
    deltaMs: Long?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    if (deltaMs == null) return
    val sign = if (deltaMs >= 0) "+" else "-"
    val absMs = if (deltaMs < 0) -deltaMs else deltaMs
    val color = if (deltaMs > 0) {
        Color(0xFFEF9A9A)
    } else {
        Color(0xFFA5D6A7)
    }
    Text(
        text = "$sign${formatTimer(absMs)}",
        style = style,
        color = color,
        modifier = modifier,
    )
}

fun formatTimer(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
