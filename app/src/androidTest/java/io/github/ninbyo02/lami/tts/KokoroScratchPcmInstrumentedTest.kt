package io.github.ninbyo02.lami.tts

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.ui.screens.home.sanitizeAssistantTextForTts
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs

/** Captures real scratch inference for explicit emulator diagnostics. */
class KokoroScratchPcmInstrumentedTest {
    @Test fun japanesePlaybackStopsAndRetriesOnTheSameEngine() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = InstrumentationRegistry.getArguments().getString("kokoroScratchBundle")
        assumeTrue("Supply a diagnostic Kokoro bundle with kokoroScratchBundle", path != null)
        val bundle = LamiKokoroScratchBundle.read(File(checkNotNull(path)), diagnostic = true)
        val session = LamiKokoroScratchSession(bundle)
        val closed = CompletableDeferred<Unit>()
        val naturalCompletions = AtomicInteger()
        val starts = AtomicInteger()
        val owned = object : LamiResidentPcmSession by session {
            override suspend fun close() {
                try {
                    session.close()
                    closed.complete(Unit)
                } catch (failure: Exception) {
                    closed.completeExceptionally(failure)
                    throw failure
                }
            }
        }
        val engine = LamiNeuralPcmVoiceEngine(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            session = owned,
            playPcm = { pcm, started -> LamiPcmPlayer.play(instrumentation.targetContext, pcm, started) },
            onEvent = { _, event -> if (event == "playback_complete") naturalCompletions.incrementAndGet() },
        )

        suspend fun play(stopAfterStart: Boolean) {
            val started = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()
            val wasBusy = AtomicBoolean()
            engine.setOnAudioStarted { starts.incrementAndGet(); started.complete(Unit) }
            engine.setOnPlaybackStateChanged { busy ->
                if (busy) wasBusy.set(true) else if (wasBusy.get()) completed.complete(Unit)
            }
            engine.setOnError { message ->
                val failure = IllegalStateException(message)
                started.completeExceptionally(failure)
                completed.completeExceptionally(failure)
            }
            if (stopAfterStart) engine.speakQueued(PLAYBACK_TEXT) else engine.speak(PLAYBACK_TEXT)
            started.await()
            if (stopAfterStart) engine.stopForVoiceInput()
            completed.await()
            assertFalse(engine.isSpeaking.value)
        }

