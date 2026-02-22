package com.sonusid.ollama.util

object RuntimeFlags {
    @Volatile
    var isUiTest: Boolean = false

    fun shouldDisableContinuousAnimations(): Boolean = isUiTest
}
