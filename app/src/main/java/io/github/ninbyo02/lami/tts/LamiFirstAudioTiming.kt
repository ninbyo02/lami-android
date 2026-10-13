package io.github.ninbyo02.lami.tts

/** Monotonic first-audio timing contract for local TTS diagnostics. */
internal object LamiFirstAudioTiming {
    enum class StartKind(val diagnosticValue: String) {
        COLD("cold"),
        WARM("warm"),
    }

    data class Request(
        val requestId: Long,
        val requestOrdinal: Long,
        val startedAtNs: Long,
        val startKind: StartKind,
    )

    fun startKindForRequestOrdinal(requestOrdinal: Long): StartKind {
        require(requestOrdinal >= 0L) { "request ordinal must be non-negative" }
        return if (requestOrdinal == 0L) StartKind.COLD else StartKind.WARM
    }

    fun start(requestId: Long, requestOrdinal: Long, startedAtNs: Long, startKind: StartKind): Request {
        require(requestId >= 0L) { "request id must be non-negative" }
        require(requestOrdinal >= 0L) { "request ordinal must be non-negative" }
        require(startKind == startKindForRequestOrdinal(requestOrdinal)) {
            "request ordinal must match cold/warm start kind"
        }
        require(startedAtNs >= 0L) { "request start must use a non-negative monotonic timestamp" }
        return Request(requestId, requestOrdinal, startedAtNs, startKind)
    }

    fun elapsedMsSince(request: Request, atNs: Long): Long {
        require(atNs >= request.startedAtNs) { "event timestamp must not precede request start" }
        return (atNs - request.startedAtNs) / 1_000_000L
    }

    fun requestStartedEvent(request: Request): String =
        "first_audio=requested " +
            "first_audio_request_id=${request.requestId} " +
            "first_audio_request_ordinal=${request.requestOrdinal} " +
            "first_audio_start=${request.startKind.diagnosticValue} " +
            "first_audio_request_started_at_ns=${request.startedAtNs} " +
            "first_audio_elapsed_ms=0 " +
            "first_audio_clock=elapsedRealtimeNanos"

    fun playbackStartedEvent(request: Request, firstAudioAtNs: Long): String =
        "first_audio=started " +
            "first_audio_request_id=${request.requestId} " +
            "first_audio_request_ordinal=${request.requestOrdinal} " +
            "first_audio_start=${request.startKind.diagnosticValue} " +
            "first_audio_request_started_at_ns=${request.startedAtNs} " +
            "first_audio_at_ns=$firstAudioAtNs " +
            "first_audio_elapsed_ms=${elapsedMsSince(request, firstAudioAtNs)} " +
            "first_audio_clock=elapsedRealtimeNanos"
}
