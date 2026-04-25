package com.woutwerkman.pa.ui.control

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.components.DeltaTimerDisplay
import com.woutwerkman.pa.ui.components.TimerDisplay
import kotlin.math.roundToInt

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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text("Start Presentation", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (state.isActive) {
            Spacer(Modifier.height(32.dp))

            TimerDisplay(
                elapsedMs = state.elapsedMs,
                style = MaterialTheme.typography.displayMedium,
            )

            Spacer(Modifier.height(4.dp))

            DeltaTimerDisplay(
                deltaMs = state.remainingVsAverage,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(32.dp))

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

@Composable
private fun SwipeSlider(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
) {
    val threshold = 120f
    var offsetX by remember { mutableStateOf(0f) }
    var hasTriggered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .then(
                Modifier.draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX += delta
                        if (!hasTriggered) {
                            if (offsetX > threshold) {
                                hasTriggered = true
                                onSwipeRight()
                            } else if (offsetX < -threshold) {
                                hasTriggered = true
                                onSwipeLeft()
                            }
                        }
                    },
                    onDragStopped = {
                        offsetX = 0f
                        hasTriggered = false
                    },
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "◀ Back",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Next ▶",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) },
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "⟷",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
