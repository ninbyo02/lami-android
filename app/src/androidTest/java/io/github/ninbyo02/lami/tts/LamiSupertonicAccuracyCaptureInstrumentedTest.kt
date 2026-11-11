package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.ui.screens.home.sanitizeAssistantTextForTts
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs

/** Captures pre-playback PCM from the selected Light5 bundle for a paired accuracy comparison. */
class LamiSupertonicAccuracyCaptureInstrumentedTest {
    @Test
    fun exportsSelectedLight5ToPcm() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val inputPath = args.getString("supertonicAccuracyCasesFile")
        assumeTrue("Supply a JSONL file using supertonicAccuracyCasesFile", inputPath != null)
        val input = File(checkNotNull(inputPath))
        require(input.isFile && input.length() in 1L..1_048_576L) { "Invalid cases file: $input" }
        val cases = input.readLines().filter(String::isNotBlank).map { line ->
            val row = JSONObject(line)
            val id = row.getString("id")
            val text = row.getString("text")
            require(SAFE_NAME.matches(id) && text.isNotBlank()) { "Invalid case ID or text: $id" }
            id to text
        }
        require(cases.size in 1..128 && cases.map { it.first }.distinct().size == cases.size) {
            "Cases file must contain 1–128 rows with unique IDs"
        }
        val expectedHash = checkNotNull(args.getString("supertonicAccuracyModelSha256")) {
            "Supply the deployed Light5 bundle SHA-256 using supertonicAccuracyModelSha256"
        }
        require(expectedHash.matches(Regex("[0-9a-f]{64}"))) { "Invalid model SHA-256" }
        val captureId = args.getString("supertonicAccuracyCaptureId") ?: "accuracy-${SystemClock.elapsedRealtime()}"
        require(SAFE_NAME.matches(captureId)) { "Invalid capture ID" }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = LamiSupertonicVoiceFactory.modelDirectory(context)
        check(directory == context.filesDir.resolve("supertonic3-light5")) { "Selected Light5 bundle is absent" }
        val readiness = LamiSupertonicVoiceFactory.availability(context)
        check(readiness.available) { readiness.message }
        val manifest = JSONObject(directory.resolve("manifest.json").readText())
        check(manifest.getString("bundle_sha256") == expectedHash) { "Deployed Light5 manifest does not match the requested model" }
        check(LamiSupertonicStreamingRuntime.available) { "Patched streaming JNI must be packaged" }
        val prefs = context.getSharedPreferences(LamiSupertonicVoiceFactory.PREFS, Context.MODE_PRIVATE)
        val threads = prefs.getInt("threads", 4)

        val output = context.filesDir.resolve("supertonic_accuracy_capture/$captureId")
        check(!output.exists() && output.mkdirs()) { "Cannot create capture directory: $output" }
        val rows = JSONArray()
        val result = JSONObject().put("schema_version", 1).put("status", "running")
            .put("capture_id", captureId).put("case_count", cases.size).put("cases_sha256", sha256(input))
            .put("device_fingerprint", Build.FINGERPRINT).put("model_directory", directory.absolutePath)
            .put("expected_model_sha256", expectedHash).put("sid", 0).put("steps", 5).put("threads", threads)
            .put("seed", 42).put("sample_rate_hz", LamiPcmContract.SAMPLE_RATE).put("pcm_format", "mono PCM16 LE WAV")
            .put("route", "assistant replay: common sanitizer, Supertonic Japanese readings, normal text chunks, adaptive stream")
            .put("timeouts_ms", JSONObject().put("prewarm", LOAD_TIMEOUT_MS).put("case", CASE_TIMEOUT_MS)
                .put("capture", CAPTURE_TIMEOUT_MS))
            .put("rows", rows)
        val summary = output.resolve("summary.json")
        fun save() {
            val temporary = output.resolve("summary.json.tmp")
            temporary.writeText(result.toString(2))
            check(temporary.renameTo(summary)) { "Cannot publish summary: $summary" }
        }

