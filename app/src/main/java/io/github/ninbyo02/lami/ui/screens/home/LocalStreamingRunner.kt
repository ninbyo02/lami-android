package io.github.ninbyo02.lami.ui.screens.home

import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import io.github.ninbyo02.lami.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TOKENIZER_COUNT_UNAVAILABLE_NOTE =
    "このビルドの LiteRT API では tokenizer-based token count を取得できませんでした。"

internal interface LocalStreamingRunner<T> {
    suspend fun run(
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        resolvedModelPath: String? = null,
        cacheDirPath: String? = null,
        onPartial: (String) -> Unit = {},
    ): T?
}

internal class DefaultLocalStreamingRunner<T>(
    private val timeoutMs: Long,
    private val runInference: suspend (
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        resolvedModelPath: String?,
        cacheDirPath: String?,
        onPartial: (String) -> Unit,
    ) -> T,
) : LocalStreamingRunner<T> {
    override suspend fun run(
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        resolvedModelPath: String?,
        cacheDirPath: String?,
        onPartial: (String) -> Unit,
    ): T? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            runInference(
                prompt,
                localBaseModelFilePath,
                localBaseModelDisplayName,
                resolvedModelPath,
                cacheDirPath,
                onPartial,
            )
        }
    }
}


internal data class ReusableLocalEngineCreateDiagnostic(
    val engine: HeldLocalEngine?,
    val stage: String?,
    val className: String?,
    val message: String?,
)

internal data class HeldEngineRunResult(
    val responseText: String,
    val startElapsedRealtimeMs: Long,
    val firstPartialElapsedRealtimeMs: Long?,
    val completedElapsedRealtimeMs: Long,
    val partialCount: Int,
    val namespace: String,
    val officialFlowUsed: Boolean,
    val localModelDisplayName: String?,
    val measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null,
    val closeLifecycleSummary: RunCloseLifecycleSummary? = null,
)

internal data class RunCloseTargetOutcome(
    val label: String,
    val targetClassName: String?,
    val strategy: String?,
    val status: String,
    val errorClassName: String?,
    val message: String?,
)

internal data class RunCloseLifecycleSummary(
    val path: String,
    val successReturned: Boolean,
    val engineOutcome: RunCloseTargetOutcome? = null,
    val conversationOutcome: RunCloseTargetOutcome? = null,
    val sessionOutcome: RunCloseTargetOutcome? = null,
    val inferenceOutcome: RunCloseTargetOutcome? = null,
    val notes: String? = null,
)

internal suspend fun runWithHeldEngine(
    heldEngine: HeldLocalEngine,
    engineHolder: LocalInferenceEngineHolder,
    chatId: Int,
    prompt: String,
    localModelDisplayName: String?,
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit = {},
): HeldEngineRunResult? {
    val startElapsedRealtimeMs = SystemClock.elapsedRealtime()
    heldEngine.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
    val namespace = heldEngine.namespace
    var conversationOutcome: RunCloseTargetOutcome? = null
    var heldFlowPartialCount = 0
    var heldFlowFirstPartialElapsedRealtimeMs: Long? = null
    var heldFlowLastChunkElapsedRealtimeMs: Long? = null
    var officialFlowUsed = false
    var closeSummaryPath = "held-official-flow"
    var measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null

    val response = runCatching {
        runWithConversation(
            engine = heldEngine.engineInstance,
            namespace = namespace,
            appendTrace = appendTrace,
            onConversationClosed = { outcome -> conversationOutcome = outcome },
        ) { conversation ->
            val flowResponse = runCatching {
                val sendMessageAsyncMethod = findSendMessageAsyncMethod(
                    conversationClass = conversation.javaClass,
                    namespace = namespace,
                ) ?: return@runCatching null
                val flowValue = invokeSendMessageAsync(
                    conversation = conversation,
                    method = sendMessageAsyncMethod,
                    namespace = namespace,
                    prompt = prompt,
                ) ?: return@runCatching null
                val flow = flowValue as? Flow<*> ?: return@runCatching null
                val builder = StringBuilder()
                var lastPartial: String? = null
                flow.collect { message ->
                    if (!currentCoroutineContext().isActive) return@collect
                    val extracted = extractOfficialMessageTextWithTrace(
                        path = "held-engine-flow",
                        value = message,
                        appendTrace = appendTrace,
                    )?.trim().orEmpty()
                    if (extracted.isBlank() || extracted == lastPartial) return@collect
                    lastPartial = extracted
                    heldFlowPartialCount += 1
                    if (heldFlowFirstPartialElapsedRealtimeMs == null) {
                        heldFlowFirstPartialElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    }
                    heldFlowLastChunkElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    builder.append(extracted)
                    onPartial(builder.toString())
                }
                builder.toString().trim().takeIf { it.isNotBlank() }
            }.getOrElse { throwable ->
                safeAppendTrace(
                    appendTrace,
                    "UPSTREAM held-run flow-error chatId=$chatId class=${throwable.javaClass.simpleName} message=${throwable.message}",
                )
                null
            }
            if (flowResponse != null) {
                officialFlowUsed = true
                closeSummaryPath = "held-official-flow"
                val measuredCollector = MeasuredTokenTimingCollector(
                    path = "held-official-flow",
                    appendTrace = appendTrace,
                )
                measuredCollector.observe(
                    timing = "after-response",
                    conversation = conversation,
                )
                if (BuildConfig.DEBUG) {
                    measuredCollector.observe(
                        timing = "before-close",
                        conversation = conversation,
                    )
                }
                measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
                measuredTokenSnapshot = mergeTokenizerRecountSnapshot(
                    base = measuredTokenSnapshot,
                    conversation = conversation,
                    promptText = prompt,
                    fullResponseText = flowResponse,
                    timing = LocalLiteRtTimingSnapshot(
                        startedAtMs = startElapsedRealtimeMs,
                        firstNonEmptyChunkAtMs = heldFlowFirstPartialElapsedRealtimeMs,
                        lastChunkAtMs = heldFlowLastChunkElapsedRealtimeMs,
                        endedAtMs = SystemClock.elapsedRealtime(),
                    ),
                    appendTrace = appendTrace,
                )
                measuredCollector.emitAdoptedTrace()
                return@runWithConversation flowResponse
            }

            val blockingResponse = runCatching {
                val sendMethod = findBlockingSendMethod(
                    conversationClass = conversation.javaClass,
                    namespace = namespace,
                ) ?: return@runCatching null
                val value = invokeBlockingSend(
                    conversation = conversation,
                    method = sendMethod,
                    namespace = namespace,
                    prompt = prompt,
                ) ?: return@runCatching null
                extractOfficialMessageTextWithTrace(
                    path = "held-engine-blocking",
                    value = value,
                    appendTrace = appendTrace,
                )?.trim()?.takeIf { it.isNotBlank() }
            }.getOrElse { throwable ->
                safeAppendTrace(
                    appendTrace,
                    "UPSTREAM held-run blocking-error chatId=$chatId class=${throwable.javaClass.simpleName} message=${throwable.message}",
                )
                null
            }
            if (blockingResponse != null) {
                officialFlowUsed = false
                closeSummaryPath = "held-official-blocking"
                val measuredCollector = MeasuredTokenTimingCollector(
                    path = "held-official-blocking",
                    appendTrace = appendTrace,
                )
                measuredCollector.observe(
                    timing = "after-response",
                    conversation = conversation,
                )
                if (BuildConfig.DEBUG) {
                    measuredCollector.observe(
                        timing = "before-close",
                        conversation = conversation,
                    )
                }
                measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
                val completedAtMs = SystemClock.elapsedRealtime()
                measuredTokenSnapshot = mergeTokenizerRecountSnapshot(
                    base = measuredTokenSnapshot,
                    conversation = conversation,
                    promptText = prompt,
                    fullResponseText = blockingResponse,
                    timing = LocalLiteRtTimingSnapshot(
                        startedAtMs = startElapsedRealtimeMs,
                        firstNonEmptyChunkAtMs = completedAtMs,
                        lastChunkAtMs = completedAtMs,
                        endedAtMs = completedAtMs,
                    ),
                    appendTrace = appendTrace,
                )
                measuredCollector.emitAdoptedTrace()
                onPartial(blockingResponse)
            }
            blockingResponse
        }
    }.getOrElse { throwable ->
        safeAppendTrace(
            appendTrace,
            "UPSTREAM held-run error chatId=$chatId class=${throwable.javaClass.simpleName} message=${throwable.message}",
        )
        null
    } ?: return null

    val closeSummary = RunCloseLifecycleSummary(
        path = closeSummaryPath,
        successReturned = true,
        conversationOutcome = conversationOutcome ?: RunCloseTargetOutcome("conversation", null, "per-send", "none", null, null),
        sessionOutcome = RunCloseTargetOutcome(
            label = "session",
            targetClassName = null,
            strategy = null,
            status = "none",
            errorClassName = null,
            message = null,
        ),
        engineOutcome = RunCloseTargetOutcome(
            label = "engine",
            targetClassName = null,
            strategy = null,
            status = "none",
            errorClassName = null,
            message = null,
        ),
    )
    safeAppendTrace(
        appendTrace,
        "UPSTREAM close-summary path=${closeSummary.path} successReturned=${closeSummary.successReturned}",
    )
    emitCloseSummaryTrace(appendTrace, closeSummary.path, closeSummary.conversationOutcome ?: RunCloseTargetOutcome("conversation", null, null, "none", null, null))
    emitCloseSummaryTrace(appendTrace, closeSummary.path, closeSummary.sessionOutcome ?: RunCloseTargetOutcome("session", null, null, "none", null, null))
    emitCloseSummaryTrace(appendTrace, closeSummary.path, closeSummary.engineOutcome ?: RunCloseTargetOutcome("engine", null, null, "none", null, null))
    safeAppendTrace(
        appendTrace,
        "UPSTREAM held-run final source=${if (officialFlowUsed) "held-official-flow" else "held-official-blocking"} closePath=${closeSummary.path}",
    )
    return HeldEngineRunResult(
        responseText = response,
        startElapsedRealtimeMs = startElapsedRealtimeMs,
        firstPartialElapsedRealtimeMs = if (officialFlowUsed) heldFlowFirstPartialElapsedRealtimeMs else SystemClock.elapsedRealtime(),
        completedElapsedRealtimeMs = SystemClock.elapsedRealtime(),
        partialCount = if (officialFlowUsed) heldFlowPartialCount else 1,
        namespace = namespace ?: "unknown",
        officialFlowUsed = officialFlowUsed,
        localModelDisplayName = localModelDisplayName,
        measuredTokenSnapshot = measuredTokenSnapshot,
        closeLifecycleSummary = closeSummary,
    )
}
internal data class LocalOfficialConversationApiProbeResult(
    val namespace: String?,
    val conversationClassFound: Boolean,
    val createConversationMethodFound: Boolean,
    val sendMessageAsyncMethodFound: Boolean,
    val sendMessageAsyncReturnsFlow: Boolean,
    val messageClassFound: Boolean,
    val fallbackNamespaceMatched: Boolean,
) {
    val isAvailable: Boolean
        get() =
            conversationClassFound &&
                createConversationMethodFound &&
                sendMessageAsyncMethodFound &&
                messageClassFound
}

