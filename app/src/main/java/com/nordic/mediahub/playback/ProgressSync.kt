package com.nordic.mediahub.playback

import kotlinx.coroutines.delay

internal class PeriodicSyncStep(
    val positionSeconds: Int,
    val isPlaying: Boolean,
    val deltaSeconds: Int?,
    val doSync: suspend (positionSeconds: Int, isPlaying: Boolean, deltaSeconds: Int) -> Unit
)

internal suspend fun runPeriodicProgressSync(
    initialBaselineSeconds: Int,
    nextStep: () -> PeriodicSyncStep?,
    onFailure: (Throwable) -> Unit
) {
    var lastSyncedPosition = initialBaselineSeconds
    while (true) {
        delay(30_000)
        val step = nextStep() ?: return
        val currentPosition = maxOf(step.positionSeconds, lastSyncedPosition)
        val currentDelta = step.deltaSeconds ?: (currentPosition - lastSyncedPosition).coerceAtLeast(0)
        runCatching {
            step.doSync(currentPosition, step.isPlaying, currentDelta)
        }.onSuccess {
            lastSyncedPosition = currentPosition
        }.onFailure(onFailure)
    }
}
