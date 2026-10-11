package io.github.ninbyo02.lami.asr.platform.audio

import io.github.ninbyo02.lami.asr.foundation.audio.AudioInputState
import io.github.ninbyo02.lami.asr.foundation.audio.AudioInputStateObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAudioRecordInputTest {
    @Test
    fun `factory request is fixed to mic 16 kHz mono PCM16 with bounded frame buffer`() {
        val factory = RecordingFactory(minBufferBytes = 900)

        val input = AndroidAudioRecordInput.create(factory)

        assertEquals(
            AudioRecordRequest(AudioInputSource.MIC, 16_000, AudioChannel.MONO, AudioEncoding.PCM_16BIT),
            factory.request,
        )
        assertTrue(factory.bufferBytes >= 1_280)
        input.release()
        assertEquals(1, factory.record.releaseCount)
    }

    @Test
    fun `recording state observer is delegated to platform record`() {
        val factory = RecordingFactory(minBufferBytes = 1_280)
        val states = mutableListOf<AudioInputState>()
        val input = AndroidAudioRecordInput.create(factory)

        input.setRecordingStateObserver(AudioInputStateObserver(states::add))
        factory.record.emitState(AudioInputState.SILENCED)
        factory.record.emitState(AudioInputState.ACTIVE)

        assertEquals(listOf(AudioInputState.SILENCED, AudioInputState.ACTIVE), states)
        input.release()
    }

    @Test
    fun `communication source is used only when explicitly requested`() {
        val factory = RecordingFactory(minBufferBytes = 1_280)

        AndroidAudioRecordInput.create(
            factory = factory,
            source = AudioInputSource.VOICE_COMMUNICATION,
        ).release()

        assertEquals(
            AudioRecordRequest(
                AudioInputSource.VOICE_COMMUNICATION,
                16_000,
                AudioChannel.MONO,
                AudioEncoding.PCM_16BIT,
            ),
            factory.request,
        )
    }

    @Test
    fun `invalid minimum buffer failure is sanitized`() {
        val canary = "private platform details"
        val factory = RecordingFactory(minBufferBytes = -2, failure = IllegalStateException(canary))

        val failure = assertThrows(AudioCaptureInitializationException::class.java) {
            AndroidAudioRecordInput.create(factory)
        }

        assertEquals("audio capture initialization failed", failure.message)
        assertTrue(!failure.message.orEmpty().contains(canary))
    }

    @Test
    fun `created uninitialized record is released once and failure is sanitized`() {
        val factory = RecordingFactory(
            minBufferBytes = 1_280,
            record = RecordingPlatformRecord(initialized = false),
        )

        val failure = assertThrows(AudioCaptureInitializationException::class.java) {
            AndroidAudioRecordInput.create(factory)
        }

        assertEquals("audio capture initialization failed", failure.message)
        assertEquals(null, failure.cause)
        assertEquals(1, factory.record.releaseCount)
    }

    @Test
    fun `created record state exception is released once without leaking platform canary`() {
        val canary = "private AudioRecord state"
        val factory = RecordingFactory(
            minBufferBytes = 1_280,
            record = RecordingPlatformRecord(initializationFailure = IllegalStateException(canary)),
        )

        val failure = assertThrows(AudioCaptureInitializationException::class.java) {
            AndroidAudioRecordInput.create(factory)
        }

        assertEquals("audio capture initialization failed", failure.message)
        assertEquals(null, failure.cause)
        assertTrue(!failure.toString().contains(canary))
        assertEquals(1, factory.record.releaseCount)
    }

    @Test
    fun `factory does not swallow JVM fatal errors`() {
        val fatal = AssertionError("fatal sentinel")
        val factory = RecordingFactory(minBufferBytes = 1_280, failure = fatal)

        val thrown = assertThrows(AssertionError::class.java) {
            AndroidAudioRecordInput.create(factory)
        }

        assertTrue(thrown === fatal)
    }

    private class RecordingFactory(
        private val minBufferBytes: Int,
        private val failure: Throwable? = null,
        val record: RecordingPlatformRecord = RecordingPlatformRecord(),
    ) : AudioRecordPlatformFactory {
        var request: AudioRecordRequest? = null
        var bufferBytes = 0


        override fun minimumBufferBytes(request: AudioRecordRequest): Int {
            this.request = request
            failure?.let { throw it }
            return minBufferBytes
        }

        override fun create(request: AudioRecordRequest, bufferBytes: Int): PlatformAudioRecord {
            this.request = request
            this.bufferBytes = bufferBytes
            return record
        }
    }

    private class RecordingPlatformRecord(
        private val initialized: Boolean = true,
        private val initializationFailure: RuntimeException? = null,
    ) : PlatformAudioRecord {
        override val isInitialized: Boolean
            get() {
                initializationFailure?.let { throw it }
                return initialized
            }
        private var stateObserver = AudioInputStateObserver.NONE
        override fun routedDeviceId(): Int? = 3
        override fun setRecordingStateObserver(observer: AudioInputStateObserver) {
            stateObserver = observer
        }
        fun emitState(state: AudioInputState) = stateObserver.onStateChanged(state)
        override fun start() = Unit
        override fun read(target: ShortArray, offset: Int, length: Int): Int = length
        override fun stop() = Unit
        var releaseCount = 0
        override fun release() { releaseCount += 1 }
    }
}
