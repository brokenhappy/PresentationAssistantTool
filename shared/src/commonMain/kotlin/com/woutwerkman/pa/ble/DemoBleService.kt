package com.woutwerkman.pa.ble

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.presentation.PresentationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DemoBleService(private val clock: Clock = Clock.System) : BleService {
    override val connectionState = MutableStateFlow(BleConnectionState.Connected)
    override val connectedPeers = MutableStateFlow(listOf(PairedPeer("demo", "Demo Desktop")))
    override val error: StateFlow<BleError?> = MutableStateFlow(null)

    private val _incomingMessages = MutableSharedFlow<BleMessage>(replay = 1)
    override val incomingMessages: Flow<BleMessage> = _incomingMessages

    private val profile = PresentationProfile(
        title = "Imprass Demo Presentation",
        bulletPoints = linkedMapOf(
            "welcome" to "Welcome and introduction",
            "what" to "What is Imprass?",
            "pairing" to "Pair your phone with the desktop app",
            "control" to "Control slides with the swipe gesture",
            "notes" to "View speaker notes during your talk",
            "timing" to "Per-bullet countdown keeps you on track",
            "stats" to "Review timing statistics after each run",
            "summary" to "Thank you!",
        ),
    )

    private val demoRuns = listOf(
        RunRecord(
            id = "run-1",
            timestamp = Instant.fromEpochMilliseconds(1714300000000),
            bulletPointDurations = profile.orderedKeys.associateWith { (25 + it.hashCode() % 15).seconds },
        ),
        RunRecord(
            id = "run-2",
            timestamp = Instant.fromEpochMilliseconds(1714400000000),
            bulletPointDurations = profile.orderedKeys.associateWith { (22 + it.hashCode() % 12).seconds },
        ),
    )

    private var state = PresentationState(
        profile = profile,
        runs = demoRuns,
        bulletAverages = profile.orderedKeys.associateWith { key ->
            demoRuns.map { it.bulletPointDurations[key] ?: 20.seconds }
                .fold(0.seconds) { a, b -> a + b } / demoRuns.size
        },
    )

    suspend fun emitInitialState() {
        _incomingMessages.emit(BleMessage.FullSync(state))
    }

    override suspend fun startAdvertisingOrScanning() {}
    override suspend fun stopAdvertisingOrScanning() {}
    override suspend fun disconnectPeer(id: String) {}
    override suspend fun getPersistedPeers(): List<PairedPeer> = emptyList()
    override suspend fun forgetPeer(id: String) {}

    override suspend fun sendMessage(message: BleMessage) {
        when (message) {
            is BleMessage.SyncRequest -> _incomingMessages.emit(BleMessage.FullSync(state))
            is BleMessage.Event -> {
                val now = clock.now()
                applyEvent(message.event, now)?.let {
                    state = it
                    _incomingMessages.emit(BleMessage.FullSync(state))
                }
            }
            else -> {}
        }
    }

    private fun applyEvent(event: com.woutwerkman.pa.presentation.PresentationEvent, now: Instant): PresentationState? {
        return when (event) {
            is com.woutwerkman.pa.presentation.PresentationEvent.Start -> state.copy(
                isActive = true,
                currentBulletIndex = 0,
                currentRunDurations = emptyMap(),
                presentationStartTime = now,
                bulletStartTime = now,
            )
            is com.woutwerkman.pa.presentation.PresentationEvent.Advance -> {
                if (!state.isActive) return null
                val key = state.currentBulletKey ?: return null
                val elapsed = state.currentBulletElapsed(now)
                val durations = state.currentRunDurations + (key to elapsed)
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
                        currentRunDurations = durations,
                        bulletStartTime = now,
                    )
                }
            }
            is com.woutwerkman.pa.presentation.PresentationEvent.GoBack -> {
                if (!state.isActive || state.currentBulletIndex <= 0) return null
                val key = state.currentBulletKey ?: return null
                val elapsed = state.currentBulletElapsed(now)
                val durations = state.currentRunDurations + (key to elapsed)
                state.copy(
                    currentBulletIndex = state.currentBulletIndex - 1,
                    currentRunDurations = durations,
                    bulletStartTime = now,
                )
            }
            is com.woutwerkman.pa.presentation.PresentationEvent.GoTo -> {
                val key = state.currentBulletKey
                val durations = if (key != null && state.isActive) {
                    state.currentRunDurations + (key to state.currentBulletElapsed(now))
                } else state.currentRunDurations
                state.copy(
                    currentBulletIndex = event.index,
                    currentRunDurations = durations,
                    bulletStartTime = now,
                )
            }
            else -> null
        }
    }
}
