package com.woutwerkman.pa

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.ProfileData
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.repository.ProfileRepository
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deserializePresentationProfile() {
        val input = """
            {
                "title": "KotlinConf 2026",
                "bulletPoints": {
                    "intro": "Welcome and introduction",
                    "demo1": "Live coding demo",
                    "summary": "Key takeaways"
                }
            }
        """.trimIndent()

        val profile = json.decodeFromString<PresentationProfile>(input)

        assertEquals("KotlinConf 2026", profile.title)
        assertEquals(3, profile.size)
        assertEquals("Welcome and introduction", profile.textAt(0))
        assertEquals("intro", profile.keyAt(0))
        assertEquals("Live coding demo", profile.textAt(1))
        assertEquals("Key takeaways", profile.textAt(2))
    }

    @Test
    fun roundTripProfileData() {
        val profile = PresentationProfile(
            title = "Test Talk",
            bulletPoints = linkedMapOf(
                "a" to "First point",
                "b" to "Second point",
            ),
        )
        val run = RunRecord(
            id = "run-1",
            timestamp = 1000L,
            bulletPointDurations = mapOf("a" to 5000L, "b" to 3000L),
        )
        val data = ProfileData(profile = profile, runs = listOf(run))

        val serialized = json.encodeToString(ProfileData.serializer(), data)
        val deserialized = json.decodeFromString<ProfileData>(serialized)

        assertEquals(data, deserialized)
        assertEquals(8000L, deserialized.runs.first().totalDuration)
    }

    @Test
    fun computeStatsWithMultipleRuns() {
        val runs = listOf(
            RunRecord("1", 100, mapOf("a" to 1000L, "b" to 2000L)),
            RunRecord("2", 200, mapOf("a" to 2000L, "b" to 3000L)),
            RunRecord("3", 300, mapOf("a" to 3000L, "b" to 4000L)),
            RunRecord("4", 400, mapOf("a" to 4000L, "b" to 5000L)),
        )

        val stats = ProfileRepository.computeStats(runs)

        assertEquals(2500L, stats.averageDurations["a"])
        assertEquals(3500L, stats.averageDurations["b"])
        assertEquals(3000L, stats.lastThreeAverageDurations["a"])
        assertEquals(4000L, stats.lastThreeAverageDurations["b"])
        assertEquals(6000L, stats.totalAverage)
        assertEquals(7000L, stats.totalLastThreeAverage)
        assertEquals(9000L, stats.lastRunTotal)
    }

    @Test
    fun computeStatsExcludesDeselectedRuns() {
        val runs = listOf(
            RunRecord("1", 100, mapOf("a" to 1000L), isIncludedInStats = false),
            RunRecord("2", 200, mapOf("a" to 2000L)),
            RunRecord("3", 300, mapOf("a" to 4000L)),
        )

        val stats = ProfileRepository.computeStats(runs)

        assertEquals(3000L, stats.averageDurations["a"])
        assertEquals(4000L, stats.lastRunTotal)
    }

    @Test
    fun computeStatsEmpty() {
        val stats = ProfileRepository.computeStats(emptyList())

        assertEquals(emptyMap(), stats.averageDurations)
        assertNull(stats.lastRunTotal)
        assertEquals(0L, stats.totalAverage)
    }

    @Test
    fun profileJsonDeserializesSeparately() {
        val input = """
            {
                "title": "My Talk",
                "bulletPoints": {
                    "k1": "Point one",
                    "k2": "Point two"
                }
            }
        """.trimIndent()

        val profile = json.decodeFromString<PresentationProfile>(input)
        val data = ProfileData(profile = profile)

        assertEquals("My Talk", data.profile.title)
        assertEquals(2, data.profile.size)
        assertEquals(emptyList(), data.runs)
    }
}