internal fun probeLocalOfficialConversationApi(): LocalOfficialConversationApiProbeResult {
    val primary = probeSingleOfficialConversationNamespace(
        namespace = "com.google.ai.edge.litertlm",
        engineClassName = "com.google.ai.edge.litertlm.Engine",
    )
    if (primary.conversationClassFound ||
        primary.createConversationMethodFound ||
        primary.sendMessageAsyncMethodFound ||
        primary.messageClassFound
    ) {
        return primary
    }
    val fallback = probeSingleOfficialConversationNamespace(
        namespace = "com.google.mediapipe.tasks.genai.llminference",
        engineClassName = "com.google.mediapipe.tasks.genai.llminference.LlmInference",
    )
    return fallback.copy(fallbackNamespaceMatched = fallback.isAvailable)
}

private fun probeSingleOfficialConversationNamespace(
    namespace: String,
    engineClassName: String,
): LocalOfficialConversationApiProbeResult {
    val conversationClass = runCatching {
        Class.forName("$namespace.Conversation")
    }.getOrNull()
    val messageClass = runCatching {
        Class.forName("$namespace.Message")
    }.getOrNull()
    val engineClass = runCatching {
        Class.forName(engineClassName)
    }.getOrNull()
    val createConversationMethodFound = engineClass?.methods?.any { it.name == "createConversation" } == true
    val sendMessageAsyncMethod = conversationClass?.methods?.firstOrNull { it.name == "sendMessageAsync" }
    val flowClass = runCatching { Class.forName("kotlinx.coroutines.flow.Flow") }.getOrNull()
    val sendMessageAsyncReturnsFlow = runCatching {
        val returnType = sendMessageAsyncMethod?.returnType ?: return@runCatching false
        when {
            flowClass != null && flowClass.isAssignableFrom(returnType) -> true
            returnType.name == "kotlinx.coroutines.flow.Flow" -> true
            returnType.interfaces.any { it.name == "kotlinx.coroutines.flow.Flow" } -> true
            else -> false
        }
    }.getOrDefault(false)

    return LocalOfficialConversationApiProbeResult(
        namespace = namespace,
        conversationClassFound = conversationClass != null,
        createConversationMethodFound = createConversationMethodFound,
        sendMessageAsyncMethodFound = sendMessageAsyncMethod != null,
        sendMessageAsyncReturnsFlow = sendMessageAsyncReturnsFlow,
        messageClassFound = messageClass != null,
        fallbackNamespaceMatched = false,
    )
}

internal data class LocalOfficialFlowStreamingResult(
    val response: String,
    val partialCount: Int,
    val firstNonEmptyPartialElapsedRealtimeMs: Long?,
    val measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null,
    val closeLifecycleSummary: RunCloseLifecycleSummary? = null,
)

internal data class LocalOfficialBlockingResult(
    val response: String?,
    val measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null,
    val closeLifecycleSummary: RunCloseLifecycleSummary? = null,
)

private data class LocalOfficialDirectBlockingResult(
    val response: String?,
    val measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null,
    val closeLifecycleSummary: RunCloseLifecycleSummary? = null,
)

private fun ensureCloseLifecycleSummary(
    summary: RunCloseLifecycleSummary?,
    path: String,
    successReturned: Boolean,
): RunCloseLifecycleSummary {
    return summary ?: RunCloseLifecycleSummary(
        path = path,
        successReturned = successReturned,
    )
}

private class MeasuredTokenTimingCollector(
    private val path: String,
    private val appendTrace: (String) -> Unit,
) {
    // 比較観測のため複数時点を読む。最終採用は「最後に non-null を返した snapshot」。
    private var adoptedTiming: String? = null
    private var adoptedSnapshot: LocalInferenceMeasuredTokenSnapshot? = null

    fun observe(
        timing: String,
        conversation: Any?,
    ): LocalInferenceMeasuredTokenSnapshot? {
        val snapshot = readMeasuredTokenSnapshotFromConversation(
            conversation = conversation,
            path = path,
            appendTrace = appendTrace,
        )
        if (BuildConfig.DEBUG) {
            safeAppendTrace(
                appendTrace,
                "UPSTREAM measured-tokens-check timing=$timing input=${snapshot?.inputTokens} output=${snapshot?.outputTokens} total=${snapshot?.totalTokens} path=$path",
            )
        }
        if (snapshot != null) {
            adoptedTiming = timing
            adoptedSnapshot = snapshot
        }
        return snapshot
    }

    fun adoptedSnapshot(): LocalInferenceMeasuredTokenSnapshot? = adoptedSnapshot

    fun emitAdoptedTrace() {
        if (!BuildConfig.DEBUG) return
        safeAppendTrace(
            appendTrace,
            "UPSTREAM measured-tokens-adopted policy=last-non-null timing=${adoptedTiming ?: "none"} input=${adoptedSnapshot?.inputTokens} output=${adoptedSnapshot?.outputTokens} total=${adoptedSnapshot?.totalTokens} path=$path",
        )
    }
}

private data class LocalLiteRtTimingSnapshot(
    val startedAtMs: Long,
    val firstNonEmptyChunkAtMs: Long?,
    val lastChunkAtMs: Long?,
    val endedAtMs: Long,
)

private fun mergeTokenizerRecountSnapshot(
    base: LocalInferenceMeasuredTokenSnapshot?,
    conversation: Any?,
    promptText: String,
    fullResponseText: String?,
    timing: LocalLiteRtTimingSnapshot,
    appendTrace: (String) -> Unit,
): LocalInferenceMeasuredTokenSnapshot? {
    val sanitizedPrompt = promptText
    val sanitizedResponse = fullResponseText.orEmpty()
    val tokenizerSnapshot = readTokenizerRecountSnapshotFromConversation(
        conversation = conversation,
        promptText = sanitizedPrompt,
        fullResponseText = sanitizedResponse,
        timing = timing,
        appendTrace = appendTrace,
    ) ?: return base
    return if (base == null) {
        tokenizerSnapshot
    } else {
        base.copy(
            inputTokens = tokenizerSnapshot.inputTokens ?: base.inputTokens,
            outputTokens = tokenizerSnapshot.outputTokens ?: base.outputTokens,
            totalTokens = tokenizerSnapshot.totalTokens ?: base.totalTokens,
            tokenCountMode = tokenizerSnapshot.tokenCountMode ?: base.tokenCountMode,
            notes = tokenizerSnapshot.notes ?: base.notes,
            tokensPerSecond = tokenizerSnapshot.tokensPerSecond ?: base.tokensPerSecond,
            charsPerSecond = tokenizerSnapshot.charsPerSecond ?: base.charsPerSecond,
            ttftMs = tokenizerSnapshot.ttftMs ?: base.ttftMs,
            decodeDurationMs = tokenizerSnapshot.decodeDurationMs ?: base.decodeDurationMs,
            totalDurationMs = tokenizerSnapshot.totalDurationMs ?: base.totalDurationMs,
        )
    }
}

private fun readTokenizerRecountSnapshotFromConversation(
    conversation: Any?,
    promptText: String,
    fullResponseText: String,
    timing: LocalLiteRtTimingSnapshot,
    appendTrace: (String) -> Unit,
): LocalInferenceMeasuredTokenSnapshot? {
    if (conversation !is Conversation) return null
    return runCatching {
        if (BuildConfig.DEBUG && promptText.isBlank()) {
            safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped-empty-prompt")
        }
        val ttftMs = timing.firstNonEmptyChunkAtMs?.let { (it - timing.startedAtMs).coerceAtLeast(0L) }
        val decodeDurationMs = if (timing.firstNonEmptyChunkAtMs != null && timing.lastChunkAtMs != null) {
            (timing.lastChunkAtMs - timing.firstNonEmptyChunkAtMs).coerceAtLeast(0L)
        } else {
            null
        }
        val totalDurationMs = (timing.endedAtMs - timing.startedAtMs).coerceAtLeast(0L)
        val charsPerSecond = if (decodeDurationMs != null && decodeDurationMs > 0L) {
            val responseChars = fullResponseText.length
            responseChars / (decodeDurationMs / 1000.0)
        } else {
            null
        }?.takeIf { it.isFinite() }

        val tokenizerRecountOutcome = tryReadTokenizerRecountViaReflection(
            conversation = conversation,
            promptText = promptText,
            fullResponseText = fullResponseText,
            appendTrace = appendTrace,
        )
        val tokenizerRecount = tokenizerRecountOutcome.result

        val inputTokenCount = tokenizerRecount?.promptTokens
        val outputTokenCount = tokenizerRecount?.responseTokens
        val totalTokenCount = tokenizerRecount?.totalTokens
        val tokenCountMode = if (
            tokenizerRecount != null &&
            inputTokenCount != null &&
            outputTokenCount != null
        ) {
            "tokenizer_recount"
        } else {
            null
        }
        val notes = if (tokenizerRecount == null) TOKENIZER_COUNT_UNAVAILABLE_NOTE else null
        val tokensPerSecond = if (
            outputTokenCount != null && decodeDurationMs != null && decodeDurationMs > 0L
        ) {
            outputTokenCount / (decodeDurationMs / 1000.0)
        } else {
            null
        }?.takeIf { it.isFinite() }

        LocalInferenceMeasuredTokenSnapshot(
            inputTokens = inputTokenCount,
            outputTokens = outputTokenCount,
            totalTokens = totalTokenCount,
            tokenizerRecountStatus = tokenizerRecountOutcome.status,
            tokenCountMode = tokenCountMode,
            notes = notes,
            tokensPerSecond = tokensPerSecond,
            charsPerSecond = charsPerSecond,
            ttftMs = ttftMs,
            decodeDurationMs = decodeDurationMs,
            totalDurationMs = totalDurationMs,
        )
    }.onFailure { throwable ->
        safeAppendTrace(
            appendTrace,
            "UPSTREAM tokenizer-recount failed ${throwable.javaClass.simpleName}:${throwable.message}",
        )
    }.getOrNull()
}

private data class TokenizerRecountResult(
    val promptTokens: Int,
    val responseTokens: Int,
) {
    val totalTokens: Int = promptTokens + responseTokens
}

private data class TokenizerRecountOutcome(
    val result: TokenizerRecountResult? = null,
    val status: String,
)

