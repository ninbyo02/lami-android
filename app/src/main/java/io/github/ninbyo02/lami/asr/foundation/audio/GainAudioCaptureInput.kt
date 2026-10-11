package io.github.ninbyo02.lami.asr.foundation.audio

/** Saturating PCM gain wrapper. The default capture path bypasses this wrapper. */
class GainAudioCaptureInput(
    private val delegate: AudioCaptureInput,
    private val gain: Double,
) : AudioCaptureInput {
    init {
        require(gain >= 1.0 && gain.isFinite())
    }

    override val isInitialized: Boolean
        get() = delegate.isInitialized

    override fun routedDeviceId(): Int? = delegate.routedDeviceId()

    override fun setRecordingStateObserver(observer: AudioInputStateObserver) =
        delegate.setRecordingStateObserver(observer)

    override fun start() = delegate.start()

    override fun read(target: ShortArray, offset: Int, length: Int): Int {
        val count = delegate.read(target, offset, length)
        if (count <= 0) return count
        for (index in offset until offset + count) {
            target[index] = (target[index] * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return count
    }

    override fun stop() = delegate.stop()

    override fun release() = delegate.release()
}
