package com.woutwerkman.pa.model

import kotlinx.serialization.Serializable

@Serializable
data class PresentationProfile(
    val title: String,
    val bulletPoints: Map<String, String>,
    val attachments: Map<String, String> = emptyMap(),
) {
    val orderedKeys: List<String> get() = bulletPoints.keys.toList()
    val size: Int get() = bulletPoints.size

    fun textAt(index: Int): String? = bulletPoints.values.elementAtOrNull(index)
    fun keyAt(index: Int): String? = orderedKeys.getOrNull(index)
    fun attachmentAt(index: Int): String? = keyAt(index)?.let { attachments[it] }
}
