package io.github.ninbyo02.lami.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTtsPcmStreamTest {
    @Test fun `first PCM is written before source EOS and completion waits for drain`() = runTest {
        val stream = AndroidTtsPcmStream(false)
        val drain = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val worker = async {
            stream.play({ object : AndroidTtsPcmSink {
                override suspend fun write(samples: ShortArray) { events += "play:${samples.size}" }
                override suspend fun drain() { events += "drain"; drain.await() }
                override fun close() { events += "closed" }
            } })
        }
        stream.begin(AndroidTtsPcmFormat(24_000, AndroidTtsPcmFormat.PCM_16, 1))
        stream.offer(pcm(shortArrayOf(1, 2, 3)), 10)
        runCurrent()
        assertEquals(listOf("play:3"), events)
        assertFalse(worker.isCompleted)
        stream.offer(pcm(shortArrayOf(4, 5)), 20)
        runCurrent()
        assertEquals(listOf("play:3", "play:2"), events)
        stream.end()
        runCurrent()
        assertEquals(listOf("play:3", "play:2", "drain"), events)
        assertFalse(worker.isCompleted)
        drain.complete(Unit)
        worker.await()
        assertEquals("closed", events.last())
    }

    @Test fun `stop discards buffered and late chunks and awaits sink release`() = runTest {
        val stream = AndroidTtsPcmStream(false)
        var writes = 0
        var released = false
        val worker = async {
            stream.play({ object : AndroidTtsPcmSink {
                override suspend fun write(samples: ShortArray) { writes++; awaitCancellation() }
                override suspend fun drain() = Unit
                override fun close() { released = true }
            } })
        }
        stream.begin(AndroidTtsPcmFormat(24_000, 2, 1))
        stream.offer(pcm(shortArrayOf(1)), 1)
        runCurrent()
        stream.offer(pcm(shortArrayOf(2)), 2)
        stream.stop()
        worker.cancelAndJoin()
        stream.offer(pcm(shortArrayOf(3)), 3)
        assertEquals(1, writes)
        assertTrue(released)
    }

    @Test fun `full callback queue fails explicitly without blocking producer`() = runTest {
        val stream = AndroidTtsPcmStream(false, maxQueuedBytes = 4)
        stream.begin(AndroidTtsPcmFormat(24_000, 2, 1))
        stream.offer(pcm(shortArrayOf(1, 2)), 1)
        stream.offer(pcm(shortArrayOf(3)), 2)
        val failure = runCatching { stream.play({ error("must not open output on overflow") }) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("overflow"))
    }

    @Test fun `PCM before format and partial frame at EOS fail explicitly`() = runTest {
        val missingFormat = AndroidTtsPcmStream(false)
        missingFormat.offer(byteArrayOf(0), 1)
        assertTrue(runCatching { missingFormat.play({ error("must not create output") }) }.isFailure)
        val partial = AndroidTtsPcmStream(false)
        partial.begin(AndroidTtsPcmFormat(24_000, 2, 2))
        partial.offer(byteArrayOf(0, 1, 2), 1)
        partial.end()
        val failure = runCatching { partial.play({ error("must not create output") }) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("partial PCM frame"))
    }

    @Test fun `source timeout ends missing callbacks without opening a player`() = runTest {
        val stream = AndroidTtsPcmStream(false, sourceTimeoutMs = 100)
        val failure = runCatching { stream.play({ error("must not create output") }) }.exceptionOrNull()
        assertNotNull(failure)
    }

    private fun pcm(samples: ShortArray): ByteArray = ByteBuffer.allocate(samples.size * 2)
        .order(ByteOrder.LITTLE_ENDIAN).apply { asShortBuffer().put(samples) }.array()
}
