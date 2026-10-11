package io.github.ninbyo02.lami.asr.platform.sherpa

import io.github.ninbyo02.lami.asr.foundation.asr.AsrEngine
import io.github.ninbyo02.lami.asr.foundation.asr.AsrSession
import io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel
import io.github.ninbyo02.lami.asr.foundation.model.ModelFileRole

internal class SherpaOnlineAsrEngine(
    private val nativeApi: SherpaNativeApi,
    private val maxDecodeStepsPerCall: Int,
) : AsrEngine {
    private var closed = false

    init {
        require(maxDecodeStepsPerCall > 0) { "maxDecodeStepsPerCall must be positive" }
    }

    override suspend fun open(model: VerifiedModel): AsrSession = synchronized(this) {
        check(!closed) { "sherpa engine is closed" }
        val config = SherpaOnlineRecognizerConfig(
            encoder = model.fileFor(ModelFileRole.ENCODER),
            decoder = model.fileFor(ModelFileRole.DECODER),
            joiner = model.fileFor(ModelFileRole.JOINER),
            tokens = model.fileFor(ModelFileRole.TOKENS),
            sampleRateHz = SAMPLE_RATE_HZ,
            provider = PROVIDER,
        )

        val recognizerHandle = nativeCreate("createRecognizer") {
            nativeApi.createRecognizer(config)
        }
        try {
            val streamHandle = nativeCreate("createStream") {
                nativeApi.createStream(recognizerHandle)
            }
            SherpaOnlineAsrSession(
                nativeApi = nativeApi,
                recognizerHandle = recognizerHandle,
                streamHandle = streamHandle,
                maxDecodeStepsPerCall = maxDecodeStepsPerCall,
            )
        } catch (failure: Throwable) {
            val releaseFailure = releaseRecognizer(recognizerHandle)
            if (failure.isFatalSherpaFailure()) throw failure
            if (releaseFailure != null && releaseFailure.isFatalSherpaFailure()) throw releaseFailure
            throw failure
        }
    }

    override fun close() {
        synchronized(this) {
            closed = true
        }
    }

    private fun nativeCreate(operation: String, create: () -> Long): Long {
        try {
            val handle = create()
            check(handle != INVALID_HANDLE) { "invalid native handle" }
            return handle
        } catch (failure: Throwable) {
            if (failure.isFatalSherpaFailure()) throw failure
            throw sanitizedNativeFailure(operation, failure)
        }
    }

    private fun releaseRecognizer(handle: Long): Throwable? = try {
        nativeApi.releaseRecognizer(handle)
        null
    } catch (failure: Throwable) {
        failure
    }

    private companion object {
        const val INVALID_HANDLE = 0L
        const val SAMPLE_RATE_HZ = 16_000
        const val PROVIDER = "cpu"
    }
}

internal fun Throwable.isFatalSherpaFailure(): Boolean =
    this is VirtualMachineError || this is ThreadDeath || this is LinkageError

internal fun sanitizedNativeFailure(operation: String, failure: Throwable): IllegalStateException =
    IllegalStateException("sherpa native $operation failed: ${failure.javaClass.simpleName}")
