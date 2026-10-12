package io.github.ninbyo02.lami.tts

internal object LamiPlaybackUnderrunAccounting {
    fun observedDelta(initialCount: Int, finalCount: Int): Int {
        require(initialCount >= 0) { "Initial underrun count must be non-negative" }
        require(finalCount >= 0) { "Final underrun count must be non-negative" }
        return (finalCount - initialCount).coerceAtLeast(0)
    }
}
