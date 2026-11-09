package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import com.google.mediapipe.tasks.audio.texttospeech.SynthesisOptions
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeechOptions
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Uses the verified tasks-audio 1.2.2 API; the loaded model stays resident. */
internal class LamiKokoroMediaPipeSession(
    private val context: Context,
    val modelFile: File,
    val gpu: Boolean = false,
    private val voice: String = "jf_tebukuro",
    private val measured: (JSONObject) -> Unit = {},
) : LamiKokoroPcmSession {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LamiKokoro").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val mutex = Mutex()
    @Volatile private var engine: TextToSpeech? = null
    private var closed = false
    private var modelHash: String? = null
    private var loadStartNs: Long? = null
    private var loadEndNs: Long? = null

    suspend fun prewarm() = withContext(dispatcher) {
        mutex.withLock { ensureLoaded() }
    }

    private fun ensureLoaded(): Long {
        check(!closed) { "Kokoro session is closed" }
        if (engine != null) return 0L
        require(modelFile.isFile && modelFile.length() > 0) { "Kokoro model is missing" }
        if (modelHash == null) {
            val digest = MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            modelHash = digest.digest().joinToString("") { "%02x".format(it) }
        }
        val start = SystemClock.elapsedRealtimeNanos()
        loadStartNs = start
        val options = TextToSpeechOptions(
            baseOptions = BaseOptions.builder().setModelAssetPath(modelFile.absolutePath)
                .setDelegate(if (gpu) Delegate.GPU else Delegate.CPU).build(),
            defaultVoiceId = voice, defaultLocale = "ja", speakingRate = 1f,
            targetBucket = 0,
            cacheDirectory = context.cacheDir.resolve("kokoro_litert").apply { mkdirs() }.absolutePath,
        )
        engine = TextToSpeech.create(options)
        loadEndNs = SystemClock.elapsedRealtimeNanos()
        return checkNotNull(loadEndNs) - start
    }

    override suspend fun synthesize(text: String): FloatArray {
        val textReady = SystemClock.elapsedRealtimeNanos()
        return withContext(dispatcher) {
            mutex.withLock {
                val loadedBeforeRequest = engine != null
                val loadNs = ensureLoaded()
                currentCoroutineContext().ensureActive()
                val active = checkNotNull(engine)
                val start = SystemClock.elapsedRealtimeNanos()
                var first: Long? = null
                var size = 0
                val chunks = mutableListOf<FloatArray>()
                try {
                    active.synthesizeStream(text, SynthesisOptions(voiceId = voice, locale = "ja")).collect { chunk ->
                        currentCoroutineContext().ensureActive()
                        require(chunk.sampleRateHz == LamiPcmContract.SAMPLE_RATE) { "Unexpected Kokoro sample rate" }
                        val samples = chunk.samples
                        if (samples.isNotEmpty()) {
                            if (first == null) first = SystemClock.elapsedRealtimeNanos()
                            require(size.toLong() + samples.size <= 24_000L * 30) { "Kokoro output exceeds clip limit" }
                            require(samples.all { it.isFinite() && it in -1f..1f }) { "Invalid Kokoro PCM" }
                            chunks.add(samples.copyOf())
                            size += samples.size
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    val pcm = FloatArray(size)
                    var offset = 0
                    for (chunk in chunks) { chunk.copyInto(pcm, offset); offset += chunk.size }
                    LamiPcmContract.validate(pcm)
                    val end = SystemClock.elapsedRealtimeNanos()
                    measured(JSONObject().put("engine", "kokoro_mediapipe").put("runtime_version", "1.2.2")
                        .put("backend_requested", if (gpu) "gpu" else "cpu")
                        .put("backend_actual", JSONObject.NULL)
                        .put("voice", voice).put("model_file", modelFile.name)
                        .put("text_sha256", textHash(text)).put("model_sha256", modelHash)
                        .put("text_ready_at_ns", textReady).put("loaded_before_request", loadedBeforeRequest)
                        .put("model_load_started_at_ns", if (loadedBeforeRequest) JSONObject.NULL else loadStartNs)
                        .put("model_load_completed_at_ns", if (loadedBeforeRequest) JSONObject.NULL else loadEndNs)
                        .put("load_ms", loadNs / 1_000_000.0)
                        .put("synthesis_started_at_ns", start).put("first_pcm_at_ns", first)
                        .put("synthesis_completed_at_ns", end).put("sample_count", size)
                        .put("sample_rate_hz", LamiPcmContract.SAMPLE_RATE)
                        .put("generation_rtf", (end - start) / 1e9 / (size / 24_000.0))
                        .put("audible_start_at_ns", JSONObject.NULL))
                    pcm
                } catch (cancelled: CancellationException) {
                    active.cancel()
                    throw cancelled
                }
            }
        }
    }

    override fun cancel() {
        // Runs independently of the synthesis thread blocked in nativeStreamReadNext.
        engine?.let { runCatching { it.cancel() } }
    }

    override suspend fun close() {
        cancel()
        withContext(NonCancellable + dispatcher) {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    engine?.close()
                    engine = null
                }
            }
        }
        dispatcher.close()
    }

    companion object {
        fun textHash(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
