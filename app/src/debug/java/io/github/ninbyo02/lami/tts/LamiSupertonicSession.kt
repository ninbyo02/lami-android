package io.github.ninbyo02.lami.tts

import android.os.SystemClock
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Resident sherpa-onnx 1.13.6 Supertonic 3. No native cancellation API is available. */
internal class LamiSupertonicSession(
    val directory: File,
    private val speakerId: Int = 0,
    private val steps: Int = 5,
    private val threads: Int = 4,
    private val measured: (JSONObject) -> Unit = {},
) : LamiResidentPcmSession {
    private val dispatcher = Executors.newSingleThreadExecutor { task -> Thread(task, "LamiSupertonic").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val mutex = Mutex()
    private val generation = AtomicLong()
    private val closing = AtomicBoolean()
    private var engine: OfflineTts? = null
    private var bundleHash: String? = null
    private var loadStart: Long? = null
    private var loadEnd: Long? = null

    init {
        require(speakerId in 0..9 && steps in 1..30 && threads in 1..16)
    }

    override suspend fun prewarm() { withContext(dispatcher) { mutex.withLock { ensureLoaded() } } }

    private fun ensureLoaded() {
        check(!closing.get()) { "Supertonic session is closed" }
        if (engine != null) return
        for (name in FILES) require(directory.resolve(name).isFile) { "Missing Supertonic asset: $name" }
        if (bundleHash == null) {
            val canonical = FILES.sorted().joinToString(",", prefix = "[", postfix = "]") { name ->
                val file = directory.resolve(name)
                val hash = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) { val count = stream.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
                }
                "{\"bytes\":${file.length()},\"name\":\"$name\",\"sha256\":\"${hex(hash.digest())}\"}"
            }
            bundleHash = hex(MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)))
        }
        fun path(name: String) = directory.resolve(name).absolutePath
        loadStart = SystemClock.elapsedRealtimeNanos()
        engine = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            supertonic = OfflineTtsSupertonicModelConfig(
                durationPredictor = path(FILES[0]), textEncoder = path(FILES[1]),
                vectorEstimator = path(FILES[2]), vocoder = path(FILES[3]),
                ttsJson = path("tts.json"), unicodeIndexer = path("unicode_indexer.bin"), voiceStyle = path("voice.bin"),
            ), numThreads = threads, provider = "cpu", debug = false,
        ), maxNumSentences = 1))
        check(checkNotNull(engine).sampleRate() == 44_100) { "Unexpected Supertonic sample rate" }
        loadEnd = SystemClock.elapsedRealtimeNanos()
    }

    override suspend fun synthesize(text: String): FloatArray {
        val ready = SystemClock.elapsedRealtimeNanos()
        return withContext(dispatcher) {
            mutex.withLock {
                val token = generation.get()
                val loaded = engine != null
                ensureLoaded()
                currentCoroutineContext().ensureActive()
                val start = SystemClock.elapsedRealtimeNanos()
                val first = AtomicLong()
                // Do not throw from JNI callbacks. In 1.13.6 the callback return is
                // ignored by this model; cancelling a Job cannot interrupt an ORT run.
                val callback = LamiNativePcmCallback { chunk ->
                    if (chunk.isNotEmpty()) first.compareAndSet(0, SystemClock.elapsedRealtimeNanos())
                }
                val audio = checkNotNull(engine).generateWithConfigAndCallback(text,
                    GenerationConfig(speed = 1f, sid = speakerId, numSteps = steps,
                        extra = mapOf("lang" to "ja", "seed" to "42", "silence_duration" to "0")),
                    callback,
                )
                val nativeEnd = SystemClock.elapsedRealtimeNanos()
                currentCoroutineContext().ensureActive()
                if (generation.get() != token) throw CancellationException("Supertonic request stopped")
                require(audio.sampleRate == 44_100)
                val resampled = LamiPcmResampler.resample(audio.samples, audio.sampleRate, LamiPcmContract.SAMPLE_RATE)
                currentCoroutineContext().ensureActive()
                LamiPcmContract.validate(resampled.samples)
                val end = SystemClock.elapsedRealtimeNanos()
                val duration = resampled.samples.size / 24_000.0
                measured(JSONObject().put("engine", "supertonic3_sherpa").put("runtime_version", "1.13.6")
                    .put("backend_requested", "cpu").put("backend_actual", JSONObject.NULL)
                    .put("voice", if (speakerId < 5) "F${speakerId + 1}" else "M${speakerId - 4}")
                    .put("sid", speakerId).put("steps", steps).put("seed", 42).put("inter_chunk_silence_seconds", 0.0).put("threads_requested", threads)
                    .put("model_file", directory.name).put("model_sha256", bundleHash)
                    .put("model_hash_scope", "sha256(canonical sorted name/bytes/sha256 JSON of seven required files)")
                    .put("text_sha256", LamiKokoroMediaPipeSession.textHash(text))
                    .put("text_ready_at_ns", ready).put("loaded_before_request", loaded)
                    .put("model_load_started_at_ns", if (loaded) JSONObject.NULL else loadStart)
                    .put("model_load_completed_at_ns", if (loaded) JSONObject.NULL else loadEnd)
                    .put("load_ms", if (loaded) 0.0 else (checkNotNull(loadEnd) - checkNotNull(loadStart)) / 1e6)
                    .put("synthesis_started_at_ns", start).put("first_pcm_at_ns", first.get().takeIf { it > 0 } ?: JSONObject.NULL)
                    .put("first_pcm_sample_rate_hz", 44_100).put("native_synthesis_completed_at_ns", nativeEnd)
                    .put("lami_pcm_ready_at_ns", end).put("resample_ms", (end - nativeEnd) / 1e6)
                    .put("resample_clipped_samples", resampled.clippedSamples)
                    .put("synthesis_completed_at_ns", end).put("sample_count", resampled.samples.size)
                    .put("sample_rate_hz", 24_000).put("generation_rtf", (end - start) / 1e9 / duration)
                    .put("native_generation_rtf", (nativeEnd - start) / 1e9 / (audio.samples.size / 44_100.0))
                    .put("audible_start_at_ns", JSONObject.NULL).put("native_cancellation", "unavailable; wait for generate completion"))
                resampled.samples
            }
        }
    }

    override fun cancel() { generation.incrementAndGet() }

    override suspend fun close() {
        if (!closing.compareAndSet(false, true)) return
        cancel()
        // Serialization is essential: release() deletes a raw native pointer.
        withContext(NonCancellable + dispatcher) { mutex.withLock { engine?.release(); engine = null } }
        dispatcher.close()
    }

    companion object {
        val FILES = listOf("duration_predictor.int8.onnx", "text_encoder.int8.onnx", "vector_estimator.int8.onnx",
            "vocoder.int8.onnx", "tts.json", "unicode_indexer.bin", "voice.bin")
        private fun hex(data: ByteArray) = data.joinToString("") { "%02x".format(it) }
    }
}
