package io.github.ninbyo02.lami.ui.screens.home

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses an already provisioned model directly; does not read or change chat/settings storage. */
@RunWith(AndroidJUnit4::class)
class IntegratedNpuInferenceSmokeInstrumentedTest {
    @Test
    fun selectedModelProducesTwoShortResponsesWithNativeStreaming() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelPath = InstrumentationRegistry.getArguments().getString("modelPath")?.trim()
        require(!modelPath.isNullOrEmpty()) { "Instrumentation argument modelPath is required" }
        val model = File(modelPath)
        require(model.isAbsolute && model.isFile && model.canRead() && model.length() > 0L) {
            "modelPath must identify an existing readable model in the target application's storage: $modelPath"
        }
        val started = SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()
        val turns = JSONArray()
        val report = JSONObject()
            .put("schema_version", 1)
            .put("status", "running")
            .put("model_path", modelPath)
            .put("model_bytes", model.length())
            .put("route_id", NpuKotlinConversationProductRoute.ROUTE_ID)
            .put("requested_max_output_tokens", MAX_OUTPUT_TOKENS)
            .put("started_at_epoch_ms", System.currentTimeMillis())
            .put("turns", turns)
        val output = context.filesDir.resolve(RESULT_FILE)
        fun save() { output.writeText(report.toString(2)) }
        save()

        try {
            NpuKotlinConversationProductRoute.reset("instrumentation_short_smoke_start")
            NpuKotlinConversationProductRoute.notifyAppForegrounded(SystemClock.elapsedRealtime())
            for ((index, prompt) in PROMPTS.withIndex()) {
                val turnStarted = SystemClock.elapsedRealtime()
                val row = JSONObject().put("turn", index + 1).put("prompt", prompt).put("status", "running")
                turns.put(row)
                save()
                val partialCount = AtomicInteger()
                val lastPartial = AtomicReference("")
                try {
                    val attempt = withTimeout(TURN_TIMEOUT_MS) {
                        NpuKotlinConversationProductRoute.run(
                            context = context,
                            chatId = SMOKE_CHAT_ID,
                            userPrompt = prompt,
                            initialTurns = emptyList(),
                            selectedModelFile = modelPath,
                            requestedMaxOutputTokens = MAX_OUTPUT_TOKENS,
                            onPartial = { partial -> partialCount.incrementAndGet(); lastPartial.set(partial) },
                            trace = { Log.i(LOG_TAG, "turn=${index + 1} $it") },
                        )
                    }
                    val result = attempt.result
                    val response = result?.actualDisplayText.orEmpty().trim()
                    val nativeTtft = attempt.timeToFirstNativeChunkMs
                    val visibleTtft = attempt.timeToFirstChunkMs
                    val turnFailures = buildList {
                        if (!attempt.succeeded) add("generation_failed:${attempt.failureReason}")
                        if (response.isBlank()) add("empty_response")
                        if (!attempt.nativeStreamingUsed || attempt.nativeStreamingChunkCount <= 0) add("native_streaming_missing")
                        if (attempt.streamingChunkCount <= 0 || partialCount.get() <= 0) add("visible_streaming_missing")
                        if (nativeTtft == null || nativeTtft < 0) add("native_ttft_missing")
                        if (visibleTtft == null || visibleTtft < 0) add("visible_ttft_missing")
                        if (nativeTtft != null && visibleTtft != null && nativeTtft > visibleTtft) add("ttft_order_invalid")
                        if (result?.npuBackendEvidence != NpuKotlinConversationProductRoute.NPU_EVIDENCE) add("npu_evidence_missing")
                        if (result?.fallbackUsed != false) add("fallback_or_no_native_result")
                        if (response.isNotBlank() && lastPartial.get().trim() != response) add("final_partial_mismatch")
                    }
                    failures += turnFailures.map { "turn=${index + 1} $it" }
                    row.put("status", if (turnFailures.isEmpty()) "success" else "failure")
                        .put("failure_reason", attempt.failureReason)
                        .put("failures", JSONArray(turnFailures))
                        .put("response", response)
                        .put("engine_reused", attempt.engineReused)
                        .put("conversation_reused", attempt.conversationReused)
                        .put("native_streaming_used", attempt.nativeStreamingUsed)
                        .put("native_streaming_chunk_count", attempt.nativeStreamingChunkCount)
                        .put("visible_streaming_chunk_count", attempt.streamingChunkCount)
                        .put("partial_callback_count", partialCount.get())
                        .put("last_partial", lastPartial.get())
                        .put("native_ttft_ms", nativeTtft ?: JSONObject.NULL)
                        .put("visible_ttft_ms", visibleTtft ?: JSONObject.NULL)
                        .put("npu_backend_evidence", result?.npuBackendEvidence ?: JSONObject.NULL)
                        .put("fallback_used", result?.fallbackUsed ?: JSONObject.NULL)
                    Log.i(LOG_TAG, "turn=${index + 1} status=${row.getString("status")} native_chunks=${attempt.nativeStreamingChunkCount} native_ttft_ms=$nativeTtft visible_ttft_ms=$visibleTtft response=${response.take(200)}")
                } catch (failure: Exception) {
                    failures += "turn=${index + 1} ${failure.javaClass.simpleName}:${failure.message.orEmpty()}"
                    row.put("status", "failure").put("exception", failure.stackTraceToString())
                    Log.e(LOG_TAG, "turn=${index + 1} failed", failure)
                } finally {
                    row.put("elapsed_ms", SystemClock.elapsedRealtime() - turnStarted)
                    save()
                }
            }
        } catch (failure: Throwable) {
            failures += "setup_or_execution:${failure.javaClass.simpleName}:${failure.message.orEmpty()}"
            throw failure
        } finally {
            try {
                withContext(NonCancellable) {
                    NpuKotlinConversationProductRoute.reset("instrumentation_short_smoke_end")
                }
            } catch (failure: Throwable) {
                failures += "cleanup:${failure.javaClass.simpleName}:${failure.message.orEmpty()}"
                Log.e(LOG_TAG, "NPU smoke cleanup failed", failure)
            }
            report.put("status", if (failures.isEmpty()) "success" else "failure")
                .put("failures", JSONArray(failures))
                .put("elapsed_ms", SystemClock.elapsedRealtime() - started)
            save()
            Log.i(LOG_TAG, "status=${report.getString("status")} result=${output.name} turns=${turns.length()}")
        }
        assertTrue("Both short NPU turns must succeed; $failures; see $RESULT_FILE", failures.isEmpty())
    }

    private companion object {
        const val LOG_TAG = "LamiIntegratedNpuSmoke"
        const val RESULT_FILE = "integrated_npu_inference_smoke.json"
        const val MAX_OUTPUT_TOKENS = 64
        const val TURN_TIMEOUT_MS = 120_000L
        const val SMOKE_CHAT_ID = 26_061_110
        val PROMPTS = listOf("こんにちは", "1+1はいくつですか。数字だけ答えてください。")
    }
}
