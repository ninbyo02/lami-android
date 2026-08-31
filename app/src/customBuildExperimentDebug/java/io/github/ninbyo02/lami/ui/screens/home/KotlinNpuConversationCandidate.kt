package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class KotlinNpuConversationCandidateResult(
    val status: String,
    val reason: String,
    val response: String = "",
    val modelPath: String = "",
    val engineInitializeMs: Long? = null,
    val sendMs: Long? = null,
    val outputTokens: Int? = null,
    val tokensPerSecond: Double? = null,
    val exceptionClass: String = "",
    val exceptionMessage: String = "",
) {
    fun toDiagnosticText(): String = listOf(
        "status=$status",
        "reason=$reason",
        "backend=NPU",
        "api_surface=LiteRT-LM Kotlin Conversation",
        "prompt_template_owner_candidate=model_metadata",
        "prompt_template_evaluator_candidate=litert_lm_conversation_api",
        "conversation_api_attempted=true",
        "app_template_used=false",
        "template_ownership_unified_candidate=true",
        "native_patch=qairt244_kotlin_npu_conversation_sampler_v1",
        "model_path=$modelPath",
        "engine_initialize_ms=${engineInitializeMs ?: -1}",
        "send_ms=${sendMs ?: -1}",
        "output_tokens=${outputTokens ?: -1}",
        "tokens_per_second=${tokensPerSecond ?: -1.0}",
        "response=$response",
        "exception_class=$exceptionClass",
        "exception_message=$exceptionMessage",
    ).joinToString("\n")
}

internal data class KotlinNpuConversationCandidateTurn(
    val index: Int,
    val prompt: String,
    val response: String,
    val sendMs: Long,
)

internal data class KotlinNpuConversationCandidateSequenceResult(
    val status: String,
    val reason: String,
    val modelPath: String = "",
    val engineInitializeMs: Long? = null,
    val turns: List<KotlinNpuConversationCandidateTurn> = emptyList(),
    val exceptionClass: String = "",
    val exceptionMessage: String = "",
) {
    fun toDiagnosticText(): String = buildList {
        add("status=$status")
        add("reason=$reason")
        add("backend=NPU")
        add("api_surface=LiteRT-LM Kotlin Conversation persistent sequence")
        add("conversation_api_attempted=true")
        add("app_template_used=false")
        add("native_patch=qairt244_kotlin_npu_conversation_sampler_v1")
        add("model_path=$modelPath")
        add("engine_initialize_ms=${engineInitializeMs ?: -1}")
        add("turn_count=${turns.size}")
        turns.forEach { turn ->
            add("turn_${turn.index}_prompt=${turn.prompt}")
            add("turn_${turn.index}_send_ms=${turn.sendMs}")
            add("turn_${turn.index}_response=${turn.response}")
        }
        add("exception_class=$exceptionClass")
        add("exception_message=$exceptionMessage")
    }.joinToString("\n")
}

@OptIn(ExperimentalApi::class)
internal object KotlinNpuConversationCandidate {
    suspend fun run(
        context: Context,
        userPrompt: String,
        history: List<LocalConversationTurn> = emptyList(),
        selectedModelFile: String? = null,
    ): KotlinNpuConversationCandidateResult = withContext(Dispatchers.IO) {
        check(BuildConfig.DEBUG && BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            "Kotlin NPU Conversation candidate is customBuildExperimentDebug-only"
        }
        val appContext = context.applicationContext
        val prompt = userPrompt.trim()
        require(prompt.isNotBlank()) { "userPrompt must not be blank" }
        val resolution = Qairt244ModelPathResolver.resolve(
            context = appContext,
            preferredModelPath = selectedModelFile,
        )
        val modelPath = resolution.path.orEmpty()
        if (!resolution.resolved || modelPath.isBlank()) {
            return@withContext KotlinNpuConversationCandidateResult(
                status = "failure",
                reason = "npu_model_unavailable",
            )
        }
        val preface = NpuConversationPrefacePlanner.plan(history, prompt)
        var engine: Engine? = null
        var conversation: Conversation? = null
        var engineInitializeMs: Long? = null
        var sendMs: Long? = null
        return@withContext try {
            val cacheDir = appContext.cacheDir.resolve("litertlm_npu_candidate").apply { mkdirs() }
            val engineStartedAt = SystemClock.elapsedRealtime()
            engine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.NPU(appContext.applicationInfo.nativeLibraryDir),
                    visionBackend = null,
                    audioBackend = null,
                    maxNumTokens = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
                    cacheDir = cacheDir.absolutePath,
                ),
            )
            engine.initialize()
            engineInitializeMs = SystemClock.elapsedRealtime() - engineStartedAt

