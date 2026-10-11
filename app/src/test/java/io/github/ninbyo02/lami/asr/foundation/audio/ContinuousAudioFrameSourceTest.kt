package io.github.ninbyo02.lami.asr.foundation.audio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousAudioFrameSourceTest {
    @Test
    fun `segment close preserves frames captured before the next segment opens`() {
        val input = BurstCaptureInput(frameCount = 4)
        val physical = AudioRecordFrameSource(
            input = input,
            permissionGate = MicrophonePermissionGate { true },
            capacityFrames = 8,
        )
        val continuous = ContinuousAudioFrameSource(physical)
        val firstSegment = continuous.openSegment()

        assertEquals(1, runImmediate { firstSegment.read() }?.copySamples()?.first()?.toInt())
        firstSegment.stop()
        firstSegment.close()
        input.allowRemainingFrames()
        assertTrue(input.allFramesProduced.await(5, TimeUnit.SECONDS))
        assertEquals(0, input.stopCount.get())
        assertEquals(0, input.releaseCount.get())
        val secondSegment = continuous.openSegment()
        val retained = List(3) {
            runImmediate { secondSegment.read() }?.copySamples()?.first()?.toInt()
        }
        secondSegment.close()
        continuous.close()
        continuous.close()

        assertEquals(listOf(2, 3, 4), retained)
        assertEquals(1, input.startCount.get())
        assertEquals(1, input.stopCount.get())
        assertEquals(1, input.releaseCount.get())
    }

    @Test
    fun `only one segment can borrow the continuous source at a time`() {
        val input = BurstCaptureInput(frameCount = 1)
        val continuous = ContinuousAudioFrameSource(
            AudioRecordFrameSource(input, MicrophonePermissionGate { true }),
        )
        val segment = continuous.openSegment()

        assertThrows(IllegalStateException::class.java) { continuous.openSegment() }

        segment.close()
        continuous.openSegment().close()
        continuous.close()
    }

    private class BurstCaptureInput(
        private val frameCount: Int,
    ) : AudioCaptureInput {
        val allFramesProduced = CountDownLatch(1)
        val startCount = AtomicInteger()
        val stopCount = AtomicInteger()
        val releaseCount = AtomicInteger()
        private val remainingFramesAllowed = CountDownLatch(1)
        private val unblockRead = CountDownLatch(1)
        private val readCount = AtomicInteger()
        override val isInitialized = true

        override fun routedDeviceId(): Int = 7

        override fun start() {
            startCount.incrementAndGet()
        }

        override fun read(target: ShortArray, offset: Int, length: Int): Int {
            val index = readCount.incrementAndGet()
            if (index <= frameCount) {
                if (index > 1) {
                    check(remainingFramesAllowed.await(5, TimeUnit.SECONDS)) {
                        "fake input remaining-frame timeout"
                    }
                }
                ShortArray(320) { index.toShort() }.copyInto(target, offset)
                if (index == frameCount) allFramesProduced.countDown()
                return 320
            }
            check(unblockRead.await(5, TimeUnit.SECONDS)) { "fake input stop timeout" }
            return -1
        }

        fun allowRemainingFrames() {
            remainingFramesAllowed.countDown()
        }

        override fun stop() {
            stopCount.incrementAndGet()
            remainingFramesAllowed.countDown()
            unblockRead.countDown()
        }

        override fun release() {
            releaseCount.incrementAndGet()
        }
    }

    private fun <T> runImmediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return checkNotNull(outcome) { "suspend block did not complete synchronously" }.getOrThrow()
    }
}
