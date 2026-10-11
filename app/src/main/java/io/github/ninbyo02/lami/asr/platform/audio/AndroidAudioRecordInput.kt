package io.github.ninbyo02.lami.asr.platform.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresPermission
import io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureInput
import io.github.ninbyo02.lami.asr.foundation.audio.AudioInputState
import io.github.ninbyo02.lami.asr.foundation.audio.AudioInputStateObserver
import io.github.ninbyo02.lami.asr.foundation.audio.MicrophonePermissionGate
import java.util.concurrent.Executor

internal enum class AudioInputSource { MIC, VOICE_COMMUNICATION }

internal enum class AudioChannel { MONO }

internal enum class AudioEncoding { PCM_16BIT }

internal data class AudioRecordRequest(
    val source: AudioInputSource,
    val sampleRateHz: Int,
    val channel: AudioChannel,
    val encoding: AudioEncoding,
)

internal interface PlatformAudioRecord {
    val isInitialized: Boolean

    fun routedDeviceId(): Int?

    fun setRecordingStateObserver(observer: AudioInputStateObserver) = Unit

    fun start()

    fun read(target: ShortArray, offset: Int, length: Int): Int

    fun stop()

    fun release()
}

internal interface AudioRecordPlatformFactory {
    fun minimumBufferBytes(request: AudioRecordRequest): Int

    fun create(request: AudioRecordRequest, bufferBytes: Int): PlatformAudioRecord
}

internal class AudioCaptureInitializationException :
    IllegalStateException("audio capture initialization failed")

/** Android AudioRecord adapter. All platform exceptions are sanitized at the factory boundary. */
internal class AndroidAudioRecordInput private constructor(
    private val record: PlatformAudioRecord,
) : AudioCaptureInput {
    override val isInitialized: Boolean
        get() = record.isInitialized

    override fun routedDeviceId(): Int? = record.routedDeviceId()

    override fun setRecordingStateObserver(observer: AudioInputStateObserver) =
        record.setRecordingStateObserver(observer)

    override fun start() = record.start()

    override fun read(target: ShortArray, offset: Int, length: Int): Int =
        record.read(target, offset, length)

    override fun stop() = record.stop()

    override fun release() = record.release()

    companion object {
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        fun create(
            factory: AudioRecordPlatformFactory = FrameworkAudioRecordFactory,
            source: AudioInputSource = AudioInputSource.MIC,
        ): AndroidAudioRecordInput {
            val request = AudioRecordRequest(
                source = source,
                sampleRateHz = SAMPLE_RATE_HZ,
                channel = AudioChannel.MONO,
                encoding = AudioEncoding.PCM_16BIT,
            )
            var created: PlatformAudioRecord? = null
            try {
                val minimum = factory.minimumBufferBytes(request)
                if (minimum <= 0) throw AudioCaptureInitializationException()
                val bufferBytes = maxOf(minimum, MINIMUM_OWNED_BUFFER_BYTES)
                created = factory.create(request, bufferBytes)
                if (!created.isInitialized) throw AudioCaptureInitializationException()
                return AndroidAudioRecordInput(created)
            } catch (_: RuntimeException) {
                try {
                    created?.release()
                } catch (_: RuntimeException) {
                    // Keep the public failure payload-free.
                }
                throw AudioCaptureInitializationException()
            }
        }

        private const val SAMPLE_RATE_HZ = 16_000
        private const val FRAME_SAMPLES = 320
        private const val BYTES_PER_SAMPLE = 2
        private const val MINIMUM_BUFFER_FRAMES = 2
        private const val MINIMUM_OWNED_BUFFER_BYTES =
            FRAME_SAMPLES * BYTES_PER_SAMPLE * MINIMUM_BUFFER_FRAMES
    }
}

