package io.github.ninbyo02.lami.asr.platform.sherpa

import io.github.ninbyo02.lami.asr.foundation.asr.AsrEvent
import io.github.ninbyo02.lami.asr.foundation.asr.AsrResult
import io.github.ninbyo02.lami.asr.foundation.asr.AsrSession
import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal class SherpaOnlineAsrSession internal constructor(
    private val nativeApi: SherpaNativeApi,
    private val recognizerHandle: Long,
    private val streamHandle: Long,
    private val maxDecodeStepsPerCall: Int,
) : AsrSession {
    private var state = State.OPEN
    private var ownsStream = true
    private var ownsRecognizer = true
    private var lastPartial = ""

    init {
        require(maxDecodeStepsPerCall > 0) { "maxDecodeStepsPerCall must be positive" }
    }

    override suspend fun accept(frame: Pcm16Frame): List<AsrEvent> {
        checkOpen()
        require(frame.sampleRateHz == SAMPLE_RATE_HZ) { "sherpa session requires 16 kHz PCM" }
        try {
            coroutineContext.ensureActive()
            val pcm = frame.copySamples()
            val normalized = FloatArray(pcm.size) { index -> pcm[index] / PCM16_SCALE }
            nativeCall("acceptWaveform") {
                nativeApi.acceptWaveform(streamHandle, frame.sampleRateHz, normalized)
            }

            val events = mutableListOf<AsrEvent>()
            var decodeSteps = 0
            while (nativeCall("isReady") { nativeApi.isReady(recognizerHandle, streamHandle) }) {
                coroutineContext.ensureActive()
                if (decodeSteps == maxDecodeStepsPerCall) failDecodeLimit()
                nativeCall("decode") { nativeApi.decode(recognizerHandle, streamHandle) }
                decodeSteps += 1
                coroutineContext.ensureActive()

                val text = nativeCall("text") { nativeApi.text(recognizerHandle, streamHandle) }
                coroutineContext.ensureActive()
                val endpoint = nativeCall("isEndpoint") {
                    nativeApi.isEndpoint(recognizerHandle, streamHandle)
                }
                coroutineContext.ensureActive()
                if (endpoint) {
                    // This app finalizes one ASR session at the first endpoint. Do not reset the
                    // native stream here: finish() must still be able to drain and read the
                    // endpoint result. Reset is only valid when the same stream continues with a
                    // subsequent utterance.
                    events += AsrEvent.Endpoint(text)
                    lastPartial = ""
                    break
                }
                if (text.isNotEmpty() && text != lastPartial) {
                    events += AsrEvent.Partial(text)
                }
                lastPartial = text
            }
            coroutineContext.ensureActive()
            return events
        } catch (failure: CancellationException) {
            cancelForCoroutine()
        }
    }

    override suspend fun finish(): AsrResult {
        checkOpen()
        try {
            coroutineContext.ensureActive()
            nativeCall("inputFinished") { nativeApi.inputFinished(streamHandle) }
            drainReadyWork()
            coroutineContext.ensureActive()
            val finalText = nativeCall("text") { nativeApi.text(recognizerHandle, streamHandle) }
            coroutineContext.ensureActive()
            state = State.FINISHED
            return AsrResult(finalText)
        } catch (failure: CancellationException) {
            cancelForCoroutine()
        }
    }

    override fun cancel() {
        if (state != State.OPEN) return
        state = State.CANCELLED
        releaseOwnedResources()?.let { throwCleanupFailure(it) }
    }

    override fun close() {
        if (state == State.CLOSED || state == State.CANCELLED) return
        state = State.CLOSED
        releaseOwnedResources()?.let { throwCleanupFailure(it) }
    }

    private suspend fun drainReadyWork() {
        var decodeSteps = 0
        while (nativeCall("isReady") { nativeApi.isReady(recognizerHandle, streamHandle) }) {
            coroutineContext.ensureActive()
            if (decodeSteps == maxDecodeStepsPerCall) failDecodeLimit()
            nativeCall("decode") { nativeApi.decode(recognizerHandle, streamHandle) }
            decodeSteps += 1
            coroutineContext.ensureActive()
        }
    }

    private fun checkOpen() {
        check(state == State.OPEN) { "sherpa session is not active" }
    }

    private inline fun <T> nativeCall(operation: String, call: () -> T): T {
        try {
            return call()
        } catch (failure: Throwable) {
            if (failure.isFatalSherpaFailure()) {
                state = State.CLOSED
                releaseOwnedResources()
                throw failure
            }
            failTerminal(operation, failure)
        }
    }

    private fun failTerminal(operation: String, failure: Throwable): Nothing {
        state = State.FAILED
        val cleanup = releaseOwnedResources()
        if (cleanup != null && cleanup.failure.isFatalSherpaFailure()) throw cleanup.failure
        throw sanitizedNativeFailure(operation, failure)
    }

    private fun releaseOwnedResources(): CleanupFailure? {
        var firstFailure: CleanupFailure? = null
        if (ownsStream) {
            ownsStream = false
            try {
                nativeApi.releaseStream(streamHandle)
            } catch (failure: Throwable) {
                firstFailure = CleanupFailure("releaseStream", failure)
            }
        }
        if (ownsRecognizer) {
            ownsRecognizer = false
            try {
                nativeApi.releaseRecognizer(recognizerHandle)
            } catch (failure: Throwable) {
                val current = firstFailure
                if (current == null ||
                    (!current.failure.isFatalSherpaFailure() && failure.isFatalSherpaFailure())
                ) {
                    firstFailure = CleanupFailure("releaseRecognizer", failure)
                }
            }
        }
        return firstFailure
    }

    private fun throwCleanupFailure(cleanup: CleanupFailure): Nothing {
        if (cleanup.failure.isFatalSherpaFailure()) throw cleanup.failure
        throw sanitizedNativeFailure(cleanup.operation, cleanup.failure)
    }

    private fun cancelForCoroutine(): Nothing {
        state = State.CANCELLED
        val cleanup = releaseOwnedResources()
        if (cleanup != null && cleanup.failure.isFatalSherpaFailure()) throw cleanup.failure
        throw CancellationException("sherpa session cancelled")
    }

    private fun failDecodeLimit(): Nothing {
        state = State.FAILED
        val cleanup = releaseOwnedResources()
        if (cleanup != null && cleanup.failure.isFatalSherpaFailure()) throw cleanup.failure
        throw IllegalStateException("sherpa native decode limit exceeded")
    }

    private data class CleanupFailure(val operation: String, val failure: Throwable)

    private enum class State {
        OPEN,
        FINISHED,
        CANCELLED,
        FAILED,
        CLOSED,
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val PCM16_SCALE = 32768.0f
    }
}
