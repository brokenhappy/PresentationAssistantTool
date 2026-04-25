package com.woutwerkman.pa.presentation

import kotlinx.serialization.Serializable

@Serializable
sealed interface PresentationEvent {
    @Serializable
    data class LoadProfile(val path: String) : PresentationEvent

    @Serializable
    data object CloseProfile : PresentationEvent

    @Serializable
    data object Start : PresentationEvent

    @Serializable
    data object Advance : PresentationEvent

    @Serializable
    data object GoBack : PresentationEvent

    @Serializable
    data class GoTo(val index: Int) : PresentationEvent

    @Serializable
    data class ToggleRunInclusion(val runId: String) : PresentationEvent
}