private fun tryReadTokenizerRecountViaReflection(
    conversation: Conversation,
    promptText: String,
    fullResponseText: String,
    appendTrace: (String) -> Unit,
) : TokenizerRecountOutcome {
    val inferenceInstance = tryResolveInferenceInstanceForTokenizerSession(conversation)
        ?: return TokenizerRecountOutcome(
            status = "skipped reason=inference-instance-not-found",
        ).also {
            safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped reason=inference-instance-not-found")
        }
    var sessionInstance: Any? = null
    return try {
        sessionInstance = tryCreateTokenizerSessionViaReflection(inferenceInstance)
            ?: return TokenizerRecountOutcome(
                status = "skipped reason=session-create-failed",
            ).also {
                safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped reason=session-create-failed")
            }
        val sizeMethod = findSizeInTokensMethod(sessionInstance)
            ?: return TokenizerRecountOutcome(
                status = "skipped reason=size-method-not-found",
            ).also {
                safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped reason=size-method-not-found")
            }
        val promptTokens = invokeSizeInTokens(sessionInstance, sizeMethod, promptText)
            ?: return TokenizerRecountOutcome(
                status = "skipped reason=prompt-token-failed",
            ).also {
                safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped reason=prompt-token-failed")
            }
        val responseTokens = invokeSizeInTokens(sessionInstance, sizeMethod, fullResponseText)
            ?: return TokenizerRecountOutcome(
                status = "skipped reason=response-token-failed",
            ).also {
                safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped reason=response-token-failed")
            }
        TokenizerRecountOutcome(
            result = TokenizerRecountResult(promptTokens = promptTokens, responseTokens = responseTokens),
            status = "success",
        )
    } catch (throwable: Throwable) {
        val status = "reflection-failed ${throwable.javaClass.simpleName}:${throwable.message}"
        safeAppendTrace(
            appendTrace,
            "UPSTREAM tokenizer-recount $status",
        )
        TokenizerRecountOutcome(status = status)
    } finally {
        tryCloseTokenizerSession(sessionInstance, appendTrace)
    }
}

private fun tryResolveInferenceInstanceForTokenizerSession(conversation: Conversation): Any? {
    val methodCandidates = conversation.javaClass.methods.filter { method ->
        method.parameterTypes.isEmpty() && (
            method.name == "getInference" ||
                method.name == "inference" ||
                method.name == "getLlmInference" ||
                method.name == "llmInference"
            )
    }
    methodCandidates.forEach { method ->
        runCatching { method.invoke(conversation) }
            .getOrNull()
            ?.let { return it }
    }
    val fieldCandidates = conversation.javaClass.declaredFields.filter { field ->
        val lowerName = field.name.lowercase(Locale.ROOT)
        lowerName.contains("inference")
    }
    fieldCandidates.forEach { field ->
        runCatching {
            field.isAccessible = true
            field.get(conversation)
        }.getOrNull()?.let { return it }
    }
    return null
}

private fun tryCreateTokenizerSessionViaReflection(inferenceInstance: Any): Any? {
    val sessionClass = runCatching {
        Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession")
    }.getOrNull() ?: return null
    val createMethods = sessionClass.methods.filter { method ->
        method.name == "createFromOptions" && java.lang.reflect.Modifier.isStatic(method.modifiers)
    }
    val createMethod = createMethods.firstOrNull { method ->
        method.parameterTypes.size == 1 &&
            method.parameterTypes[0].isAssignableFrom(inferenceInstance.javaClass)
    } ?: createMethods.firstOrNull { method ->
        method.parameterTypes.size == 2 &&
            method.parameterTypes[0].isAssignableFrom(inferenceInstance.javaClass)
    } ?: return null

    return when (createMethod.parameterTypes.size) {
        1 -> runCatching { createMethod.invoke(null, inferenceInstance) }.getOrNull()
        2 -> {
            val optionsClass = createMethod.parameterTypes[1]
            val options = buildTokenizerSessionOptionsViaReflection(optionsClass) ?: return null
            runCatching { createMethod.invoke(null, inferenceInstance, options) }.getOrNull()
        }
        else -> null
    }
}

private fun buildTokenizerSessionOptionsViaReflection(optionsClass: Class<*>): Any? {
    val builderFactory = optionsClass.methods.firstOrNull { method ->
        method.name == "builder" &&
            method.parameterTypes.isEmpty() &&
            java.lang.reflect.Modifier.isStatic(method.modifiers)
    }
    val builder = if (builderFactory != null) {
        runCatching { builderFactory.invoke(null) }.getOrNull()
    } else {
        val builderClass = optionsClass.declaredClasses.firstOrNull { it.simpleName == "Builder" } ?: return null
        val constructor = builderClass.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() } ?: return null
        runCatching {
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrNull()
    } ?: return null

    val buildMethod = builder.javaClass.methods.firstOrNull { method ->
        method.name == "build" && method.parameterTypes.isEmpty()
    } ?: return null
    return runCatching { buildMethod.invoke(builder) }.getOrNull()
}

private fun findSizeInTokensMethod(sessionInstance: Any): Method? {
    return sessionInstance.javaClass.methods.firstOrNull { method ->
        method.name == "sizeInTokens" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == String::class.java
    }
}

private fun invokeSizeInTokens(
    sessionInstance: Any,
    sizeMethod: Method,
    input: String,
): Int? {
    val result = runCatching { sizeMethod.invoke(sessionInstance, input) }.getOrNull() ?: return null
    return (result as? Number)?.toInt()
}

private fun tryCloseTokenizerSession(
    sessionInstance: Any?,
    appendTrace: (String) -> Unit,
) {
    if (sessionInstance == null) return
    val closeMethod = sessionInstance.javaClass.methods.firstOrNull { method ->
        method.name == "close" && method.parameterTypes.isEmpty()
    } ?: return
    runCatching {
        closeMethod.invoke(sessionInstance)
    }.onFailure { throwable ->
        safeAppendTrace(
            appendTrace,
            "UPSTREAM tokenizer-recount close-failed ${throwable.javaClass.simpleName}:${throwable.message}",
        )
    }
}

@OptIn(ExperimentalApi::class)
private fun readMeasuredTokenSnapshotFromConversation(
    conversation: Any?,
    path: String,
    appendTrace: (String) -> Unit,
): LocalInferenceMeasuredTokenSnapshot? {
    val liteRtConversation = conversation as? Conversation ?: return null
    return runCatching {
        val benchmarkInfo = liteRtConversation.getBenchmarkInfo()
        val lastPrefillTokenCount = benchmarkInfo.lastPrefillTokenCount.takeIf { it >= 0 }
        val lastDecodeTokenCount = benchmarkInfo.lastDecodeTokenCount.takeIf { it >= 0 }
        val benchmarkPrefillTokenCount = benchmarkInfo.readBenchmarkInfoRawValue(
            candidates = listOf("prefillTokenCount", "lastPrefillTokenCount"),
        )
        val benchmarkDecodeTokenCount = benchmarkInfo.readBenchmarkInfoRawValue(
            candidates = listOf("decodeTokenCount", "lastDecodeTokenCount"),
        )
        val benchmarkPrefillTokensPerSecond = benchmarkInfo.readBenchmarkInfoRawValue(
            candidates = listOf("prefillTokensPerSecond", "lastPrefillTokensPerSecond"),
        )
        val benchmarkDecodeTokensPerSecond = benchmarkInfo.readBenchmarkInfoRawValue(
            candidates = listOf("decodeTokensPerSecond", "lastDecodeTokensPerSecond"),
        )
        val benchmarkTimeToFirstTokenMs = benchmarkInfo.readBenchmarkInfoRawValue(
            candidates = listOf("timeToFirstTokenMs", "lastTimeToFirstTokenMs"),
        )
        val benchmarkModelInitMs = benchmarkInfo.readBenchmarkInfoRawValue(
            candidates = listOf("modelInitMs", "lastModelInitMs"),
        )
        val inputTokens = lastPrefillTokenCount
        val outputTokens = lastDecodeTokenCount
        val totalTokens = if (inputTokens != null && outputTokens != null) {
            inputTokens + outputTokens
        } else {
            null
        }
        val snapshot = if (
            inputTokens == null &&
            outputTokens == null &&
            totalTokens == null &&
            benchmarkPrefillTokenCount == null &&
            benchmarkDecodeTokenCount == null &&
            benchmarkPrefillTokensPerSecond == null &&
            benchmarkDecodeTokensPerSecond == null &&
            benchmarkTimeToFirstTokenMs == null &&
            benchmarkModelInitMs == null
        ) {
            null
        } else {
            LocalInferenceMeasuredTokenSnapshot(
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalTokens = totalTokens,
                lastPrefillTokenCount = lastPrefillTokenCount,
                lastDecodeTokenCount = lastDecodeTokenCount,
                rawPrefillTokenCount = benchmarkPrefillTokenCount,
                rawDecodeTokenCount = benchmarkDecodeTokenCount,
                rawPrefillTokensPerSecond = benchmarkPrefillTokensPerSecond,
                rawDecodeTokensPerSecond = benchmarkDecodeTokensPerSecond,
                rawTimeToFirstTokenMs = benchmarkTimeToFirstTokenMs,
                rawModelInitMs = benchmarkModelInitMs,
            )
        }
        if (BuildConfig.DEBUG) {
            safeAppendTrace(
                appendTrace,
                "UPSTREAM measured-tokens input=${snapshot?.inputTokens} output=${snapshot?.outputTokens} total=${snapshot?.totalTokens} path=$path",
            )
        }
        snapshot
    }.onFailure { throwable ->
        safeAppendTrace(
            appendTrace,
            "UPSTREAM $path benchmarkInfo failed ${throwable.javaClass.simpleName}:${throwable.message}",
        )
    }.getOrNull()
}

private fun Any.readBenchmarkInfoRawValue(candidates: List<String>): String? {
    for (name in candidates) {
        readBenchmarkInfoRawValue(name)?.let { return it }
    }
    return null
}

private fun Any.readBenchmarkInfoRawValue(name: String): String? {
    val methodSuffix = name.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }
    return runCatching {
        javaClass.methods.firstOrNull { method ->
            method.parameterCount == 0 &&
                (method.name == name || method.name == "get$methodSuffix")
        }?.invoke(this)?.toString()
    }.getOrNull()
}

private val OFFICIAL_TEXT_CANDIDATES = listOf(
    "text",
    "getText",
    "content",
    "getContent",
    "result",
    "getResult",
    "token",
    "getToken",
    "parts",
    "getParts",
    "toString",
)

