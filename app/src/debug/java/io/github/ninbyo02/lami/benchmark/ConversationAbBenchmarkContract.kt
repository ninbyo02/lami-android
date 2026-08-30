package io.github.ninbyo02.lami.benchmark

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.ninbyo02.lami.ui.screens.home.LocalConversationPolicy
import io.github.ninbyo02.lami.ui.screens.home.LocalInferenceResponseSanitizer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

internal data class ConversationAbTurnResult(
    val index: Int,
    val prompt: String,
    val rawOutput: String,
    val sanitizedOutput: String,
    val status: String,
    val reason: String,
    val sendMs: Long? = null,
    val ttftMs: Long? = null,
    val outputTokens: Int? = null,
    val tokensPerSecond: Double? = null,
)

internal data class ConversationAbRunResult(
    val schemaVersion: Int = ConversationAbBenchmarkContract.SCHEMA_VERSION,
    val scenarioId: String,
    val backend: String,
    val apiSurface: String,
    val conversationApiUsed: Boolean = true,
    val directSessionApiUsed: Boolean = false,
    val modelFileName: String,
    val modelBytes: Long,
    val modelTemplateSource: String = "model_metadata",
    val promptTemplateOwner: String = LocalConversationPolicy.PROMPT_TEMPLATE_OWNER,
    val appTemplateUsed: Boolean = LocalConversationPolicy.APP_TEMPLATE_USED,
    val samplerProfile: String = LocalConversationPolicy.SAMPLER_PROFILE,
    val samplerTopK: Int = LocalConversationPolicy.SAMPLER_TOP_K,
    val samplerTopP: Double = LocalConversationPolicy.SAMPLER_TOP_P,
    val samplerTemperature: Double = LocalConversationPolicy.SAMPLER_TEMPERATURE,
    val samplerSeed: Int = LocalConversationPolicy.SAMPLER_SEED,
    val requestedMaxOutputTokens: Int?,
    val effectiveMaxOutputTokens: Int?,
    val outputLimitSource: String,
    val engineCreateMs: Long? = null,
    val conversationCreateMs: Long? = null,
    val totalMs: Long? = null,
    val status: String,
    val reason: String,
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
    val turns: List<ConversationAbTurnResult>,
)

internal object ConversationAbBenchmarkContract {
    const val SCHEMA_VERSION = 1
    const val DEFAULT_SCENARIO_ID = "conversation_api_ab_v1"
    const val RESULT_FILE_NAME = "conversation_ab_benchmark_result.json"
    const val GPU_ACTION = "io.github.ninbyo02.lami.action.CONVERSATION_AB_GPU"
    const val EXTRA_SCENARIO_ID = "scenario_id"
    const val EXTRA_PROMPTS_BASE64 = "conversation_prompts_base64"
    const val EXTRA_MODEL_PATH_BASE64 = "model_path_base64"
    const val EXTRA_MAX_OUTPUT_TOKENS = "max_output_tokens"
    const val EXTRA_TIMEOUT_MS = "timeout_ms"
    const val MAX_TURNS = 11
    const val GPU_CONTEXT_MAX_TOKENS = 4096
    const val DEFAULT_TIMEOUT_MS = 120_000L

    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    fun decodePrompts(encodedPrompts: String?, fallback: List<String> = emptyList()): List<String> {
        if (encodedPrompts.isNullOrBlank()) {
            require(fallback.size in 1..MAX_TURNS)
            require(fallback.none { it.isBlank() })
            return fallback
        }
        val json = String(Base64.getDecoder().decode(encodedPrompts), Charsets.UTF_8)
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson<List<String>>(json, type)
            .also { prompts ->
                require(prompts.size in 1..MAX_TURNS)
                require(prompts.none { it.isBlank() })
            }
    }

    fun decodeOptionalBase64(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return runCatching {
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        }.getOrNull()
    }

    fun sanitize(prompt: String, rawOutput: String): String =
        LocalInferenceResponseSanitizer
            .sanitize(rawOutput = rawOutput, prompt = prompt)
            .sanitizedOutput
            .trim()

    fun parseNativeTurns(
        nativeText: String,
        prompts: List<String>,
    ): List<ConversationAbTurnResult> {
        val records = mutableListOf<MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        nativeText.lineSequence().forEach { line ->
            val key = line.substringBefore('=', missingDelimiterValue = "")
            val value = line.substringAfter('=', missingDelimiterValue = "")
            if (key == "turn_index") {
                current?.let(records::add)
                current = linkedMapOf(key to value)
            } else if (current != null && key in NATIVE_TURN_KEYS) {
                current[key] = value
            }
        }
        current?.let(records::add)
        return records.mapIndexed { recordIndex, values ->
            val index = values["turn_index"]?.toIntOrNull() ?: recordIndex + 1
            val prompt = prompts.getOrElse(index - 1) {
                values["user_prompt"].orEmpty()
            }
            val rawOutput = values["raw_output"].orEmpty()
            val nativeStatus = values["turn_status"].orEmpty().ifBlank { "failure" }
            val sanitized = sanitize(prompt = prompt, rawOutput = rawOutput)
            val success = nativeStatus == "success" && sanitized.isNotBlank()
            ConversationAbTurnResult(
                index = index,
                prompt = prompt,
                rawOutput = rawOutput,
                sanitizedOutput = sanitized,
                status = if (success) "success" else "failure",
                reason = when {
                    nativeStatus != "success" -> values["turn_reason"].orEmpty().ifBlank {
                        "native_turn_failure"
                    }
                    sanitized.isBlank() -> "blank_output_after_sanitize"
                    else -> "completed"
                },
                sendMs = values["send_ms"]?.toLongOrNull(),
            )
        }
    }

    fun write(
        context: Context,
        result: ConversationAbRunResult,
    ) {
        val target = File(context.filesDir, RESULT_FILE_NAME)
        val temporary = File(context.filesDir, RESULT_FILE_NAME + ".tmp")
        temporary.writeText(gson.toJson(result), Charsets.UTF_8)
        runCatching {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun toJson(result: ConversationAbRunResult): String = gson.toJson(result)

    private val NATIVE_TURN_KEYS = setOf(
        "turn_index",
        "user_prompt",
        "turn_status",
        "turn_reason",
        "raw_output",
        "response_json",
        "send_ms",
    )
}
