package io.github.ninbyo02.lami.asr.foundation.asr

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame

/** Deterministic in-process engine for state-machine and UI development before JNI integration. */
class FakeAsrEngine(
    scriptedEvents: List<AsrEvent> = emptyList(),
) : AsrEngine {
    private val scriptedEvents = scriptedEvents.toList()
    private var closed = false

    override suspend fun open(model: VerifiedModel): AsrSession {
        check(!closed) { "engine is closed" }
        return FakeAsrSession(scriptedEvents)
    }

    override fun close() {
        closed = true
    }
}

private class FakeAsrSession(
    scriptedEvents: List<AsrEvent>,
) : AsrSession {
    private val scriptedEvents = scriptedEvents.toList()
    private var state = State.OPEN
    private var delivered = false
    private var endpointText = ""

    override suspend fun accept(frame: Pcm16Frame): List<AsrEvent> {
        checkOpen()
        if (delivered) return emptyList()
        delivered = true
        endpointText = scriptedEvents.filterIsInstance<AsrEvent.Endpoint>().lastOrNull()?.finalText.orEmpty()
        return scriptedEvents.toList()
    }

    override suspend fun finish(): AsrResult {
        checkOpen()
        state = State.FINISHED
        return AsrResult(endpointText)
    }

    override fun cancel() {
        if (state == State.OPEN) state = State.CANCELLED
    }

    override fun close() {
        state = State.CLOSED
    }

    private fun checkOpen() {
        check(state == State.OPEN) { "ASR session is not active: $state" }
    }

    private enum class State {
        OPEN,
        FINISHED,
        CANCELLED,
        CLOSED,
    }
}