internal suspend fun tryRunOfficialLiteRtFlowStreaming(
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit = {},
    onFallbackReason: (String) -> Unit = {},
): LocalOfficialFlowStreamingResult? {
    val startElapsedMs = SystemClock.elapsedRealtime()
    val attempts = listOf(
        LocalOfficialNamespaceSpec(
            namespace = "com.google.ai.edge.litertlm",
            engineClassName = "com.google.ai.edge.litertlm.Engine",
            optionsCandidates = listOf(
                "com.google.ai.edge.litertlm.Engine\$Options",
                "com.google.ai.edge.litertlm.EngineOptions",
            ),
        ),
        LocalOfficialNamespaceSpec(
            namespace = "com.google.mediapipe.tasks.genai.llminference",
            engineClassName = "com.google.mediapipe.tasks.genai.llminference.LlmInference",
            optionsCandidates = listOf(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$Options",
            ),
        ),
    )
    var fallbackReasonReported = false
    attempts.forEach { spec ->
        val result = runCatching {
            runOfficialFlowStreamingSingleNamespace(
                spec = spec,
                prompt = prompt,
                modelPath = modelPath,
                cacheDirPath = cacheDirPath,
                startElapsedMs = startElapsedMs,
                onPartial = onPartial,
                appendTrace = appendTrace,
            )
        }.onFailure { throwable ->
            val reasonCode = (throwable as? OfficialFlowFallbackException)?.reasonCode ?: "official_exception"
            fallbackReasonReported = true
            runCatching { onFallbackReason(reasonCode) }
            safeAppendTrace(
                appendTrace = appendTrace,
                message = "UPSTREAM official-flow fallback reason=$reasonCode namespace=${spec.namespace}, error=${throwable.javaClass.simpleName}:${throwable.message}",
            )
        }.getOrNull()
        if (result != null) {
            val resolvedResult = result.copy(
                closeLifecycleSummary = ensureCloseLifecycleSummary(
                    summary = result.closeLifecycleSummary,
                    path = "fallback-official-flow",
                    successReturned = true,
                ),
            )
            safeAppendTrace(
                appendTrace,
                "UPSTREAM official-flow final source=official-flow closePath=${resolvedResult.closeLifecycleSummary?.path ?: "fallback-official-flow"}",
            )
            return resolvedResult
        }
    }
    if (!fallbackReasonReported) {
        runCatching { onFallbackReason("no_partial_emitted") }
    }
    return null
}

internal fun tryRunOfficialLiteRtBlockingConversation(
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
    appendTrace: (String) -> Unit = {},
    onFallbackReason: (String) -> Unit = {},
): LocalOfficialBlockingResult? {
    val attempts = listOf(
        LocalOfficialNamespaceSpec(
            namespace = "com.google.ai.edge.litertlm",
            engineClassName = "com.google.ai.edge.litertlm.Engine",
            optionsCandidates = listOf(
                "com.google.ai.edge.litertlm.Engine\$Options",
                "com.google.ai.edge.litertlm.EngineOptions",
            ),
        ),
        LocalOfficialNamespaceSpec(
            namespace = "com.google.mediapipe.tasks.genai.llminference",
            engineClassName = "com.google.mediapipe.tasks.genai.llminference.LlmInference",
            optionsCandidates = listOf(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$Options",
            ),
        ),
    )
    attempts.forEach { spec ->
        val response = runCatching {
            runOfficialBlockingConversationSingleNamespace(
                spec = spec,
                prompt = prompt,
                modelPath = modelPath,
                cacheDirPath = cacheDirPath,
                appendTrace = appendTrace,
            )
        }.onFailure { throwable ->
            val reasonCode = (throwable as? OfficialFlowFallbackException)?.reasonCode ?: "official_blocking_exception"
            runCatching { onFallbackReason(reasonCode) }
            safeAppendTrace(
                appendTrace = appendTrace,
                message = "UPSTREAM official-blocking fallback reason=$reasonCode namespace=${spec.namespace}, error=${throwable.javaClass.simpleName}:${throwable.message}",
            )
        }.getOrNull()
        if (!response?.response.isNullOrBlank()) {
            val resolvedResponse = response.copy(
                closeLifecycleSummary = ensureCloseLifecycleSummary(
                    summary = response.closeLifecycleSummary,
                    path = "fallback-official-blocking",
                    successReturned = true,
                ),
            )
            safeAppendTrace(
                appendTrace,
                "UPSTREAM official-blocking final source=official-blocking closePath=${resolvedResponse.closeLifecycleSummary?.path ?: "fallback-official-blocking"}",
            )
            return resolvedResponse
        }
    }
    return null
}

private class OfficialFlowFallbackException(
    val reasonCode: String,
    cause: Throwable? = null,
) : RuntimeException(reasonCode, cause)

private data class LocalOfficialNamespaceSpec(
    val namespace: String,
    val engineClassName: String,
    val optionsCandidates: List<String>,
)

internal fun createReusableLocalInferenceEngine(
    context: android.content.Context,
    engineKey: HeldEngineKey,
    appendTrace: ((String) -> Unit)? = null,
): HeldLocalEngine? = createReusableLocalInferenceEngineWithDiagnostic(
    context = context,
    engineKey = engineKey,
    appendTrace = appendTrace,
).engine

internal fun createReusableLocalInferenceEngineWithDiagnostic(
    context: android.content.Context,
    engineKey: HeldEngineKey,
    appendTrace: ((String) -> Unit)? = null,
): ReusableLocalEngineCreateDiagnostic {
    val safeTrace: (String) -> Unit = { message ->
        runCatching { appendTrace?.invoke(message) }
    }
    var stage = "official-create-engine"
    val createdAt = SystemClock.elapsedRealtime()
    safeAppendTrace(safeTrace, "UPSTREAM held-create engine-config-create-start")
    val officialEngine = runCatching {
        createOfficialLiteRtLmEngineInstance(
            modelPath = engineKey.modelPath,
            cacheDirPath = engineKey.cacheDirPath,
            appendTrace = safeTrace,
        )
    }.getOrElse { throwable ->
        val className = throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name }
        return ReusableLocalEngineCreateDiagnostic(
            engine = null,
            stage = stage,
            className = className,
            message = (throwable.message ?: "official create engine failed").take(200),
        )
    }
    stage = if (officialEngine != null) "official-engine-created" else "official-engine-null"
    safeAppendTrace(safeTrace, "UPSTREAM held-create engine-created result=${if (officialEngine == null) "null" else "non-null"}")
    if (officialEngine != null) {
        safeAppendTrace(safeTrace, "UPSTREAM held-create engine-initialize-start")
        val initializeSucceeded = runCatching {
            val initializeMethod = officialEngine.javaClass.methods.firstOrNull { method ->
                method.name == "initialize" && method.parameterTypes.isEmpty()
            } ?: return@runCatching false
            initializeMethod.invoke(officialEngine)
            true
        }.onFailure { throwable ->
            safeAppendTrace(
                safeTrace,
                "UPSTREAM held-create engine-initialize-fail class=${throwable.javaClass.simpleName} message=${throwable.message}",
            )
        }.getOrDefault(false)
        if (initializeSucceeded) {
            safeAppendTrace(safeTrace, "UPSTREAM held-create engine-initialize-success")
        } else {
            safeAppendTrace(safeTrace, "UPSTREAM held-create engine-initialize-fail class=InitializeUnavailable message=initialize method missing or failed")
        }
        val held = HeldLocalEngine(
            engineKey = engineKey,
            modelPath = engineKey.modelPath,
            engineInstance = officialEngine,
            namespace = "com.google.ai.edge.litertlm",
            createdAtElapsedMs = createdAt,
            lastUsedAtElapsedMs = createdAt,
            useCount = 0,
            closeEngine = { trace -> closeQuietly(officialEngine, trace) },
        )
        stage = "held-engine-store"
        safeAppendTrace(safeTrace, "UPSTREAM held-engine created namespace=com.google.ai.edge.litertlm")
        safeAppendTrace(safeTrace, "UPSTREAM held-create conversation-create=deferred")
        stage = "success"
        return ReusableLocalEngineCreateDiagnostic(
            engine = held,
            stage = stage,
            className = null,
            message = null,
        )
    }

    stage = "official-engine-null"
    return ReusableLocalEngineCreateDiagnostic(
        engine = null,
        stage = stage,
        className = "ReturnedNull",
        message = "official litertlm engine returned null".take(200),
    )
}

private suspend fun <T> runWithConversation(
    engine: Any,
    namespace: String?,
    appendTrace: (String) -> Unit,
    closeSummaryPath: String? = null,
    onConversationClosed: ((RunCloseTargetOutcome) -> Unit)? = null,
    block: suspend (conversation: Any) -> T?,
): T? {
    var conversation: Any? = null
    return try {
        conversation = createConversationForHeldEngine(engine = engine, namespace = namespace, appendTrace = appendTrace)
        if (conversation == null) return null
        safeAppendTrace(
            appendTrace,
            "UPSTREAM held-conversation acquired class=${conversation.javaClass.name}",
        )
        block(conversation)
    } finally {
        safeAppendTrace(
            appendTrace,
            "UPSTREAM held-conversation close-start class=${conversation?.javaClass?.name ?: "null"}",
        )
        val outcome = tryCloseWithOutcome(
            label = "conversation",
            target = conversation,
            appendTrace = appendTrace,
            path = closeSummaryPath,
        )
        onConversationClosed?.invoke(outcome)
    }
}

private fun createConversationForHeldEngine(
    engine: Any,
    namespace: String?,
    appendTrace: (String) -> Unit,
): Any? {
    safeAppendTrace(
        appendTrace,
        "UPSTREAM held-conversation create-start namespace=${namespace ?: "null"} engineClass=${engine.javaClass.name}",
    )
    val conversation = if (namespace == "com.google.ai.edge.litertlm") {
        createOfficialLiteRtLmConversation(
            engine = engine,
            engineClass = engine.javaClass,
            appendTrace = appendTrace,
        )
    } else {
        val method = engine.javaClass.methods.firstOrNull { it.name == "createConversation" }
        if (method == null) {
            safeAppendTrace(
                appendTrace,
                "UPSTREAM held-conversation create-failed namespace=${namespace ?: "null"}",
            )
            return null
        }
        createOfficialConversation(
            engine = engine,
            createConversationMethod = method,
            appendTrace = appendTrace,
        )
    }
    if (conversation == null) {
        safeAppendTrace(
            appendTrace,
            "UPSTREAM held-conversation create-failed namespace=${namespace ?: "null"}",
        )
    } else {
        safeAppendTrace(
            appendTrace,
            "UPSTREAM held-conversation create-success class=${conversation.javaClass.name}",
        )
    }
    return conversation
}

private fun findSendMessageAsyncMethod(
    conversationClass: Class<*>,
    namespace: String?,
): Method? {
    return if (namespace == "com.google.ai.edge.litertlm") {
        conversationClass.methods.firstOrNull { method ->
            method.name == "sendMessageAsync" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                Map::class.java.isAssignableFrom(method.parameterTypes[1])
        }
    } else {
        conversationClass.methods.firstOrNull { it.name == "sendMessageAsync" && it.parameterTypes.size == 1 }
    }
}

private fun invokeSendMessageAsync(
    conversation: Any,
    method: Method,
    namespace: String?,
    prompt: String,
): Any? {
    return runCatching {
        if (namespace == "com.google.ai.edge.litertlm") {
            method.invoke(conversation, prompt, emptyMap<String, Any>())
        } else {
            val argument = buildSendMessageArgument(
                parameterType = method.parameterTypes.first(),
                namespace = namespace ?: "com.google.mediapipe.tasks.genai.llminference",
                prompt = prompt,
            ) ?: return null
            method.invoke(conversation, argument)
        }
    }.getOrNull()
}

private fun findBlockingSendMethod(
    conversationClass: Class<*>,
    namespace: String?,
): Method? {
    return if (namespace == "com.google.ai.edge.litertlm") {
        conversationClass.methods.firstOrNull { method ->
            method.name == "sendMessage" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                Map::class.java.isAssignableFrom(method.parameterTypes[1])
        }
    } else {
        conversationClass.methods.firstOrNull { method ->
            (method.name == "sendMessage" || method.name == "generateResponse") && method.parameterTypes.size == 1
        }
    }
}

