package io.github.ninbyo02.lami.asr.foundation.audio

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CaptureFinishTest {
    @Test fun finishPreservesAllQueuedFramesThenEof() {
        val channel = BoundedPcmFrameChannel(3)
        for (id in 1..3) channel.offer(Pcm16Frame(16_000, ShortArray(320) { id.toShort() }))
        channel.finish()
        assertEquals(FrameOfferResult.CLOSED, channel.offer(Pcm16Frame(16_000, ShortArray(320))))
        for (id in 1..3) assertEquals(id.toShort(), channel.take()!!.copySamples().first())
        assertNull(channel.take()); channel.close()
    }
    @Test fun lifecycleCancelAfterFinishStillDiscards() {
        val channel = BoundedPcmFrameChannel(1)
        channel.offer(Pcm16Frame(16_000, ShortArray(320) { 12 }))
        channel.finish(); channel.cancel()
        assertThrows(CancellationException::class.java) { channel.take() }
    }
    @Test fun closeAfterFinishClearsUndrainedData() {
        val channel = BoundedPcmFrameChannel(1)
        channel.offer(Pcm16Frame(16_000, ShortArray(320) { 12 }))
        channel.finish(); channel.close(); assertNull(channel.take())
    }
}
