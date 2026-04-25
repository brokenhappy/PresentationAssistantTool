package com.woutwerkman.pa.repository

import com.woutwerkman.pa.model.RunRecord

data class BulletPointStats(
    val averageDurations: Map<String, Long>,
    val lastThreeAverageDurations: Map<String, Long>,
    val totalAverage: Long,
    val totalLastThreeAverage: Long,
    val lastRunTotal: Long?,
) {
    companion object {
        val EMPTY = BulletPointStats(
            averageDurations = emptyMap(),
            lastThreeAverageDurations = emptyMap(),
            totalAverage = 0,
            totalLastThreeAverage = 0,
            lastRunTotal = null,
        )

        fun compute(runs: List<RunRecord>): BulletPointStats {
            val included = runs.filter { it.isIncludedInStats }
            if (included.isEmpty()) return EMPTY

            val allKeys = included.flatMap { it.bulletPointDurations.keys }.toSet()

            val averageDurations = allKeys.associateWith { key ->
                val durations = included.mapNotNull { it.bulletPointDurations[key] }
                if (durations.isEmpty()) 0L else durations.average().toLong()
            }

            val lastThree = included.sortedByDescending { it.timestamp }.take(3)
            val lastThreeAverageDurations = allKeys.associateWith { key ->
                val durations = lastThree.mapNotNull { it.bulletPointDurations[key] }
                if (durations.isEmpty()) 0L else durations.average().toLong()
            }

            return BulletPointStats(
                averageDurations = averageDurations,
                lastThreeAverageDurations = lastThreeAverageDurations,
                totalAverage = included.map { it.totalDuration }.average().toLong(),
                totalLastThreeAverage = lastThree.map { it.totalDuration }.average().toLong(),
                lastRunTotal = included.maxByOrNull { it.timestamp }?.totalDuration,
            )
        }
    }
}
