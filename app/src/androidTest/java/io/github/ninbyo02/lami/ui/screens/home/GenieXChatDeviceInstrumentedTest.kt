package io.github.ninbyo02.lami.ui.screens.home

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.tts.LamiTtsPreviewController
import io.github.ninbyo02.lami.tts.LamiVoiceEngineFactory
import io.github.ninbyo02.lami.tts.TtsEngineChoice
import io.github.ninbyo02.lamigeniexprobe.integration.GenieXBridgeClient
import io.github.ninbyo02.lamigeniexprobe.integration.LamiComputeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run explicitly on a Snapdragon device with the same-signature companion and LFM3 model. */
@RunWith(AndroidJUnit4::class)
class GenieXChatDeviceInstrumentedTest {
    @Test
    fun residentConversationStreamsAndItsReplyPlaysThroughSelectedTts(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val compute = LamiComputeChoice.entries.single { it.wireName == (args.getString("genieXCompute") ?: "npu") }
        val voice = TtsEngineChoice.fromStoredValue(args.getString("ttsSmokeEngine"))
        val client = GenieXBridgeClient(context)
        val history = mutableListOf<LocalConversationTurn>()
        var reply = ""
        try {
            assertTrue("Import the LFM3 GGUF in the GenieX companion first", client.checkModelReady())
            val backend = GenieXChatBackend(client)
            for (prompt in listOf("日本語で短く挨拶してください。", "今度は自己紹介を日本語で一文だけお願いします。")) {
                var latestPartial = ""
                val result = backend.run(compute, prompt, history) { latestPartial = it }
                reply = requireNotNull(result.response)
                assertTrue(reply.isNotBlank())
                assertEquals(reply, latestPartial)
                assertTrue(result.trace.realPartialChunkCount > 0)
                assertEquals("geniex", result.trace.selectedAssistantResponseSource)
                history += LocalConversationTurn(LocalConversationRole.USER, prompt)
                history += LocalConversationTurn(LocalConversationRole.MODEL, reply)
                Log.i(TAG, "compute=${compute.wireName} turn=${history.size / 2} chunks=${result.trace.realPartialChunkCount}")
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val preview = LamiTtsPreviewController(
                scope = scope,
                available = { LamiVoiceEngineFactory.availability(context, it) },
                create = { LamiVoiceEngineFactory.createSelected(context, it) },
                nowMs = SystemClock::elapsedRealtime,
            )
            try {
                val available = withTimeout(30_000) {
                    preview.state.first { it.availability.size == TtsEngineChoice.entries.size }.availability
                }.getValue(voice)
                assertTrue(available.message, available.available)
                withContext(Dispatchers.Main.immediate) { preview.play(voice, reply) }
                val playback = withTimeout(120_000) {
                    preview.state.first { it.results.isNotEmpty() }.results.single()
                }
                assertNull(playback.error)
                assertNotNull(playback.playbackStartMs)
                assertNotNull(playback.totalMs)
                Log.i(TAG, "tts=${voice.storedValue} playback_start_ms=${playback.playbackStartMs} total_ms=${playback.totalMs}")
            } finally {
                withContext(Dispatchers.Main.immediate) { preview.shutdown() }
                scope.cancel()
            }
        } finally {
            client.close()
        }
    }

    private companion object {
        const val TAG = "LamiGenieXDevice"
    }
}
