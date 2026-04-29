package com.woutwerkman.pa

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.presentation.LONG_VIBRATION
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.presentation.SHORT_VIBRATION
import com.woutwerkman.pa.presentation.runTimingAlerts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimingAlertsTest {

    private val profile = PresentationProfile(
        title = "Test",
        bulletPoints = mapOf("a" to "First", "b" to "Second"),
    )

    private fun TestScope.now() = Instant.fromEpochMilliseconds(currentTime)

    private fun TestScope.stateWithAverage(
        average: Duration,
        bulletIndex: Int = 0,
        alreadyElapsed: Duration = Duration.ZERO,
    ): PresentationState {
        val run = RunRecord(
            id = "r1",
            timestamp = Instant.fromEpochMilliseconds(1000),
            bulletPointDurations = profile.orderedKeys.associateWith { average },
        )
        return PresentationState(
            profile = profile,
            runs = listOf(run),
            isActive = true,
            currentBulletIndex = bulletIndex,
            bulletStartTime = now() - alreadyElapsed,
            presentationStartTime = now() - alreadyElapsed,
        )
    }

    private val inactive = PresentationState(profile = profile)

    @Test
    fun warningFires10sBeforeAverage() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            runTimingAlerts(state, { now() }) { duration ->
                vibrations.add(currentTime to duration)
            }
        }

        state.value = stateWithAverage(20.seconds)
        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals(2, vibrations.size)
        assertEquals(10_000, vibrations[0].first)
        assertEquals(SHORT_VIBRATION, vibrations[0].second)
        assertEquals(SHORT_VIBRATION, vibrations[1].second)

        job.cancel()
    }

    @Test
    fun exceededFiresAtAverage() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            runTimingAlerts(state, { now() }) { duration ->
                vibrations.add(currentTime to duration)
            }
        }

        state.value = stateWithAverage(20.seconds)
        advanceTimeBy(20.seconds)
        runCurrent()

        assertEquals(3, vibrations.size)
        assertEquals(20_000, vibrations[2].first)
        assertEquals(LONG_VIBRATION, vibrations[2].second)

        job.cancel()
    }

    @Test
    fun vibrateDelayShiftsExceededTime() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            runTimingAlerts(state, { now() }) { duration ->
                vibrations.add(currentTime to duration)
                kotlinx.coroutines.delay(duration)
            }
        }

        state.value = stateWithAverage(20.seconds)
        advanceTimeBy(20.seconds + 200.milliseconds)
        runCurrent()

        assertEquals(3, vibrations.size)
        assertEquals(10_000, vibrations[0].first)
        assertEquals(10_100, vibrations[1].first)
        assertEquals(20_200, vibrations[2].first)

        job.cancel()
    }

    @Test
    fun warningFiresImmediatelyWhenAverageUnder10s() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            runTimingAlerts(state, { now() }) { duration ->
                vibrations.add(currentTime to duration)
            }
        }

        state.value = stateWithAverage(5.seconds)
        runCurrent()

        assertEquals(2, vibrations.size)
        assertEquals(0, vibrations[0].first)

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(3, vibrations.size)
        assertEquals(5_000, vibrations[2].first)

        job.cancel()
    }

    @Test
    fun bulletChangeResetsTimers() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            runTimingAlerts(state, { now() }) { duration ->
                vibrations.add(currentTime to duration)
            }
        }

        state.value = stateWithAverage(20.seconds, bulletIndex = 0)
        advanceTimeBy(8.seconds)
        runCurrent()
        assertEquals(0, vibrations.size)

        state.value = stateWithAverage(20.seconds, bulletIndex = 1)
        advanceTimeBy(8.seconds)
        runCurrent()
        assertEquals(0, vibrations.size)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(2, vibrations.size)

        job.cancel()
    }

    @Test
    fun noVibrationsWhenInactive() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Duration>()

        val job = launch {
            runTimingAlerts(state, { now() }) { vibrations.add(it) }
        }

        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(0, vibrations.size)

        job.cancel()
    }

    @Test
    fun noVibrationsWithoutHistoricalData() = runTest {
        val state = MutableStateFlow(
            PresentationState(profile = profile, isActive = true, currentBulletIndex = 0)
        )
        val vibrations = mutableListOf<Duration>()

        val job = launch {
            runTimingAlerts(state, { now() }) { vibrations.add(it) }
        }

        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(0, vibrations.size)

        job.cancel()
    }

    @Test
    fun accumulatedTimeReducesWarningDelay() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            runTimingAlerts(state, { now() }) { duration ->
                vibrations.add(currentTime to duration)
            }
        }

        state.value = stateWithAverage(20.seconds, alreadyElapsed = 5.seconds)
        advanceTimeBy(5.seconds)
        runCurrent()

        assertEquals(2, vibrations.size)
        assertEquals(5_000, vibrations[0].first)
        assertEquals(SHORT_VIBRATION, vibrations[0].second)

        job.cancel()
    }

    @Test
    fun accumulatedTimePastWarningSkipsWarning() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            runTimingAlerts(state, { now() }) { duration ->
                vibrations.add(currentTime to duration)
            }
        }

        state.value = stateWithAverage(20.seconds, alreadyElapsed = 15.seconds)
        advanceTimeBy(5.seconds)
        runCurrent()

        assertEquals(1, vibrations.size)
        assertEquals(5_000, vibrations[0].first)
        assertEquals(LONG_VIBRATION, vibrations[0].second)

        job.cancel()
    }

    @Test
    fun accumulatedTimePastAverageSkipsAll() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Duration>()

        val job = launch {
            runTimingAlerts(state, { now() }) { vibrations.add(it) }
        }

        state.value = stateWithAverage(20.seconds, alreadyElapsed = 25.seconds)
        advanceTimeBy(30.seconds)
        runCurrent()

        assertEquals(0, vibrations.size)

        job.cancel()
    }

    @Test
    fun deactivatingCancelsTimers() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Duration>()

        val job = launch {
            runTimingAlerts(state, { now() }) { vibrations.add(it) }
        }

        state.value = stateWithAverage(20.seconds)
        advanceTimeBy(8.seconds)
        runCurrent()

        state.value = inactive
        advanceTimeBy(20.seconds)
        runCurrent()

        assertEquals(0, vibrations.size)

        job.cancel()
    }
}
