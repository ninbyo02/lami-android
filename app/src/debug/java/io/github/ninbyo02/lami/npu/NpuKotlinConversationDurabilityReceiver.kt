package io.github.ninbyo02.lami.npu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.ninbyo02.lami.ui.screens.home.NPU_RESIDENT_BACKGROUND_TIMEOUT_MS
import io.github.ninbyo02.lami.ui.screens.home.NpuKotlinConversationProductRoute
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import kotlinx.coroutines.runBlocking

class NpuKotlinConversationDurabilityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread({
            val resultFile = File(appContext.filesDir, RESULT_FILE)
            try {
                resultFile.writeText("status=starting\n")
                resultFile.writeText(runDurability(appContext, intent, resultFile))
            } catch (throwable: Throwable) {
                resultFile.writeText(
                    "status=failure\nreason=receiver_exception\n" +
                        "exception=${throwable.javaClass.name}:${throwable.message.orEmpty()}\n",
                )
            } finally {
                pendingResult.finish()
            }
        }, "npu-kotlin-durability").start()
    }

    private fun runDurability(
        context: Context,
        intent: Intent,
        resultFile: File,
    ): String = runBlocking {
        val settings = SettingsPreferences(context)
        val selectedModel = intent.getStringExtra(EXTRA_MODEL_PATH)?.trim().orEmpty()
            .ifBlank { settings.getValidLocalBaseModelPathOrNull().orEmpty() }
        val requestedTurns = intent.getIntExtra(EXTRA_TURNS, DEFAULT_TURNS)
            .coerceIn(MIN_TURNS, MAX_TURNS)
        val lines = mutableListOf<String>()
        var successCount = 0
        var semanticPassCount = 0
        var engineReuseCount = 0
        var conversationReuseCount = 0
        var briefBackgroundReusePass = false
        var chatSwitchEngineReusePass = false
        var backgroundTimeoutRecreatePass = false
        var lowMemoryRecreatePass = false

        lines += "status=running"
        lines += "model_path=$selectedModel"
        lines += "turns_requested=$requestedTurns"
        persistProgress(resultFile, lines)
        NpuKotlinConversationProductRoute.reset("durability_start")
        NpuKotlinConversationProductRoute.notifyAppForegrounded(SystemClock.elapsedRealtime())

        try {
            for (turn in 1..requestedTurns) {
                when (turn) {
                    BRIEF_BACKGROUND_NEXT_TURN -> {
                        val now = SystemClock.elapsedRealtime()
                        NpuKotlinConversationProductRoute.notifyAppBackgrounded(now)
                        NpuKotlinConversationProductRoute.notifyAppForegrounded(now + BRIEF_BACKGROUND_MS)
                        lines += "event_before_turn_$turn=brief_background_foreground"
                    }
                    BACKGROUND_TIMEOUT_NEXT_TURN -> {
                        val now = SystemClock.elapsedRealtime()
                        NpuKotlinConversationProductRoute.notifyAppBackgrounded(now)
                        NpuKotlinConversationProductRoute.notifyAppForegrounded(
                            now + NPU_RESIDENT_BACKGROUND_TIMEOUT_MS,
                        )
                        lines += "event_before_turn_$turn=background_timeout"
                    }
                    LOW_MEMORY_NEXT_TURN -> {
                        NpuKotlinConversationProductRoute.notifyLowMemory()
                        lines += "event_before_turn_$turn=low_memory"
                    }
                }

                val chatId = if (turn == CHAT_SWITCH_TURN) CHAT_ID_SECONDARY else CHAT_ID_PRIMARY
                val expected = turn + 1
                val prompt = "$turn+1はいくつですか。数字だけ答えてください。"
                val attempt = NpuKotlinConversationProductRoute.run(
                    context = context,
                    chatId = chatId,
                    userPrompt = prompt,
                    initialTurns = emptyList(),
                    selectedModelFile = selectedModel,
                    requestedMaxOutputTokens = MAX_OUTPUT_TOKENS,
                )
                val response = attempt.result?.sanitizedOutput.orEmpty().trim()
                if (attempt.succeeded) successCount += 1
                if (response == expected.toString()) semanticPassCount += 1
                if (attempt.engineReused) engineReuseCount += 1
                if (attempt.conversationReused) conversationReuseCount += 1

                if (turn == BRIEF_BACKGROUND_NEXT_TURN) {
                    briefBackgroundReusePass = attempt.engineReused && attempt.conversationReused
                }
                if (turn == CHAT_SWITCH_TURN) {
                    chatSwitchEngineReusePass = attempt.engineReused && !attempt.conversationReused
                }
                if (turn == BACKGROUND_TIMEOUT_NEXT_TURN) {
                    backgroundTimeoutRecreatePass = !attempt.engineReused && !attempt.conversationReused
                }
                if (turn == LOW_MEMORY_NEXT_TURN) {
                    lowMemoryRecreatePass = !attempt.engineReused && !attempt.conversationReused
                }

                lines += "turn_${turn}_chat_id=$chatId"
                lines += "turn_${turn}_success=${attempt.succeeded}"
                lines += "turn_${turn}_engine_reused=${attempt.engineReused}"
                lines += "turn_${turn}_conversation_reused=${attempt.conversationReused}"
                lines += "turn_${turn}_response=${response.replace("\n", "\\n")}"
                if (!attempt.succeeded) lines += "turn_${turn}_failure_reason=${attempt.failureReason}"
                persistProgress(resultFile, lines)
            }
        } finally {
            NpuKotlinConversationProductRoute.reset("durability_end")
        }

        val lifecyclePass = briefBackgroundReusePass &&
            chatSwitchEngineReusePass &&
            backgroundTimeoutRecreatePass &&
            lowMemoryRecreatePass
        val routePass = successCount == requestedTurns
        lines += "turns_completed=$requestedTurns"
        lines += "route_success_count=$successCount"
        lines += "semantic_pass_count=$semanticPassCount"
        lines += "engine_reuse_count=$engineReuseCount"
        lines += "conversation_reuse_count=$conversationReuseCount"
        lines += "brief_background_reuse_pass=$briefBackgroundReusePass"
        lines += "chat_switch_engine_reuse_pass=$chatSwitchEngineReusePass"
        lines += "background_timeout_recreate_pass=$backgroundTimeoutRecreatePass"
        lines += "low_memory_recreate_pass=$lowMemoryRecreatePass"
        lines += "lifecycle_pass=$lifecyclePass"
        lines += "route_pass=$routePass"
        lines += "status=${if (routePass && lifecyclePass) "success" else "failure"}"
        persistProgress(resultFile, lines)
        lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun persistProgress(resultFile: File, lines: List<String>) {
        resultFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    companion object {
        const val ACTION = "io.github.ninbyo02.lami.action.NPU_KOTLIN_CONVERSATION_DURABILITY"
        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_TURNS = "turns"
        const val RESULT_FILE = "npu_kotlin_conversation_durability_result.txt"
        const val DEFAULT_TURNS = 20
        const val MIN_TURNS = 18
        const val MAX_TURNS = 40
        const val MAX_OUTPUT_TOKENS = 64
        const val CHAT_ID_PRIMARY = 9101
        const val CHAT_ID_SECONDARY = 9102
        const val BRIEF_BACKGROUND_NEXT_TURN = 7
        const val CHAT_SWITCH_TURN = 11
        const val BACKGROUND_TIMEOUT_NEXT_TURN = 14
        const val LOW_MEMORY_NEXT_TURN = 17
        const val BRIEF_BACKGROUND_MS = 1_000L
    }
}