            conversation = engine.createConversation(
                LocalConversationPolicy.conversationConfig(preface.initialTurns),
            )
            val sendStartedAt = SystemClock.elapsedRealtime()
            val message = conversation.sendMessage(
                preface.currentUserPrompt,
                LocalConversationPolicy.generationExtraContext,
            )
            sendMs = SystemClock.elapsedRealtime() - sendStartedAt
            val response = renderMessage(message).trim()
            val benchmark = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
            if (response.isBlank()) {
                KotlinNpuConversationCandidateResult(
                    status = "failure",
                    reason = "blank_output",
                    modelPath = modelPath,
                    engineInitializeMs = engineInitializeMs,
                    sendMs = sendMs,
                )
            } else {
                KotlinNpuConversationCandidateResult(
                    status = "success",
                    reason = "completed",
                    response = response,
                    modelPath = modelPath,
                    engineInitializeMs = engineInitializeMs,
                    sendMs = sendMs,
                    outputTokens = benchmark?.lastDecodeTokenCount,
                    tokensPerSecond = benchmark?.lastDecodeTokensPerSecond,
                )
            }
        } catch (throwable: Throwable) {
            KotlinNpuConversationCandidateResult(
                status = "failure",
                reason = "kotlin_npu_conversation_failure",
                modelPath = modelPath,
                engineInitializeMs = engineInitializeMs,
                sendMs = sendMs,
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = throwable.message.orEmpty(),
            )
        } finally {
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
        }
    }

    suspend fun runSequence(
        context: Context,
        prompts: List<String>,
        selectedModelFile: String? = null,
    ): KotlinNpuConversationCandidateSequenceResult = withContext(Dispatchers.IO) {
        check(BuildConfig.DEBUG && BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            "Kotlin NPU Conversation sequence is customBuildExperimentDebug-only"
        }
        val normalizedPrompts = prompts.map(String::trim).filter(String::isNotBlank)
        require(normalizedPrompts.isNotEmpty()) { "prompts must not be empty" }
        require(normalizedPrompts.size <= 8) { "prompts must contain at most 8 turns" }
        val appContext = context.applicationContext
        val resolution = Qairt244ModelPathResolver.resolve(
            context = appContext,
            preferredModelPath = selectedModelFile,
        )
        val modelPath = resolution.path.orEmpty()
        if (!resolution.resolved || modelPath.isBlank()) {
            return@withContext KotlinNpuConversationCandidateSequenceResult(
                status = "failure",
                reason = "npu_model_unavailable",
            )
        }
        var engine: Engine? = null
        var conversation: Conversation? = null
        var engineInitializeMs: Long? = null
        val turns = mutableListOf<KotlinNpuConversationCandidateTurn>()
        return@withContext try {
            val cacheDir = appContext.cacheDir.resolve("litertlm_npu_candidate").apply { mkdirs() }
            val engineStartedAt = SystemClock.elapsedRealtime()
            engine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.NPU(appContext.applicationInfo.nativeLibraryDir),
                    visionBackend = null,
                    audioBackend = null,
                    maxNumTokens = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
                    cacheDir = cacheDir.absolutePath,
                ),
            )
            engine.initialize()
            engineInitializeMs = SystemClock.elapsedRealtime() - engineStartedAt
            conversation = engine.createConversation(LocalConversationPolicy.conversationConfig())
            normalizedPrompts.forEachIndexed { index, prompt ->
                val sendStartedAt = SystemClock.elapsedRealtime()
                val message = conversation.sendMessage(prompt, LocalConversationPolicy.generationExtraContext)
                val sendMs = SystemClock.elapsedRealtime() - sendStartedAt
                val response = renderMessage(message).trim()
                if (response.isBlank()) error("blank_output_at_turn_${index + 1}")
                turns += KotlinNpuConversationCandidateTurn(index + 1, prompt, response, sendMs)
            }
            KotlinNpuConversationCandidateSequenceResult(
                status = "success",
                reason = "completed",
                modelPath = modelPath,
                engineInitializeMs = engineInitializeMs,
                turns = turns.toList(),
            )
        } catch (throwable: Throwable) {
            KotlinNpuConversationCandidateSequenceResult(
                status = "failure",
                reason = "kotlin_npu_conversation_sequence_failure",
                modelPath = modelPath,
                engineInitializeMs = engineInitializeMs,
                turns = turns.toList(),
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = throwable.message.orEmpty(),
            )
        } finally {
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
        }
    }

    private fun renderMessage(message: Message): String {
        val text = message.contents.contents.joinToString(separator = "") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> ""
            }
        }
        return text.takeIf { it.isNotBlank() }
            ?: message.contents.toString().takeIf { it.isNotBlank() }
            ?: message.toString()
    }
}
