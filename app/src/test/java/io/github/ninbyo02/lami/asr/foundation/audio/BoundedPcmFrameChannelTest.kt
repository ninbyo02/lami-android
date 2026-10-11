package io.github.ninbyo02.lami.asr.foundation.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedPcmFrameChannelTest {
    @Test
    fun `raw PCM offer snapshots a reusable producer buffer`() {
        val channel = BoundedPcmFrameChannel(capacityFrames = 1)
        val reusable = ShortArray(320) { it.toShort() }

        assertEquals(FrameOfferResult.ACCEPTED, channel.offerPcm16(reusable))
        reusable.fill(Short.MIN_VALUE)

        val frame = channel.take()
        assertEquals(16_000, frame?.sampleRateHz)
        assertEquals(ShortArray(320) { it.toShort() }.toList(), frame?.copySamples()?.toList())
    }

    @Test
    fun `default 500 ms limit rejects the 26th frame and discards queued audio`() {
        val channel = BoundedPcmFrameChannel()
        repeat(25) { index ->
            assertEquals(
                FrameOfferResult.ACCEPTED,
                channel.offer(Pcm16Frame(16_000, ShortArray(320) { index.toShort() })),
            )
        }

        assertEquals(
            FrameOfferResult.OVERFLOW,
            channel.offer(Pcm16Frame(16_000, ShortArray(320) { 25 })),
        )
        val failure = assertThrows(AudioCaptureException::class.java) { channel.take() }
        assertEquals(AudioCaptureFailure.BUFFER_OVERFLOW, failure.failure)
    }

    @Test
    fun `frames are delivered FIFO before normal close`() {
        val channel = BoundedPcmFrameChannel(capacityFrames = 2)
        channel.offer(Pcm16Frame(16_000, ShortArray(320) { 1 }))
        channel.offer(Pcm16Frame(16_000, ShortArray(320) { 2 }))

        assertEquals(1, channel.take()?.copySamples()?.first()?.toInt())
        assertEquals(2, channel.take()?.copySamples()?.first()?.toInt())
        channel.close()
        assertEquals(null, channel.take())
    }

    @Test
    fun `cancel discards queued frames and remains cancellation terminal`() {
        val channel = BoundedPcmFrameChannel(capacityFrames = 2)
        channel.offer(Pcm16Frame(16_000, ShortArray(320) { 9 }))
        channel.cancel()
        channel.close()

        assertThrows(java.util.concurrent.CancellationException::class.java) { channel.take() }
        assertEquals(
            FrameOfferResult.CLOSED,
            channel.offer(Pcm16Frame(16_000, ShortArray(320) { 10 })),
        )
    }

    @Test
    fun `failure discards queued frames and first terminal reason wins`() {
        val channel = BoundedPcmFrameChannel(capacityFrames = 2)
        channel.offer(Pcm16Frame(16_000, ShortArray(320) { 7 }))
        channel.fail(AudioCaptureFailure.ROUTE_CHANGED)
        channel.fail(AudioCaptureFailure.READ_FAILED)

        val failure = assertThrows(AudioCaptureException::class.java) { channel.take() }
        assertEquals(AudioCaptureFailure.ROUTE_CHANGED, failure.failure)
    }

    @Test
    fun `overflow fails closed instead of silently dropping audio`() {
        val channel = BoundedPcmFrameChannel(capacityFrames = 1)

        assertEquals(
            FrameOfferResult.ACCEPTED,
            channel.offer(Pcm16Frame(16_000, ShortArray(320) { 1 })),
        )
        assertEquals(
            FrameOfferResult.OVERFLOW,
            channel.offer(Pcm16Frame(16_000, ShortArray(320) { 2 })),
        )

        val failure = assertThrows(AudioCaptureException::class.java) {
            channel.take()
        }
        assertEquals(AudioCaptureFailure.BUFFER_OVERFLOW, failure.failure)
        assertEquals("audio capture failed: BUFFER_OVERFLOW", failure.message)
    }
}
