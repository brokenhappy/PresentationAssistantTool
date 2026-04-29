package com.woutwerkman.pa.repository

import com.woutwerkman.pa.model.RunRecord
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class BulletPointStats(
    val averageDurations: Map<String, Duration>,
    val lastThreeAverageDurations: Map<String, Duration>,
    val totalAverage: Duration,
    val totalLastThreeAverage: Duration,
    val lastRunTotal: Duration?,
) {
    companion object {
        val EMPTY = BulletPointStats(
            averageDurations = emptyMap(),
            lastThreeAverageDurations = emptyMap(),
            totalAverage = Duration.ZERO,
            totalLastThreeAverage = Duration.ZERO,
            lastRunTotal = null,
        )

        fun compute(runs: List<RunRecord>): BulletPointStats {
            val included = runs.filter { it.isIncludedInStats }
            if (included.isEmpty()) return EMPTY

            val allKeys = included.flatMap { it.bulletPointDurations.keys }.toSet()

            val averageDurations = allKeys.associateWith { key ->
                val durations = included.mapNotNull { it.bulletPointDurations[key] }
                if (durations.isEmpty()) Duration.ZERO
                else durations.map { it.inWholeMilliseconds }.average().toLong().milliseconds
            }

            val lastThree = included.sortedByDescending { it.timestamp }.take(3)
            val lastThreeAverageDurations = allKeys.associateWith { key ->
                val durations = lastThree.mapNotNull { it.bulletPointDurations[key] }
                if (durations.isEmpty()) Duration.ZERO
                else durations.map { it.inWholeMilliseconds }.average().toLong().milliseconds
            }

            return BulletPointStats(
                averageDurations = averageDurations,
                lastThreeAverageDurations = lastThreeAverageDurations,
                totalAverage = included.map { it.totalDuration.inWholeMilliseconds }.average().toLong().milliseconds,
                totalLastThreeAverage = lastThree.map { it.totalDuration.inWholeMilliseconds }.average().toLong().milliseconds,
                lastRunTotal = included.maxByOrNull { it.timestamp }?.totalDuration,
            )
        }
    }
}