private fun invokeBlockingSend(
    conversation: Any,
    method: Method,
    namespace: String?,
    prompt: String,
): Any? {
    return runCatching {
        if (namespace == "com.google.ai.edge.litertlm") {
            method.invoke(conversation, prompt, emptyMap<String, Any>())
        } else {
            val argument = buildSendMessageArgument(
                parameterType = method.parameterTypes.first(),
                namespace = namespace ?: "com.google.mediapipe.tasks.genai.llminference",
                prompt = prompt,
            ) ?: return null
            method.invoke(conversation, argument)
        }
    }.getOrNull()
}

private suspend fun runOfficialFlowStreamingSingleNamespace(
    spec: LocalOfficialNamespaceSpec,
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
    startElapsedMs: Long,
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit,
): LocalOfficialFlowStreamingResult? {
    if (spec.namespace == "com.google.ai.edge.litertlm") {
        return runOfficialLiteRtLmDirect(
            prompt = prompt,
            modelPath = modelPath,
            cacheDirPath = cacheDirPath,
            startElapsedMs = startElapsedMs,
            onPartial = onPartial,
            appendTrace = appendTrace,
        )
    }
    safeAppendTrace(appendTrace, "UPSTREAM official-flow start namespace=${spec.namespace}")
    val engineClass = runCatching { Class.forName(spec.engineClassName) }.getOrNull() ?: return null
    val conversationClass = runCatching { Class.forName("${spec.namespace}.Conversation") }.getOrNull() ?: return null
    val sendMessageAsyncMethod = if (spec.namespace == "com.google.ai.edge.litertlm") {
        conversationClass.methods.firstOrNull { method ->
            method.name == "sendMessageAsync" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                Map::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                Flow::class.java.isAssignableFrom(method.returnType)
        }?.also {
            safeAppendTrace(appendTrace, "UPSTREAM official-flow selectedMethod=sendMessageAsync(String,Map) namespace=${spec.namespace}")
        }
    } else {
        conversationClass.methods.firstOrNull { it.name == "sendMessageAsync" && it.parameterTypes.size == 1 }
    } ?: throw OfficialFlowFallbackException("send_message_async_missing")
    val createConversationMethod =
        engineClass.methods.firstOrNull { it.name == "createConversation" }
            ?: throw OfficialFlowFallbackException("conversation_create_failed")
    val engine = if (spec.namespace == "com.google.ai.edge.litertlm") {
        createOfficialLiteRtLmEngineInstance(
            modelPath = modelPath,
            cacheDirPath = cacheDirPath,
            appendTrace = appendTrace,
        )
    } else {
        createOfficialEngineInstance(engineClass, spec.optionsCandidates, modelPath)
    }
        ?: throw OfficialFlowFallbackException("conversation_create_failed")
    var conversation: Any? = null
    var successReached = false
    var conversationCloseOutcome: RunCloseTargetOutcome? = null
    var engineCloseOutcome: RunCloseTargetOutcome? = null
    var finalResult: LocalOfficialFlowStreamingResult? = null
    var measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null
    val measuredCollector = MeasuredTokenTimingCollector(
        path = "official-flow",
        appendTrace = appendTrace,
    )
    try {
        conversation = runCatching {
            if (spec.namespace == "com.google.ai.edge.litertlm") {
                createOfficialLiteRtLmConversation(
                    engine = engine,
                    engineClass = engineClass,
                    appendTrace = appendTrace,
                )
            } else {
                createOfficialConversation(
                    engine = engine,
                    createConversationMethod = createConversationMethod,
                    appendTrace = appendTrace,
                )
            }
        }.getOrElse { throwable ->
            throw OfficialFlowFallbackException("conversation_create_failed", throwable)
        } ?: throw OfficialFlowFallbackException("conversation_create_failed")
        val flowValue = if (spec.namespace == "com.google.ai.edge.litertlm") {
            safeAppendTrace(appendTrace, "UPSTREAM official-flow invoke promptLength=${prompt.length} mapSize=0")
            runCatching {
                sendMessageAsyncMethod.invoke(conversation, prompt, emptyMap<String, Any>())
            }.getOrElse { throwable ->
                throw OfficialFlowFallbackException("send_message_async_missing", throwable)
            }
        } else {
            val sendArgument =
                buildSendMessageArgument(
                    parameterType = sendMessageAsyncMethod.parameterTypes.first(),
                    namespace = spec.namespace,
                    prompt = prompt,
                ) ?: throw OfficialFlowFallbackException("send_message_async_missing")
            runCatching {
                sendMessageAsyncMethod.invoke(conversation, sendArgument)
            }.getOrElse { throwable ->
                throw OfficialFlowFallbackException("send_message_async_missing", throwable)
            }
        }
        safeAppendTrace(appendTrace, "UPSTREAM official-flow flowClass=${flowValue?.javaClass?.name ?: "null"}")
        val flow = flowValue as? Flow<*> ?: throw OfficialFlowFallbackException("send_message_async_missing")
        val builder = StringBuilder()
        var partialCount = 0
        var extractFailureCount = 0
        var firstPartialMs: Long? = null
        var lastNonEmptyChunkAtMs: Long? = null
        var lastPartial: String? = null
        runCatching {
            flow.collect { message ->
                if (!currentCoroutineContext().isActive) return@collect
                safeAppendTrace(
                    appendTrace = appendTrace,
                    message = "UPSTREAM official-flow chunkClass=${message?.javaClass?.name ?: "null"}",
                )
                val extractedText = extractOfficialMessageTextWithTrace(
                    path = "official-flow",
                    value = message,
                    appendTrace = appendTrace,
                )
                val extracted = extractedText?.trim().orEmpty()
                if (extracted.isBlank()) {
                    extractFailureCount += 1
                    return@collect
                }
                if (extracted.isBlank() || extracted == lastPartial) return@collect
                lastPartial = extracted
                builder.append(extracted)
                partialCount += 1
                if (firstPartialMs == null) {
                    firstPartialMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(0L)
                }
                lastNonEmptyChunkAtMs = SystemClock.elapsedRealtime()
                onPartial(builder.toString())
            }
        }.getOrElse { throwable ->
            if (throwable is OfficialFlowFallbackException) throw throwable
            throw OfficialFlowFallbackException("flow_collect_failed", throwable)
        }
        if (partialCount <= 0) {
            throw OfficialFlowFallbackException(if (extractFailureCount > 0) "message_extract_failed" else "no_partial_emitted")
        }
        val response = builder.toString().trim()
        if (response.isBlank()) throw OfficialFlowFallbackException("blank_response")
        measuredCollector.observe(
            timing = "after-response",
            conversation = conversation,
        )
        safeAppendTrace(appendTrace, "UPSTREAM official-flow extracted length=${response.length} partialCount=$partialCount namespace=${spec.namespace}")
        if (BuildConfig.DEBUG) {
            measuredCollector.observe(
                timing = "around-success-reached",
                conversation = conversation,
            )
        }
        successReached = true
        measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
        measuredTokenSnapshot = mergeTokenizerRecountSnapshot(
            base = measuredTokenSnapshot,
            conversation = conversation,
            promptText = prompt,
            fullResponseText = response,
            timing = LocalLiteRtTimingSnapshot(
                startedAtMs = startElapsedMs,
                firstNonEmptyChunkAtMs = firstPartialMs?.let { startElapsedMs + it },
                lastChunkAtMs = lastNonEmptyChunkAtMs,
                endedAtMs = SystemClock.elapsedRealtime(),
            ),
            appendTrace = appendTrace,
        )
        measuredCollector.emitAdoptedTrace()
        finalResult = LocalOfficialFlowStreamingResult(
            response = response,
            partialCount = partialCount,
            firstNonEmptyPartialElapsedRealtimeMs = firstPartialMs,
            measuredTokenSnapshot = measuredTokenSnapshot,
        )
    } finally {
        if (BuildConfig.DEBUG) {
            measuredCollector.observe(
                timing = "before-close",
                conversation = conversation,
            )
            measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
            measuredCollector.emitAdoptedTrace()
        }
        conversationCloseOutcome = tryCloseWithOutcome(
            label = "conversation",
            target = conversation,
            appendTrace = appendTrace,
            path = "fallback-official-flow",
        )
        engineCloseOutcome = tryCloseWithOutcome(
            label = "engine",
            target = engine,
            appendTrace = appendTrace,
            path = "fallback-official-flow",
        )
        val summary = RunCloseLifecycleSummary(
            path = "fallback-official-flow",
            successReturned = successReached,
            engineOutcome = engineCloseOutcome,
            conversationOutcome = conversationCloseOutcome,
            sessionOutcome = RunCloseTargetOutcome(
                label = "session",
                targetClassName = null,
                strategy = null,
                status = "none",
                errorClassName = null,
                message = null,
            ),
        )
        safeAppendTrace(
            appendTrace,
            "UPSTREAM close-summary path=${summary.path} successReturned=${summary.successReturned}",
        )
        summary.sessionOutcome?.let { emitCloseSummaryTrace(appendTrace, summary.path, it) }
        finalResult = finalResult?.copy(
            measuredTokenSnapshot = measuredTokenSnapshot,
            closeLifecycleSummary = summary,
        )
    }
    return finalResult
}

