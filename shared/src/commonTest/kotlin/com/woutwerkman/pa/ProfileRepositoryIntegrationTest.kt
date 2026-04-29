package com.woutwerkman.pa

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.ProfileData
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.repository.ProfileRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProfileRepositoryIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private val profile = PresentationProfile(
        title = "Test Talk",
        bulletPoints = mapOf("a" to "First", "b" to "Second"),
    )

    private fun createFileSystem(): InMemoryFileSystem {
        val fs = InMemoryFileSystem()
        fs.putFile("/talk.json", json.encodeToString(PresentationProfile.serializer(), profile))
        return fs
    }

    @Test
    fun loadProfileFromFile() = runTest {
        val fs = createFileSystem()
        val repo = ProfileRepository(fs)

        val loaded = repo.loadProfileFromFile("/talk.json")

        assertEquals("Test Talk", loaded.title)
        assertEquals(2, loaded.size)
    }

    @Test
    fun saveAndLoadProfileData() = runTest {
        val fs = createFileSystem()
        val repo = ProfileRepository(fs)
        val run = RunRecord("r1", Instant.fromEpochMilliseconds(1000), mapOf("a" to 5.seconds, "b" to 3.seconds))
        val data = ProfileData(profile = profile, runs = listOf(run))

        repo.saveProfileData(data)
        val loaded = repo.loadProfileData("Test Talk")

        assertNotNull(loaded)
        assertEquals(1, loaded.runs.size)
        assertEquals(8.seconds, loaded.runs.first().totalDuration)
    }

    @Test
    fun loadProfileDataReturnsNullWhenNotSaved() = runTest {
        val fs = createFileSystem()
        val repo = ProfileRepository(fs)

        assertNull(repo.loadProfileData("Nonexistent"))
    }

    @Test
    fun loadOrCreateWithNoExistingData() = runTest {
        val fs = createFileSystem()
        val repo = ProfileRepository(fs)

        val data = repo.loadOrCreateProfileData("/talk.json")

        assertEquals("Test Talk", data.profile.title)
        assertEquals(0, data.runs.size)
    }

    @Test
    fun loadOrCreateMergesWithExistingRuns() = runTest {
        val fs = createFileSystem()
        val repo = ProfileRepository(fs)
        val run = RunRecord("r1", Instant.fromEpochMilliseconds(1000), mapOf("a" to 5.seconds, "b" to 3.seconds))
        repo.saveProfileData(ProfileData(profile = profile, runs = listOf(run)))

        val updatedProfile = profile.copy(
            bulletPoints = mapOf("a" to "Updated First", "b" to "Second", "c" to "New Third"),
        )
        fs.putFile("/talk.json", json.encodeToString(PresentationProfile.serializer(), updatedProfile))

        val data = repo.loadOrCreateProfileData("/talk.json")

        assertEquals("Updated First", data.profile.textAt(0))
        assertEquals(3, data.profile.size)
        assertEquals(1, data.runs.size)
    }
}
