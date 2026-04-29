package com.woutwerkman.pa.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.woutwerkman.pa.ble.BleConnectionState
import com.woutwerkman.pa.ble.BleMessage
import com.woutwerkman.pa.ble.BleService
import com.woutwerkman.pa.ble.DemoBleService
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.ui.connection.MobileConnectionView
import com.woutwerkman.pa.ui.control.ControlView
import com.woutwerkman.pa.ui.control.SpeakerNotesView
import com.woutwerkman.pa.ui.expanded.ExpandedView
import com.woutwerkman.pa.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

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
    onVibrate: (Duration) -> Unit = {},
    clock: Clock = Clock.System,
) {
    var activeBleService by remember { mutableStateOf(bleService) }
    var isDemo by remember { mutableStateOf(false) }

    val connectionState by activeBleService.connectionState.collectAsState()
    val connectedPeers by activeBleService.connectedPeers.collectAsState()
    val bleError by activeBleService.error.collectAsState()
    var currentScreen by remember { mutableStateOf(MobileScreen.Connection) }
    var state by remember { mutableStateOf(PresentationState()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeBleService) {
        activeBleService.incomingMessages.collect { message ->
            when (message) {
                is BleMessage.FullSync -> {
                    val now = clock.now()
                    val received = message.state
                    state = if (received.isActive) received.copy(
                        presentationStartTime = now - received.presentationStartTime.toEpochMilliseconds().milliseconds,
                        bulletStartTime = now - received.bulletStartTime.toEpochMilliseconds().milliseconds,
                    ) else received
                }
                is BleMessage.Event -> {
                    val now = clock.now()
                    applyRemoteEvent(state, message.event, now)?.let { state = it }
                }
                is BleMessage.SyncRequest -> {}
                is BleMessage.Vibrate -> {
                    if (currentScreen != MobileScreen.SpeakerNotes) {
                        onVibrate(message.duration)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        bleService.startAdvertisingOrScanning()
    }

    LaunchedEffect(connectionState) {
        if (connectionState == BleConnectionState.Connected) {
            if (currentScreen == MobileScreen.Connection) {
                currentScreen = MobileScreen.Control
            }
            activeBleService.sendMessage(BleMessage.SyncRequest)
        }
        if (connectionState == BleConnectionState.Disconnected && currentScreen != MobileScreen.Connection) {
            currentScreen = MobileScreen.Connection
        }
    }

    val sendEvent: (PresentationEvent) -> Unit = { event ->
        scope.launch { activeBleService.sendMessage(BleMessage.Event(event)) }
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
                    onEnterDemo = {
                        val demo = DemoBleService(clock)
                        activeBleService = demo
                        isDemo = true
                        currentScreen = MobileScreen.Control
                        scope.launch { demo.emitInitialState() }
                    },
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

private fun applyRemoteEvent(state: PresentationState, event: PresentationEvent, now: Instant): PresentationState? {
    return when (event) {
        is PresentationEvent.Start -> state.copy(
            isActive = true,
            currentBulletIndex = 0,
            currentRunDurations = emptyMap(),
            presentationStartTime = now,
            bulletStartTime = now,
        )
        is PresentationEvent.Advance -> {
            if (!state.isActive) return null
            val updatedDurations = saveBulletDuration(state, now)
            if (state.isLastBullet) {
                state.copy(
                    isActive = false,
                    currentBulletIndex = 0,
                    currentRunDurations = emptyMap(),
                    presentationStartTime = Instant.fromEpochMilliseconds(0),
                    bulletStartTime = Instant.fromEpochMilliseconds(0),
                )
            } else {
                state.copy(
                    currentBulletIndex = state.currentBulletIndex + 1,
                    currentRunDurations = updatedDurations,
                    bulletStartTime = now,
                )
            }
        }
        is PresentationEvent.GoBack -> {
            if (!state.isActive || state.currentBulletIndex <= 0) return null
            val updatedDurations = saveBulletDuration(state, now)
            state.copy(
                currentBulletIndex = state.currentBulletIndex - 1,
                currentRunDurations = updatedDurations,
                bulletStartTime = now,
            )
        }
        is PresentationEvent.GoTo -> {
            val updatedDurations = saveBulletDuration(state, now)
            state.copy(
                currentBulletIndex = event.index,
                currentRunDurations = updatedDurations,
                bulletStartTime = now,
            )
        }
        is PresentationEvent.CloseProfile -> PresentationState()
        is PresentationEvent.LoadProfile -> null
        is PresentationEvent.ToggleRunInclusion -> null
    }
}

private fun saveBulletDuration(state: PresentationState, now: Instant): Map<String, Duration> {
    val key = state.currentBulletKey ?: return state.currentRunDurations
    return state.currentRunDurations + (key to state.currentBulletElapsed(now))
}
