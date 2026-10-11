package io.github.ninbyo02.lami.tts

/** Monotonic first-audio timing contract for local TTS diagnostics. */
internal object LamiFirstAudioTiming {
    enum class StartKind(val diagnosticValue: String) {
        COLD("cold"),
        WARM("warm"),
    }

    data class Request(
        val requestId: Long,
        val startedAtNs: Long,
        val startKind: StartKind,
    )

    fun start(requestId: Long, startedAtNs: Long, startKind: StartKind): Request {
        require(requestId >= 0L) { "request id must be non-negative" }
        require(startedAtNs >= 0L) { "request start must use a non-negative monotonic timestamp" }
        return Request(requestId, startedAtNs, startKind)
    }

    fun elapsedMsSince(request: Request, atNs: Long): Long {
        require(atNs >= request.startedAtNs) { "event timestamp must not precede request start" }
        return (atNs - request.startedAtNs) / 1_000_000L
    }

    fun playbackStartedEvent(request: Request, firstAudioAtNs: Long): String =
        "first_audio=started " +
            "first_audio_start=${request.startKind.diagnosticValue} " +
            "first_audio_elapsed_ms=${elapsedMsSince(request, firstAudioAtNs)} " +
            "first_audio_clock=elapsedRealtimeNanos"
}
