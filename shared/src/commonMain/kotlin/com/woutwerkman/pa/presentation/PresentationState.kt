@file:UseSerializers(DurationAsLongMillisSerializer::class, InstantAsLongMillisSerializer::class)

package com.woutwerkman.pa.presentation

import com.woutwerkman.pa.model.DurationAsLongMillisSerializer
import com.woutwerkman.pa.model.InstantAsLongMillisSerializer
import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.repository.BulletPointStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
data class PresentationState(
    val profile: PresentationProfile? = null,
    val runs: List<RunRecord> = emptyList(),
    val isActive: Boolean = false,
    val currentBulletIndex: Int = 0,
    val currentRunDurations: Map<String, Duration> = emptyMap(),
    val bulletAverages: Map<String, Duration> = emptyMap(),
    val presentationStartTime: Instant = Instant.fromEpochMilliseconds(0),
    val bulletStartTime: Instant = Instant.fromEpochMilliseconds(0),
) {
    val bulletCount: Int
        get() = profile?.size ?: 0

    val currentBulletText: String?
        get() = profile?.textAt(currentBulletIndex)

    val currentBulletKey: String?
        get() = profile?.keyAt(currentBulletIndex)

    val isLastBullet: Boolean
        get() = currentBulletIndex >= bulletCount - 1

    val currentBulletAttachment: String?
        get() = profile?.attachmentAt(currentBulletIndex)

    val stats: BulletPointStats
        get() = BulletPointStats.compute(runs)

    val averageForCurrentBullet: Duration?
        get() {
            val key = currentBulletKey ?: return null
            return stats.averageDurations[key]
        }

    fun elapsed(now: Instant): Duration =
        if (isActive) now - presentationStartTime
        else Duration.ZERO

    fun currentBulletElapsed(now: Instant): Duration {
        if (!isActive) return Duration.ZERO
        val key = currentBulletKey ?: return Duration.ZERO
        val accumulated = currentRunDurations[key] ?: Duration.ZERO
        return accumulated + (now - bulletStartTime)
    }

    fun bulletCountdown(now: Instant): Duration? {
        val key = currentBulletKey ?: return null
        val avg = averageForCurrentBullet ?: bulletAverages[key] ?: return null
        if (avg == Duration.ZERO) return null
        return avg - currentBulletElapsed(now)
    }

    fun globalScheduleDelta(now: Instant): Duration? {
        val profile = profile ?: return null
        val averages = stats.averageDurations
        if (averages.isEmpty()) return null
        var expected = Duration.ZERO
        for (i in 0..currentBulletIndex) {
            expected += averages[profile.keyAt(i)] ?: return null
        }
        return elapsed(now) - expected
    }
}
