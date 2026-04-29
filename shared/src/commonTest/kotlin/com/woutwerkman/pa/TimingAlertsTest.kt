package com.woutwerkman.pa

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.presentation.PresentationState
import com.woutwerkman.pa.presentation.TimingAlerts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TimingAlertsTest {

    private val profile = PresentationProfile(
        title = "Test",
        bulletPoints = mapOf("a" to "First", "b" to "Second"),
    )

    private fun stateWithAverage(average: Duration, bulletIndex: Int = 0): PresentationState {
        val run = RunRecord(
            id = "r1",
            timestamp = 1000L,
            bulletPointDurations = profile.orderedKeys.associateWith { average },
        )
        return PresentationState(
            profile = profile,
            runs = listOf(run),
            isActive = true,
            currentBulletIndex = bulletIndex,
        )
    }

    private val inactive = PresentationState(profile = profile)

    @Test
    fun warningFires10sBeforeAverage() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            TimingAlerts(state) { duration ->
                vibrations.add(currentTime to duration)
            }.run()
        }

        state.value = stateWithAverage(20.seconds)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(2, vibrations.size)
        assertEquals(10_000, vibrations[0].first)
        assertEquals(TimingAlerts.SHORT_VIBRATION, vibrations[0].second)
        assertEquals(TimingAlerts.SHORT_VIBRATION, vibrations[1].second)

        job.cancel()
    }

    @Test
    fun exceededFiresAtAverage() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            TimingAlerts(state) { duration ->
                vibrations.add(currentTime to duration)
            }.run()
        }

        state.value = stateWithAverage(20.seconds)
        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(3, vibrations.size)
        assertEquals(20_000, vibrations[2].first)
        assertEquals(TimingAlerts.LONG_VIBRATION, vibrations[2].second)

        job.cancel()
    }

    @Test
    fun vibrateDelayShiftsExceededTime() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Pair<Long, Duration>>()

        val job = launch {
            TimingAlerts(state) { duration ->
                vibrations.add(currentTime to duration)
                kotlinx.coroutines.delay(duration)
            }.run()
        }

        state.value = stateWithAverage(20.seconds)
        advanceTimeBy(20_200)
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
            TimingAlerts(state) { duration ->
                vibrations.add(currentTime to duration)
            }.run()
        }

        state.value = stateWithAverage(5.seconds)
        runCurrent()

        assertEquals(2, vibrations.size)
        assertEquals(0, vibrations[0].first)

        advanceTimeBy(5_000)
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
            TimingAlerts(state) { duration ->
                vibrations.add(currentTime to duration)
            }.run()
        }

        state.value = stateWithAverage(20.seconds, bulletIndex = 0)
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(0, vibrations.size)

        state.value = stateWithAverage(20.seconds, bulletIndex = 1)
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(0, vibrations.size)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, vibrations.size)

        job.cancel()
    }

    @Test
    fun noVibrationsWhenInactive() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Duration>()

        val job = launch {
            TimingAlerts(state) { vibrations.add(it) }.run()
        }

        advanceTimeBy(30_000)
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
            TimingAlerts(state) { vibrations.add(it) }.run()
        }

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(0, vibrations.size)

        job.cancel()
    }

    @Test
    fun deactivatingCancelsTimers() = runTest {
        val state = MutableStateFlow(inactive)
        val vibrations = mutableListOf<Duration>()

        val job = launch {
            TimingAlerts(state) { vibrations.add(it) }.run()
        }

        state.value = stateWithAverage(20.seconds)
        advanceTimeBy(8_000)
        runCurrent()

        state.value = inactive
        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(0, vibrations.size)

        job.cancel()
    }
}
