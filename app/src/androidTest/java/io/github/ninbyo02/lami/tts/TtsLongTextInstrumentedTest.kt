package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.ui.screens.home.ResponseSpeechSession
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses provisioned real models, their normal streaming players, and cumulative chat text. */
@RunWith(AndroidJUnit4::class)
class TtsLongTextInstrumentedTest {
    @Test
    fun longSpeechCompletesAndSupertonicResumesAfterSourceIdle() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requested = InstrumentationRegistry.getArguments().getString("ttsLongEngine")
        val results = JSONArray()
        val choices = listOf(TtsEngineChoice.SUPERTONIC3, TtsEngineChoice.LAMI_QWEN3)
            .filter { requested == null || it.storedValue == requested }
        require(choices.isNotEmpty()) { "Unknown ttsLongEngine" }
        for (choice in choices) {
            val availability = LamiVoiceEngineFactory.availability(context, choice)
            assertTrue("${choice.label}: ${availability.message}", availability.available)
            val engine = withContext(Dispatchers.Main.immediate) {
                LamiVoiceEngineFactory.createSelected(context, choice)
            }
            try {
                val result = JSONObject().put("engine", choice.storedValue)
                if (choice == TtsEngineChoice.SUPERTONIC3) {
                    val session = ResponseSpeechSession().apply { begin() }
                    val owner = session.generation
                    // No punctuation within this sentence: exercises the hard model chunk limit.
                    val prefix = "今日は窓から入る柔らかな光を感じながら部屋を片付けて机の上に積まれていた本を種類ごとに分けて棚へ戻しそれから台所でお湯を沸かして温かいお茶を用意しゆっくりと深呼吸をしながらこれから始まる一日の予定を思い浮かべていました。"
                    val partial = prefix + "\n```kotlin\n"
                    val first = session.take(partial, final = false)
                    assertNotNull(first)
                    val before = playAndAwait(engine, checkNotNull(first), queued = true)
                    assertTrue("Long Supertonic input must use bounded clips", before.getInt("generations") >= 2)
                    result.put("before_idle", before)
                    delay(2_000)
                    assertNull(session.take(partial + "val answer = 42\n", final = false))
                    assertTrue("A source pause/code block must retain the response owner", session.accepts(owner))
                    delay(1_000)
                    val suffix = "続きの説明も最後まで読み上げます。待ち時間の後でも発話は再開します。"
                    val tail = session.take(partial + "val answer = 42\n```\n" + suffix, final = true)
                    assertEquals(suffix, checkNotNull(tail).trim())
                    assertEquals(owner, session.generation)
                    result.put("after_idle", playAndAwait(engine, tail, queued = true))
                } else {
                    val longText = "今日は天気が良いので、窓を開けて空気を入れ替えます。準備ができたら、一緒に散歩へ出かけましょう。帰ってからは温かいお茶を飲みながら、明日の予定をゆっくり確認します。最後の一文まで落ち着いて読み上げます。"
                    val playback = playAndAwait(engine, longText, queued = false)
                    assertTrue("Long Qwen input must finish several independent cache-bounded segments", playback.getInt("generations") >= 3)
                    assertEquals(playback.getInt("generations"), playback.getInt("playbacks"))
                    result.put("long_text", playback)
                }
                results.put(result)
                File(context.filesDir, "tts_long_text_result.json").writeText(results.toString(2))
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    engine.stopForVoiceInput()
                    engine.shutdown()
                }
            }
        }
    }

    private suspend fun playAndAwait(engine: LamiVoiceEngine, text: String, queued: Boolean): JSONObject {
        val completed = CompletableDeferred<Unit>()
        val wasBusy = AtomicBoolean()
        val starts = AtomicInteger()
        val busyEnds = AtomicInteger()
        val events = CopyOnWriteArrayList<TtsStreamingProgress>()
        val startMs = SystemClock.elapsedRealtime()
        withContext(Dispatchers.Main.immediate) {
            engine.setOnPlaybackStateChanged { busy ->
                if (busy) wasBusy.set(true)
                else if (wasBusy.get()) {
                    busyEnds.incrementAndGet()
                    completed.complete(Unit)
                }
            }
            engine.setOnAudioStarted { starts.incrementAndGet() }
            engine.setOnStreamingProgress { events += it }
            engine.setOnError { completed.completeExceptionally(AssertionError(it)) }
            if (queued) engine.speakQueued(text) else engine.speak(text)
        }
        withTimeout(600_000) { completed.await() }
        assertTrue("Real playback must start", starts.get() > 0)
        assertEquals("Busy must remain true for the whole long request", 1, busyEnds.get())
        val generations = events.count { it.event == TtsStreamEvent.GENERATION_COMPLETED }
        val playbacks = events.count { it.event == TtsStreamEvent.PLAYBACK_COMPLETED }
        val samples = events.filter { it.event == TtsStreamEvent.PCM_CHUNK }.sumOf { it.sampleCount.toLong() }
        assertTrue("Real synthesis must reach its natural completion", generations > 0)
        assertTrue("Real playback must drain", playbacks > 0)
        assertTrue("Real PCM must be generated", samples > 0)
        return JSONObject().put("characters", text.codePointCount(0, text.length))
            .put("elapsed_ms", SystemClock.elapsedRealtime() - startMs)
            .put("audio_starts", starts.get()).put("generations", generations)
            .put("playbacks", playbacks).put("pcm_samples", samples)
            .put("underruns", events.filter { it.event == TtsStreamEvent.PLAYBACK_COMPLETED }.sumOf { it.underruns })
    }
}
