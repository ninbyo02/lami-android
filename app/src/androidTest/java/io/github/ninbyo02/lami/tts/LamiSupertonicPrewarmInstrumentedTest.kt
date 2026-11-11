package io.github.ninbyo02.lami.tts

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.*
import org.junit.Test

/** Real cold/warm native first-PCM measurement with an immediate consumer and no AudioTrack. */
class LamiSupertonicPrewarmInstrumentedTest {
    @Test fun prewarmFinishesBeforeSpeechAndKeepsNativeOwnership() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = context.filesDir.resolve("supertonic_prewarm_capture.json")
        val rows = JSONArray()
        for ((index, warm) in listOf(false, true, true, false).withIndex()) {
            val prepared = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()
            val done = CompletableDeferred<Unit>()
            val metrics = mutableListOf<JSONObject>()
            val native = LamiSupertonicSession(LamiSupertonicVoiceFactory.modelDirectory(context),
                speakerId = 0, steps = 5, threads = 4, measured = { metrics += it })
            val session = object : LamiResidentPcmSession {
                override suspend fun prewarm() { native.prewarm(); prepared.complete(Unit) }
                override suspend fun synthesize(text: String) = native.synthesize(text)
                override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) = native.stream(text, emit)
                override fun cancel() = native.cancel()
                override suspend fun close() { native.close(); closed.complete(Unit) }
            }
            var busyStarted = false
            var firstNs = 0L
            var samples = 0
            val engine = LamiNeuralPcmVoiceEngine(CoroutineScope(SupervisorJob() + Dispatchers.Default), session,
                { _, _ -> error("Expected native streaming") }, streamPcm = { chunks, started, _ ->
                    var firstInClip = true
                    for (pcm in chunks) {
                        if (firstInClip) {
                            firstInClip = false
                            if (firstNs == 0L) firstNs = SystemClock.elapsedRealtimeNanos()
                            started()
                        }
                        assertTrue(pcm.isNotEmpty() && pcm.all { it.isFinite() && it in -1f..1f })
                        samples += pcm.size
                    }
                })
            engine.setOnPlaybackStateChanged { busy -> if (busy) busyStarted = true else if (busyStarted) done.complete(Unit) }
            engine.setOnError { done.completeExceptionally(AssertionError(it)) }
            try {
                val warmStart = SystemClock.elapsedRealtimeNanos()
                if (warm) { engine.prewarm(); withTimeout(120_000) { prepared.await() } }
                val warmMs = (SystemClock.elapsedRealtimeNanos() - warmStart) / 1e6
                assertFalse(engine.isSpeaking.value)
                val speechStart = SystemClock.elapsedRealtimeNanos()
                engine.speakQueued("こんにちはラミィがお手伝いします")
                withTimeout(120_000) { done.await() }
                assertTrue(samples > 0 && firstNs > speechStart)
                val nativeMetrics = metrics.first()
                assertEquals(warm, nativeMetrics.getBoolean("loaded_before_request"))
                rows.put(JSONObject().put("order", index).put("prewarmed", warm).put("prewarm_ms", warmMs)
                    .put("speech_to_first_pcm_ms", (firstNs - speechStart) / 1e6)
                    .put("samples", samples).put("native", nativeMetrics))
                output.writeText(JSONObject().put("status", "running").put("rows", rows).toString(2))
            } finally {
                withContext(NonCancellable) {
                    engine.stopForVoiceInput()
                    engine.shutdown()
                    withTimeout(120_000) { closed.await() }
                }
            }
        }
        output.writeText(JSONObject().put("status", "success").put("rows", rows)
            .put("scope", "Native first PCM; no AudioTrack, LLM timing, or acoustic output").toString(2))
    }
}