internal class AndroidMicrophonePermissionGate(
    private val context: Context,
) : MicrophonePermissionGate {
    override fun isGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private object FrameworkAudioRecordFactory : AudioRecordPlatformFactory {
    override fun minimumBufferBytes(request: AudioRecordRequest): Int = AudioRecord.getMinBufferSize(
        request.sampleRateHz,
        request.channel.toFrameworkValue(),
        request.encoding.toFrameworkValue(),
    )

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun create(request: AudioRecordRequest, bufferBytes: Int): PlatformAudioRecord {
        val record = AudioRecord(
            request.source.toFrameworkValue(),
            request.sampleRateHz,
            request.channel.toFrameworkValue(),
            request.encoding.toFrameworkValue(),
            bufferBytes,
        )
        return FrameworkAudioRecord(record)
    }
}

private class FrameworkAudioRecord(
    private val record: AudioRecord,
) : PlatformAudioRecord {
    private val monitor = Any()
    private var stateObserver = AudioInputStateObserver.NONE
    private var lastState: AudioInputState? = null
    private var recordingExpected = false
    private var callbackRegistered = false
    private val directExecutor = Executor { command -> command.run() }
    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            val matching = configs.firstOrNull {
                it.clientAudioSessionId == record.audioSessionId
            }
            publishState(
                when {
                    matching == null -> AudioInputState.INACTIVE
                    Build.VERSION.SDK_INT >= 29 && matching.isClientSilenced ->
                        AudioInputState.SILENCED
                    else -> AudioInputState.ACTIVE
                },
            )
        }
    }

    override val isInitialized: Boolean
        get() = record.state == AudioRecord.STATE_INITIALIZED

    override fun routedDeviceId(): Int? = record.routedDevice?.id

    override fun setRecordingStateObserver(observer: AudioInputStateObserver) {
        if (Build.VERSION.SDK_INT < 29) {
            synchronized(monitor) { stateObserver = observer }
            return
        }
        val register = synchronized(monitor) {
            stateObserver = observer
            if (callbackRegistered) false else {
                callbackRegistered = true
                true
            }
        }
        if (register) {
            try {
                record.registerAudioRecordingCallback(directExecutor, recordingCallback)
            } catch (failure: RuntimeException) {
                synchronized(monitor) { callbackRegistered = false }
                throw failure
            }
        }
    }

    override fun start() {
        synchronized(monitor) {
            recordingExpected = true
            lastState = null
        }
        try {
            record.startRecording()
        } catch (failure: RuntimeException) {
            synchronized(monitor) { recordingExpected = false }
            throw failure
        }
        if (Build.VERSION.SDK_INT >= 29) {
            record.activeRecordingConfiguration?.let { configuration ->
                publishState(
                    if (configuration.isClientSilenced) {
                        AudioInputState.SILENCED
                    } else {
                        AudioInputState.ACTIVE
                    },
                )
            }
        }
    }

    override fun read(target: ShortArray, offset: Int, length: Int): Int =
        record.read(target, offset, length, AudioRecord.READ_BLOCKING)

    override fun stop() {
        synchronized(monitor) {
            recordingExpected = false
            lastState = null
        }
        record.stop()
    }

    override fun release() {
        val unregister = synchronized(monitor) {
            recordingExpected = false
            stateObserver = AudioInputStateObserver.NONE
            callbackRegistered.also { callbackRegistered = false }
        }
        try {
            if (unregister && Build.VERSION.SDK_INT >= 29) {
                record.unregisterAudioRecordingCallback(recordingCallback)
            }
        } finally {
            record.release()
        }
    }

    private fun publishState(state: AudioInputState) {
        val observer = synchronized(monitor) {
            if (!recordingExpected || lastState == state) return
            lastState = state
            stateObserver
        }
        try {
            observer.onStateChanged(state)
        } catch (_: RuntimeException) {
            // Capture-state monitoring must never interrupt audio delivery.
        }
    }
}

private fun AudioInputSource.toFrameworkValue(): Int = when (this) {
    AudioInputSource.MIC -> MediaRecorder.AudioSource.MIC
    AudioInputSource.VOICE_COMMUNICATION -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
}

private fun AudioChannel.toFrameworkValue(): Int = when (this) {
    AudioChannel.MONO -> AudioFormat.CHANNEL_IN_MONO
}

private fun AudioEncoding.toFrameworkValue(): Int = when (this) {
    AudioEncoding.PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
}
