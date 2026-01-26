package com.sonusid.ollama.ui.animation

object SpriteAnimationDefaults {
    // UI 表記(1-9)は +1 だが、ここは内部フレーム(0-8)で保持する。
    val ERROR_LIGHT_FRAMES = listOf(4, 6, 7, 6, 4)
    const val ERROR_LIGHT_INTERVAL_MS = 390
    const val ERROR_LIGHT_INSERTION_ENABLED = true
    const val ERROR_LIGHT_INSERTION_INTERVAL_MS = 390
    val ERROR_LIGHT_INSERTION_PATTERN_FRAMES = listOf(2, 4)
    const val ERROR_LIGHT_INSERTION_PATTERN_WEIGHT = 1
    const val ERROR_LIGHT_INSERTION_PATTERN_INTERVAL_MS = 390
    const val ERROR_LIGHT_EVERY_N_LOOPS = 3
    const val ERROR_LIGHT_PROBABILITY_PERCENT = 60
    const val ERROR_LIGHT_COOLDOWN_LOOPS = 4
    const val ERROR_LIGHT_EXCLUSIVE = false
}
