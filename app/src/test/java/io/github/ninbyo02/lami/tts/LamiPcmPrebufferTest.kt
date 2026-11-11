package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiPcmPrebufferTest {
    @Test fun sourceEosCanReleaseAnExistingReserveWithoutAnotherPcmChunk() = runTest {
        val first = pcm(640, 0.1f)
        val chunks = channel(first)
        var ready = false
        val collected = async {
            LamiPcmPrebuffer.collect(chunks,
                LamiPcmPrebufferConfig(minimumMs = 640, maximumMs = 640,
                    startupReady = { _, _ -> ready }),
                nowMs = { testScheduler.currentTime }, onChunk = {})
        }
        runCurrent()
        assertFalse(collected.isCompleted)
        ready = true
        advanceTimeBy(20)
        runCurrent()
        assertArrayEquals(first, collected.await(), 0f)
        val tail = floatArrayOf(0.2f)
        chunks.send(tail)
        assertSame(tail, chunks.receive())
        chunks.close()
    }

    @Test fun cancellationDuringStartupGateWaitDoesNotConsumeFuturePcm() = runTest {
        val chunks = channel(pcm(640, 0.1f))
        val collected = async {
            LamiPcmPrebuffer.collect(chunks,
                LamiPcmPrebufferConfig(minimumMs = 640, maximumMs = 640,
                    startupReady = { _, _ -> false }),
                nowMs = { testScheduler.currentTime }, onChunk = {})
        }
        runCurrent()
        collected.cancelAndJoin()
        assertTrue(collected.isCancelled)
        val tail = floatArrayOf(0.3f)
        chunks.send(tail)
        assertSame(tail, chunks.receive())
        chunks.close()
    }

    @Test
    fun fastArrivalsStartAtMinimumAndLeaveLaterPcmForStreaming() = runTest {
        val first = pcm(160, 0.1f)
        val second = pcm(480, 0.2f)
        val later = pcm(640, 0.3f)
        val chunks = channel(first, second, later)
        val arrivals = ArrayDeque(listOf(0L, 80L))
        val notifications = mutableListOf<FloatArray>()

        val collected = LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(),
            nowMs = { arrivals.removeFirst() }, onChunk = { notifications += it })

        assertArrayEquals(first + second, collected, 0f)
        assertEquals(640 * 24, collected.size)
        assertEquals(2, notifications.size)
        assertSame(first, notifications[0])
        assertSame(second, notifications[1])
        assertSame(later, chunks.receive())
        chunks.close()
    }

    @Test
    fun longGapRaisesReserveToMaximumAndFollowingBurstDoesNotShrinkIt() = runTest {
        val pieces = arrayOf(pcm(160, 0.1f), pcm(480, 0.2f), pcm(640, 0.3f), pcm(640, 0.4f), pcm(480, 0.5f))
        val later = pcm(640, 0.6f)
        val chunks = channel(*pieces, later)
        val arrivals = ArrayDeque(listOf(0L, 2400L, 2401L, 2402L, 2403L))
        var notifications = 0

        val collected = LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(),
            nowMs = { arrivals.removeFirst() }, onChunk = { notifications++ })

        assertEquals(2400 * 24, collected.size)
        assertArrayEquals(concatenate(pieces.toList()), collected, 0f)
        assertEquals(5, notifications)
        assertTrue(arrivals.isEmpty())
        assertSame(later, chunks.receive())
        chunks.close()
    }

    @Test
    fun slowSourceReserveSurvivesFastPcmCatchUpBursts() = runTest {
        val pieces = arrayOf(pcm(160, 0.1f), pcm(480, 0.2f), pcm(640, 0.3f), pcm(640, 0.4f), pcm(480, 0.5f))
        val later = pcm(640, 0.6f)
        val chunks = channel(*pieces, later)
        val arrivals = ArrayDeque(listOf(0L, 1L, 2L, 3L, 4L))
        val sourceRequirements = ArrayDeque(listOf(640, 2400, 640, 640, 640))
        var notifications = 0

        val collected = LamiPcmPrebuffer.collect(chunks,
            LamiPcmPrebufferConfig(sourceMinimumMs = { sourceRequirements.removeFirst() }),
            nowMs = { arrivals.removeFirst() }, onChunk = { notifications++ })

        assertEquals(2400 * 24, collected.size)
        assertArrayEquals(concatenate(pieces.toList()), collected, 0f)
        assertEquals(5, notifications)
        assertTrue(sourceRequirements.isEmpty())
        assertSame(later, chunks.receive())
        chunks.close()
    }

    @Test
    fun gapAndSafetyMarginIncreaseReserveAboveMinimumWithoutAlwaysUsingMaximum() = runTest {
        val pieces = arrayOf(pcm(160, 0.1f), pcm(480, 0.2f), pcm(320, 0.3f))
        val chunks = channel(*pieces)
        val arrivals = ArrayDeque(listOf(0L, 800L, 810L))

        val collected = LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(),
            nowMs = { arrivals.removeFirst() }, onChunk = {})

        assertEquals((800 + 160) * 24, collected.size)
        assertArrayEquals(concatenate(pieces.toList()), collected, 0f)
        chunks.close()
    }

    @Test
    fun aSingleSufficientChunkDoesNotWaitForAnotherChunkOrEos() = runTest {
        val enough = pcm(640, 0.1f)
        val chunks = channel(enough)
        var notifications = 0
        val collected = LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(),
            nowMs = { 0L }, onChunk = { notifications++ })

        assertArrayEquals(enough, collected, 0f)
        assertEquals(1, notifications)
        assertFalse(chunks.isClosedForReceive)
        chunks.close()
    }

    @Test
    fun normalEosFlushesShortSpeechAndTinyTailExactlyOnce() = runTest {
        val first = pcm(160, 0.1f)
        val tail = floatArrayOf(0.2f, 0.3f, 0.4f)
        val chunks = channel(first, tail).also { it.close() }
        val arrivals = ArrayDeque(listOf(0L, 5000L))
        val notifications = mutableListOf<FloatArray>()

        val collected = LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(),
            nowMs = { arrivals.removeFirst() }, onChunk = { notifications += it })

        assertArrayEquals(first + tail, collected, 0f)
        assertEquals(2, notifications.size)
        assertSame(tail, notifications[1])
        assertTrue(chunks.receiveCatching().isClosed)
    }

    @Test
    fun cancellationWhileWaitingDiscardsReserveAndDoesNotConsumeLatePcm() = runTest {
        val chunks = channel(pcm(160, 0.1f))
        var notifications = 0
        val collect = async {
            LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(),
                nowMs = { testScheduler.currentTime }, onChunk = { notifications++ })
        }
        runCurrent()
        assertTrue(collect.isActive)
        assertEquals(1, notifications)

        collect.cancelAndJoin()
        val late = pcm(640, 0.2f)
        chunks.send(late)
        runCurrent()

        assertTrue(collect.isCancelled)
        assertEquals(1, notifications)
        assertSame(late, chunks.receive())
        chunks.close()
    }

    @Test
    fun failedEosPropagatesItsCauseInsteadOfReleasingShortPartialSpeech() = runTest {
        val cause = IllegalStateException("decoder failed")
        val chunks = channel(pcm(160, 0.1f)).also { it.close(cause) }
        var notifications = 0

        val result = runCatching {
            LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(),
                nowMs = { 0L }, onChunk = { notifications++ })
        }

        assertSame(cause, result.exceptionOrNull())
        assertEquals(1, notifications)
    }

    @Test
    fun emptyChannelAndEmptyPcmChunkAreRejected() = runTest {
        for (chunks in listOf(channel().also { it.close() }, channel(FloatArray(0)))) {
            val result = runCatching {
                LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(), nowMs = { 0L }, onChunk = {})
            }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            chunks.close()
        }
    }

    @Test
    fun nonFiniteAndOutOfRangePcmAreRejected() = runTest {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1.01f, -1.01f)) {
            val chunks = channel(floatArrayOf(value))
            val result = runCatching {
                LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(), nowMs = { 0L }, onChunk = {})
            }
            assertTrue("Invalid PCM value $value", result.exceptionOrNull() is IllegalArgumentException)
            chunks.close()
        }
    }

    @Test
    fun thirtySecondLimitIsCheckedBeforeReturningLargeFirstChunk() = runTest {
        val chunks = channel(FloatArray(24_000 * 30 + 1))
        val result = runCatching {
            LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(), nowMs = { 0L }, onChunk = {})
        }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        chunks.close()
    }

    @Test
    fun combinedChunksCannotExceedThirtySeconds() = runTest {
        val chunks = channel(FloatArray(24_000 * 30 - 1), floatArrayOf(0f, 0f))
        val result = runCatching {
            LamiPcmPrebuffer.collect(chunks, LamiPcmPrebufferConfig(minimumMs = 30_000, maximumMs = 30_000),
                nowMs = { 0L }, onChunk = {})
        }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        chunks.close()
    }

    @Test
    fun fixedReserveAndZeroMarginAreSupported() = runTest {
        val chunks = channel(pcm(320, -1f), pcm(320, 1f))
        val collected = LamiPcmPrebuffer.collect(chunks,
            LamiPcmPrebufferConfig(minimumMs = 640, maximumMs = 640, gapSafetyMarginMs = 0),
            nowMs = { 0L }, onChunk = {})
        assertEquals(640 * 24, collected.size)
        assertEquals(-1f, collected.first(), 0f)
        assertEquals(1f, collected.last(), 0f)
        chunks.close()
    }

    @Test
    fun invalidReserveConfigurationsAreRejected() {
        for (create in listOf(
            { LamiPcmPrebufferConfig(minimumMs = 0) },
            { LamiPcmPrebufferConfig(minimumMs = 641, maximumMs = 640) },
            { LamiPcmPrebufferConfig(maximumMs = 30_001) },
            { LamiPcmPrebufferConfig(gapSafetyMarginMs = -1) },
        )) assertThrows(IllegalArgumentException::class.java) { create() }
    }

    private fun pcm(milliseconds: Int, value: Float): FloatArray = FloatArray(milliseconds * 24) { value }

    private fun channel(vararg chunks: FloatArray): Channel<FloatArray> = Channel<FloatArray>(Channel.UNLIMITED).also {
        for (chunk in chunks) check(it.trySend(chunk).isSuccess)
    }

    private fun concatenate(chunks: List<FloatArray>): FloatArray = FloatArray(chunks.sumOf { it.size }).also { output ->
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(output, offset); offset += chunk.size }
    }
}
