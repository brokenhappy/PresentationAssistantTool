package com.woutwerkman.pa.repository

import com.woutwerkman.pa.model.RunRecord
import kotlin.time.Duration

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
                else durations.reduce { a, b -> a + b } / durations.size
            }

            val lastThree = included.sortedByDescending { it.timestamp }.take(3)
            val lastThreeAverageDurations = allKeys.associateWith { key ->
                val durations = lastThree.mapNotNull { it.bulletPointDurations[key] }
                if (durations.isEmpty()) Duration.ZERO
                else durations.reduce { a, b -> a + b } / durations.size
            }

            return BulletPointStats(
                averageDurations = averageDurations,
                lastThreeAverageDurations = lastThreeAverageDurations,
                totalAverage = included.map { it.totalDuration }.reduce { a, b -> a + b } / included.size,
                totalLastThreeAverage = lastThree.map { it.totalDuration }.reduce { a, b -> a + b } / lastThree.size,
                lastRunTotal = included.maxByOrNull { it.timestamp }?.totalDuration,
            )
        }
    }
}
