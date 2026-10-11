package io.github.ninbyo02.lami.asr.foundation.vad

import io.github.ninbyo02.lami.asr.foundation.audio.AudioFrameSource
import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import java.util.ArrayDeque
import java.util.concurrent.CancellationException

/**
 * Applies VAD and endpoint policy before frames reach ASR.
 *
 * The delegate, VAD engine, and retained PCM are single-owner resources. A terminal finalize is
 * exposed as end-of-stream; no-speech and explicit discard are exposed as cancellation so the
 * coordinator cannot publish an empty transcript.
 */
class EndpointingAudioFrameSource(
    private val delegate: AudioFrameSource,
    private val vad: VadEngine,
    private val policy: VadEndpointPolicy,
) : AudioFrameSource {
    private val ready = ArrayDeque<Pcm16Frame>()
    private var terminal: VadEndpointTerminal? = null
    private var stopped = false
    private var closed = false

    override suspend fun read(): Pcm16Frame? {
        check(!closed) { "endpointing source is closed" }
        ready.pollFirst()?.let { return it }

        terminal?.let { return completeTerminal(it) }
        while (true) {
            val frame = delegate.read()
            val decision = if (frame == null) {
                policy.stopAndFinalize()
            } else {
                policy.onFrame(frame, vad.accept(frame))
            }
            ready.addAll(decision.framesForAsr)
            terminal = decision.terminal

            ready.pollFirst()?.let { return it }
            terminal?.let { return completeTerminal(it) }

            if (frame == null) {
                throw IllegalStateException("VAD policy did not terminate at end of input")
            }
        }
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        if (terminal == null) {
            terminal = policy.cancelAndDiscard().terminal
        }
        ready.clear()
        delegate.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        var firstFailure: Throwable? = null
        try {
            stop()
        } catch (failure: Throwable) {
            firstFailure = failure
        }
        try {
            delegate.close()
        } catch (failure: Throwable) {
            if (firstFailure == null) firstFailure = failure
        }
        try {
            vad.close()
        } catch (failure: Throwable) {
            if (firstFailure == null) firstFailure = failure
        }
        firstFailure?.let { throw it }
    }

    private fun completeTerminal(value: VadEndpointTerminal): Pcm16Frame? = when (value) {
        is VadEndpointTerminal.Finalize -> null
        is VadEndpointTerminal.Discard -> throw VadInputDiscardedException(value.reason)
        is VadEndpointTerminal.Failed -> {
            throw IllegalStateException("VAD endpoint failed: ${value.reason.name}")
        }
    }
}

class VadInputDiscardedException(
    val reason: DiscardReason,
) : CancellationException("VAD input discarded: ${reason.name}")
