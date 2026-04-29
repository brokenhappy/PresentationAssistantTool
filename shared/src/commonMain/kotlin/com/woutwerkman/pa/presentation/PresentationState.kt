@file:UseSerializers(DurationAsLongMillisSerializer::class)

package com.woutwerkman.pa.presentation

import com.woutwerkman.pa.model.DurationAsLongMillisSerializer
import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.repository.BulletPointStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.Duration

@Serializable
data class PresentationState(
    val profile: PresentationProfile? = null,
    val runs: List<RunRecord> = emptyList(),
    val isActive: Boolean = false,
    val currentBulletIndex: Int = 0,
    val elapsed: Duration = Duration.ZERO,
    val currentBulletElapsed: Duration = Duration.ZERO,
    val currentRunDurations: Map<String, Duration> = emptyMap(),
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

    val remainingVsAverage: Duration?
        get() {
            val avg = averageForCurrentBullet ?: return null
            if (avg == Duration.ZERO) return null
            return currentBulletElapsed - avg
        }

    val globalScheduleDelta: Duration?
        get() {
            val profile = profile ?: return null
            val averages = stats.averageDurations
            if (averages.isEmpty()) return null
            var expected = Duration.ZERO
            for (i in 0..currentBulletIndex) {
                expected += averages[profile.keyAt(i)] ?: return null
            }
            return elapsed - expected
        }
}
