package com.woutwerkman.pa.repository

data class BulletPointStats(
    val averageDurations: Map<String, Long>,
    val lastThreeAverageDurations: Map<String, Long>,
    val totalAverage: Long,
    val totalLastThreeAverage: Long,
    val lastRunTotal: Long?,
)
