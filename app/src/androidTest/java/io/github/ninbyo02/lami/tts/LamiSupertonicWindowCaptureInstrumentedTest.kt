package io.github.ninbyo02.lami.tts

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Captures real native PCM without AudioTrack, UI, model copies, or preference changes.
 *
 * Optional instrumentation arguments: supertonicWindows=8,16,24,
 * supertonicRepeats=3, supertonicThreads=4, supertonicText or
 * supertonicTextsJson=["short text","long text"], supertonicModelPath, and
 * supertonicCaptureId. Output is files/supertonic_window_capture/<capture id>/.
 * PCM is mono float32 little endian at 24 kHz, suitable for independent F0 analysis.
 */
class LamiSupertonicWindowCaptureInstrumentedTest {
    @Test
    fun capturesNativeWindowTimingAndPreservesBufferedWaveform() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val windows = (args.getString("supertonicWindows") ?: "8,16,24")
            .split(',').map { it.trim().toInt() }
        require(windows.isNotEmpty() && windows.distinct().size == windows.size && windows.all { it in 1..256 })
        val repeats = (args.getString("supertonicRepeats") ?: "3").toInt().also { require(it in 1..10) }
        val threads = (args.getString("supertonicThreads") ?: "4").toInt().also { require(it in 1..16) }
        val texts = when {
            args.containsKey("supertonicTextsJson") -> JSONArray(checkNotNull(args.getString("supertonicTextsJson"))).let { array ->
                List(array.length()) { array.getString(it) }
            }
            args.containsKey("supertonicText") -> listOf(checkNotNull(args.getString("supertonicText")))
            else -> listOf(
                "こんにちは。ラミィがお手伝いします。",
                "今日は窓から入る柔らかな光を感じながら部屋を片付けて机の上に積まれていた本を種類ごとに分けて棚へ戻しそれから台所でお湯を沸かして温かいお茶を用意しゆっくりと深呼吸をしながらこれから始まる一日の予定を思い浮かべていました。",
                "シーピーユーとエヌピーユーの使用率を確認し、バッテリーの残量と温度を記録します。",
            )
        }
        require(texts.isNotEmpty() && texts.all { it.isNotBlank() })
        val directory = args.getString("supertonicModelPath")?.let(::File)
            ?: LamiSupertonicVoiceFactory.modelDirectory(context)
        assertTrue("Supertonic model must be provisioned: $directory",
            LamiSupertonicSession.FILES.all { directory.resolve(it).isFile })
        assertTrue("Patched streaming JNI must be packaged", LamiSupertonicStreamingRuntime.available)
        val captureId = args.getString("supertonicCaptureId") ?: "window-${SystemClock.elapsedRealtime()}"
        require(captureId.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid supertonicCaptureId" }
        val output = context.filesDir.resolve("supertonic_window_capture/$captureId")
        check(!output.exists()) { "Capture already exists: $output" }
        check(output.mkdirs()) { "Cannot create capture: $output" }
        val cases = JSONArray(texts.mapIndexed { index, text ->
            JSONObject().put("case", index).put("text", text)
                .put("code_points", text.codePointCount(0, text.length))
                .put("text_sha256", sha256(text.toByteArray(Charsets.UTF_8)))
        })
        val rows = JSONArray()
        val result = JSONObject().put("status", "running").put("capture_id", captureId)
            .put("device", Build.MODEL).put("hardware", Build.HARDWARE).put("sdk", Build.VERSION.SDK_INT)
            .put("sid", 0).put("steps", 5).put("seed", 42).put("speed", 1.0)
            .put("threads", threads).put("windows", JSONArray(windows)).put("repeats", repeats)
            .put("model_directory", directory.absolutePath).put("sample_rate_hz", LamiPcmContract.SAMPLE_RATE)
            .put("pcm_format", "mono IEEE754 float32 little-endian; resampled and clipped, no normalization")
            .put("timing_scope", "prewarmed native inference with immediate consumer; no AudioTrack or acoustic latency")
            .put("ordering", "one prewarmed session; rotate window order by case plus repeat")
            .put("cases", cases).put("rows", rows)
        val summary = output.resolve("summary.json")
        fun save() = summary.writeText(result.toString(2))
        save()
        var lastMetric: JSONObject? = null
        val session = LamiSupertonicSession(directory, speakerId = 0, steps = 5, threads = threads,
            measured = { lastMetric = it })
        try {
            session.prewarm()
            for ((case, text) in texts.withIndex()) {
                // Buffered inference has the same model, seed, duration
                // prediction, and denoising without vocoder windowing.
                val reference = withTimeout(180_000) { session.synthesize(text) }
                cases.getJSONObject(case).put("reference_pcm", writePcm(output, "case-$case-reference", reference))
                    .put("reference_metrics", checkNotNull(lastMetric))
                save()
                repeat(repeats) { repetition ->
                    // Recreating ORT sessions or grouping every request by its
                    // window confounds the comparison with thread-pool/load order.
                    // Each window runs at each position over three repetitions.
                    val rotation = (case + repetition) % windows.size
                    val order = windows.drop(rotation) + windows.take(rotation)
                    for ((orderIndex, window) in order.withIndex()) {
                        lastMetric = null
                        val chunks = mutableListOf<FloatArray>()
                        var firstOutputAt = 0L
                        val start = SystemClock.elapsedRealtimeNanos()
                        withTimeout(180_000) {
                            session.stream(text, window) { pcm ->
                                if (firstOutputAt == 0L) firstOutputAt = SystemClock.elapsedRealtimeNanos()
                                chunks += pcm.copyOf()
                            }
                        }
                        val end = SystemClock.elapsedRealtimeNanos()
                        val metric = checkNotNull(lastMetric)
                        val pcm = FloatArray(chunks.sumOf { it.size })
                        var offset = 0
                        for (chunk in chunks) { chunk.copyInto(pcm, offset); offset += chunk.size }
                        LamiPcmContract.validate(pcm)
                        assertEquals("Window $window changes duration for case $case", reference.size, pcm.size)
                        var maxError = 0f
                        var squaredError = 0.0
                        for (index in pcm.indices) {
                            val difference = pcm[index] - reference[index]
                            maxError = maxOf(maxError, abs(difference))
                            squaredError += difference.toDouble() * difference
                        }
                        val firstNativeAt = metric.getLong("first_pcm_at_ns")
                        val nativeStart = metric.getLong("synthesis_started_at_ns")
                        val nativeEnd = metric.getLong("native_synthesis_completed_at_ns")
                        val row = JSONObject().put("case", case).put("window_frames", window).put("repeat", repetition)
                            .put("request_order", rows.length()).put("window_order_index", orderIndex)
                            .put("window_order", JSONArray(order))
                            .put("first_native_pcm_ms", (firstNativeAt - nativeStart) / 1e6)
                            .put("first_output_pcm_ms", (firstOutputAt - start) / 1e6)
                            .put("native_total_ms", (nativeEnd - nativeStart) / 1e6)
                            .put("callback_blocked_ms", metric.getLong("native_callback_blocked_ns") / 1e6)
                            .put("native_excluding_callback_ms", metric.getDouble("native_generate_excluding_callback_ms"))
                            .put("stream_total_ms", (end - start) / 1e6)
                            .put("audio_seconds", pcm.size / LamiPcmContract.SAMPLE_RATE.toDouble())
                            .put("max_abs_error_from_buffered", maxError.toDouble())
                            .put("rms_error_from_buffered", sqrt(squaredError / pcm.size))
                            .put("pcm", writePcm(output, "case-$case-window-$window-repeat-$repetition", pcm))
                            .put("metrics", metric)
                        rows.put(row)
                        save()
                        instrumentation.sendStatus(0, Bundle().apply {
                            putString("supertonic_window_capture", output.absolutePath)
                            putString("supertonic_window_row", row.toString())
                        })
                        assertTrue("No early PCM for window $window, case $case", firstNativeAt in nativeStart until nativeEnd)
                        assertTrue("Window $window changes buffered PCM for case $case: max_abs=$maxError", maxError <= 2e-5f)
                    }
                }
            }
            result.put("status", "passed")
        } catch (failure: Throwable) {
            result.put("status", "failed").put("failure", failure.stackTraceToString())
            throw failure
        } finally {
            withContext(NonCancellable) { session.close() }
            save()
            instrumentation.sendStatus(0, Bundle().apply {
                putString("supertonic_window_capture", output.absolutePath)
                putString("supertonic_window_summary", summary.absolutePath)
            })
        }
    }

    private fun writePcm(directory: File, name: String, samples: FloatArray): JSONObject {
        val bytes = ByteBuffer.allocate(samples.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (sample in samples) putFloat(sample)
        }.array()
        val file = directory.resolve("$name.f32le")
        file.writeBytes(bytes)
        return JSONObject().put("file", file.name).put("samples", samples.size).put("bytes", bytes.size)
            .put("sha256", sha256(bytes)).put("peak", samples.maxOf { abs(it) }.toDouble())
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