        var lastMetric: JSONObject? = null
        val session = LamiSupertonicSession(directory, speakerId = 0, steps = 5, threads = threads,
            adaptiveWindow = true, measured = { lastMetric = it })
        try {
            result.put("active_stage", "prewarm")
            save()
            withTimeout(CAPTURE_TIMEOUT_MS) {
                withTimeout(LOAD_TIMEOUT_MS) { session.prewarm() }
                result.put("active_stage", "case")
                for ((id, text) in cases) {
                    result.put("active_case", id)
                    save()
                    val spoken = sanitizeAssistantTextForTts(text)
                    val pieces = LamiSupertonicJapaneseText.split(spoken)
                    require(pieces.isNotEmpty()) { "No speakable text for case $id" }
                    val started = SystemClock.elapsedRealtimeNanos()
                    val file = output.resolve("$id.wav")
                    val temporary = output.resolve("$id.wav.part")
                    val segments = JSONArray()
                    var sampleCount = 0L
                    var peak = 0f
                    try {
                        withTimeout(CASE_TIMEOUT_MS) {
                            RandomAccessFile(temporary, "rw").use { wav ->
                                wav.seek(WAV_HEADER_BYTES.toLong())
                                for (piece in pieces) {
                                    val before = sampleCount
                                    lastMetric = null
                                    session.stream(piece) { pcm ->
                                        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                                        for (sample in pcm) {
                                            require(sample.isFinite() && sample in -1f..1f) { "Invalid PCM in case $id" }
                                            peak = maxOf(peak, abs(sample))
                                            bytes.putShort((sample * 32_767f).toInt().toShort())
                                        }
                                        wav.write(bytes.array())
                                        sampleCount += pcm.size
                                    }
                                    val metric = checkNotNull(lastMetric) { "Missing streaming metric for case $id" }
                                    check(metric.getString("model_sha256") == expectedHash) { "Loaded Light5 assets do not match the manifest" }
                                    check(metric.getLong("sample_count") == sampleCount - before) { "Incomplete PCM for case $id" }
                                    result.put("model_sha256", metric.getString("model_sha256"))
                                    segments.put(JSONObject().put("text", piece).put("metrics", metric))
                                }
                                check(sampleCount > 0) { "Empty PCM for case $id" }
                                wav.seek(0)
                                wav.write(waveHeader(sampleCount))
                            }
                        }
                        check(temporary.renameTo(file)) { "Cannot publish WAV for case $id" }
                    } finally {
                        temporary.delete()
                    }
                    rows.put(JSONObject().put("id", id).put("text", text).put("spoken_text", spoken)
                        .put("file", file.name).put("wav_sha256", sha256(file)).put("bytes", file.length())
                        .put("samples", sampleCount).put("seconds", sampleCount / LamiPcmContract.SAMPLE_RATE.toDouble())
                        .put("pcm_peak", peak.toDouble()).put("segments", segments)
                        .put("elapsed_ms", (SystemClock.elapsedRealtimeNanos() - started) / 1e6))
                    save()
                }
            }
            result.remove("active_stage")
            result.remove("active_case")
            result.put("status", "passed")
        } catch (failure: Throwable) {
            result.put("status", if (failure is TimeoutCancellationException) "timed_out" else "failed")
                .put("failure", failure.stackTraceToString())
            throw failure
        } finally {
            save()
            withContext(NonCancellable) { session.close() }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("supertonic_accuracy_summary", summary.absolutePath)
            })
        }
    }

    private fun waveHeader(samples: Long): ByteArray {
        val rate = LamiPcmContract.SAMPLE_RATE
        val dataBytes = Math.toIntExact(samples * 2)
        return ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(Math.addExact(36, dataBytes))
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
        }.array()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(32 * 1024)
            var bytes = stream.read(buffer)
            while (bytes != -1) {
                digest.update(buffer, 0, bytes)
                bytes = stream.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,100}")
        private const val WAV_HEADER_BYTES = 44
        private const val LOAD_TIMEOUT_MS = 180_000L
        private const val CASE_TIMEOUT_MS = 180_000L
        private const val CAPTURE_TIMEOUT_MS = 1_800_000L
    }
}
