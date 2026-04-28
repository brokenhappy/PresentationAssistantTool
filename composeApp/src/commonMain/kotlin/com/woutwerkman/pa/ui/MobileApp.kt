package com.woutwerkman.pa.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.woutwerkman.pa.ble.BleConnectionState
import com.woutwerkman.pa.ble.BleMessage
import com.woutwerkman.pa.ble.BleService
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.connection.MobileConnectionView
import com.woutwerkman.pa.ui.control.ControlView
import com.woutwerkman.pa.ui.control.SpeakerNotesView
import com.woutwerkman.pa.ui.expanded.ExpandedView
import com.woutwerkman.pa.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

enum class MobileScreen {
    Connection,
    Control,
    SpeakerNotes,
    Expanded,
}

@Composable
fun MobileApp(
    bleService: BleService,
    deviceId: String,
    onKeepAwakeChanged: (Boolean) -> Unit,
    onVibrate: (durationMs: Long) -> Unit = {},
) {
    val connectionState by bleService.connectionState.collectAsState()
    val connectedPeers by bleService.connectedPeers.collectAsState()
    val bleError by bleService.error.collectAsState()
    var currentScreen by remember { mutableStateOf(MobileScreen.Connection) }
    var state by remember { mutableStateOf(PresentationState()) }
    val scope = rememberCoroutineScope()

    // Absolute timestamps for local timer computation
    var presentationStartMs by remember { mutableLongStateOf(0L) }
    var bulletStartMs by remember { mutableLongStateOf(0L) }

    // Local timer: ticks when presentation is active
    LaunchedEffect(state.isActive) {
        if (state.isActive) {
            while (true) {
                delay(100.milliseconds)
                val now = currentTimeMs()
                state = state.copy(
                    elapsedMs = now - presentationStartMs,
                    currentBulletElapsedMs = now - bulletStartMs,
                )
            }
        }
    }

    // Listen for messages from desktop
    LaunchedEffect(bleService) {
        bleService.incomingMessages.collect { message ->
            when (message) {
                is BleMessage.FullSync -> {
                    val now = currentTimeMs()
                    presentationStartMs = now - message.state.elapsedMs
                    bulletStartMs = now - message.state.currentBulletElapsedMs
                    state = message.state
                }
                is BleMessage.Event -> {
                    val event = message.event
                    applyRemoteEvent(state, event)?.let { newState ->
                        if (event is PresentationEvent.Start) {
                            val now = currentTimeMs()
                            presentationStartMs = now
                            bulletStartMs = now
                        } else if (newState.currentBulletElapsedMs == 0L) {
                            bulletStartMs = currentTimeMs()
                        }
                        state = newState
                    }
                }
                is BleMessage.SyncRequest -> {}
                is BleMessage.Vibrate -> {
                    if (currentScreen != MobileScreen.SpeakerNotes) {
                        onVibrate(message.durationMs)
                    }
                }
            }
        }
    }

    // Auto-start BLE advertising
    LaunchedEffect(Unit) {
        bleService.startAdvertisingOrScanning()
    }

    // Navigate to control when connected, and request state from desktop
    LaunchedEffect(connectionState) {
        if (connectionState == BleConnectionState.Connected) {
            if (currentScreen == MobileScreen.Connection) {
                currentScreen = MobileScreen.Control
            }
            bleService.sendMessage(BleMessage.SyncRequest)
        }
        if (connectionState == BleConnectionState.Disconnected && currentScreen != MobileScreen.Connection) {
            currentScreen = MobileScreen.Connection
        }
    }

    val sendEvent: (PresentationEvent) -> Unit = { event ->
        scope.launch { bleService.sendMessage(BleMessage.Event(event)) }
    }

    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            PlatformBackHandler(
                enabled = currentScreen != MobileScreen.Connection && currentScreen != MobileScreen.Control,
            ) {
                if (currentScreen == MobileScreen.SpeakerNotes) {
                    onKeepAwakeChanged(false)
                }
                currentScreen = MobileScreen.Control
            }

            when (currentScreen) {
                MobileScreen.Connection -> MobileConnectionView(
                    connectionState = connectionState,
                    connectedDeviceName = connectedPeers.firstOrNull()?.name,
                    deviceId = deviceId,
                    bleError = bleError,
                )

                MobileScreen.Control -> ControlView(
                    state = state,
                    onEvent = sendEvent,
                    onSwitchToNotes = {
                        onKeepAwakeChanged(true)
                        currentScreen = MobileScreen.SpeakerNotes
                    },
                    onSwitchToExpanded = { currentScreen = MobileScreen.Expanded },
                )

                MobileScreen.SpeakerNotes -> SpeakerNotesView(
                    state = state,
                    onSwitchToControl = {
                        onKeepAwakeChanged(false)
                        currentScreen = MobileScreen.Control
                    },
                )

                MobileScreen.Expanded -> ExpandedView(
                    state = state,
                    onEvent = sendEvent,
                    onBack = { currentScreen = MobileScreen.Control },
                )
            }
        }
    }
}

private fun applyRemoteEvent(state: PresentationState, event: PresentationEvent): PresentationState? {
    return when (event) {
        is PresentationEvent.Start -> state.copy(
            isActive = true,
            currentBulletIndex = 0,
            elapsedMs = 0,
            currentBulletElapsedMs = 0,
            currentRunDurations = emptyMap(),
        )
        is PresentationEvent.Advance -> {
            if (!state.isActive) return null
            if (state.isLastBullet) {
                state.copy(
                    isActive = false,
                    currentBulletIndex = 0,
                    elapsedMs = 0,
                    currentBulletElapsedMs = 0,
                )
            } else {
                state.copy(
                    currentBulletIndex = state.currentBulletIndex + 1,
                    currentBulletElapsedMs = 0,
                )
            }
        }
        is PresentationEvent.GoBack -> {
            if (!state.isActive || state.currentBulletIndex <= 0) return null
            state.copy(
                currentBulletIndex = state.currentBulletIndex - 1,
                currentBulletElapsedMs = 0,
            )
        }
        is PresentationEvent.GoTo -> state.copy(
            currentBulletIndex = event.index,
            currentBulletElapsedMs = 0,
        )
        is PresentationEvent.CloseProfile -> PresentationState()
        is PresentationEvent.LoadProfile -> null
        is PresentationEvent.ToggleRunInclusion -> null
    }
}

private fun currentTimeMs(): Long = com.woutwerkman.pa.platform.currentTimeMs()
