package com.woutwerkman.pa.model

import kotlinx.serialization.Serializable

@Serializable
data class RunRecord(
    val id: String,
    val timestamp: Long,
    val bulletPointDurations: Map<String, Long>,
    val isIncludedInStats: Boolean = true,
) {
    val totalDuration: Long get() = bulletPointDurations.values.sum()
}
