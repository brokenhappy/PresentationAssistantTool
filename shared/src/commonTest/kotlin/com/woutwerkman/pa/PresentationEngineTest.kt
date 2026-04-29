package com.woutwerkman.pa

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.ProfileData
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.presentation.PresentationEngine
import com.woutwerkman.pa.presentation.PresentationEvent
import com.woutwerkman.pa.repository.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationEngineTest {

    private val profile = PresentationProfile(
        title = "Test Talk",
        bulletPoints = mapOf(
            "a" to "First point",
            "b" to "Second point",
            "c" to "Third point",
        ),
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private fun createFileSystem(): InMemoryFileSystem {
        val fs = InMemoryFileSystem()
        fs.putFile("/talk.json", json.encodeToString(PresentationProfile.serializer(), profile))
        return fs
    }

    private var runIdCounter = 0

    private fun TestScope.createEngine(fileSystem: InMemoryFileSystem = createFileSystem()): PresentationEngine {
        runIdCounter = 0
        return PresentationEngine(
            repository = ProfileRepository(fileSystem),
            scope = backgroundScope,
            clock = { currentTime },
            idGenerator = { "run-${runIdCounter++}" },
        )
    }

    private fun TestScope.loadAndStart(engine: PresentationEngine) {
        engine.onEvent(PresentationEvent.LoadProfile("/talk.json"))
        runCurrent()
        engine.onEvent(PresentationEvent.Start)
        runCurrent()
    }

    @Test
    fun loadProfileSetsState() = runTest {
        val engine = createEngine()
        engine.onEvent(PresentationEvent.LoadProfile("/talk.json"))
        runCurrent()

        val state = engine.state.value
        assertEquals("Test Talk", state.profile?.title)
        assertEquals(3, state.bulletCount)
        assertFalse(state.isActive)
    }

    @Test
    fun loadProfileSetsProfilePath() = runTest {
        val engine = createEngine()
        engine.onEvent(PresentationEvent.LoadProfile("/talk.json"))
        runCurrent()

        assertEquals("/talk.json", engine.profilePath.value)
    }

    @Test
    fun startPresentation() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        val state = engine.state.value
        assertTrue(state.isActive)
        assertEquals(0, state.currentBulletIndex)
        assertEquals("First point", state.currentBulletText)
    }

    @Test
    fun startWithoutProfileDoesNothing() = runTest {
        val engine = createEngine()
        engine.onEvent(PresentationEvent.Start)
        runCurrent()

        assertFalse(engine.state.value.isActive)
    }

    @Test
    fun advanceMovesThroughBulletPoints() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        advanceTimeBy(5_000)
        engine.onEvent(PresentationEvent.Advance)
        runCurrent()

        val state = engine.state.value
        assertTrue(state.isActive)
        assertEquals(1, state.currentBulletIndex)
        assertEquals("Second point", state.currentBulletText)
    }

    @Test
    fun advanceWhenNotActiveStartsPresentation() = runTest {
        val engine = createEngine()
        engine.onEvent(PresentationEvent.LoadProfile("/talk.json"))
        runCurrent()

        engine.onEvent(PresentationEvent.Advance)
        runCurrent()

        assertTrue(engine.state.value.isActive)
        assertEquals(0, engine.state.value.currentBulletIndex)
    }

    @Test
    fun goBackMovesBulletPointBackward() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        advanceTimeBy(3_000)
        engine.onEvent(PresentationEvent.Advance)
        runCurrent()

        advanceTimeBy(2_000)
        engine.onEvent(PresentationEvent.GoBack)
        runCurrent()

        assertEquals(0, engine.state.value.currentBulletIndex)
    }

    @Test
    fun goBackOnFirstBulletDoesNothing() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        engine.onEvent(PresentationEvent.GoBack)
        runCurrent()

        assertEquals(0, engine.state.value.currentBulletIndex)
    }

    @Test
    fun goToJumpsToBulletPoint() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        advanceTimeBy(1_000)
        engine.onEvent(PresentationEvent.GoTo(2))
        runCurrent()

        assertEquals(2, engine.state.value.currentBulletIndex)
        assertEquals("Third point", engine.state.value.currentBulletText)
    }

    @Test
    fun goToOutOfRangeDoesNothing() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        engine.onEvent(PresentationEvent.GoTo(10))
        runCurrent()

        assertEquals(0, engine.state.value.currentBulletIndex)
    }

    @Test
    fun advancePastLastBulletFinishesPresentation() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        advanceTimeBy(5_000)
        engine.onEvent(PresentationEvent.Advance)
        advanceTimeBy(3_000)
        engine.onEvent(PresentationEvent.Advance)
        advanceTimeBy(2_000)
        engine.onEvent(PresentationEvent.Advance)
        runCurrent()

        val state = engine.state.value
        assertFalse(state.isActive)
        assertEquals(1, state.runs.size)
    }

    @Test
    fun finishedRunRecordsDurations() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        advanceTimeBy(5_000)
        engine.onEvent(PresentationEvent.Advance)
        advanceTimeBy(3_000)
        engine.onEvent(PresentationEvent.Advance)
        advanceTimeBy(2_000)
        engine.onEvent(PresentationEvent.Advance)
        runCurrent()

        val run = engine.state.value.runs.first()
        assertEquals(5.seconds, run.bulletPointDurations["a"])
        assertEquals(3.seconds, run.bulletPointDurations["b"])
        assertEquals(2.seconds, run.bulletPointDurations["c"])
        assertEquals(10.seconds, run.totalDuration)
    }

    @Test
    fun goingBackAndForthAccumulatesDuration() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        advanceTimeBy(3_000)
        engine.onEvent(PresentationEvent.Advance)

        advanceTimeBy(2_000)
        engine.onEvent(PresentationEvent.GoBack)

        advanceTimeBy(4_000)
        engine.onEvent(PresentationEvent.Advance)

        advanceTimeBy(1_000)
        engine.onEvent(PresentationEvent.Advance)

        advanceTimeBy(1_000)
        engine.onEvent(PresentationEvent.Advance)
        runCurrent()

        val run = engine.state.value.runs.first()
        assertEquals(7.seconds, run.bulletPointDurations["a"])
        assertEquals(3.seconds, run.bulletPointDurations["b"])
        assertEquals(1.seconds, run.bulletPointDurations["c"])
    }

    @Test
    fun elapsedTimeComputedFromTimestamps() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        advanceTimeBy(5_000)

        val state = engine.state.value
        assertEquals(5.seconds, state.elapsed(currentTime))
        assertEquals(5.seconds, state.currentBulletElapsed(currentTime))
    }

    @Test
    fun closeProfileResetsState() = runTest {
        val engine = createEngine()
        loadAndStart(engine)

        engine.onEvent(PresentationEvent.CloseProfile)
        runCurrent()

        val state = engine.state.value
        assertNull(state.profile)
        assertFalse(state.isActive)
        assertNull(engine.profilePath.value)
    }

    @Test
    fun toggleRunInclusionUpdatesRunAndPersists() = runTest {
        val fs = createFileSystem()
        val engine = createEngine(fs)
        loadAndStart(engine)

        advanceTimeBy(2_000)
        engine.onEvent(PresentationEvent.Advance)
        advanceTimeBy(2_000)
        engine.onEvent(PresentationEvent.Advance)
        advanceTimeBy(2_000)
        engine.onEvent(PresentationEvent.Advance)
        runCurrent()

        val runId = engine.state.value.runs.first().id
        assertTrue(engine.state.value.runs.first().isIncludedInStats)

        engine.onEvent(PresentationEvent.ToggleRunInclusion(runId))
        runCurrent()

        assertFalse(engine.state.value.runs.first().isIncludedInStats)
    }

    @Test
    fun finishedRunIsSavedToDisk() = runTest {
        val fs = createFileSystem()
        val engine = createEngine(fs)
        loadAndStart(engine)

        advanceTimeBy(1_000)
        engine.onEvent(PresentationEvent.Advance)
        advanceTimeBy(1_000)
        engine.onEvent(PresentationEvent.Advance)
        advanceTimeBy(1_000)
        engine.onEvent(PresentationEvent.Advance)
        runCurrent()

        val repository = ProfileRepository(fs)
        val savedData = repository.loadProfileData("Test Talk")
        assertNotNull(savedData)
        assertEquals(1, savedData.runs.size)
        assertEquals(3.seconds, savedData.runs.first().totalDuration)
    }

    @Test
    fun loadProfileMergesExistingRuns() = runTest {
        val fs = createFileSystem()
        val existingRun = RunRecord(
            id = "old-run",
            timestamp = 500,
            bulletPointDurations = mapOf("a" to 10.seconds, "b" to 5.seconds, "c" to 3.seconds),
        )
        val existingData = ProfileData(profile = profile, runs = listOf(existingRun))
        val repo = ProfileRepository(fs)
        repo.saveProfileData(existingData)

        val engine = createEngine(fs)
        engine.onEvent(PresentationEvent.LoadProfile("/talk.json"))
        runCurrent()

        assertEquals(1, engine.state.value.runs.size)
        assertEquals("old-run", engine.state.value.runs.first().id)
    }

    @Test
    fun loadMissingFileEmitsError() = runTest {
        val fs = InMemoryFileSystem()
        val engine = createEngine(fs)
        val errors = mutableListOf<String>()

        backgroundScope.launch {
            engine.error.collect { errors.add(it) }
        }
        runCurrent()

        engine.onEvent(PresentationEvent.LoadProfile("/nonexistent.json"))
        runCurrent()

        assertEquals(1, errors.size)
        assertTrue(errors.first().contains("Failed to load profile"))
    }
}