private fun runOfficialBlockingConversationSingleNamespace(
    spec: LocalOfficialNamespaceSpec,
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
    appendTrace: (String) -> Unit,
): LocalOfficialBlockingResult? {
    if (spec.namespace == "com.google.ai.edge.litertlm") {
        val result = runOfficialLiteRtLmBlocking(
            prompt = prompt,
            modelPath = modelPath,
            cacheDirPath = cacheDirPath,
            appendTrace = appendTrace,
        )
        return LocalOfficialBlockingResult(
            response = result.response,
            measuredTokenSnapshot = result.measuredTokenSnapshot,
            closeLifecycleSummary = ensureCloseLifecycleSummary(
                summary = result.closeLifecycleSummary,
                path = "fallback-official-blocking",
                successReturned = !result.response.isNullOrBlank(),
            ),
        )
    }
    safeAppendTrace(appendTrace, "UPSTREAM official-blocking start namespace=${spec.namespace}")
    val engineClass = runCatching { Class.forName(spec.engineClassName) }.getOrNull() ?: return null
    val conversationClass = runCatching { Class.forName("${spec.namespace}.Conversation") }.getOrNull() ?: return null
    val sendMethod = if (spec.namespace == "com.google.ai.edge.litertlm") {
        conversationClass.methods.firstOrNull { method ->
            method.name == "sendMessage" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                Map::class.java.isAssignableFrom(method.parameterTypes[1])
        }?.also {
            safeAppendTrace(appendTrace, "UPSTREAM official-blocking selectedMethod=sendMessage(String,Map) namespace=${spec.namespace}")
        }
    } else {
        conversationClass.methods.firstOrNull { method ->
            (method.name == "sendMessage" || method.name == "generateResponse") && method.parameterTypes.size == 1
        }
    } ?: return null
    val createConversationMethod =
        engineClass.methods.firstOrNull { it.name == "createConversation" }
            ?: throw OfficialFlowFallbackException("conversation_create_failed")
    val engine = if (spec.namespace == "com.google.ai.edge.litertlm") {
        createOfficialLiteRtLmEngineInstance(
            modelPath = modelPath,
            cacheDirPath = cacheDirPath,
            appendTrace = appendTrace,
        )
    } else {
        createOfficialEngineInstance(engineClass, spec.optionsCandidates, modelPath)
    }
        ?: throw OfficialFlowFallbackException("conversation_create_failed")
    var conversation: Any? = null
    var successReached = false
    var conversationCloseOutcome: RunCloseTargetOutcome? = null
    var engineCloseOutcome: RunCloseTargetOutcome? = null
    var finalResponse: String? = null
    var measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null
    var closeSummary: RunCloseLifecycleSummary? = null
    val measuredCollector = MeasuredTokenTimingCollector(
        path = "official-blocking",
        appendTrace = appendTrace,
    )
    try {
        conversation = if (spec.namespace == "com.google.ai.edge.litertlm") {
            createOfficialLiteRtLmConversation(
                engine = engine,
                engineClass = engineClass,
                appendTrace = appendTrace,
            )
        } else {
            createOfficialConversation(
                engine = engine,
                createConversationMethod = createConversationMethod,
                appendTrace = appendTrace,
            )
        }
            ?: throw OfficialFlowFallbackException("conversation_create_failed")
        val responseValue = if (spec.namespace == "com.google.ai.edge.litertlm") {
            safeAppendTrace(appendTrace, "UPSTREAM official-blocking invoke promptLength=${prompt.length} mapSize=0")
            runCatching {
                sendMethod.invoke(conversation, prompt, emptyMap<String, Any>())
            }.getOrElse { throwable ->
                throw OfficialFlowFallbackException("send_message_missing", throwable)
            }
        } else {
            val sendArgument = buildSendMessageArgument(
                parameterType = sendMethod.parameterTypes.first(),
                namespace = spec.namespace,
                prompt = prompt,
            ) ?: throw OfficialFlowFallbackException("send_message_missing")
            runCatching {
                sendMethod.invoke(conversation, sendArgument)
            }.getOrElse { throwable ->
                throw OfficialFlowFallbackException("send_message_missing", throwable)
            }
        }
        safeAppendTrace(appendTrace, "UPSTREAM official-blocking returnClass=${responseValue?.javaClass?.name ?: "null"}")
        val responseText = extractOfficialMessageTextWithTrace(
            path = "official-blocking",
            value = responseValue,
            appendTrace = appendTrace,
        )?.trim()?.takeIf { it.isNotBlank() } ?: throw OfficialFlowFallbackException("message_extract_failed")
        measuredCollector.observe(
            timing = "after-response",
            conversation = conversation,
        )
        safeAppendTrace(appendTrace, "UPSTREAM official-blocking success responseLength=${responseText.length}")
        if (BuildConfig.DEBUG) {
            measuredCollector.observe(
                timing = "around-success-reached",
                conversation = conversation,
            )
        }
        successReached = true
        measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
        measuredCollector.emitAdoptedTrace()
        finalResponse = responseText
    } finally {
        if (BuildConfig.DEBUG) {
            measuredCollector.observe(
                timing = "before-close",
                conversation = conversation,
            )
            measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
            measuredCollector.emitAdoptedTrace()
        }
        conversationCloseOutcome = tryCloseWithOutcome(
            label = "conversation",
            target = conversation,
            appendTrace = appendTrace,
            path = "fallback-official-blocking",
        )
        engineCloseOutcome = tryCloseWithOutcome(
            label = "engine",
            target = engine,
            appendTrace = appendTrace,
            path = "fallback-official-blocking",
        )
        val summary = RunCloseLifecycleSummary(
            path = "fallback-official-blocking",
            successReturned = successReached,
            engineOutcome = engineCloseOutcome,
            conversationOutcome = conversationCloseOutcome,
            sessionOutcome = RunCloseTargetOutcome(
                label = "session",
                targetClassName = null,
                strategy = null,
                status = "none",
                errorClassName = null,
                message = null,
            ),
        )
        safeAppendTrace(
            appendTrace,
            "UPSTREAM close-summary path=${summary.path} successReturned=${summary.successReturned}",
        )
        summary.sessionOutcome?.let { emitCloseSummaryTrace(appendTrace, summary.path, it) }
        closeSummary = summary
    }
    return LocalOfficialBlockingResult(
        response = finalResponse,
        measuredTokenSnapshot = measuredTokenSnapshot,
        closeLifecycleSummary = closeSummary,
    )
}

private suspend fun runOfficialLiteRtLmDirect(
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
    startElapsedMs: Long,
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit,
): LocalOfficialFlowStreamingResult? {
    safeAppendTrace(appendTrace, "UPSTREAM official-direct flowStart")
    safeAppendTrace(appendTrace, "UPSTREAM official-direct backend=text=GPU vision=GPU audio=CPU")
    safeAppendTrace(appendTrace, "UPSTREAM official-direct cacheDirPresent=${cacheDirPath.isNotBlank()}")

    return runCatching {
        val engineConfig = buildLiteRtEngineConfig(modelPath = modelPath, cacheDirPath = cacheDirPath)
        safeAppendTrace(appendTrace, "UPSTREAM official-direct engineConfig-created")
        var engine: Engine? = null
        var conversation: Any? = null
        var successReached = false
        var measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null
        var result: LocalOfficialFlowStreamingResult? = null
        val measuredCollector = MeasuredTokenTimingCollector(
            path = "official-direct-flow",
            appendTrace = appendTrace,
        )
        try {
            engine = Engine(engineConfig)
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engine-created")
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engineCreated")
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engine-initialize-start")
            engine.initialize()
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engine-initialize-success")
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engineInitialized")

            safeAppendTrace(appendTrace, "UPSTREAM official-direct conversation-create-start")
            conversation = engine.createConversation()
            safeAppendTrace(appendTrace, "UPSTREAM official-direct conversation-create-success")
            safeAppendTrace(appendTrace, "UPSTREAM official-direct conversationCreated")

            val builder = StringBuilder()
            var lastChunk: String? = null
            var partialCount = 0
            var firstPartialMs: Long? = null
            var lastNonEmptyChunkAtMs: Long? = null
            conversation.sendMessageAsync(prompt).collect { message ->
                val extractedText = message.contents.toString().trim()
                    .ifBlank { message.toString().trim() }
                if (extractedText.isNotBlank()) {
                    if (extractedText == lastChunk) return@collect
                    lastChunk = extractedText
                    builder.append(extractedText)
                    if (firstPartialMs == null) {
                        firstPartialMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(0L)
                    }
                    lastNonEmptyChunkAtMs = SystemClock.elapsedRealtime()
                    partialCount += 1
                    onPartial(builder.toString())
                }
            }

            val response = builder.toString().trim()
            safeAppendTrace(appendTrace, "UPSTREAM official-direct resultLength=${response.length}")
            if (response.isBlank()) throw OfficialFlowFallbackException("blank_response")
            measuredCollector.observe(
                timing = "after-response",
                conversation = conversation,
            )
            if (BuildConfig.DEBUG) {
                measuredCollector.observe(
                    timing = "around-success-reached",
                    conversation = conversation,
                )
            }
            successReached = true
            measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
            measuredTokenSnapshot = mergeTokenizerRecountSnapshot(
                base = measuredTokenSnapshot,
                conversation = conversation,
                promptText = prompt,
                fullResponseText = response,
                timing = LocalLiteRtTimingSnapshot(
                    startedAtMs = startElapsedMs,
                    firstNonEmptyChunkAtMs = firstPartialMs?.let { startElapsedMs + it },
                    lastChunkAtMs = lastNonEmptyChunkAtMs,
                    endedAtMs = SystemClock.elapsedRealtime(),
                ),
                appendTrace = appendTrace,
            )
            measuredCollector.emitAdoptedTrace()
            result = LocalOfficialFlowStreamingResult(
                response = response,
                partialCount = partialCount,
                firstNonEmptyPartialElapsedRealtimeMs = firstPartialMs,
                measuredTokenSnapshot = measuredTokenSnapshot,
            )
        } finally {
            if (BuildConfig.DEBUG) {
                measuredCollector.observe(
                    timing = "before-close",
                    conversation = conversation,
                )
                measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
                measuredCollector.emitAdoptedTrace()
            }
            val conversationCloseOutcome = tryCloseWithOutcome(
                label = "conversation",
                target = conversation,
                appendTrace = appendTrace,
                path = "fallback-official-flow",
            )
            val engineCloseOutcome = tryCloseWithOutcome(
                label = "engine",
                target = engine,
                appendTrace = appendTrace,
                path = "fallback-official-flow",
            )
            val closeSummary = RunCloseLifecycleSummary(
                path = "fallback-official-flow",
                successReturned = successReached,
                conversationOutcome = conversationCloseOutcome,
                engineOutcome = engineCloseOutcome,
                sessionOutcome = RunCloseTargetOutcome(
                    label = "session",
                    targetClassName = null,
                    strategy = null,
                    status = "none",
                    errorClassName = null,
                    message = null,
                ),
            )
            safeAppendTrace(
                appendTrace,
                "UPSTREAM close-summary path=${closeSummary.path} successReturned=${closeSummary.successReturned}",
            )
            closeSummary.sessionOutcome?.let { emitCloseSummaryTrace(appendTrace, closeSummary.path, it) }
            result = result?.copy(
                measuredTokenSnapshot = measuredTokenSnapshot,
                closeLifecycleSummary = closeSummary,
            )
        }
        result
    }.getOrElse { throwable ->
        safeAppendTrace(
            appendTrace,
            "UPSTREAM official-direct failed ${throwable.javaClass.simpleName}:${throwable.message}",
        )
        null
    }
}

