package com.woutwerkman.pa.presentation

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.repository.BulletPointStats
import kotlinx.serialization.Serializable

@Serializable
data class PresentationState(
    val profile: PresentationProfile? = null,
    val runs: List<RunRecord> = emptyList(),
    val isActive: Boolean = false,
    val currentBulletIndex: Int = 0,
    val elapsedMs: Long = 0,
    val currentBulletElapsedMs: Long = 0,
    val currentRunDurations: Map<String, Long> = emptyMap(),
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

    val averageForCurrentBullet: Long?
        get() {
            val key = currentBulletKey ?: return null
            return stats.averageDurations[key]
        }

    val remainingVsAverage: Long?
        get() {
            val avg = averageForCurrentBullet ?: return null
            if (avg == 0L) return null
            return currentBulletElapsedMs - avg
        }

    val globalScheduleDelta: Long?
        get() {
            val profile = profile ?: return null
            val averages = stats.averageDurations
            if (averages.isEmpty()) return null
            val expectedMs = (0..currentBulletIndex).sumOf { i ->
                averages[profile.keyAt(i)] ?: return null
            }
            return elapsedMs - expectedMs
        }
}
