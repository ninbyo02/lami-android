package io.github.ninbyo02.lami.tts

/** Debug observation only: no native underflow count is removed or reclassified. */
internal data class LamiPcmPlaybackSnapshot(
    val phase: String,
    val timestampNs: Long,
    val submittedFrames: Int,
    val headFrames: Int?,
    val pendingWriteFrames: Int,
    val rawUnderruns: Int,
    val observedUnderruns: Int,
)