private fun runOfficialLiteRtLmBlocking(
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
    appendTrace: (String) -> Unit,
): LocalOfficialDirectBlockingResult {
    safeAppendTrace(appendTrace, "UPSTREAM official-direct blockingStart")
    safeAppendTrace(appendTrace, "UPSTREAM official-direct backend=text=GPU vision=GPU audio=CPU")
    safeAppendTrace(appendTrace, "UPSTREAM official-direct cacheDirPresent=${cacheDirPath.isNotBlank()}")

    return runCatching {
        val engineConfig = buildLiteRtEngineConfig(modelPath = modelPath, cacheDirPath = cacheDirPath)
        safeAppendTrace(appendTrace, "UPSTREAM official-direct engineConfig-created")
        var engine: Engine? = null
        var conversation: Any? = null
        var successReached = false
        var responseText: String? = null
        var measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null
        var closeSummary: RunCloseLifecycleSummary? = null
        val measuredCollector = MeasuredTokenTimingCollector(
            path = "official-direct-blocking",
            appendTrace = appendTrace,
        )
        try {
            engine = Engine(engineConfig)
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engine-created")
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engineCreated")
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engine-initialize-start")
            engine.initialize()
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engine-initialize-success")
            safeAppendTrace(appendTrace, "UPSTREAM official-direct engineInitialized")

            safeAppendTrace(appendTrace, "UPSTREAM official-direct conversation-create-start")
            conversation = engine.createConversation()
            safeAppendTrace(appendTrace, "UPSTREAM official-direct conversation-create-success")
            safeAppendTrace(appendTrace, "UPSTREAM official-direct conversationCreated")

            val startedAtMs = SystemClock.elapsedRealtime()
            val message = conversation.sendMessage(prompt)
            val response = message.contents.toString().trim()
                .ifBlank { message.toString().trim() }

            safeAppendTrace(appendTrace, "UPSTREAM official-direct resultLength=${response.length}")
            responseText = response.takeIf { it.isNotBlank() }
            if (!responseText.isNullOrBlank()) {
                val completedAtMs = SystemClock.elapsedRealtime()
                measuredCollector.observe(
                    timing = "after-response",
                    conversation = conversation,
                )
                if (BuildConfig.DEBUG) {
                    measuredCollector.observe(
                        timing = "around-success-reached",
                        conversation = conversation,
                    )
                }
                measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
                measuredTokenSnapshot = mergeTokenizerRecountSnapshot(
                    base = measuredTokenSnapshot,
                    conversation = conversation,
                    promptText = prompt,
                    fullResponseText = responseText,
                    timing = LocalLiteRtTimingSnapshot(
                        startedAtMs = startedAtMs,
                        firstNonEmptyChunkAtMs = completedAtMs,
                        lastChunkAtMs = completedAtMs,
                        endedAtMs = completedAtMs,
                    ),
                    appendTrace = appendTrace,
                )
                measuredCollector.emitAdoptedTrace()
            }
            successReached = !responseText.isNullOrBlank()
        } finally {
            if (BuildConfig.DEBUG && !responseText.isNullOrBlank()) {
                measuredCollector.observe(
                    timing = "before-close",
                    conversation = conversation,
                )
                measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
                measuredCollector.emitAdoptedTrace()
            }
            val conversationCloseOutcome = tryCloseWithOutcome(
                label = "conversation",
                target = conversation,
                appendTrace = appendTrace,
                path = "fallback-official-blocking",
            )
            val engineCloseOutcome = tryCloseWithOutcome(
                label = "engine",
                target = engine,
                appendTrace = appendTrace,
                path = "fallback-official-blocking",
            )
            closeSummary = RunCloseLifecycleSummary(
                path = "fallback-official-blocking",
                successReturned = successReached,
                conversationOutcome = conversationCloseOutcome,
                engineOutcome = engineCloseOutcome,
                sessionOutcome = RunCloseTargetOutcome(
                    label = "session",
                    targetClassName = null,
                    strategy = null,
                    status = "none",
                    errorClassName = null,
                    message = null,
                ),
            )
            safeAppendTrace(
                appendTrace,
                "UPSTREAM close-summary path=${closeSummary.path} successReturned=${closeSummary.successReturned}",
            )
            closeSummary.sessionOutcome?.let { emitCloseSummaryTrace(appendTrace, closeSummary.path, it) }
        }
        LocalOfficialDirectBlockingResult(
            response = responseText,
            measuredTokenSnapshot = measuredTokenSnapshot,
            closeLifecycleSummary = closeSummary,
        )
    }.getOrElse {
        safeAppendTrace(appendTrace, "UPSTREAM official-direct failed ${it.javaClass.simpleName}:${it.message}")
        LocalOfficialDirectBlockingResult(response = null)
    }
}

private fun createOfficialEngineInstance(
    engineClass: Class<*>,
    optionClassNames: List<String>,
    modelPath: String,
): Any? {
    val factoryMethod = engineClass.methods.firstOrNull { method ->
        method.name == "createFromOptions" && method.parameterTypes.size == 1
    } ?: return null
    val options = optionClassNames.firstNotNullOfOrNull { optionClassName ->
        val optionClass = runCatching { Class.forName(optionClassName) }.getOrNull() ?: return@firstNotNullOfOrNull null
        buildOptionsObject(optionClass = optionClass, modelPath = modelPath)
    } ?: return null
    return runCatching { factoryMethod.invoke(null, options) }.getOrNull()
}

private fun createOfficialLiteRtLmEngineInstance(
    modelPath: String,
    cacheDirPath: String? = null,
    appendTrace: (String) -> Unit,
): Any? {
    safeAppendTrace(appendTrace, "UPSTREAM official-helper start helper=createOfficialLiteRtLmEngineInstance")
    safeAppendTrace(appendTrace, "UPSTREAM official-helper backend=text=GPU vision=GPU audio=CPU")
    safeAppendTrace(appendTrace, "UPSTREAM official-helper cacheDirPresent=${!cacheDirPath.isNullOrBlank()}")
    return runCatching {
        val engineConfig = buildLiteRtEngineConfig(modelPath = modelPath, cacheDirPath = cacheDirPath)
        safeAppendTrace(appendTrace, "UPSTREAM official-helper engine-config-created non-null")
        Engine(engineConfig).also {
            safeAppendTrace(appendTrace, "UPSTREAM official-helper engine-new-instance-result non-null")
        }
    }.getOrElse { throwable ->
        safeAppendTrace(appendTrace, "UPSTREAM official-helper engine-create fail class=${throwable.javaClass.simpleName} message=${throwable.message}")
        safeAppendTrace(appendTrace, "UPSTREAM official-engine create failed ${throwable.javaClass.simpleName}:${throwable.message}")
        null
    }
}

private fun buildLiteRtEngineConfig(
    modelPath: String,
    cacheDirPath: String?,
): EngineConfig = EngineConfig(
    modelPath = modelPath,
    backend = Backend.GPU(),
    visionBackend = Backend.GPU(),
    audioBackend = Backend.CPU(),
    maxNumTokens = null,
    cacheDir = cacheDirPath,
)

private fun buildOptionsObject(optionClass: Class<*>, modelPath: String): Any? {
    val builderFactory = optionClass.methods.firstOrNull { method ->
        method.name == "builder" && method.parameterTypes.isEmpty()
    } ?: return null
    val builder = runCatching { builderFactory.invoke(null) }.getOrNull() ?: return null
    val setterNames = listOf("setModelPath", "setModelFilePath", "setModelAssetPath")
    val setter = builder.javaClass.methods.firstOrNull { method ->
        setterNames.contains(method.name) &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes.first() == String::class.java
    } ?: return null
    runCatching {
        setter.invoke(builder, modelPath)
    }.getOrNull() ?: return null
    val buildMethod = builder.javaClass.methods.firstOrNull { method ->
        method.name == "build" && method.parameterTypes.isEmpty()
    } ?: return null
    return runCatching { buildMethod.invoke(builder) }.getOrNull()
}

private fun createOfficialLiteRtLmConversation(
    engine: Any,
    engineClass: Class<*>,
    appendTrace: (String) -> Unit,
): Any? {
    val configClassName = "com.google.ai.edge.litertlm.ConversationConfig"
    safeAppendTrace(appendTrace, "UPSTREAM official-conversation configClass=$configClassName")
    return runCatching {
        val configClass = Class.forName(configClassName)
        val config = configClass.getDeclaredConstructor().newInstance()
        safeAppendTrace(appendTrace, "UPSTREAM official-conversation configCreated class=${config.javaClass.name}")
        val createConversationMethod = engineClass.methods.first { method ->
            method.name == "createConversation" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == configClassName
        }
        val conversation = createConversationMethod.invoke(engine, config)
        safeAppendTrace(appendTrace, "UPSTREAM official-conversation created class=${conversation?.javaClass?.name ?: "null"}")
        conversation
    }.getOrElse { throwable ->
        safeAppendTrace(appendTrace, "UPSTREAM official-conversation create failed ${throwable.javaClass.simpleName}:${throwable.message}")
        null
    }
}

private fun createOfficialConversation(
    engine: Any,
    createConversationMethod: Method,
    appendTrace: (String) -> Unit,
): Any? {
    safeAppendTrace(
        appendTrace,
        "UPSTREAM official-conversation createMethod=${createConversationMethod.name}${createConversationMethod.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }}",
    )
    val conversation = when (createConversationMethod.parameterTypes.size) {
        0 -> runCatching { createConversationMethod.invoke(engine) }.getOrNull()
        else -> {
            val arg = createConversationMethod.parameterTypes.firstNotNullOfOrNull { parameterType ->
                buildEmptyByBuilder(parameterType)
            } ?: return null
            runCatching { createConversationMethod.invoke(engine, arg) }.getOrNull()
        }
    }
    safeAppendTrace(
        appendTrace,
        "UPSTREAM official-conversation created class=${conversation?.javaClass?.name ?: "null"}",
    )
    return conversation
}

private fun buildEmptyByBuilder(type: Class<*>): Any? {
    val builderFactory = type.methods.firstOrNull { it.name == "builder" && it.parameterTypes.isEmpty() } ?: return null
    val builder = runCatching { builderFactory.invoke(null) }.getOrNull() ?: return null
    val buildMethod = builder.javaClass.methods.firstOrNull { it.name == "build" && it.parameterTypes.isEmpty() } ?: return null
    return runCatching { buildMethod.invoke(builder) }.getOrNull()
}

private fun buildSendMessageArgument(
    parameterType: Class<*>,
    namespace: String,
    prompt: String,
): Any? {
    if (parameterType == String::class.java) return prompt
    if (parameterType.name == "$namespace.Message") {
        val fromText = parameterType.methods.firstOrNull { method ->
            (method.name == "fromText" || method.name == "create") &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes.first() == String::class.java
        }
        if (fromText != null) {
            runCatching { fromText.invoke(null, prompt) }.getOrNull()?.let { return it }
        }
        parameterType.constructors.firstOrNull { ctor ->
            ctor.parameterTypes.size == 1 && ctor.parameterTypes.first() == String::class.java
        }?.let { ctor ->
            runCatching { ctor.newInstance(prompt) }.getOrNull()?.let { return it }
        }
    }
    return null
}

