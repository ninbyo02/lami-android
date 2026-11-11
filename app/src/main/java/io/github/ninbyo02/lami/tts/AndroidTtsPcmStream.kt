package io.github.ninbyo02.lami.tts

import java.util.ArrayDeque
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

internal data class AndroidTtsPcmFormat(val rate: Int, val encoding: Int, val channels: Int) {
    init {
        require(rate in 8_000..48_000 && channels in 1..2) { "Unsupported Android TTS PCM rate/channels" }
        require(encoding in setOf(PCM_16, PCM_8, PCM_FLOAT)) { "Unsupported Android TTS PCM encoding" }
    }
    val sampleBytes get() = when (encoding) { PCM_8 -> 1; PCM_16 -> 2; else -> 4 }
    val frameBytes get() = sampleBytes * channels
    companion object {
        const val PCM_16 = 2
        const val PCM_8 = 3
        const val PCM_FLOAT = 4
    }
}

internal interface AndroidTtsPcmSink {
    val underruns: Int get() = 0
    suspend fun write(samples: ShortArray)
    suspend fun drain()
    fun close()
}

/** Binder callbacks only copy bounded buffers; one coroutine owns conversion and the output sink. */
internal class AndroidTtsPcmStream(
    private val usbStereo48k: Boolean,
    private val maxQueuedBytes: Int = 2 * 1024 * 1024,
    private val maxQueuedChunks: Int = 512,
    private val sourceTimeoutMs: Long = 60_000,
) {
    private data class Chunk(val bytes: ByteArray, val atNs: Long)
    private val lock = Any()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val chunks = ArrayDeque<Chunk>()
    private var queuedBytes = 0
    private var format: AndroidTtsPcmFormat? = null
    private var ended = false
    private var error: String? = null
    private var stopped = false

    fun begin(value: AndroidTtsPcmFormat): Unit = synchronized(lock) {
        if (stopped || ended || error != null) return@synchronized
        if (format != null) failLocked("Android TTS reported its PCM format twice") else format = value
        wake.trySend(Unit)
    }

    fun offer(bytes: ByteArray, atNs: Long): Unit = synchronized(lock) {
        if (stopped || ended || error != null || bytes.isEmpty()) return@synchronized
        when {
            format == null -> failLocked("Android TTS supplied PCM before its format")
            bytes.size > maxQueuedBytes - queuedBytes || chunks.size >= maxQueuedChunks ->
                failLocked("Android TTS PCM callback buffer overflow")
            else -> {
                chunks.add(Chunk(bytes.copyOf(), atNs))
                queuedBytes += bytes.size
            }
        }
        wake.trySend(Unit)
    }

    fun end(): Unit = synchronized(lock) { ended = true; wake.trySend(Unit); Unit }
    fun fail(message: String): Unit = synchronized(lock) { failLocked(message); wake.trySend(Unit); Unit }
    fun stop(): Unit = synchronized(lock) {
        stopped = true
        chunks.clear()
        queuedBytes = 0
        wake.trySend(Unit)
    }

    fun ensureHealthy(): Unit = synchronized(lock) {
        check(!stopped) { "Android TTS PCM stream stopped" }
        error?.let { throw IllegalStateException(it) }
    }

    private fun failLocked(message: String) {
        if (error == null) error = message
        chunks.clear()
        queuedBytes = 0
    }

    suspend fun play(
        createSink: (AndroidTtsPcmFormat) -> AndroidTtsPcmSink,
        onChunk: (Int, Long) -> Unit = { _, _ -> },
    ): Int {
        var converter: AndroidTtsPcmConverter? = null
        var sink: AndroidTtsPcmSink? = null
        var samplesWritten = 0L
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val next = synchronized(lock) {
                    check(!stopped) { "Android TTS PCM stream stopped" }
                    error?.let { throw IllegalStateException(it) }
                    if (converter == null && format != null) converter = AndroidTtsPcmConverter(checkNotNull(format), usbStereo48k)
                    val chunk = chunks.pollFirst()
                    if (chunk != null) queuedBytes -= chunk.bytes.size
                    Triple(chunk, ended, converter)
                }
                val chunk = next.first
                if (chunk != null) {
                    val activeConverter = checkNotNull(next.third)
                    val samples = activeConverter.convert(chunk.bytes)
                    if (activeConverter.lastInputSampleCount > 0) onChunk(activeConverter.lastInputSampleCount, chunk.atNs)
                    if (samples.isNotEmpty()) {
                        val output = sink ?: createSink(activeConverter.outputFormat).also { sink = it }
                        samplesWritten += samples.size
                        check(samplesWritten <= activeConverter.outputFormat.rate.toLong() * activeConverter.outputFormat.channels * 180) { "Android TTS speech exceeds three minutes" }
                        output.write(samples)
                    }
                } else if (next.second) {
                    val activeConverter = checkNotNull(next.third) { "Android TTS completed without PCM format" }
                    val tail = activeConverter.finish()
                    if (tail.isNotEmpty()) {
                        val output = sink ?: createSink(activeConverter.outputFormat).also { sink = it }
                        samplesWritten += tail.size
                        output.write(tail)
                    }
                    check(samplesWritten > 0) { "Android TTS completed without audio" }
                    val output = checkNotNull(sink)
                    output.drain()
                    return output.underruns
                } else {
                    withTimeout(sourceTimeoutMs) { wake.receive() }
                }
            }
        } finally {
            stop()
            sink?.close()
        }
    }
}
