package io.github.ninbyo02.lami.asr.platform.sherpa

import java.nio.file.Path

/** Host-fakeable, handle-based boundary for the future sherpa-onnx JNI binding. */
internal interface SherpaNativeApi {
    fun createRecognizer(config: SherpaOnlineRecognizerConfig): Long

    fun createStream(recognizerHandle: Long): Long

    fun acceptWaveform(streamHandle: Long, sampleRateHz: Int, samples: FloatArray)

    fun isReady(recognizerHandle: Long, streamHandle: Long): Boolean

    fun decode(recognizerHandle: Long, streamHandle: Long)

    fun text(recognizerHandle: Long, streamHandle: Long): String

    fun isEndpoint(recognizerHandle: Long, streamHandle: Long): Boolean

    fun reset(recognizerHandle: Long, streamHandle: Long)

    fun inputFinished(streamHandle: Long)

    fun releaseStream(streamHandle: Long)

    fun releaseRecognizer(recognizerHandle: Long)
}

internal class SherpaOnlineRecognizerConfig(
    val encoder: Path,
    val decoder: Path,
    val joiner: Path,
    val tokens: Path,
    val sampleRateHz: Int,
    val provider: String,
) {
    override fun toString(): String =
        "SherpaOnlineRecognizerConfig(sampleRateHz=$sampleRateHz, provider=$provider)"
}
