package com.woutwerkman.pa.ui.minified

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.components.DeltaTimerDisplay
import com.woutwerkman.pa.ui.components.TimerDisplay

@Composable
fun MinifiedView(
    state: PresentationState,
    onEvent: (PresentationEvent) -> Unit,
    onExpand: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when {
            state.profile == null -> EmptyState()
            state.isActive -> ActiveState(state, onEvent, onExpand, onHide)
            else -> IdleState(state, onEvent, onExpand, onHide)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Drop a .json presentation file here",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IdleState(
    state: PresentationState,
    onEvent: (PresentationEvent) -> Unit,
    onExpand: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.profile?.textAt(0) ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { onEvent(PresentationEvent.Start) }) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = MaterialTheme.colorScheme.onSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = onExpand) {
            Text("⤢", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onHide) {
            Text("✕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActiveState(
    state: PresentationState,
    onEvent: (PresentationEvent) -> Unit,
    onExpand: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimerDisplay(
            elapsedMs = state.elapsedMs,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(6.dp))
        DeltaTimerDisplay(
            deltaMs = state.remainingVsAverage,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = state.currentBulletText ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onExpand) {
            Text("⤢", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onHide) {
            Text("✕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
