package com.woutwerkman.pa.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfileData(
    val profile: PresentationProfile,
    val runs: List<RunRecord> = emptyList(),
)
