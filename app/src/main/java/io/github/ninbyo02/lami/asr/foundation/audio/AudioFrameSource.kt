package io.github.ninbyo02.lami.asr.foundation.audio

/** Source of bounded PCM frames. Implementations must make [stop] and [close] idempotent. */
interface AudioFrameSource : AutoCloseable {
    suspend fun read(): Pcm16Frame?

    fun stop()
}
