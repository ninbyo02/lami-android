package io.github.ninbyo02.lami.ui.screens.home

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuKotlinConversationDurabilityInstrumentedTest {
    @Test
    fun productNpuConversation_survivesTwentyTurnsAndLifecycleTransitions() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val modelFile = stageNpuModelForTest(instrumentation = instrumentation, targetDir = context.filesDir)
        val modelPath = modelFile.absolutePath
        assertTrue("staged NPU model file must exist", modelFile.isFile && modelFile.length() > 0L)

        val failures = mutableListOf<String>()
        var briefBackgroundPass = false
        var chatSwitchPass = false
        var backgroundTimeoutPass = false
        var lowMemoryPass = false

        NpuKotlinConversationProductRoute.reset("instrumentation_durability_start")
        NpuKotlinConversationProductRoute.notifyAppForegrounded(SystemClock.elapsedRealtime())
        try {
            for (turn in 1..20) {
                when (turn) {
                    7 -> {
                        val now = SystemClock.elapsedRealtime()
                        NpuKotlinConversationProductRoute.notifyAppBackgrounded(now)
                        NpuKotlinConversationProductRoute.notifyAppForegrounded(now + 1_000L)
                    }
                    14 -> {
                        val now = SystemClock.elapsedRealtime()
                        NpuKotlinConversationProductRoute.notifyAppBackgrounded(now)
                        NpuKotlinConversationProductRoute.notifyAppForegrounded(
                            now + NPU_RESIDENT_BACKGROUND_TIMEOUT_MS,
                        )
                    }
                    17 -> NpuKotlinConversationProductRoute.notifyLowMemory()
                }

                val chatId = if (turn == 11) 9202 else 9201
                val partials = mutableListOf<String>()
                val attempt = NpuKotlinConversationProductRoute.run(
                    context = context,
                    chatId = chatId,
                    userPrompt = "$turn+1はいくつですか。数字だけ答えてください。",
                    initialTurns = emptyList(),
                    selectedModelFile = modelPath,
                    requestedMaxOutputTokens = 64,
                    onPartial = { partial -> partials += partial },
                )
                if (!attempt.succeeded) {
                    failures += "turn=$turn reason=${attempt.failureReason}"
                }
                if (!attempt.nativeStreamingUsed) {
                    failures += "turn=$turn native_streaming_used=false"
                }
                if (attempt.nativeStreamingChunkCount <= 0) {
                    failures += "turn=$turn no_native_streaming_chunks"
                }
                if (attempt.streamingChunkCount <= 0 || partials.isEmpty()) {
                    failures += "turn=$turn no_visible_streaming_updates"
                }
                if (attempt.timeToFirstNativeChunkMs == null) {
                    failures += "turn=$turn backend_ttft_unavailable"
                }
                if (attempt.timeToFirstChunkMs == null) {
                    failures += "turn=$turn lami_ttft_unavailable"
                }
                if (attempt.timeToFirstNativeChunkMs != null && attempt.timeToFirstChunkMs != null &&
                    attempt.timeToFirstNativeChunkMs > attempt.timeToFirstChunkMs
                ) {
                    failures += "turn=$turn backend_ttft_after_lami_ttft"
                }
                val finalResponse = attempt.result?.actualDisplayText.orEmpty().trim()
                if (finalResponse.isNotBlank() && partials.lastOrNull()?.trim() != finalResponse) {
                    failures += "turn=$turn final_partial_mismatch"
                }
                when (turn) {
                    7 -> briefBackgroundPass = attempt.engineReused && attempt.conversationReused
                    11 -> chatSwitchPass = attempt.engineReused && !attempt.conversationReused
                    14 -> backgroundTimeoutPass = !attempt.engineReused && !attempt.conversationReused
                    17 -> lowMemoryPass = !attempt.engineReused && !attempt.conversationReused
                }
            }
        } finally {
            NpuKotlinConversationProductRoute.reset("instrumentation_durability_end")
        }

        assertTrue("all NPU turns must succeed: $failures", failures.isEmpty())
        assertTrue("brief background should preserve Engine and Conversation", briefBackgroundPass)
        assertTrue("chat switch should preserve Engine and recreate Conversation", chatSwitchPass)
        assertTrue("background timeout should recreate Engine and Conversation", backgroundTimeoutPass)
        assertTrue("low memory should recreate Engine and Conversation", lowMemoryPass)
        assertFalse("failure list must remain empty", failures.isNotEmpty())
    }
    private fun stageNpuModelForTest(
        instrumentation: android.app.Instrumentation,
        targetDir: File,
    ): File {
        val localModels = File(targetDir, "local_models").apply { mkdirs() }
        val target = File(localModels, "durability_npu_model.litertlm")
        if (target.isFile && target.length() > 0L) return target
        val parcelFd = instrumentation.uiAutomation.executeShellCommand("cat $SHELL_MODEL_PATH")
        parcelFd.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output, bufferSize = COPY_BUFFER_BYTES) }
            }
        }
        return target
    }

    private companion object {
        const val SHELL_MODEL_PATH = "/data/local/tmp/lami_npu_durability_model.litertlm"
        const val COPY_BUFFER_BYTES = 4 * 1024 * 1024
    }

}
