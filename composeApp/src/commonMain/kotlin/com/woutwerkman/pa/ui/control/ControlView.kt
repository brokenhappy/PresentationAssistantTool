package com.woutwerkman.pa.ui.control

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.components.DeltaTimerDisplay
import com.woutwerkman.pa.ui.components.TimerDisplay
import com.woutwerkman.pa.ui.components.rememberNow

@Composable
fun ControlView(
    state: PresentationState,
    onEvent: (PresentationEvent) -> Unit,
    onSwitchToNotes: () -> Unit,
    onSwitchToExpanded: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!state.isActive && state.profile != null) {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onEvent(PresentationEvent.Start) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text("Start Presentation", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (state.isActive) {
            val now = rememberNow(ticking = true)
            Spacer(Modifier.height(32.dp))

            val countdown = state.bulletCountdown(now)
            TimerDisplay(
                elapsed = countdown ?: state.currentBulletElapsed(now),
                style = MaterialTheme.typography.displayMedium,
                color = if (countdown != null && countdown.isNegative()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            DeltaTimerDisplay(
                delta = state.globalScheduleDelta(now),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${state.currentBulletIndex + 1} / ${state.bulletCount}",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = state.currentBulletText ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Spacer(Modifier.weight(1f))

            LinearProgressIndicator(
                progress = { (state.currentBulletIndex + 1).toFloat() / state.bulletCount },
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            SwipeSlider(
                onSwipeRight = { onEvent(PresentationEvent.Advance) },
                onSwipeLeft = { onEvent(PresentationEvent.GoBack) },
            )

            Spacer(Modifier.height(24.dp))
        }

        if (!state.isActive) {
            Spacer(Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onSwitchToNotes) {
                Text("Speaker Notes")
            }
            TextButton(onClick = onSwitchToExpanded) {
                Text("Expanded View")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
