package com.woutwerkman.pa.ui.minified

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.components.DeltaTimerDisplay
import com.woutwerkman.pa.ui.components.TimerDisplay
import com.woutwerkman.pa.ui.components.rememberNow

@Composable
fun MinifiedView(
    state: PresentationState,
    onEvent: (PresentationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.profile == null -> EmptyState()
                    state.isActive -> ActiveState(state)
                    else -> IdleState(state, onEvent)
                }
            }
            if (state.isActive && state.bulletCount > 0) {
                LinearProgressIndicator(
                    progress = { (state.currentBulletIndex + 1).toFloat() / state.bulletCount },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Drop a .json presentation file here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IdleState(
    state: PresentationState,
    onEvent: (PresentationEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.profile?.title ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = { onEvent(PresentationEvent.Start) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp),
        ) {
            Text("Start", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ActiveState(state: PresentationState) {
    val now = rememberNow(ticking = true)
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val countdown = state.bulletCountdown(now)
        TimerDisplay(
            elapsed = countdown ?: state.currentBulletElapsed(now),
            style = MaterialTheme.typography.titleMedium,
            color = if (countdown != null && countdown.isNegative()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        DeltaTimerDisplay(
            delta = state.globalScheduleDelta(now),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = state.currentBulletText ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
