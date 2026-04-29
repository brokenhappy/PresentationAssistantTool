package com.woutwerkman.pa.presentation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

val WARNING_BEFORE: Duration = 10.seconds
val SHORT_VIBRATION: Duration = 100.milliseconds
val LONG_VIBRATION: Duration = 300.milliseconds

suspend fun runTimingAlerts(
    stateFlow: StateFlow<PresentationState>,
    vibrate: suspend (Duration) -> Unit,
) {
    stateFlow
        .map { ActiveBullet.from(it) }
        .distinctUntilChanged()
        .collectLatest { bullet ->
            if (bullet == null) return@collectLatest

            val warningBefore = (bullet.average - WARNING_BEFORE).coerceAtLeast(Duration.ZERO)
            delay(warningBefore)
            vibrate(SHORT_VIBRATION)
            vibrate(SHORT_VIBRATION)

            delay(bullet.average - warningBefore)
            vibrate(LONG_VIBRATION)
        }
}

private data class ActiveBullet(val index: Int, val average: Duration) {
    companion object {
        fun from(state: PresentationState): ActiveBullet? {
            if (!state.isActive) return null
            val avg = state.averageForCurrentBullet ?: return null
            if (avg == Duration.ZERO) return null
            return ActiveBullet(state.currentBulletIndex, avg)
        }
    }
}
