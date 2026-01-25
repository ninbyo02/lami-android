package com.sonusid.ollama.ui.animation

object SpriteAnimationDefaults {
    // UI 表記(1-9)は +1 だが、ここは内部フレーム(0-8)で保持する。
    val ERROR_LIGHT_FRAMES = listOf(4, 6, 7, 6, 4)
    const val ERROR_LIGHT_INTERVAL_MS = 390
}
