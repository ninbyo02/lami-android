package io.github.ninbyo02.lami.tts

/** Diagnostic failures disable capture without altering speech control flow. */
internal class LamiSpeechTraceGuard private constructor(
    private var capture: LamiSpeechTraceCapture?,
) {
    @Synchronized private fun attempt(action: (LamiSpeechTraceCapture) -> Unit) {
        val current = capture ?: return
        try { action(current) } catch (_: Exception) { capture = null }
    }
    fun event(stage: String) = attempt { it.event(stage) }
    fun success(pcm: FloatArray) {
        attempt { it.success(pcm) }
        synchronized(this) { capture = null }
    }
    fun failure(stage: String) {
        attempt { it.failure(stage) }
        synchronized(this) { capture = null }
    }
    companion object {
        fun create(factory: () -> LamiSpeechTraceCapture): LamiSpeechTraceGuard =
            LamiSpeechTraceGuard(try { factory() } catch (_: Exception) { null })
    }
}
