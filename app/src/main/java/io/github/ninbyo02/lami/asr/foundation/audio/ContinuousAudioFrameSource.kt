package io.github.ninbyo02.lami.asr.foundation.audio

/**
 * Owns one physical capture source while exposing sequential, non-owning segment views.
 *
 * Closing a segment releases only that view. The physical source remains active so frames captured
 * while the previous segment is decoded stay in its bounded handoff for the next segment.
 */
internal class ContinuousAudioFrameSource(
    private val delegate: AudioFrameSource,
) : AutoCloseable {
    private val monitor = Any()
    private var activeSegment: SegmentSource? = null
    private var stopped = false
    private var closed = false

    fun openSegment(): AudioFrameSource = synchronized(monitor) {
        check(!closed) { "continuous audio source is closed" }
        check(!stopped) { "continuous audio source is stopped" }
        check(activeSegment == null) { "continuous audio source already has an active segment" }
        SegmentSource().also { activeSegment = it }
    }

    fun stop() {
        val shouldStop = synchronized(monitor) {
            if (stopped || closed) false else {
                stopped = true
                true
            }
        }
        if (shouldStop) delegate.stop()
    }

    override fun close() {
        val shouldClose = synchronized(monitor) {
            if (closed) false else {
                closed = true
                stopped = true
                activeSegment = null
                true
            }
        }
        if (shouldClose) delegate.close()
    }

    private inner class SegmentSource : AudioFrameSource {
        private var segmentClosed = false

        override suspend fun read(): Pcm16Frame? {
            synchronized(monitor) {
                check(!segmentClosed) { "audio segment is closed" }
                check(!closed) { "continuous audio source is closed" }
                check(activeSegment === this) { "audio segment is not active" }
            }
            return delegate.read()
        }

        override fun stop() = Unit

        override fun close() {
            synchronized(monitor) {
                if (segmentClosed) return
                segmentClosed = true
                if (activeSegment === this) activeSegment = null
            }
        }
    }
}
