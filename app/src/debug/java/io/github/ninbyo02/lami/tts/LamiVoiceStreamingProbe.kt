package io.github.ninbyo02.lami.tts

import android.content.Context
import java.io.File
import android.os.SystemClock
import org.json.JSONObject
import org.json.JSONArray
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel

/** Live PCM prefixes for explicit Qwen selection; full parity replay remains diagnostic. */
internal object LamiVoiceStreamingProbe {
    private const val CAUSAL_DECODER_SHA256 = "05288ee2c763f80067139019ca66915bad150de705acc940200735f653c1b7c5"
    // Measured x86_64 Android 24->32 prefixes: max_abs=3.785e-6,
    // rms=1.789e-7. Keep both bounds; generic/diagnostic paths remain exact.
    internal val LIVE_PREFIX_TOLERANCE = LamiVoiceChunkProbeMath.PrefixTolerance(maxAbsolute = 1e-5, rms = 1e-6)

    internal fun requireStreamingDecoder(root: File) {
        val manifest = JSONObject(root.resolve("voice-text-bundle.json").readText())
        require(manifest.getJSONObject("sha256").getString("speech-decoder-dynamic-et14.pte") == CAUSAL_DECODER_SHA256) {
            "Qwen streaming requires the validated causal PCM decoder"
        }
        require(manifest.getInt("decoder_frames_min") == 2 && manifest.getInt("decoder_frames_max") == 256 &&
            manifest.getInt("samples_per_frame") == 1920 && manifest.getInt("sample_rate") == LamiPcmContract.SAMPLE_RATE) {
            "Qwen streaming decoder contract mismatch"
        }
    }

    suspend fun run(context: Context, root: File, text: String, progress: (String) -> Unit) {
        runSpeech(context, root, text, progress, verifyFullDecode = true)
    }

