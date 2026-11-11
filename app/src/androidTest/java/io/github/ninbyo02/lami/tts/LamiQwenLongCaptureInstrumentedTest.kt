package io.github.ninbyo02.lami.tts

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Actual Qwen generation/prefetch with a timed PCM consumer; never opens AudioTrack. */
class LamiQwenLongCaptureInstrumentedTest {
    @Test fun capturesLongGenerationAndPrefetchWithoutPlayingSound() = runBlocking<Unit> {
        val registry = InstrumentationRegistry.getInstrumentation()
        val context = registry.targetContext
        val args = InstrumentationRegistry.getArguments()
        val id = args.getString("qwenCaptureId") ?: "qwen-${SystemClock.elapsedRealtime()}"
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val text = args.getString("qwenCaptureText") ?: "今日は天気が良いので、窓を開けて空気を入れ替えます。準備ができたら、一緒に散歩へ出かけましょう。帰ってからは温かいお茶を飲みながら、明日の予定をゆっくり確認します。最後の一文まで落ち着いて読み上げます。"
        val queued = args.getString("qwenQueuedSpeech") == "true"
        val output = context.filesDir.resolve("qwen_long_capture/$id")
        check(!output.exists() && output.mkdirs())
        val rows = CopyOnWriteArrayList<JSONObject>()
        val events = CopyOnWriteArrayList<TtsStreamingProgress>()
        val done = CompletableDeferred<Unit>()
        val wasBusy = AtomicBoolean()
        val began = SystemClock.elapsedRealtimeNanos()
        val result = JSONObject().put("status", "running").put("device", Build.MODEL)
            .put("text", text).put("text_code_points", text.codePointCount(0, text.length)).put("queued_speech", queued)
            .put("prefer_early_start", false)
            .put("consumer", "No AudioTrack; starts each validated PCM and waits its duration. Not an acoustic or route test.")
        fun save() {
            result.put("segments", JSONArray(rows.toList()))
            output.resolve("summary.json").writeText(result.toString(2))
        }
        save()
        val engine = LamiNeuralChatVoiceEngine(context,
            context.filesDir.resolve("local_models/lami_tts/prepared_hai"), streamingEnabled = true,
            preferEarlyStart = false,
            streamSpeechWithProgress = { _, _, _, _ -> error("Capture requires a multi-segment request") },
            playPreparedSpeech = { pcm, onStarted, progress ->
                LamiPcmContract.validate(pcm)
                val started = SystemClock.elapsedRealtimeNanos()
                val index = rows.size
                val bytes = ByteBuffer.allocate(pcm.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                pcm.forEach(bytes::putFloat)
                val row = JSONObject().put("segment", index).put("samples", pcm.size)
                    .put("audio_ms", pcm.size / 24.0).put("started_ns", started)
                    .put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) })
                rows += row
                onStarted()
                progress(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_STARTED, timestampNs = started))
                output.resolve("segment-$index.f32le").writeBytes(bytes.array())
                progress(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, pcm.size, started))
                delay((pcm.size * 1000L + 23_999L) / 24_000L)
                val ended = SystemClock.elapsedRealtimeNanos()
                row.put("ended_ns", ended)
                progress(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_COMPLETED, pcm.size, ended))
                save()
            })
        engine.setOnStreamingProgress { events += it }
        engine.setOnPlaybackStateChanged { busy ->
            if (busy) wasBusy.set(true) else if (wasBusy.get()) done.complete(Unit)
        }
        engine.setOnError { done.completeExceptionally(AssertionError(it)) }
        try {
            if (queued) text.split(Regex("(?<=[。！？、])")).filter { it.isNotBlank() }.forEach(engine::speakQueued)
            else engine.speak(text)
            withTimeout(600_000) { done.await() }
            assertTrue(rows.size >= 3)
            assertEquals(rows.size, events.count { it.event == TtsStreamEvent.GENERATION_COMPLETED })
            assertEquals(rows.size, events.count { it.event == TtsStreamEvent.PLAYBACK_COMPLETED })
            val gaps = rows.zipWithNext().map { (a, b) -> (b.getLong("started_ns") - a.getLong("ended_ns")) / 1e6 }
            result.put("status", "success").put("total_ms", (SystemClock.elapsedRealtimeNanos() - began) / 1e6)
                .put("first_pcm_start_ms", (rows.first().getLong("started_ns") - began) / 1e6)
                .put("segment_gaps_ms", JSONArray(gaps))
                .put("total_audio_ms", rows.sumOf { it.getDouble("audio_ms") })
            save()
        } catch (failure: Throwable) {
            result.put("status", "failure").put("error", failure.stackTraceToString())
            save()
            throw failure
        } finally {
            withContext(NonCancellable) { engine.stopForVoiceInput(); engine.shutdown() }
        }
    }
}
