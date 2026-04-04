package io.github.ninbyo02.lami.util

object RuntimeFlags {
    @Volatile
    var isUiTest: Boolean = false

    fun isUiTestRuntime(): Boolean = isUiTest || isInstrumentationRuntime()

    fun shouldDisableContinuousAnimations(): Boolean = isUiTestRuntime()

    private fun isInstrumentationRuntime(): Boolean {
        return try {
            Class.forName("androidx.test.platform.app.InstrumentationRegistry")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