    /** Long-request lookahead: require natural codec EOS, then decode it once. */
    suspend fun prepareSpeech(context: Context, root: File, text: String, progress: (String) -> Unit): FloatArray {
        requireStreamingDecoder(root)
        val started = SystemClock.elapsedRealtime()
        return LamiVoiceDiagnostic.synthesizeText(context, root, text, progress = progress).also { pcm ->
            val bytes = java.nio.ByteBuffer.allocate(pcm.size * Float.SIZE_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            pcm.forEach(bytes::putFloat)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
            progress("pcm_sha256=$hash")
            progress("metric=segment_ready mode=natural_eos_single_decode samples=${pcm.size} ms=${SystemClock.elapsedRealtime() - started}")
        }
    }

    /** The consumer alone owns AudioTrack; generation never creates a second player. */
    suspend fun playPreparedSpeech(context: Context, pcm: FloatArray, onStarted: () -> Unit,
        onStreamingProgress: (TtsStreamingProgress) -> Unit) {
        val chunks = Channel<FloatArray>(1)
        try {
            chunks.send(pcm)
            chunks.close()
            val stats = LamiPcmStreamPlayer.play(context, chunks, onStarted, onStreamingProgress, stopOnEos = true)
            require(stats.samples == pcm.size) { "Prepared Qwen playback lost PCM samples" }
        } finally {
            chunks.cancel()
        }
    }

    suspend fun runSpeech(
        context: Context, root: File, text: String, progress: (String) -> Unit,
        onStarted: () -> Unit = {}, verifyFullDecode: Boolean = false,
        onStreamingProgress: (TtsStreamingProgress) -> Unit = {},
        preferEarlyStart: Boolean = false,
    ): FloatArray = coroutineScope {
        // Synthesis verifies the actual hashes before producing its first prefix.
        // Pin live streaming to the decoder whose source and prefix parity were
        // checked; do not assume arbitrary replacement PTEs are causal.
        if (!verifyFullDecode) requireStreamingDecoder(root)
        val started = SystemClock.elapsedRealtime()
        val generationPace = LamiPcmGenerationPace()
        val startupBudget = LamiQwenStartupBudget { generationPace.minimumBufferMs >= 2_400 }
        val earlyStartup = preferEarlyStart && !verifyFullDecode
        progress("metric=stream_startup_policy prefer_early_start=$earlyStartup codec_eos_gate=${!earlyStartup && !verifyFullDecode} minimum_ms=640")
        val decoderPreload = VerifiedDecoderPreload(
            preload = { LamiVoiceDecoderProcess.preloadDynamicDecoder(context, root, progress) },
            report = { status ->
                progress(if (status.isSuccess) "metric=decoder_preload_join status=success" else
                    "metric=decoder_preload_join status=failed class=${status.exceptionOrNull()?.javaClass?.simpleName}")
            },
        )
        onStreamingProgress(TtsStreamingProgress(TtsStreamEvent.GENERATION_STARTED,
            timestampNs = SystemClock.elapsedRealtimeNanos()))
        val result = LamiPcmStreamingPipeline.run(
            frames = { codes: LamiVoiceCodes -> codes.frames },
            generate = { emit ->
                coroutineScope {
                    val generationScope = this
                    LamiVoiceModuleCache.withSession(root) { session ->
                        LamiPreparedVoiceSynthesizer.generateText(root, text, session, onPrefix = { prefix ->
                            val oldReserve = generationPace.minimumBufferMs
                            generationPace.prefixProduced(prefix.frames * 1920, SystemClock.elapsedRealtime())
                            if (!earlyStartup && oldReserve != generationPace.minimumBufferMs) {
                                progress("metric=stream_buffer source=codec minimum_ms=${generationPace.minimumBufferMs}")
                            }
                            progress("stage=stream_codes frames=${prefix.frames} generated_ms=${SystemClock.elapsedRealtime() - started} audio_ms=${prefix.frames * 80L}")
                            emit(prefix)
                        }, onBundleVerified = {
                            decoderPreload.start(generationScope)
                        }, progress = progress)
                    }
                }
            },
            decode = { prefix ->
                // Join exactly once at the first real prefix; a preload failure
                // is reported, then the normal decoder load path can retry.
                decoderPreload.awaitBeforeDecode()
                val decodeStarted = SystemClock.elapsedRealtime()
                startupBudget.decodeStarted(prefix.frames * 1920, decodeStarted)
                LamiVoiceDecoderProcess.decode(context, root, prefix, progress).also {
                    startupBudget.decodeCompleted(prefix.frames * 1920, SystemClock.elapsedRealtime())
                    progress("metric=prefix_decode frames=${prefix.frames} ms=${SystemClock.elapsedRealtime() - decodeStarted}")
                }
            },
            play = { chunks ->
                var startupSamples = 0
                var firstPcmAtNs = 0L
                var playbackStarted = false
                LamiPcmStreamPlayer.play(context, chunks, {
                    onStarted()
                    progress("playback=started start_delay_ms=${SystemClock.elapsedRealtime() - started}")
                }, { event ->
                    if (event.event == TtsStreamEvent.PCM_CHUNK && !playbackStarted) {
                        startupSamples += event.sampleCount
                        if (firstPcmAtNs == 0L) firstPcmAtNs = event.timestampNs
                    }
                    if (event.event == TtsStreamEvent.PLAYBACK_STARTED) {
                        playbackStarted = true
                        progress("metric=stream_startup_buffer samples=$startupSamples audio_ms=${startupSamples / 24} wait_ms=${(event.timestampNs - firstPcmAtNs) / 1_000_000}")
                    }
                    onStreamingProgress(event)
                }, prebuffer = startupPrebuffer(earlyStartup, verifyFullDecode, generationPace, startupBudget),
                    stopOnEos = !verifyFullDecode,
                    onSnapshot = if (verifyFullDecode) null else { snapshot ->
                        progress("metric=pcm_track phase=${snapshot.phase} source_ns=${snapshot.timestampNs} written=${snapshot.submittedFrames} head=${snapshot.headFrames} pending=${snapshot.pendingWriteFrames} raw_underruns=${snapshot.rawUnderruns} observed_underruns=${snapshot.observedUnderruns}")
                    })
            },
            coalescePendingPrefixes = !verifyFullDecode,
            onGenerationComplete = {
                startupBudget.codecCompleted(it.frames * 1920)
                progress("stage=stream_eos frames=${it.frames} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            },
            onPcmComplete = { pcm ->
                progress("stage=stream_pcm_complete samples=${pcm.size} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
                onStreamingProgress(TtsStreamingProgress(TtsStreamEvent.GENERATION_COMPLETED,
                    sampleCount = pcm.size, timestampNs = SystemClock.elapsedRealtimeNanos()))
            },
            onPrefixMismatch = { prefix, previous, next ->
                val evidence = capturePrefixDifference(context, prefix, previous, next)
                progress("stage=stream_prefix_mismatch evidence=${evidence.absolutePath} ${LamiVoiceChunkProbeMath.prefixDifference(previous, next)}")
            },
            prefixTolerance = if (verifyFullDecode) LamiVoiceChunkProbeMath.PrefixTolerance() else LIVE_PREFIX_TOLERANCE,
        )
        val fullCodes = result.codes
        val assembled = result.pcm
        val stats = result.playback
        progress("playback=complete samples=${stats.samples} underruns=${stats.underruns}")
        require(stats.samples == assembled.size)
        val full = assembled
        val codeBytes = java.nio.ByteBuffer.allocate(fullCodes.values.size * 8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        fullCodes.values.forEach(codeBytes::putLong)
        val pcmBytes = java.nio.ByteBuffer.allocate(full.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        full.forEach(pcmBytes::putFloat)
        fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        progress("codes_sha256=${hash(codeBytes.array())} frames=${fullCodes.frames}")
        progress("pcm_sha256=${hash(pcmBytes.array())}")
        if (verifyFullDecode) {
            val decoded = LamiVoiceDecoderProcess.decode(context, root, fullCodes, progress)
            require(decoded.size == assembled.size)
            val difference = LamiVoiceChunkProbeMath.difference(decoded, assembled, decoded.size)
            check(difference.maxAbsolute == 0.0) { "Stream PCM differs from full decode" }
            progress("metric=stream_parity max_abs=${difference.maxAbsolute} rms=${difference.rms} frames=${fullCodes.frames}")
        }
        assembled
    }

    /** Early-start is a user tradeoff; slow generation must not turn it into an EOS wait. */
    internal fun startupPrebuffer(preferEarlyStart: Boolean, verifyFullDecode: Boolean,
        generationPace: LamiPcmGenerationPace, startupBudget: LamiQwenStartupBudget): LamiPcmPrebufferConfig? = when {
        verifyFullDecode -> null
        preferEarlyStart -> LamiPcmPrebufferConfig(minimumMs = 640, maximumMs = 640, gapSafetyMarginMs = 0)
        else -> LamiPcmPrebufferConfig(sourceMinimumMs = { generationPace.minimumBufferMs }, startupReady = startupBudget::canStart)
    }

    /** Generation owns the async job, so cancellation also waits for its binding cleanup. */
    internal class VerifiedDecoderPreload(
        private val preload: suspend () -> Unit,
        private val report: (Result<Unit>) -> Unit,
    ) {
        private val started = CompletableDeferred<Deferred<Result<Unit>>>()
        private val reported = AtomicBoolean()

        fun start(generationScope: CoroutineScope) {
            check(!started.isCompleted) { "Decoder preload was already started" }
            started.complete(generationScope.async {
                try {
                    preload()
                    Result.success(Unit)
                } catch (failure: Exception) {
                    // An internal preload timeout can fall back to normal load;
                    // cancellation of generation itself must still propagate.
                    currentCoroutineContext().ensureActive()
                    Result.failure(failure)
                }
            })
        }

        suspend fun awaitBeforeDecode() {
            val result = started.await().await()
            if (reported.compareAndSet(false, true)) report(result)
        }
    }

    /** Debug evidence contains generated codes/PCM, never raw text or model weights. */
    internal fun capturePrefixDifference(context: Context, codes: LamiVoiceCodes, previous: FloatArray, next: FloatArray): File {
        val difference = LamiVoiceChunkProbeMath.prefixDifference(previous, next)
        val directory = context.filesDir.resolve("diagnostics/qwen-stream-prefix-${UUID.randomUUID()}")
        check(directory.mkdirs())
        DataOutputStream(directory.resolve("previous.f32be").outputStream().buffered()).use { out -> previous.forEach(out::writeFloat) }
        DataOutputStream(directory.resolve("next.f32be").outputStream().buffered()).use { out -> next.forEach(out::writeFloat) }
        DataOutputStream(directory.resolve("codes.i64be").outputStream().buffered()).use { out -> codes.values.forEach(out::writeLong) }
        val frames = JSONArray()
        for (start in previous.indices step 1920) {
            val end = minOf(previous.size, start + 1920)
            val frame = LamiVoiceChunkProbeMath.prefixDifference(previous.copyOfRange(start, end), next.copyOfRange(start, end))
            frames.put(JSONObject().put("frame", start / 1920).put("changed_samples", frame.changedSamples)
                .put("max_abs", frame.maxAbsolute).put("rms", frame.rms))
        }
        val report = JSONObject().put("decoder_sha256", CAUSAL_DECODER_SHA256)
            .put("code_layout", "channel_major_16_by_frames_big_endian_int64")
            .put("pcm_layout", "mono_big_endian_float32_24000hz")
            .put("frames", codes.frames).put("previous_samples", previous.size).put("next_samples", next.size)
            .put("changed_samples", difference.changedSamples).put("max_abs", difference.maxAbsolute).put("rms", difference.rms)
            .put("first_changed", difference.firstChanged).put("last_changed", difference.lastChanged)
            .put("max_difference_index", difference.maxDifferenceIndex).put("per_frame", frames)
        for (threshold in listOf(1e-7, 1e-6, 1e-5, 1e-4)) {
            report.put("changed_above_$threshold", previous.indices.count { kotlin.math.abs(previous[it].toDouble() - next[it].toDouble()) > threshold })
        }
        directory.resolve("summary.json").writeText(report.toString(2))
        return directory
    }
}
