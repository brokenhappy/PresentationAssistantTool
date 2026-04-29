@file:UseSerializers(DurationAsLongMillisSerializer::class)

package com.woutwerkman.pa.presentation

import com.woutwerkman.pa.model.DurationAsLongMillisSerializer
import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.repository.BulletPointStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class PresentationState(
    val profile: PresentationProfile? = null,
    val runs: List<RunRecord> = emptyList(),
    val isActive: Boolean = false,
    val currentBulletIndex: Int = 0,
    val currentRunDurations: Map<String, Duration> = emptyMap(),
    val bulletAverages: Map<String, Duration> = emptyMap(),
    val presentationStartTime: Long = 0L,
    val bulletStartTime: Long = 0L,
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

    fun elapsed(now: Long): Duration =
        if (isActive) (now - presentationStartTime).milliseconds
        else Duration.ZERO

    fun currentBulletElapsed(now: Long): Duration {
        if (!isActive) return Duration.ZERO
        val key = currentBulletKey ?: return Duration.ZERO
        val accumulated = currentRunDurations[key] ?: Duration.ZERO
        return accumulated + (now - bulletStartTime).milliseconds
    }

    fun bulletCountdown(now: Long): Duration? {
        val key = currentBulletKey ?: return null
        val avg = averageForCurrentBullet ?: bulletAverages[key] ?: return null
        if (avg == Duration.ZERO) return null
        return avg - currentBulletElapsed(now)
    }

    fun globalScheduleDelta(now: Long): Duration? {
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
