package io.github.ninbyo02.lami.asr.platform.sherpa

import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import io.github.ninbyo02.lami.asr.foundation.asr.AsrEngine
import io.github.ninbyo02.lami.asr.foundation.asr.AsrEvent
import io.github.ninbyo02.lami.asr.foundation.asr.AsrResult
import io.github.ninbyo02.lami.asr.foundation.asr.AsrSession
import io.github.ninbyo02.lami.asr.foundation.asr.VerifiedModel
import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import io.github.ninbyo02.lami.asr.foundation.model.ModelFileRole
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptOrigin
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptRevision
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptTiming

internal class SherpaOfflineAsrEngine : AsrEngine {
    private var recognizer: OfflineRecognizer? = null
    private val pcmBuffer = ReusablePcmBuffer(MAX_SESSION_SAMPLES)
    private var modelFingerprint: String? = null
    private var modelId: String? = null
    private var modelVersion: String? = null
    private var closed = false

    override suspend fun open(model: VerifiedModel): AsrSession {
        check(!closed) { "offline ASR engine is closed" }
        val active = recognizer
        if (active != null) {
            check(modelFingerprint == model.contentFingerprint) {
                "offline ASR engine model changed"
            }
            return newSession(active, checkNotNull(modelId), checkNotNull(modelVersion))
        }

        val created = OfflineRecognizer(config = recognizerConfig(model))
        recognizer = created
        modelFingerprint = model.contentFingerprint
        modelId = model.id
        modelVersion = model.version
        return newSession(created, model.id, model.version)
    }

    private fun newSession(
        activeRecognizer: OfflineRecognizer,
        activeModelId: String,
        activeModelVersion: String,
    ) = SherpaOfflineAsrSession(
        recognizer = activeRecognizer,
        modelId = activeModelId,
        modelVersion = activeModelVersion,
        pcm = pcmBuffer.acquire(),
        releasePcm = pcmBuffer::release,
    )

    override fun close() {
        if (closed) return
        closed = true
        recognizer?.release()
        recognizer = null
        modelFingerprint = null
    }

    private fun recognizerConfig(model: VerifiedModel): OfflineRecognizerConfig {
        val modelConfig = OfflineModelConfig().apply {
            transducer = OfflineTransducerModelConfig(
                encoder = model.fileFor(ModelFileRole.ENCODER).toString(),
                decoder = model.fileFor(ModelFileRole.DECODER).toString(),
                joiner = model.fileFor(ModelFileRole.JOINER).toString(),
            )
            tokens = model.fileFor(ModelFileRole.TOKENS).toString()
            numThreads = 4
            provider = "cpu"
            modelingUnit = "cjkchar"
        }
        return OfflineRecognizerConfig().apply {
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE_HZ,
                featureDim = 80,
                dither = 0.0f,
            )
            this.modelConfig = modelConfig
            decodingMethod = "greedy_search"
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val MAX_SESSION_SECONDS = 30
        const val MAX_SESSION_SAMPLES = SAMPLE_RATE_HZ * MAX_SESSION_SECONDS
    }
}

private class SherpaOfflineAsrSession(
    private val recognizer: OfflineRecognizer,
    private val modelId: String,
    private val modelVersion: String,
    private val pcm: ShortArray,
    private val releasePcm: (ShortArray) -> Unit,
) : AsrSession {
    private var sampleCount = 0
    private var startedAtMillis: Long? = null
    private var terminal = false
    private var closed = false

    override suspend fun accept(frame: Pcm16Frame): List<AsrEvent> {
        check(!terminal) { "offline ASR session is terminal" }
        require(frame.sampleRateHz == SAMPLE_RATE_HZ) {
            "offline ASR sample rate is unsupported"
        }
        check(sampleCount + frame.sampleCount <= MAX_SAMPLES) {
            "offline ASR utterance exceeds hard limit"
        }
        if (startedAtMillis == null) startedAtMillis = SystemClock.elapsedRealtime()
        val samples = frame.copySamples()
        try {
            samples.copyInto(pcm, destinationOffset = sampleCount)
            sampleCount += samples.size
        } finally {
            samples.fill(0)
        }
        return emptyList()
    }

    override suspend fun finish(): AsrResult {
        check(!terminal) { "offline ASR session is terminal" }
        check(sampleCount > 0) { "offline ASR session has no speech" }
        terminal = true
        val acceptedSampleCount = sampleCount
        val samples = FloatArray(acceptedSampleCount)
        for (index in 0 until acceptedSampleCount) {
            samples[index] = pcm[index] / PCM_SCALE
        }
        zeroizePcm()

        val stream = recognizer.createStream()
        val text = try {
            stream.acceptWaveform(samples, SAMPLE_RATE_HZ)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            samples.fill(0.0f)
            stream.release()
        }
        check(text.isNotBlank()) { "offline ASR produced an empty transcript" }
        val started = checkNotNull(startedAtMillis)
        val durationMillis =
            acceptedSampleCount.toLong() * MILLIS_PER_SECOND / SAMPLE_RATE_HZ
        val transcript = TranscriptRevision.final(
            text = text,
            timing = TranscriptTiming(started, started + durationMillis),
            confidence = null,
            origin = TranscriptOrigin(
                backend = "sherpa-onnx-offline",
                modelId = modelId,
                modelVersion = modelVersion,
            ),
        )
        return AsrResult(text, transcript)
    }

    override fun cancel() {
        if (terminal) return
        terminal = true
        zeroizePcm()
    }

    override fun close() {
        if (closed) return
        closed = true
        terminal = true
        sampleCount = 0
        releasePcm(pcm)
    }

    private fun zeroizePcm() {
        pcm.fill(0)
        sampleCount = 0
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val MAX_SAMPLES = SAMPLE_RATE_HZ * 30
        const val MILLIS_PER_SECOND = 1_000
        const val PCM_SCALE = 32_768.0f
    }
}

internal class ReusablePcmBuffer(maxSamples: Int) {
    private val samples = ShortArray(maxSamples)
    private var leased = false

    init {
        require(maxSamples > 0)
    }

    @Synchronized
    fun acquire(): ShortArray {
        check(!leased) { "PCM buffer is already leased" }
        leased = true
        samples.fill(0)
        return samples
    }

    @Synchronized
    fun release(buffer: ShortArray) {
        check(leased) { "PCM buffer is not leased" }
        check(buffer === samples) { "PCM buffer ownership mismatch" }
        samples.fill(0)
        leased = false
    }
}
