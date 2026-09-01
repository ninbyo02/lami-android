package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class NpuKotlinConversationProductAttempt(
    val result: NpuStandardRouteS1Result? = null,
    val failureReason: String = "",
    val engineReused: Boolean = false,
    val conversationReused: Boolean = false,
    val nativeStreamingUsed: Boolean = false,
    val nativeStreamingChunkCount: Int = 0,
    val streamingChunkCount: Int = 0,
    val timeToFirstNativeChunkMs: Long? = null,
    val timeToFirstChunkMs: Long? = null,
) {
    val succeeded: Boolean
        get() = result?.successCriteriaMet == true
}

internal object NpuKotlinConversationProductRoute : NpuConversationLifecycle {
    const val ROUTE_ID = "npu_kotlin_conversation_product_candidate_v1"
    const val NATIVE_PATCH_MARKER = "qairt244_kotlin_npu_conversation_sampler_v1"
    const val NPU_EVIDENCE = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE

    private val mutex = Mutex()
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appInForeground = true
    private var backgroundedAtElapsedMs: Long? = null
    private var lastUsedAtElapsedMs: Long? = null
    private var backgroundReleaseJob: Job? = null
    private var engine: Engine? = null
    private var engineModelPath: String? = null
    private var conversation: Conversation? = null
    private var conversationChatId: Int? = null

    val enabled: Boolean
        get() = (BuildConfig.DEBUG || BuildConfig.STANDARD_NPU_RUNTIME_ENABLED) &&
            (BuildConfig.CURRENT_FLAVOR == "standard" || BuildConfig.CUSTOM_BUILD_EXPERIMENT)

