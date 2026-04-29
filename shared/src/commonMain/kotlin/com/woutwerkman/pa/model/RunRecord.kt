@file:UseSerializers(DurationAsLongMillisSerializer::class)

package com.woutwerkman.pa.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.Duration

@Serializable
data class RunRecord(
    val id: String,
    val timestamp: Long,
    val bulletPointDurations: Map<String, Duration>,
    val isIncludedInStats: Boolean = true,
) {
    val totalDuration: Duration get() = bulletPointDurations.values.fold(Duration.ZERO) { acc, d -> acc + d }
}
