package io.github.ninbyo02.lami.ui.components

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Sprite sheets contain discrete frames; sub-display intervals must still suspend.
internal const val MIN_SPRITE_FRAME_DELAY_MS = 16L

internal fun nextSpriteFrameDelayMs(nowMs: Long, epochMs: Long, intervalMs: Long): Long {
    val interval = intervalMs.coerceAtLeast(1L)
    val elapsed = (nowMs - epochMs).coerceAtLeast(0L)
    return (interval - elapsed % interval).coerceAtLeast(MIN_SPRITE_FRAME_DELAY_MS)
}

/** Keep a shared epoch without waking on every display refresh; pause outside STARTED. */
internal suspend fun Lifecycle.runSpriteFrameClock(
    epochMs: Long,
    intervalMs: Long,
    nowMs: () -> Long,
    onTick: (Long) -> Unit,
) {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (currentCoroutineContext().isActive) {
            onTick(nowMs())
            // Re-read after the callback so processing time does not accumulate as clock drift.
            delay(nextSpriteFrameDelayMs(nowMs(), epochMs, intervalMs))
        }
    }
}

/** Null means the image cannot change until settings or lifecycle restart this clock. */
internal suspend fun Lifecycle.runSpriteEventClock(
    nowMs: () -> Long,
    onFrame: (Long) -> Long?,
) {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (currentCoroutineContext().isActive) {
            val now = nowMs()
            val waitMs = onFrame(now) ?: awaitCancellation()
            val processingMs = (nowMs() - now).coerceAtLeast(0)
            delay((waitMs - processingMs).coerceAtLeast(MIN_SPRITE_FRAME_DELAY_MS))
        }
    }
}
