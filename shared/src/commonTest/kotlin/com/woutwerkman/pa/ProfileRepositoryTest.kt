package com.woutwerkman.pa

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.ProfileData
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.repository.BulletPointStats
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
            bulletPoints = mapOf(
                "a" to "First point",
                "b" to "Second point",
            ),
        )
        val run = RunRecord(
            id = "run-1",
            timestamp = 1000L,
            bulletPointDurations = mapOf("a" to 5.seconds, "b" to 3.seconds),
        )
        val data = ProfileData(profile = profile, runs = listOf(run))

        val serialized = json.encodeToString(ProfileData.serializer(), data)
        val deserialized = json.decodeFromString<ProfileData>(serialized)

        assertEquals(data, deserialized)
        assertEquals(8.seconds, deserialized.runs.first().totalDuration)
    }

    @Test
    fun computeStatsWithMultipleRuns() {
        val runs = listOf(
            RunRecord("1", 100, mapOf("a" to 1.seconds, "b" to 2.seconds)),
            RunRecord("2", 200, mapOf("a" to 2.seconds, "b" to 3.seconds)),
            RunRecord("3", 300, mapOf("a" to 3.seconds, "b" to 4.seconds)),
            RunRecord("4", 400, mapOf("a" to 4.seconds, "b" to 5.seconds)),
        )

        val stats = BulletPointStats.compute(runs)

        assertEquals(2500.milliseconds, stats.averageDurations["a"])
        assertEquals(3500.milliseconds, stats.averageDurations["b"])
        assertEquals(3.seconds, stats.lastThreeAverageDurations["a"])
        assertEquals(4.seconds, stats.lastThreeAverageDurations["b"])
        assertEquals(6.seconds, stats.totalAverage)
        assertEquals(7.seconds, stats.totalLastThreeAverage)
        assertEquals(9.seconds, stats.lastRunTotal)
    }

    @Test
    fun computeStatsExcludesDeselectedRuns() {
        val runs = listOf(
            RunRecord("1", 100, mapOf("a" to 1.seconds), isIncludedInStats = false),
            RunRecord("2", 200, mapOf("a" to 2.seconds)),
            RunRecord("3", 300, mapOf("a" to 4.seconds)),
        )

        val stats = BulletPointStats.compute(runs)

        assertEquals(3.seconds, stats.averageDurations["a"])
        assertEquals(4.seconds, stats.lastRunTotal)
    }

    @Test
    fun computeStatsEmpty() {
        val stats = BulletPointStats.compute(emptyList())

        assertEquals(emptyMap(), stats.averageDurations)
        assertNull(stats.lastRunTotal)
        assertEquals(Duration.ZERO, stats.totalAverage)
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
