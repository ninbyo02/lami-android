package io.github.ninbyo02.lami.ui.screens.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal const val KOTLIN_NPU_CONVERSATION_CANDIDATE_ACTION =
    "io.github.ninbyo02.lami.action.KOTLIN_NPU_CONVERSATION_CANDIDATE"
internal const val KOTLIN_NPU_CONVERSATION_CANDIDATE_RESULT_FILE =
    "kotlin_npu_conversation_candidate_result.txt"

class KotlinNpuConversationCandidateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != KOTLIN_NPU_CONVERSATION_CANDIDATE_ACTION) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scenarioPrompts = when (intent.getStringExtra(EXTRA_SCENARIO)) {
            SCENARIO_COLOR_CORRECTION_JA -> listOf(
                "好きな色は赤です。",
                "好きな色を青に訂正します。",
                "現在の好きな色は何ですか。色だけ答えてください。",
            )
            else -> emptyList()
        }
        val encodedSequence = intent.getStringExtra(EXTRA_SEQUENCE_BASE64)
        val sequenceText = encodedSequence?.let { encoded ->
            runCatching { String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) }.getOrNull()
        } ?: intent.getStringExtra(EXTRA_SEQUENCE)
        val sequencePrompts = sequenceText
            ?.split(SEQUENCE_SEPARATOR)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        val prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty().ifBlank { DEFAULT_PROMPT }
        val prompt2 = intent.getStringExtra(EXTRA_PROMPT_2)?.takeIf { it.isNotBlank() }
        val prompt3 = intent.getStringExtra(EXTRA_PROMPT_3)?.takeIf { it.isNotBlank() }
        val selectedModelFile = intent.getStringExtra(EXTRA_MODEL_PATH)?.takeIf { it.isNotBlank() }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val diagnosticText = if (scenarioPrompts.isNotEmpty() || sequencePrompts.isNotEmpty() || prompt2 != null || prompt3 != null) {
                    KotlinNpuConversationCandidate.runSequence(
                        context = appContext,
                        prompts = scenarioPrompts.ifEmpty {
                            sequencePrompts.ifEmpty { listOfNotNull(prompt, prompt2, prompt3) }
                        },
                        selectedModelFile = selectedModelFile,
                    ).toDiagnosticText()
                } else {
                    KotlinNpuConversationCandidate.run(
                        context = appContext,
                        userPrompt = prompt,
                        selectedModelFile = selectedModelFile,
                    ).toDiagnosticText()
                }
                appContext.filesDir
                    .resolve(KOTLIN_NPU_CONVERSATION_CANDIDATE_RESULT_FILE)
                    .writeText(diagnosticText)
            } catch (throwable: Throwable) {
                appContext.filesDir
                    .resolve(KOTLIN_NPU_CONVERSATION_CANDIDATE_RESULT_FILE)
                    .writeText(
                        listOf(
                            "status=failure",
                            "reason=receiver_failure",
                            "exception_class=${throwable.javaClass.name}",
                            "exception_message=${throwable.message.orEmpty()}",
                        ).joinToString("\n"),
                    )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SCENARIO = "scenario"
        const val SCENARIO_COLOR_CORRECTION_JA = "color_correction_ja"
        const val EXTRA_SEQUENCE_BASE64 = "sequence_base64"
        const val EXTRA_SEQUENCE = "sequence"
        const val SEQUENCE_SEPARATOR = "__LAMI_TURN__"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_PROMPT_2 = "prompt_2"
        const val EXTRA_PROMPT_3 = "prompt_3"
        const val EXTRA_MODEL_PATH = "model_path"
        const val DEFAULT_PROMPT = "こんにちは"
    }
}
