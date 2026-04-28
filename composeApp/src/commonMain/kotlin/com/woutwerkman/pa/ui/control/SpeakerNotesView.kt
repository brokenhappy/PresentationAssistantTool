package com.woutwerkman.pa.ui.control

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.LockLandscape
import com.woutwerkman.pa.ui.components.DeltaTimerDisplay
import com.woutwerkman.pa.ui.components.TimerDisplay

@Composable
fun SpeakerNotesView(
    state: PresentationState,
    onSwitchToControl: () -> Unit,
) {
    LockLandscape()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimerDisplay(
                elapsedMs = state.currentBulletElapsedMs,
                style = MaterialTheme.typography.headlineMedium,
            )
            DeltaTimerDisplay(
                deltaMs = state.globalScheduleDelta,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.height(16.dp))

        val prevText = if (state.currentBulletIndex > 0) {
            state.profile?.textAt(state.currentBulletIndex - 1)
        } else null

        if (prevText != null) {
            Text(
                text = prevText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = state.currentBulletText ?: "",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        val nextText = state.profile?.textAt(state.currentBulletIndex + 1)
        if (nextText != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = nextText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onSwitchToControl) {
            Text("Back to Controls")
        }
    }
}