        try {
            withTimeout(180_000) {
                engine.prewarm()
                play(stopAfterStart = true)
                assertEquals("Stop must interrupt playback before its natural end", 0, naturalCompletions.get())
                play(stopAfterStart = false)
                assertEquals(2, starts.get())
                assertEquals("The same engine must drain after retry", 1, naturalCompletions.get())
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    engine.stopForVoiceInput()
                } finally {
                    engine.shutdown()
                    withTimeout(30_000) { closed.await() }
                }
            }
        }
        val result = JSONObject().put("status", "passed").put("bundle_identity", bundle.identity)
            .put("text", PLAYBACK_TEXT).put("audio_starts", starts.get())
            .put("natural_completions", naturalCompletions.get()).put("stopped_then_retried", true)
        val output = instrumentation.targetContext.filesDir.resolve("kokoro_scratch_playback_result.json")
        output.writeText(result.toString(2))
        instrumentation.sendStatus(0, Bundle().apply {
            putString("kokoro_scratch_playback", output.absolutePath)
        })
    }

    @Test fun exportsKnownIdsAndJapaneseTextToPcm() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val useChatChunks = arguments.getString("kokoroScratchChatChunks") == "true"
        val path = arguments.getString("kokoroScratchBundle")
        assumeTrue("Supply a diagnostic Kokoro bundle with kokoroScratchBundle", path != null)
        val directory = File(checkNotNull(path))
        val bundle = LamiKokoroScratchBundle.read(directory, diagnostic = true)
        val manifest = JSONObject(directory.resolve("manifest.json").readText())
        val probe = manifest.getJSONArray("validation").getJSONObject(0)
        val ids = probe.getJSONArray("ids").let { values -> LongArray(values.length()) { values.getLong(it) } }
        val captureId = arguments.getString("kokoroScratchCaptureId") ?: "kokoro-${SystemClock.elapsedRealtime()}"
        require(captureId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val output = instrumentation.targetContext.filesDir.resolve("kokoro_scratch_capture/$captureId")
        check(output.mkdirs()) { "Cannot create capture directory: $output" }
        val rows = JSONArray()
        val result = JSONObject().put("status", "running").put("bundle_identity", bundle.identity)
            .put("checkpoint_sha256", manifest.getString("checkpoint_sha256"))
            .put("diagnostic_only", true).put("sample_rate_hz", LamiPcmContract.SAMPLE_RATE)
            .put("chat_chunks", useChatChunks)
            .put("max_frames", bundle.maxFrames).put("rows", rows)
        val summary = output.resolve("summary.json")
        fun save() = summary.writeText(result.toString(2))

        val session = LamiKokoroScratchSession(bundle)
        try {
            withTimeout(600_000) {
                assertTrue("The shared ORT 1.27.1 JNI bridge must load", LamiKokoroScratchNative.available)
                result.put("ort_version", "1.27.1")
                session.prewarm()
                val direct = session.synthesizeDiagnostic(ids)
                assertEquals("Android must match the exported duration for the probe", probe.getInt("samples"), direct.size)
                rows.put(writeWave(output, "known-ids", direct).put("id", probe.getString("id"))
                    .put("input", "exported_phoneme_ids"))
                save()

                val cases = mutableListOf("ordinary-80" to LONG_TEXT)
                arguments.getString("kokoroScratchText")?.let { cases += "custom" to it }
                arguments.getString("kokoroScratchCasesFile")?.let { input ->
                    val file = File(input)
                    require(file.isFile && file.length() in 1L..1_048_576L)
                    for (line in file.readLines()) {
                        if (line.isBlank()) continue
                        val row = JSONObject(line)
                        cases += row.getString("id") to row.getString("text")
                    }
                    require(cases.size <= 128)
                }
                assertEquals(80, LONG_TEXT.codePointCount(0, LONG_TEXT.length))
                for ((index, case) in cases.withIndex()) {
                    val (id, text) = case
                    val spoken = sanitizeAssistantTextForTts(text)
                    val chunks = if (useChatChunks) LamiTtsTextChunks.split(spoken) else listOf(spoken)
                    require(chunks.isNotEmpty()) { "No speech text for $id" }
                    val started = SystemClock.elapsedRealtimeNanos()
                    val fragments = chunks.map { session.synthesize(it) }
                    val pcm = if (fragments.size == 1) fragments.single() else {
                        FloatArray(fragments.sumOf { it.size }).also { outputPcm ->
                            var offset = 0
                            for (fragment in fragments) {
                                fragment.copyInto(outputPcm, destinationOffset = offset)
                                offset += fragment.size
                            }
                        }
                    }
                    val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
                    rows.put(writeWave(output, "text-$index", pcm).put("id", id).put("text", text)
                        .put("input", "japanese_text").put("chunks", JSONArray(chunks))
                        .put("elapsed_ms", elapsed))
                    save()
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
                putString("kokoro_scratch_capture", output.absolutePath)
                putString("kokoro_scratch_summary", summary.absolutePath)
            })
        }
    }

    private fun writeWave(directory: File, stem: String, pcm: FloatArray): JSONObject {
        LamiPcmContract.validate(pcm)
        val dataBytes = pcm.size * 2
        val rate = LamiPcmContract.SAMPLE_RATE
        val wav = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataBytes)
            .put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            .putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
        for (sample in pcm) wav.putShort((sample * 32_767f).toInt().toShort())
        val file = directory.resolve("$stem.wav")
        val bytes = wav.array()
        file.writeBytes(bytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return JSONObject().put("file", file.name).put("wav_sha256", hash)
            .put("samples", pcm.size).put("seconds", pcm.size / rate.toDouble())
            .put("pcm_peak", pcm.maxOf { abs(it) }.toDouble())
    }

    companion object {
        private const val PLAYBACK_TEXT = "ストレージの空き容量を確認しました。"
        private const val LONG_TEXT = "今日は朝から窓を開けて部屋の空気を入れ替え棚にある本を整理して机の上の書類を片付けてから温かいお茶を入れて椅子に座り明日の予定を落ち着いてゆっくり確認しました。"
    }
}
