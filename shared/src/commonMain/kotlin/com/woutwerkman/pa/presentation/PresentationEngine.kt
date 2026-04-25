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
import kotlin.time.Duration.Companion.milliseconds

class PresentationEngine(
    private val repository: ProfileRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PresentationState())
    val state: StateFlow<PresentationState> = _state.asStateFlow()

    private val _appliedEvents = MutableSharedFlow<PresentationEvent>()
    val appliedEvents: SharedFlow<PresentationEvent> = _appliedEvents.asSharedFlow()

    private var timerJob: Job? = null
    private var bulletStartTime: Long = 0L
    private var presentationStartTime: Long = 0L
    var profilePath: String? = null
        private set

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
            val data = repository.loadOrCreateProfileData(filePath)
            profilePath = filePath
            _state.update {
                PresentationState(
                    profile = data.profile,
                    runs = data.runs,
                )
            }
            _appliedEvents.emit(PresentationEvent.LoadProfile(filePath))
        }
    }

    private fun closeProfile() {
        timerJob?.cancel()
        timerJob = null
        profilePath = null
        _state.value = PresentationState()
        scope.launch { _appliedEvents.emit(PresentationEvent.CloseProfile) }
    }

    private fun startPresentation() {
        val current = _state.value
        if (current.profile == null || current.isActive) return

        presentationStartTime = currentTimeMs()
        bulletStartTime = presentationStartTime

        _state.update {
            it.copy(
                isActive = true,
                currentBulletIndex = 0,
                elapsedMs = 0,
                currentBulletElapsedMs = 0,
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
        val bulletDuration = currentTimeMs() - bulletStartTime
        val updatedDurations = current.currentRunDurations + (key to bulletDuration)

        if (current.isLastBullet) {
            finishPresentation(updatedDurations)
            return
        }

        bulletStartTime = currentTimeMs()
        _state.update {
            it.copy(
                currentBulletIndex = it.currentBulletIndex + 1,
                currentBulletElapsedMs = 0,
                currentRunDurations = updatedDurations,
            )
        }
        scope.launch { _appliedEvents.emit(PresentationEvent.Advance) }
    }

    private fun goToBulletPoint(index: Int) {
        val current = _state.value
        if (!current.isActive || index < 0 || index >= current.bulletCount) return

        val key = current.currentBulletKey
        val bulletDuration = currentTimeMs() - bulletStartTime
        val updatedDurations = if (key != null) {
            current.currentRunDurations + (key to bulletDuration)
        } else {
            current.currentRunDurations
        }

        bulletStartTime = currentTimeMs()
        _state.update {
            it.copy(
                currentBulletIndex = index,
                currentBulletElapsedMs = 0,
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
            repository.saveProfileData(
                com.woutwerkman.pa.model.ProfileData(profile = profile, runs = updatedRuns)
            )
            _appliedEvents.emit(PresentationEvent.ToggleRunInclusion(runId))
        }
    }

    private fun finishPresentation(durations: Map<String, Long>) {
        timerJob?.cancel()
        timerJob = null

        scope.launch {
            val current = _state.value
            val profile = current.profile ?: return@launch

            val run = RunRecord(
                id = generateRunId(),
                timestamp = currentTimeMs(),
                bulletPointDurations = durations,
            )
            val updatedRuns = current.runs + run
            repository.saveProfileData(
                com.woutwerkman.pa.model.ProfileData(profile = profile, runs = updatedRuns)
            )

            _state.update {
                it.copy(
                    isActive = false,
                    runs = updatedRuns,
                    currentBulletIndex = 0,
                    elapsedMs = 0,
                    currentBulletElapsedMs = 0,
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
                val now = currentTimeMs()
                _state.update {
                    it.copy(
                        elapsedMs = now - presentationStartTime,
                        currentBulletElapsedMs = now - bulletStartTime,
                    )
                }
            }
        }
    }

    private fun currentTimeMs(): Long =
        com.woutwerkman.pa.platform.currentTimeMs()

    private fun generateRunId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars.random() }.joinToString("")
    }
}
