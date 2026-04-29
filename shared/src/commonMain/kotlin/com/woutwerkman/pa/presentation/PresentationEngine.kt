package com.woutwerkman.pa.presentation

import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PresentationEngine(
    private val repository: ProfileRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { com.woutwerkman.pa.platform.currentTimeMs() },
    private val idGenerator: () -> String = { defaultGenerateRunId() },
) {
    private val _state = MutableStateFlow(PresentationState())
    val state: StateFlow<PresentationState> = _state.asStateFlow()

    private val _appliedEvents = MutableSharedFlow<PresentationEvent>()
    val appliedEvents: SharedFlow<PresentationEvent> = _appliedEvents.asSharedFlow()

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()

    private var timerJob: Job? = null
    private var bulletStartTime: Long = 0L
    private var presentationStartTime: Long = 0L
    private val _profilePath = MutableStateFlow<String?>(null)
    val profilePath: StateFlow<String?> = _profilePath.asStateFlow()

    fun onEvent(event: PresentationEvent) {
        when (event) {
            is PresentationEvent.LoadProfile -> loadProfile(event.path)
            is PresentationEvent.CloseProfile -> closeProfile()
            is PresentationEvent.Start -> startPresentation()
            is PresentationEvent.Advance -> advanceBulletPoint()
            is PresentationEvent.GoBack -> goBack()
            is PresentationEvent.GoTo -> goToBulletPoint(event.index)
            is PresentationEvent.ToggleRunInclusion -> toggleRunInclusion(event.runId)
        }
    }

    private fun loadProfile(filePath: String) {
        scope.launch {
            try {
                val data = repository.loadOrCreateProfileData(filePath)
                _profilePath.value = filePath
                _state.update {
                    PresentationState(
                        profile = data.profile,
                        runs = data.runs,
                    )
                }
                _appliedEvents.emit(PresentationEvent.LoadProfile(filePath))
            } catch (e: Exception) {
                _error.emit("Failed to load profile: ${e.message}")
            }
        }
    }

    private fun closeProfile() {
        timerJob?.cancel()
        timerJob = null
        _profilePath.value = null
        _state.value = PresentationState()
        scope.launch { _appliedEvents.emit(PresentationEvent.CloseProfile) }
    }

    private fun startPresentation() {
        val current = _state.value
        if (current.profile == null || current.isActive) return

        presentationStartTime = clock()
        bulletStartTime = presentationStartTime

        _state.update {
            it.copy(
                isActive = true,
                currentBulletIndex = 0,
                elapsed = Duration.ZERO,
                currentBulletElapsed = Duration.ZERO,
                currentRunDurations = emptyMap(),
            )
        }

        startTimer()
        scope.launch { _appliedEvents.emit(PresentationEvent.Start) }
    }

    private fun advanceBulletPoint() {
        val current = _state.value
        if (!current.isActive) {
            startPresentation()
            return
        }

        val key = current.currentBulletKey ?: return
        val visitDuration = (clock() - bulletStartTime).milliseconds
        val accumulated = (current.currentRunDurations[key] ?: Duration.ZERO) + visitDuration
        val updatedDurations = current.currentRunDurations + (key to accumulated)

        if (current.isLastBullet) {
            finishPresentation(updatedDurations)
            return
        }

        val nextIndex = current.currentBulletIndex + 1
        val nextKey = current.profile?.keyAt(nextIndex)
        val nextAccumulated = if (nextKey != null) updatedDurations[nextKey] ?: Duration.ZERO else Duration.ZERO

        bulletStartTime = clock()
        _state.update {
            it.copy(
                currentBulletIndex = nextIndex,
                currentBulletElapsed = nextAccumulated,
                currentRunDurations = updatedDurations,
            )
        }
        scope.launch { _appliedEvents.emit(PresentationEvent.Advance) }
    }

    private fun goToBulletPoint(index: Int) {
        val current = _state.value
        if (!current.isActive || index < 0 || index >= current.bulletCount) return

        val key = current.currentBulletKey
        val visitDuration = (clock() - bulletStartTime).milliseconds
        val updatedDurations = if (key != null) {
            val accumulated = (current.currentRunDurations[key] ?: Duration.ZERO) + visitDuration
            current.currentRunDurations + (key to accumulated)
        } else {
            current.currentRunDurations
        }

        val targetKey = current.profile?.keyAt(index)
        val targetAccumulated = if (targetKey != null) updatedDurations[targetKey] ?: Duration.ZERO else Duration.ZERO

        bulletStartTime = clock()
        _state.update {
            it.copy(
                currentBulletIndex = index,
                currentBulletElapsed = targetAccumulated,
                currentRunDurations = updatedDurations,
            )
        }
        scope.launch { _appliedEvents.emit(PresentationEvent.GoTo(index)) }
    }

    private fun goBack() {
        val current = _state.value
        if (!current.isActive || current.currentBulletIndex <= 0) return
        goToBulletPoint(current.currentBulletIndex - 1)
    }

    private fun toggleRunInclusion(runId: String) {
        scope.launch {
            val current = _state.value
            val profile = current.profile ?: return@launch
            val updatedRuns = current.runs.map { run ->
                if (run.id == runId) run.copy(isIncludedInStats = !run.isIncludedInStats)
                else run
            }
            _state.update { it.copy(runs = updatedRuns) }
            try {
                repository.saveProfileData(
                    com.woutwerkman.pa.model.ProfileData(profile = profile, runs = updatedRuns)
                )
            } catch (e: Exception) {
                _error.emit("Failed to save run data: ${e.message}")
            }
            _appliedEvents.emit(PresentationEvent.ToggleRunInclusion(runId))
        }
    }

    private fun finishPresentation(durations: Map<String, Duration>) {
        timerJob?.cancel()
        timerJob = null

        scope.launch {
            val current = _state.value
            val profile = current.profile ?: return@launch

            val run = RunRecord(
                id = idGenerator(),
                timestamp = clock(),
                bulletPointDurations = durations,
            )
            val updatedRuns = current.runs + run
            try {
                repository.saveProfileData(
                    com.woutwerkman.pa.model.ProfileData(profile = profile, runs = updatedRuns)
                )
            } catch (e: Exception) {
                _error.emit("Failed to save run data: ${e.message}")
            }

            _state.update {
                it.copy(
                    isActive = false,
                    runs = updatedRuns,
                    currentBulletIndex = 0,
                    elapsed = Duration.ZERO,
                    currentBulletElapsed = Duration.ZERO,
                    currentRunDurations = emptyMap(),
                )
            }
            _appliedEvents.emit(PresentationEvent.Advance)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(100.milliseconds)
                val now = clock()
                _state.update {
                    val currentKey = it.currentBulletKey
                    val accumulated = if (currentKey != null) it.currentRunDurations[currentKey] ?: Duration.ZERO else Duration.ZERO
                    it.copy(
                        elapsed = (now - presentationStartTime).milliseconds,
                        currentBulletElapsed = accumulated + (now - bulletStartTime).milliseconds,
                    )
                }
            }
        }
    }

}

private fun defaultGenerateRunId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..12).map { chars.random() }.joinToString("")
}
