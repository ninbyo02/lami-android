package io.github.ninbyo02.lami.tts

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.ninbyo02.lami.BuildConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal interface AndroidTtsPcmPlayback {
    fun setListener(completed: (String, Boolean, String?) -> Unit, started: (String) -> Unit,
        progress: (String, TtsStreamingProgress) -> Unit)
    fun isActive(): Boolean
    fun isUsbActive(): Boolean
    fun target(): AudioDeviceInfo?
    fun begin(id: String, device: AudioDeviceInfo?): File
    fun beginSynthesis(id: String?, rate: Int, encoding: Int, channels: Int): Boolean
    fun audioAvailable(id: String?, bytes: ByteArray): Boolean
    fun synthesized(id: String?): Boolean
    fun failed(id: String?, reason: String): Boolean
    fun stop()
    suspend fun awaitCleanup()
    fun shutdown()
}

/** Captures engine PCM immediately; the temporary file is a sink, never a player input. */
internal class UsbTtsPlayback(
    private val context: Context,
    private val trace: (String) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val createSink: ((AndroidTtsPcmFormat, AudioDeviceInfo?, () -> Unit, () -> Unit) -> AndroidTtsPcmSink)? = null,
) : AndroidTtsPcmPlayback {
    private class Request(val id: String, val file: File, val device: AudioDeviceInfo?, val stream: AndroidTtsPcmStream) {
        lateinit var job: Job
        var synthesisEnded = false
    }
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private var request: Request? = null
    private val jobs = linkedSetOf<Job>()
    private var completed: (String, Boolean, String?) -> Unit = { _, _, _ -> }
    private var onAudioStarted: (String) -> Unit = {}
    private var progress: (String, TtsStreamingProgress) -> Unit = { _, _ -> }

    override fun setListener(completed: (String, Boolean, String?) -> Unit, started: (String) -> Unit,
        progress: (String, TtsStreamingProgress) -> Unit) {
        this.completed = completed; this.onAudioStarted = started; this.progress = progress
    }
    override fun isActive(): Boolean = synchronized(lock) { request != null }
    override fun isUsbActive(): Boolean = synchronized(lock) { request?.device != null }

    override fun target(): AudioDeviceInfo? {
        if (!BuildConfig.APPLICATION_ID.endsWith(".assistantprobe")) return null
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).singleOrNull {
            (it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE) &&
                it.productName.toString().contains("Jabra Evolve2 40 SE", ignoreCase = true)
        }
    }

    override fun begin(id: String, device: AudioDeviceInfo?): File = synchronized(lock) {
        stop()
        val pending = Request(id, File.createTempFile("lami-android-speech-", ".wav", context.cacheDir), device, AndroidTtsPcmStream(device != null))
        val previous = jobs.toList()
        request = pending
        val worker = scope.launch(start = CoroutineStart.LAZY) {
            var succeeded = false
            var failure: String? = null
            try {
                previous.forEach { it.join() }
                val underruns = pending.stream.play(createSink = { format ->
                    val healthy = { pending.stream.ensureHealthy() }
                    val started = {
                        progress(id, TtsStreamingProgress(TtsStreamEvent.PLAYBACK_STARTED, timestampNs = SystemClock.elapsedRealtimeNanos()))
                        onAudioStarted(id)
                        trace("android_pcm_started usb=${device != null} rate=${format.rate} channels=${format.channels}")
                    }
                    createSink?.invoke(format, device, healthy, started)
                        ?: AndroidTtsAudioTrackSink(context, format, device, healthy, started,
                            onRouteFailure = { reason -> pending.stream.fail(reason) })
                }, onChunk = { count, atNs ->
                    progress(id, TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, sampleCount = count, timestampNs = atNs))
                })
                succeeded = true
                progress(id, TtsStreamingProgress(TtsStreamEvent.PLAYBACK_COMPLETED, timestampNs = SystemClock.elapsedRealtimeNanos(), underruns = underruns))
            } catch (error: Exception) {
                failure = error.message ?: error.javaClass.simpleName
            } finally {
                pending.file.delete()
                val deliver = synchronized(lock) {
                    if (request === pending) { request = null; true } else false
                }
                if (deliver) handler.post {
                    trace("android_pcm_end id=$id succeeded=$succeeded reason=$failure")
                    completed(id, succeeded, failure)
                }
            }
        }
        pending.job = worker
        jobs.add(worker)
        worker.invokeOnCompletion { synchronized(lock) { jobs.remove(worker) } }
        worker.start()
        pending.file
    }

    override fun beginSynthesis(id: String?, rate: Int, encoding: Int, channels: Int): Boolean = withRequest(id) {
        runCatching { it.stream.begin(AndroidTtsPcmFormat(rate, encoding, channels)) }
            .onFailure { error -> it.stream.fail(error.message ?: "Invalid Android TTS PCM format") }
        progress(it.id, TtsStreamingProgress(TtsStreamEvent.GENERATION_STARTED, timestampNs = SystemClock.elapsedRealtimeNanos()))
    }
    override fun audioAvailable(id: String?, bytes: ByteArray): Boolean = withRequest(id) {
        it.stream.offer(bytes, SystemClock.elapsedRealtimeNanos())
    }
    override fun synthesized(id: String?): Boolean = withRequest(id) {
        if (!it.synthesisEnded) {
            it.synthesisEnded = true
            progress(it.id, TtsStreamingProgress(TtsStreamEvent.GENERATION_COMPLETED, timestampNs = SystemClock.elapsedRealtimeNanos()))
            it.stream.end()
        }
    }
    override fun failed(id: String?, reason: String): Boolean = withRequest(id) { it.stream.fail(reason) }
    private fun withRequest(id: String?, action: (Request) -> Unit): Boolean = synchronized(lock) {
        val active = request?.takeIf { it.id == id } ?: return false
        action(active)
        true
    }

    override fun stop(): Unit = synchronized(lock) {
        val previous = request
        request = null
        previous?.stream?.stop()
        previous?.job?.cancel()
        previous?.file?.delete()
    }
    override suspend fun awaitCleanup() {
        val previous = synchronized(lock) { jobs.toList() }
        previous.forEach { it.join() }
    }
    override fun shutdown() {
        stop()
        scope.launch { awaitCleanup(); scope.cancel() }
    }
}