    suspend fun run(
        context: Context,
        chatId: Int,
        userPrompt: String,
        initialTurns: List<LocalConversationTurn>,
        selectedModelFile: String?,
        requestedMaxOutputTokens: Int,
        markdownStreamingMode: MarkdownStreamingMode = MarkdownStreamingMode.DEFAULT,
        onPartial: ((String) -> Unit)? = null,
        trace: (String) -> Unit = {},
    ): NpuKotlinConversationProductAttempt = withContext(Dispatchers.IO) {
        if (!enabled) {
            return@withContext NpuKotlinConversationProductAttempt(
                failureReason = "kotlin_conversation_product_route_disabled",
            )
        }
        val prompt = userPrompt.trim()
        if (prompt.isBlank()) {
            return@withContext NpuKotlinConversationProductAttempt(
                failureReason = "blank_prompt",
            )
        }
        val modelPath = selectedModelFile?.trim().orEmpty()
        val modelFile = File(modelPath)
        if (!modelFile.isFile || modelFile.length() <= 0L) {
            return@withContext NpuKotlinConversationProductAttempt(
                failureReason = "npu_model_unavailable",
            )
        }

        mutex.withLock {
            maybeReleaseExpiredLocked(SystemClock.elapsedRealtime(), trace)
            var engineReused = false
            var conversationReused = false
            try {
                if (engineModelPath != null && engineModelPath != modelPath) {
                    closeLocked("model_changed", trace)
                }
                if (engine == null) {
                    val cacheDir = context.applicationContext.cacheDir
                        .resolve("litertlm_npu_product_candidate")
                        .apply { mkdirs() }
                    val startedAt = SystemClock.elapsedRealtime()
                    engine = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = Backend.NPU(context.applicationInfo.nativeLibraryDir),
                            visionBackend = null,
                            audioBackend = null,
                            maxNumTokens = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
                            cacheDir = cacheDir.absolutePath,
                        ),
                    ).also { it.initialize() }
                    engineModelPath = modelPath
                    trace("$ROUTE_ID engine_created_ms=${SystemClock.elapsedRealtime() - startedAt}")
                } else {
                    engineReused = true
                    trace("$ROUTE_ID engine_reused=true")
                }

                if (conversation != null && conversationChatId != chatId) {
                    closeConversationLocked("chat_changed", trace)
                }
                if (conversation == null) {
                    val activeEngine = requireNotNull(engine)
                    conversation = activeEngine.createConversation(
                        LocalConversationPolicy.conversationConfig(initialTurns),
                    )
                    conversationChatId = chatId
                    trace("$ROUTE_ID conversation_created=true chat_id=$chatId initial_turns=${initialTurns.size}")
                } else {
                    conversationReused = true
                    trace("$ROUTE_ID conversation_reused=true chat_id=$chatId")
                }

                val activeConversation = requireNotNull(conversation)
                val sendStartedAt = SystemClock.elapsedRealtime()
                val streamingResult = if (onPartial != null) {
                    val builder = StringBuilder()
                    val appendContext = StreamingAppendContext()
                    var nativeChunkCount = 0
                    var visibleChunkCount = 0
                    var firstNativeChunkMs: Long? = null
                    var firstVisibleChunkMs: Long? = null
                    activeConversation.sendMessageAsync(
                        prompt,
                        LocalConversationPolicy.generationExtraContext,
                    ).collect { message ->
                        currentCoroutineContext().ensureActive()
                        val chunk = renderStreamingMessageChunk(message)
                        if (!chunk.isNullOrEmpty() && isViableStreamingChunk(chunk)) {
                            nativeChunkCount += 1
                            if (firstNativeChunkMs == null) {
                                firstNativeChunkMs = (SystemClock.elapsedRealtime() - sendStartedAt)
                                    .coerceAtLeast(0L)
                            }
                            appendMarkdownStreamingChunk(
                                builder = builder,
                                extractedRaw = chunk,
                                context = appendContext,
                                markdownStreamingMode = markdownStreamingMode,
                                appendTrace = trace,
                            )
                            if (builder.isNotEmpty()) {
                                val accumulated = builder.toString()
                                val provisionalQuality = evaluateNpuStandardRouteQualityCandidate(
                                    rawOutput = accumulated,
                                    sanitizedOutput = accumulated.trim(),
                                    inputPrompt = prompt,
                                    conversationApiUsed = true,
                                )
                                val safePartial = provisionalQuality.preparedOutput
                                    .takeIf {
                                        provisionalQuality.status == NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS &&
                                            it.isNotBlank()
                                    }
                                if (safePartial != null) {
                                    if (firstVisibleChunkMs == null) {
                                        firstVisibleChunkMs = (SystemClock.elapsedRealtime() - sendStartedAt)
                                            .coerceAtLeast(0L)
                                    }
                                    visibleChunkCount += 1
                                    onPartial(safePartial)
                                }
                            }
                        }
                    }
                    NpuConversationGenerationResult(
                        response = builder.toString().trim(),
                        durationMs = (SystemClock.elapsedRealtime() - sendStartedAt).coerceAtLeast(0L),
                        nativeStreamingUsed = true,
                        nativeStreamingChunkCount = nativeChunkCount,
                        streamingChunkCount = visibleChunkCount,
                        timeToFirstNativeChunkMs = firstNativeChunkMs,
                        timeToFirstChunkMs = firstVisibleChunkMs,
                    )
                } else {
                    val message = activeConversation.sendMessage(
                        prompt,
                        LocalConversationPolicy.generationExtraContext,
                    )
                    NpuConversationGenerationResult(
                        response = renderMessage(message).trim(),
                        durationMs = (SystemClock.elapsedRealtime() - sendStartedAt).coerceAtLeast(0L),
                    )
                }
                val sendMs = streamingResult.durationMs
                val response = streamingResult.response
                if (response.isBlank()) {
                    closeLocked("blank_output", trace)
                    return@withLock NpuKotlinConversationProductAttempt(
                        failureReason = "kotlin_conversation_blank_output",
                        engineReused = engineReused,
                        conversationReused = conversationReused,
                    )
                }

                val effectiveMaxOutputTokens = NpuStandardRouteS1Contract.maxOutputTokensForPrompt(
                    userPrompt = prompt,
                    requestedMaxOutputTokens = requestedMaxOutputTokens,
                )
                val mapped = NpuStandardRouteS1Mapper.map(
                    NpuStandardRouteS1RawResult(
                        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                        success = true,
                        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                        rawOutput = response,
                        sanitizedOutput = response,
                        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                        runDecodeReached = true,
                        npuBackendEvidence = NPU_EVIDENCE,
                        fallbackUsed = false,
                        timeout = false,
                        freshCrash = false,
                        requestedMaxOutputTokens = requestedMaxOutputTokens,
                        effectiveMaxOutputTokens = effectiveMaxOutputTokens,
                        selectedModelName = modelFile.name,
                        selectedModelFile = modelPath,
                        npuModelEligible = true,
                        npuS1DecodeMs = sendMs,
                        npuS1NativeTtftMs = streamingResult.timeToFirstNativeChunkMs,
                        npuS1TtftMs = streamingResult.timeToFirstChunkMs,
                        npuS1OutputTokens = NpuStandardRouteS1Contract.estimateOutputTokensFromText(response),
                        npuS1TokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
                        inputPrompt = prompt,
                    ),
                )
                val unified = withConversationApiProvenance(mapped)
                if (!unified.successCriteriaMet) {
                    closeLocked("quality_gate_failed", trace)
                    return@withLock NpuKotlinConversationProductAttempt(
                        failureReason = "kotlin_conversation_quality_gate_failed:${unified.outputQualityCandidateReason}",
                        engineReused = engineReused,
                        conversationReused = conversationReused,
                    )
                }
                lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
                if (!appInForeground) scheduleBackgroundReleaseLocked()
                trace("$ROUTE_ID success=true send_ms=$sendMs response_length=${response.length} native_streaming_used=${streamingResult.nativeStreamingUsed} native_streaming_chunk_count=${streamingResult.nativeStreamingChunkCount} visible_streaming_chunk_count=${streamingResult.streamingChunkCount} backend_ttft_ms=${streamingResult.timeToFirstNativeChunkMs ?: -1} lami_ttft_ms=${streamingResult.timeToFirstChunkMs ?: -1}")
                NpuKotlinConversationProductAttempt(
                    result = unified,
                    engineReused = engineReused,
                    conversationReused = conversationReused,
                    nativeStreamingUsed = streamingResult.nativeStreamingUsed,
                    nativeStreamingChunkCount = streamingResult.nativeStreamingChunkCount,
                    streamingChunkCount = streamingResult.streamingChunkCount,
                    timeToFirstNativeChunkMs = streamingResult.timeToFirstNativeChunkMs,
                    timeToFirstChunkMs = streamingResult.timeToFirstChunkMs,
                )
            } catch (cancelled: CancellationException) {
                trace("$ROUTE_ID cancelled=true")
                conversation?.let { activeConversation ->
                    runCatching { activeConversation.cancelProcess() }
                        .onSuccess { trace("$ROUTE_ID cancel_process=true") }
                        .onFailure { throwable ->
                            trace("$ROUTE_ID cancel_process=false error=${throwable.javaClass.simpleName}:${throwable.message.orEmpty()}")
                        }
                }
                closeLocked("cancelled", trace)
                throw cancelled
            } catch (throwable: Throwable) {
                val reason = "${throwable.javaClass.simpleName}:${throwable.message.orEmpty()}"
                trace("$ROUTE_ID failure=$reason")
                closeLocked("failure", trace)
                NpuKotlinConversationProductAttempt(
                    failureReason = "kotlin_conversation_failure:$reason",
                    engineReused = engineReused,
                    conversationReused = conversationReused,
                )
            }
        }
    }


    private data class NpuConversationGenerationResult(
        val response: String,
        val durationMs: Long,
        val nativeStreamingUsed: Boolean = false,
        val nativeStreamingChunkCount: Int = 0,
        val streamingChunkCount: Int = 0,
        val timeToFirstNativeChunkMs: Long? = null,
        val timeToFirstChunkMs: Long? = null,
    )

    private fun withConversationApiProvenance(
        result: NpuStandardRouteS1Result,
    ): NpuStandardRouteS1Result {
        val owner = LocalConversationPolicy.PROMPT_TEMPLATE_OWNER
        val evaluator = LocalConversationPolicy.PROMPT_TEMPLATE_EVALUATOR
        return result.copy(
            promptTemplateOwner = owner,
            promptTemplateEvaluator = evaluator,
            conversationApiUsed = true,
            appTemplateUsed = false,
            templateOwnershipUnified = true,
            displayText = NpuStandardRouteS1Contract.displayText(
                selection = result.selection,
                status = result.status,
                reason = result.reason,
                rawOutput = result.rawOutput,
                sanitizedOutput = result.sanitizedOutput,
                qualityClassification = result.qualityClassification,
                runDecodeReached = result.runDecodeReached,
                npuBackendEvidence = result.npuBackendEvidence,
                fallbackUsed = result.fallbackUsed,
                timeout = result.timeout,
                freshCrash = result.freshCrash,
                selectedModelName = result.selectedModelName,
                selectedModelFile = result.selectedModelFile,
                npuModelEligible = result.npuModelEligible,
                timing = result.timing,
                nativeDiagnostics = result.nativeDiagnostics,
                inputPrompt = result.inputPrompt,
                promptTemplateOwner = owner,
                promptTemplateEvaluator = evaluator,
                conversationApiUsed = true,
                appTemplateUsed = false,
                templateOwnershipUnified = true,
            ),
        )
    }

    override suspend fun notifyAppForegrounded(nowElapsedMs: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            backgroundReleaseJob?.cancel()
            backgroundReleaseJob = null
            maybeReleaseExpiredLocked(nowElapsedMs)
            appInForeground = true
            backgroundedAtElapsedMs = null
        }
    }

    override suspend fun notifyAppBackgrounded(nowElapsedMs: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            appInForeground = false
            backgroundedAtElapsedMs = nowElapsedMs
            scheduleBackgroundReleaseLocked()
        }
    }

    override suspend fun notifyLowMemory() = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked("low-memory", {})
        }
    }

    suspend fun reset(
        reason: String,
        trace: (String) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked(reason, trace)
        }
    }

    private fun scheduleBackgroundReleaseLocked() {
        backgroundReleaseJob?.cancel()
        backgroundReleaseJob = lifecycleScope.launch {
            delay(NPU_RESIDENT_BACKGROUND_TIMEOUT_MS)
            mutex.withLock {
                maybeReleaseExpiredLocked(SystemClock.elapsedRealtime())
            }
        }
    }

    private fun maybeReleaseExpiredLocked(
        nowElapsedMs: Long,
        trace: (String) -> Unit = {},
    ) {
        val state = NpuResidentLifecycleState(
            appInForeground = appInForeground,
            backgroundedAtElapsedMs = backgroundedAtElapsedMs,
            lastUsedAtElapsedMs = lastUsedAtElapsedMs,
        )
        when {
            isNpuBackgroundReleaseDue(state, nowElapsedMs) -> closeLocked("background-timeout", trace)
            isNpuIdleReleaseDue(state, nowElapsedMs) -> closeLocked("idle-timeout", trace)
        }
    }

    private fun closeConversationLocked(
        reason: String,
        trace: (String) -> Unit,
    ) {
        val activeConversation = conversation
        conversation = null
        conversationChatId = null
        if (activeConversation != null) {
            runCatching { activeConversation.close() }
            trace("$ROUTE_ID conversation_closed=true reason=$reason")
        }
    }

    private fun closeLocked(
        reason: String,
        trace: (String) -> Unit,
    ) {
        closeConversationLocked(reason, trace)
        val activeEngine = engine
        engine = null
        engineModelPath = null
        lastUsedAtElapsedMs = null
        backgroundReleaseJob?.cancel()
        backgroundReleaseJob = null
        if (activeEngine != null) {
            runCatching { activeEngine.close() }
            trace("$ROUTE_ID engine_closed=true reason=$reason")
        }
    }

    private fun renderStreamingMessageChunk(message: Message): String? =
        message.contents.contents.joinToString(separator = "") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> ""
            }
        }.takeIf { it.isNotEmpty() }

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
