package io.github.ninbyo02.lami.asr.foundation.audio

import java.util.concurrent.CancellationException

/** Why a capture stream stopped without including raw audio or platform error payloads. */
enum class AudioCaptureFailure {
    BUFFER_OVERFLOW,
    PERMISSION_REVOKED,
    ROUTE_CHANGED,
    START_FAILED,
    READ_FAILED,
    NOT_INITIALIZED,
    WORKER_TERMINATION_FAILED,
}

class AudioCaptureException(
    val failure: AudioCaptureFailure,
) : IllegalStateException("audio capture failed: ${failure.name}")

internal class AudioCaptureCancellationException :
    CancellationException("audio capture cancelled")

enum class FrameOfferResult {
    ACCEPTED,
    OVERFLOW,
    CLOSED,
}

/**
 * Memory-only bounded PCM16 handoff backed by preallocated slots. The capture worker can use
 * [offerPcm16] without allocating a frame object. Terminal and consumed slots are zeroized.
 */
class BoundedPcmFrameChannel(
    private val capacityFrames: Int = DEFAULT_CAPACITY_FRAMES,
) : AutoCloseable {
    private val monitor = Object()
    private val slots: Array<ShortArray>
    private var head = 0
    private var size = 0
    private var closed = false
    private var cancelled = false
    private var finished = false
    private var terminalFailure: AudioCaptureFailure? = null

    init {
        require(capacityFrames > 0) { "capacityFrames must be positive" }
        slots = Array(capacityFrames) { ShortArray(FRAME_SAMPLES) }
    }

    /** Convenience boundary for non-capture callers. The capture worker uses [offerPcm16]. */
    fun offer(frame: Pcm16Frame): FrameOfferResult {
        require(frame.sampleRateHz == SAMPLE_RATE_HZ && frame.sampleCount == FRAME_SAMPLES) {
            "frame must be 16 kHz PCM16 with 320 samples"
        }
        return offerPcm16(frame.copySamples())
    }

    /** Copies one 20 ms frame into a preallocated slot; does not retain [samples]. */
    fun offerPcm16(samples: ShortArray): FrameOfferResult = synchronized(monitor) {
        require(samples.size == FRAME_SAMPLES) { "PCM16 frame must contain 320 samples" }
        if (closed) return@synchronized FrameOfferResult.CLOSED
        if (size == capacityFrames) {
            failLocked(AudioCaptureFailure.BUFFER_OVERFLOW)
            return@synchronized FrameOfferResult.OVERFLOW
        }
        val tail = (head + size) % capacityFrames
        samples.copyInto(slots[tail])
        size += 1
        monitor.notifyAll()
        FrameOfferResult.ACCEPTED
    }

    /** Frame allocation and defensive ownership copy occur on the consumer, not capture, thread. */
    fun take(): Pcm16Frame? = synchronized(monitor) {
        while (size == 0 && !closed) {
            try {
                monitor.wait()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw AudioCaptureCancellationException()
            }
        }
        terminalFailure?.let { throw AudioCaptureException(it) }
        if (cancelled) throw AudioCaptureCancellationException()
        if (size == 0) return@synchronized null

        val slot = slots[head]
        val frame = Pcm16Frame(SAMPLE_RATE_HZ, slot)
        slot.fill(0)
        head = (head + 1) % capacityFrames
        size -= 1
        frame
    }

    fun fail(failure: AudioCaptureFailure) {
        synchronized(monitor) {
            if (closed) return
            failLocked(failure)
        }
    }

    fun cancel() {
        synchronized(monitor) {
            if (closed && !finished) return
            clearSlotsLocked()
            finished = false
            cancelled = true
            closed = true
            monitor.notifyAll()
        }
    }

    /** Stop accepting input, but deliver already-captured full frames before EOF. */
    fun finish() {
        synchronized(monitor) {
            if (closed) return
            finished = true
            closed = true
            monitor.notifyAll()
        }
    }

    override fun close() {
        synchronized(monitor) {
            clearSlotsLocked()
            finished = false
            closed = true
            monitor.notifyAll()
        }
    }

    private fun failLocked(failure: AudioCaptureFailure) {
        clearSlotsLocked()
        terminalFailure = failure
        closed = true
        monitor.notifyAll()
    }

    private fun clearSlotsLocked() {
        slots.forEach { it.fill(0) }
        head = 0
        size = 0
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_SAMPLES = 320
        const val DEFAULT_CAPACITY_FRAMES = 25
    }
}
