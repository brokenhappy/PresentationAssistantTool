package com.woutwerkman.pa.presentation

import com.woutwerkman.pa.model.PresentationProfile
import com.woutwerkman.pa.model.RunRecord
import com.woutwerkman.pa.repository.BulletPointStats
import com.woutwerkman.pa.repository.ProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    @Transient
    val bulletCount: Int = profile?.size ?: 0

    val currentBulletText: String?
        get() = profile?.textAt(currentBulletIndex)

    val currentBulletKey: String?
        get() = profile?.keyAt(currentBulletIndex)

    val isLastBullet: Boolean
        get() = currentBulletIndex >= bulletCount - 1

    val stats: BulletPointStats
        get() = ProfileRepository.computeStats(runs)

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
}
