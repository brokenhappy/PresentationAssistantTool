package com.woutwerkman.pa.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Composable
fun TimerDisplay(
    elapsed: Duration,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = formatTimer(elapsed),
        style = style.copy(fontFamily = FontFamily.Monospace),
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
        style = style.copy(fontFamily = FontFamily.Monospace),
        color = color,
        modifier = modifier,
    )
}

fun formatTimer(duration: Duration): String {
    val negative = duration.isNegative()
    val abs = if (negative) duration.absoluteValue else duration
    val totalSeconds = abs.inWholeSeconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val formatted = "$minutes:${seconds.toString().padStart(2, '0')}"
    return if (negative) "-$formatted" else formatted
}

fun formatTimestamp(instant: Instant): String {
    val epochMs = instant.toEpochMilliseconds()
    val totalSeconds = epochMs / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

@Composable
fun rememberNow(ticking: Boolean): Instant {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(ticking) {
        while (ticking) {
            now = Clock.System.now()
            delay(100.milliseconds)
        }
    }
    return now
}
