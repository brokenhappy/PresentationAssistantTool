package com.woutwerkman.pa.presentation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

val WARNING_BEFORE: Duration = 10.seconds
val SHORT_VIBRATION: Duration = 100.milliseconds
val LONG_VIBRATION: Duration = 300.milliseconds

suspend fun runTimingAlerts(
    stateFlow: StateFlow<PresentationState>,
    clock: Clock = Clock.System,
    vibrate: suspend (Duration) -> Unit,
) {
    stateFlow
        .map { ActiveBullet.from(it) }
        .distinctUntilChanged()
        .collectLatest { bullet ->
            if (bullet == null) return@collectLatest

            val warningAt = (bullet.average - WARNING_BEFORE).coerceAtLeast(Duration.ZERO)
            val alreadyElapsed = stateFlow.value.currentBulletElapsed(clock.now())

            if (alreadyElapsed <= warningAt) {
                delay(warningAt - alreadyElapsed)
                vibrate(SHORT_VIBRATION)
                vibrate(SHORT_VIBRATION)
            }

            val overtimeDelay = (bullet.average - maxOf(alreadyElapsed, warningAt)).coerceAtLeast(Duration.ZERO)
            if (overtimeDelay > Duration.ZERO) {
                delay(overtimeDelay)
                vibrate(LONG_VIBRATION)
            }
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