private fun extractOfficialMessageText(value: Any?): String? {
    when (value) {
        null -> return null
        is String -> return value
        is CharSequence -> return value.toString()
        is Iterable<*> -> {
            value.forEach { nested ->
                extractOfficialMessageText(nested)?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }
    }
    if (value.javaClass.isArray) {
        (value as? Array<*>)?.forEach { nested ->
            extractOfficialMessageText(nested)?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    val getterNames = OFFICIAL_TEXT_CANDIDATES.filterNot { it == "toString" }
    getterNames.forEach { getterName ->
        val method = value.javaClass.methods.firstOrNull { it.name == getterName && it.parameterTypes.isEmpty() } ?: return@forEach
        val extracted = runCatching { extractOfficialMessageText(method.invoke(value)) }.getOrNull()
        if (!extracted.isNullOrBlank()) return extracted
    }
    val loweredMethods = value.javaClass.methods.filter { it.parameterTypes.isEmpty() }.sortedBy { it.name }
    loweredMethods.forEach { method ->
        val lowerName = method.name.lowercase(Locale.ROOT)
        if (!lowerName.contains("text") && !lowerName.contains("content") && !lowerName.contains("part")) return@forEach
        val extracted = runCatching { extractOfficialMessageText(method.invoke(value)) }.getOrNull()
        if (!extracted.isNullOrBlank()) return extracted
    }
    val toStringValue = value.toString()
    return toStringValue.takeIf { isMeaningfulToStringFallback(value, it) }
}

private fun extractOfficialMessageTextWithTrace(
    path: String,
    value: Any?,
    appendTrace: (String) -> Unit,
): String? {
    safeAppendTrace(appendTrace, "UPSTREAM $path returnClass=${value?.javaClass?.name ?: "null"}")
    safeAppendTrace(appendTrace, "UPSTREAM extract-text path=$path candidates=$OFFICIAL_TEXT_CANDIDATES")
    OFFICIAL_TEXT_CANDIDATES.forEach { candidate ->
        val candidateValue = when (candidate) {
            "toString" -> value?.toString()
            else -> value?.javaClass?.methods?.firstOrNull {
                it.name == candidate && it.parameterTypes.isEmpty()
            }?.let { method ->
                runCatching { method.invoke(value) }.getOrNull()
            }
        }
        val extracted = runCatching { extractOfficialMessageText(candidateValue) }.getOrNull()?.trim()
        if (!extracted.isNullOrBlank()) {
            if (candidate == "toString" && value != null && !isMeaningfulToStringFallback(value, extracted)) {
                safeAppendTrace(appendTrace, "UPSTREAM extract-text candidate=$candidate result=blank")
                return@forEach
            }
            safeAppendTrace(appendTrace, "UPSTREAM extract-text candidate=$candidate result=nonBlank length=${extracted.length}")
            safeAppendTrace(appendTrace, "UPSTREAM $path extracted length=${extracted.length}")
            return extracted
        }
        val resultLabel = if (candidateValue == null) "null" else "blank"
        safeAppendTrace(appendTrace, "UPSTREAM extract-text candidate=$candidate result=$resultLabel")
    }
    safeAppendTrace(appendTrace, "UPSTREAM $path extracted length=0")
    return null
}

private fun isMeaningfulToStringFallback(source: Any, value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return false
    if (trimmed == source.javaClass.name || trimmed == source.javaClass.simpleName) return false
    val identityPattern = "^${Regex.escape(source.javaClass.name)}@[0-9a-fA-F]+$".toRegex()
    return !identityPattern.matches(trimmed)
}

private fun safeAppendTrace(
    appendTrace: (String) -> Unit,
    message: String,
) {
    runCatching { appendTrace(message) }
}

private fun closeQuietly(
    target: Any?,
    appendTrace: ((String) -> Unit)? = null,
) {
    tryCloseWithOutcome(
        label = "target",
        target = target,
        appendTrace = appendTrace,
        path = null,
    )
}

internal fun tryCloseWithOutcome(
    label: String,
    target: Any?,
    appendTrace: ((String) -> Unit)? = null,
    path: String? = null,
): RunCloseTargetOutcome {
    val targetClass = target?.javaClass?.name
    if (target == null) {
        val outcome = RunCloseTargetOutcome(
            label = label,
            targetClassName = targetClass,
            strategy = null,
            status = "none",
            errorClassName = null,
            message = null,
        )
        appendTrace?.let { trace ->
            path?.let { emitCloseSummaryTrace(trace, it, outcome) }
        }
        return outcome
    }
    if (target is AutoCloseable) {
        return runCatching { target.close() }
            .fold(
                onSuccess = {
                    runCatching {
                        appendTrace?.invoke("UPSTREAM closeQuietly targetClass=$targetClass strategy=AutoCloseable.close success")
                    }
                    val outcome = RunCloseTargetOutcome(
                        label = label,
                        targetClassName = targetClass,
                        status = "success",
                        strategy = "AutoCloseable.close",
                        errorClassName = null,
                        message = null,
                    )
                    appendTrace?.let { trace ->
                        path?.let { emitCloseSummaryTrace(trace, it, outcome) }
                    }
                    outcome
                },
                onFailure = { throwable ->
                    runCatching {
                        appendTrace?.invoke(
                            "UPSTREAM closeQuietly targetClass=$targetClass strategy=AutoCloseable.close failed ${throwable.javaClass.simpleName}",
                        )
                    }
                    val outcome = RunCloseTargetOutcome(
                        label = label,
                        targetClassName = targetClass,
                        status = "failed",
                        strategy = "AutoCloseable.close",
                        errorClassName = throwable.javaClass.name,
                        message = throwable.message?.take(120),
                    )
                    appendTrace?.let { trace ->
                        path?.let { emitCloseSummaryTrace(trace, it, outcome) }
                    }
                    outcome
                },
            )
    }
    val releaseMethodNames = listOf("close", "release", "destroy", "shutdown", "cancel")
    val selectedMethod = releaseMethodNames.firstNotNullOfOrNull { methodName ->
        target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }?.let { methodName to it }
    }
    if (selectedMethod == null) {
        runCatching {
            appendTrace?.invoke("UPSTREAM closeQuietly targetClass=$targetClass strategy=none")
        }
        val outcome = RunCloseTargetOutcome(
            label = label,
            targetClassName = targetClass,
            strategy = null,
            status = "skipped",
            errorClassName = null,
            message = null,
        )
        appendTrace?.let { trace ->
            path?.let { emitCloseSummaryTrace(trace, it, outcome) }
        }
        return outcome
    }
    val (strategyName, method) = selectedMethod
    return runCatching { method.invoke(target) }
        .fold(
            onSuccess = {
                runCatching {
                    appendTrace?.invoke("UPSTREAM closeQuietly targetClass=$targetClass strategy=$strategyName success")
                }
                val outcome = RunCloseTargetOutcome(
                    label = label,
                    targetClassName = targetClass,
                    status = "success",
                    strategy = strategyName,
                    errorClassName = null,
                    message = null,
                )
                appendTrace?.let { trace ->
                    path?.let { emitCloseSummaryTrace(trace, it, outcome) }
                }
                outcome
            },
            onFailure = { throwable ->
                runCatching {
                    appendTrace?.invoke(
                        "UPSTREAM closeQuietly targetClass=$targetClass strategy=$strategyName failed ${throwable.javaClass.simpleName}",
                    )
                }
                val outcome = RunCloseTargetOutcome(
                    label = label,
                    targetClassName = targetClass,
                    status = "failed",
                    strategy = strategyName,
                    errorClassName = throwable.javaClass.name,
                    message = throwable.message?.take(120),
                )
                appendTrace?.let { trace ->
                    path?.let { emitCloseSummaryTrace(trace, it, outcome) }
                }
                outcome
            },
        )
}

private fun emitCloseSummaryTrace(
    appendTrace: (String) -> Unit,
    path: String,
    outcome: RunCloseTargetOutcome,
) {
    safeAppendTrace(
        appendTrace,
        "UPSTREAM close-summary path=$path label=${outcome.label} status=${outcome.status} strategy=${outcome.strategy ?: "none"} class=${outcome.targetClassName ?: "null"} error=${outcome.errorClassName ?: "none"} message=${outcome.message ?: "none"}",
    )
}

internal data class LocalRealPartialHookSnapshot(
    val attempted: Boolean = false,
    val attached: Boolean = false,
    val callbackCount: Int = 0,
)

internal class LocalRealPartialHookHandle(
    private val snapshotProvider: () -> LocalRealPartialHookSnapshot,
) {
    fun snapshot(): LocalRealPartialHookSnapshot = snapshotProvider()
}

internal fun tryAttachSingleListenerPartialHook(
    inferenceInstance: Any,
    onPartial: (String) -> Unit,
): LocalRealPartialHookHandle {
    val callbackCount = AtomicInteger(0)
    val lastPartial = AtomicReference<String?>(null)
    var attempted = false
    var attached = false

    runCatching {
        val listenerMethodNames = listOf(
            "setResultListener",
            "setPartialResultListener",
            "setTokenListener",
            "setStreamingListener",
        )
        val listenerMethod = listenerMethodNames.firstNotNullOfOrNull { targetName ->
            inferenceInstance.javaClass.methods.firstOrNull { method ->
                method.name.equals(targetName, ignoreCase = true) && method.parameterTypes.size == 1
            }
        } ?: inferenceInstance.javaClass.methods.firstOrNull { method ->
            val lowerName = method.name.lowercase(Locale.ROOT)
            (lowerName.contains("listener") || lowerName.contains("callback")) && method.parameterTypes.size == 1
        }
        if (listenerMethod == null) {
            return@runCatching
        }
        attempted = true
        val listenerType = listenerMethod.parameterTypes.firstOrNull() ?: return@runCatching
        if (!listenerType.isInterface) {
            return@runCatching
        }
        val listenerProxy = Proxy.newProxyInstance(
            listenerType.classLoader,
            arrayOf(listenerType),
        ) { _, method, args ->
            if (method.declaringClass == Any::class.java) {
                return@newProxyInstance when (method.name) {
                    "toString" -> "LocalRealPartialListenerProxy"
                    "hashCode" -> 0
                    "equals" -> false
                    else -> null
                }
            }
            val extracted = extractPartialTextFromListenerArgs(args)
            if (!extracted.isNullOrBlank()) {
                val normalized = extracted.trim()
                val previous = lastPartial.get()
                if (previous != normalized) {
                    lastPartial.set(normalized)
                    val currentCount = callbackCount.incrementAndGet()
                    if (currentCount >= 2) {
                        onPartial(normalized)
                    }
                }
            }
            null
        }
        listenerMethod.isAccessible = true
        listenerMethod.invoke(inferenceInstance, listenerProxy)
        attached = true
    }

    return LocalRealPartialHookHandle(
        snapshotProvider = {
            LocalRealPartialHookSnapshot(
                attempted = attempted,
                attached = attached,
                callbackCount = callbackCount.get(),
            )
        }
    )
}

private fun extractPartialTextFromListenerArgs(args: Array<out Any?>?): String? {
    if (args.isNullOrEmpty()) return null
    args.forEach { value ->
        when (value) {
            is String -> if (value.isNotBlank()) return value
            is CharSequence -> if (value.isNotBlank()) return value.toString()
        }
    }
    args.forEach { value ->
        val text = runCatching {
            value?.javaClass?.methods?.firstNotNullOfOrNull { method ->
                if (method.parameterTypes.isNotEmpty()) return@firstNotNullOfOrNull null
                if (method.returnType != String::class.java &&
                    method.returnType != CharSequence::class.java
                ) {
                    return@firstNotNullOfOrNull null
                }
                val lowerName = method.name.lowercase(Locale.ROOT)
                if (!lowerName.contains("text") && !lowerName.contains("result") && !lowerName.contains("token")) {
                    return@firstNotNullOfOrNull null
                }
                (method.invoke(value) as? CharSequence)?.toString()
            }
        }.getOrNull()
        if (!text.isNullOrBlank()) {
            return text
        }
    }
    return null
}
