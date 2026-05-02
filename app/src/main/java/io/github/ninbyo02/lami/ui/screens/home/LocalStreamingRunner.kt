package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TOKENIZER_COUNT_UNAVAILABLE_NOTE =
    "このビルドの LiteRT API では tokenizer-based token count を取得できませんでした。"
private const val MEDIAPIPE_TOKEN_COUNT_MODE = "mediapipe_tokenizer_recount"
private const val LITERT_TOKEN_COUNT_MODE = "tokenizer_recount"
private const val LOCAL_STREAMING_WHITESPACE_LOG_TAG = "LocalWsTrace"
private val STREAMING_NO_JOIN_PREVIOUS_CHARS = setOf(
    '(', '[', '{', '"', '\'', '`', '/', '\\', '.', ',', ':', ';', '!', '?',
)
private val STREAMING_NO_JOIN_NEXT_CHARS = setOf(
    ')', ']', '}', '"', '\'', '`', '.', ',', ':', ';', '!', '?', '/', '\\',
)

internal interface LocalStreamingRunner<T> {
    suspend fun run(
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        resolvedModelPath: String? = null,
        cacheDirPath: String? = null,
        mediaPipeProbeContext: Context? = null,
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
        mediaPipeProbeContext: Context?,
        onPartial: (String) -> Unit,
    ) -> T,
) : LocalStreamingRunner<T> {
    override suspend fun run(
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        resolvedModelPath: String?,
        cacheDirPath: String?,
        mediaPipeProbeContext: Context?,
        onPartial: (String) -> Unit,
    ): T? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            runInference(
                prompt,
                localBaseModelFilePath,
                localBaseModelDisplayName,
                resolvedModelPath,
                cacheDirPath,
                mediaPipeProbeContext,
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
    val runnerWhitespaceTraceText: String? = null,
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
    mediaPipeProbeModelPath: String? = null,
    mediaPipeProbeContext: Context? = null,
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
    val runnerWhitespaceTraceEntries = mutableListOf<Pair<String, String?>>()

    fun appendRunnerWhitespaceStage(
        stage: String,
        text: String?,
    ) {
        if (!BuildConfig.DEBUG) return
        runnerWhitespaceTraceEntries += stage to text
    }

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
                val appendContext = StreamingAppendContext()
                var lastPartial: String? = null
                flow.collect { message ->
                    if (!currentCoroutineContext().isActive) return@collect
                    val messageContentsRaw = extractMessageContentsForTrace(message)
                    val extractedText = extractOfficialMessageTextWithTrace(
                        path = "held-engine-flow",
                        value = message,
                        appendTrace = appendTrace,
                    )
                    val extracted = extractedText.orEmpty()
                    appendRunnerWhitespaceStage("message.contents", messageContentsRaw)
                    appendRunnerWhitespaceStage("extract.raw", extractedText)
                    appendRunnerWhitespaceStage("extract.trimmed", extractedText?.trim())
                    logLocalStreamingWhitespace(
                        stage = "LocalStreamingRunner#held.flow.extract",
                        raw = extractedText,
                        normalized = extractedText?.trim(),
                    )
                    if (!isViableStreamingChunk(extracted) || extracted == lastPartial) return@collect
                    lastPartial = extracted
                    heldFlowPartialCount += 1
                    if (heldFlowFirstPartialElapsedRealtimeMs == null) {
                        heldFlowFirstPartialElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    }
                    heldFlowLastChunkElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    appendRunnerWhitespaceStage("append.boundary.before", builder.toString().takeLast(64))
                    appendRunnerWhitespaceStage("append.boundary.extracted", extracted)
                    val joinApplied = appendStreamingChunk(
                        builder = builder,
                        extractedRaw = extracted,
                        context = appendContext,
                        appendTrace = appendTrace,
                    )
                    appendRunnerWhitespaceStage("lane", appendContext.lane.label)
                    appendRunnerWhitespaceStage("append.boundary.join", joinApplied)
                    appendRunnerWhitespaceStage("append.boundary.after", builder.toString().takeLast(64))
                    onPartial(builder.toString())
                }
                val built = builder.toString()
                val trimmedBuilt = built.trim()
                appendRunnerWhitespaceStage("builder.raw", built)
                appendRunnerWhitespaceStage("builder.trimmed", trimmedBuilt)
                appendRunnerWhitespaceStage("responseText.raw", built)
                appendRunnerWhitespaceStage("responseText.trimmed", trimmedBuilt)
                logLocalStreamingWhitespace(
                    stage = "LocalStreamingRunner#held.flow.builder",
                    raw = built,
                    normalized = trimmedBuilt,
                )
                trimmedBuilt.takeIf { it.isNotBlank() }
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
                    tokenizerSessionSource = heldEngine.engineInstance,
                    mediaPipeProbeModelPath = mediaPipeProbeModelPath,
                    mediaPipeProbeContext = mediaPipeProbeContext,
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
                appendRunnerWhitespaceStage("message.contents", extractMessageContentsForTrace(value))
                val extractedText = extractOfficialMessageTextWithTrace(
                    path = "held-engine-blocking",
                    value = value,
                    appendTrace = appendTrace,
                )
                val responseRaw = extractedText
                val responseTrimmed = extractedText?.trim()
                appendRunnerWhitespaceStage("extract.raw", responseRaw)
                appendRunnerWhitespaceStage("extract.trimmed", responseTrimmed)
                appendRunnerWhitespaceStage("responseText.raw", responseRaw)
                appendRunnerWhitespaceStage("responseText.trimmed", responseTrimmed)
                responseTrimmed?.takeIf { it.isNotBlank() }
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
                    tokenizerSessionSource = heldEngine.engineInstance,
                    mediaPipeProbeModelPath = mediaPipeProbeModelPath,
                    mediaPipeProbeContext = mediaPipeProbeContext,
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
        runnerWhitespaceTraceText = buildRunnerWhitespaceTraceBlock(runnerWhitespaceTraceEntries),
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
    tokenizerSessionSource: Any? = null,
    mediaPipeProbeModelPath: String? = null,
    mediaPipeProbeContext: Context? = null,
    promptText: String,
    fullResponseText: String?,
    timing: LocalLiteRtTimingSnapshot,
    appendTrace: (String) -> Unit,
): LocalInferenceMeasuredTokenSnapshot? {
    val sanitizedPrompt = promptText
    val sanitizedResponse = fullResponseText.orEmpty()
    val tokenizerSnapshot = readTokenizerRecountSnapshotFromConversation(
        conversation = conversation,
        tokenizerSessionSource = tokenizerSessionSource,
        mediaPipeProbeModelPath = mediaPipeProbeModelPath,
        mediaPipeProbeContext = mediaPipeProbeContext,
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
            tokenizerSourceTraceSummary = tokenizerSnapshot.tokenizerSourceTraceSummary ?: base.tokenizerSourceTraceSummary,
            mediaPipeTokenizerStatus = tokenizerSnapshot.mediaPipeTokenizerStatus ?: base.mediaPipeTokenizerStatus,
            mediaPipeTokenizerSummary = tokenizerSnapshot.mediaPipeTokenizerSummary ?: base.mediaPipeTokenizerSummary,
            mediaPipeInputTokens = tokenizerSnapshot.mediaPipeInputTokens ?: base.mediaPipeInputTokens,
            mediaPipeOutputTokens = tokenizerSnapshot.mediaPipeOutputTokens ?: base.mediaPipeOutputTokens,
            mediaPipeTotalTokens = tokenizerSnapshot.mediaPipeTotalTokens ?: base.mediaPipeTotalTokens,
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
    tokenizerSessionSource: Any?,
    mediaPipeProbeModelPath: String?,
    mediaPipeProbeContext: Context?,
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
            tokenizerSessionSource = tokenizerSessionSource,
            promptText = promptText,
            fullResponseText = fullResponseText,
            appendTrace = appendTrace,
        )
        val mediaPipeProbeOutcome = tryReadMediaPipeTokenizerProbeViaReflection(
            tokenizerSessionSource = tokenizerSessionSource,
            preferredModelPath = mediaPipeProbeModelPath,
            mediaPipeProbeContext = mediaPipeProbeContext,
            promptText = promptText,
            fullResponseText = fullResponseText,
        )
        val tokenizerRecount = tokenizerRecountOutcome.result

        val inputTokenCount = mediaPipeProbeOutcome.promptTokens ?: tokenizerRecount?.promptTokens
        val outputTokenCount = mediaPipeProbeOutcome.responseTokens ?: tokenizerRecount?.responseTokens
        val totalTokenCount = mediaPipeProbeOutcome.totalTokens ?: tokenizerRecount?.totalTokens
        val tokenCountMode = if (
            mediaPipeProbeOutcome.succeeded &&
            inputTokenCount != null &&
            outputTokenCount != null
        ) {
            MEDIAPIPE_TOKEN_COUNT_MODE
        } else if (
            tokenizerRecount != null &&
            inputTokenCount != null &&
            outputTokenCount != null
        ) {
            LITERT_TOKEN_COUNT_MODE
        } else {
            null
        }
        val notes = if (tokenizerRecount == null && !mediaPipeProbeOutcome.succeeded) {
            TOKENIZER_COUNT_UNAVAILABLE_NOTE
        } else {
            null
        }
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
            tokenizerSourceTraceSummary = tokenizerRecountOutcome.sourceTraceSummary,
            mediaPipeTokenizerStatus = mediaPipeProbeOutcome.status,
            mediaPipeTokenizerSummary = mediaPipeProbeOutcome.summary,
            mediaPipeInputTokens = mediaPipeProbeOutcome.promptTokens,
            mediaPipeOutputTokens = mediaPipeProbeOutcome.responseTokens,
            mediaPipeTotalTokens = mediaPipeProbeOutcome.totalTokens,
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
    val sourceTraceSummary: String? = null,
)

private data class MediaPipeTokenizerProbeOutcome(
    val attempted: Boolean,
    val succeeded: Boolean,
    val promptTokens: Int? = null,
    val responseTokens: Int? = null,
    val totalTokens: Int? = null,
    val status: String,
    val summary: String,
)

private data class MediaPipeTokenizerModelPathResolution(
    val rawCandidate: String?,
    val adoptedPath: String?,
    val source: String,
    val exists: Boolean,
    val isFile: Boolean,
    val readable: Boolean,
    val status: String,
)

private data class ExistingTokenizerSessionResolution(
    val sourceKind: String,
    val path: String,
    val sourceObject: Any?,
    val sessionInstance: Any,
    val sessionClassName: String,
    val sizeInTokensMethod: Method?,
)

private data class ConversationTokenizerResolution(
    val path: String,
    val sourceObject: Any,
    val className: String,
    val sizeInTokensMethod: Method?,
)

private data class EngineCreateSessionAttempt(
    val session: Any? = null,
    val attempted: Boolean = false,
    val status: String = "engine-createSession-not-attempted",
    val createdSessionPath: String? = null,
    val sessionConfigProbe: SessionConfigProbeSummary? = null,
    val samplerConfigProbe: SamplerConfigProbeSummary? = null,
    val failureSummary: EngineCreateSessionFailureSummary? = null,
)

private data class EngineCreateSessionFailureSummary(
    val path: String,
    val exceptionClass: String,
    val exceptionMessage: String?,
    val causeClass: String?,
    val causeMessage: String?,
)

private data class EngineCreateSessionInvokeResult(
    val session: Any? = null,
    val failureSummary: EngineCreateSessionFailureSummary? = null,
)

private data class SessionConfigProbeSummary(
    val classStatus: String,
    val factorySignatures: List<String> = emptyList(),
    val constructorSignatures: List<String> = emptyList(),
    val triedPaths: List<String> = emptyList(),
    val selectedPath: String? = null,
)

private data class SamplerConfigProbeSummary(
    val classStatus: String,
    val factorySignatures: List<String> = emptyList(),
    val constructorSignatures: List<String> = emptyList(),
    val triedPaths: List<String> = emptyList(),
    val defaultAttempt: String = "not-attempted",
    val selectedPath: String? = null,
)

private data class TokenizerSourceTraceSummary(
    val kind: String,
    val className: String,
    val methodsSummary: String,
    val fieldsSummary: String,
    val createSessionSignatures: List<String> = emptyList(),
    val engineCreateSessionStatus: String? = null,
    val engineCreateSessionFailureSummary: EngineCreateSessionFailureSummary? = null,
    val sessionConfigProbe: SessionConfigProbeSummary? = null,
    val samplerConfigProbe: SamplerConfigProbeSummary? = null,
    val existingSessionPath: String? = null,
    val existingSessionClass: String? = null,
    val existingSessionSizeInTokensStatus: String = "not-found",
    val createdSessionPath: String? = null,
    val createdSessionClass: String? = null,
    val createdSessionSizeInTokensStatus: String = "not-found",
    val conversationTokenizerPath: String? = null,
    val conversationTokenizerClass: String? = null,
) {
    fun toMeasuredTokenSummary(): String {
        return buildString {
            engineCreateSessionStatus?.takeIf { it.isNotBlank() }?.let {
                appendLine("engine-createSession status: $it")
            }
            engineCreateSessionFailureSummary?.let { failure ->
                appendLine("engine-createSession failure-path: ${failure.path}")
                appendLine("engine-createSession exception: ${failure.exceptionClass}")
                appendLine("engine-createSession exception-message: ${failure.exceptionMessage ?: "none"}")
                appendLine("engine-createSession cause: ${failure.causeClass ?: "none"}")
                appendLine("engine-createSession cause-message: ${failure.causeMessage ?: "none"}")
            }
            appendLine("tokenizer-source kind: $kind")
            appendLine("tokenizer-source class: $className")
            appendLine("tokenizer-source methods: $methodsSummary")
            appendLine("tokenizer-source fields: $fieldsSummary")
            appendLine("createSession signatures:")
            createSessionSignatures.ifEmpty { listOf("none") }.forEach { signature ->
                appendLine("- $signature")
            }
            sessionConfigProbe?.let { probe ->
                appendLine("SessionConfig class: ${probe.classStatus}")
                appendLine("SessionConfig factories:")
                probe.factorySignatures.ifEmpty { listOf("none") }.forEach { signature ->
                    appendLine("- $signature")
                }
                appendLine("SessionConfig constructors:")
                probe.constructorSignatures.ifEmpty { listOf("none") }.forEach { signature ->
                    appendLine("- $signature")
                }
                appendLine("SessionConfig tried-paths:")
                probe.triedPaths.ifEmpty { listOf("none") }.forEach { path ->
                    appendLine("- $path")
                }
                appendLine("SessionConfig selected-path: ${probe.selectedPath ?: "none"}")
            }
            samplerConfigProbe?.let { probe ->
                appendLine("SamplerConfig class: ${probe.classStatus}")
                appendLine("SamplerConfig factories:")
                probe.factorySignatures.ifEmpty { listOf("none") }.forEach { signature ->
                    appendLine("- $signature")
                }
                appendLine("SamplerConfig constructors:")
                probe.constructorSignatures.ifEmpty { listOf("none") }.forEach { signature ->
                    appendLine("- $signature")
                }
                appendLine("SamplerConfig tried-paths:")
                probe.triedPaths.ifEmpty { listOf("none") }.forEach { path ->
                    appendLine("- $path")
                }
                appendLine("SamplerConfig default-attempt: ${probe.defaultAttempt}")
                appendLine("SamplerConfig selected-path: ${probe.selectedPath ?: "none"}")
            }
            appendLine("existing-session path: ${existingSessionPath ?: "none"}")
            appendLine("existing-session class: ${existingSessionClass ?: "none"}")
            appendLine("existing-session sizeInTokens: $existingSessionSizeInTokensStatus")
            appendLine("created-session path: ${createdSessionPath ?: "none"}")
            appendLine("created-session class: ${createdSessionClass ?: "none"}")
            appendLine("created-session sizeInTokens: $createdSessionSizeInTokensStatus")
            appendLine("conversation-tokenizer path: ${conversationTokenizerPath ?: "none"}")
            appendLine("conversation-tokenizer class: ${conversationTokenizerClass ?: "none"}")
        }.trimEnd()
    }
}

private data class CreatedTokenizerSessionResolution(
    val path: String,
    val sessionClassName: String,
    val sizeInTokensMethod: Method?,
)

private fun tryReadTokenizerRecountViaReflection(
    conversation: Conversation,
    tokenizerSessionSource: Any?,
    promptText: String,
    fullResponseText: String,
    appendTrace: (String) -> Unit,
): TokenizerRecountOutcome {
    val conversationResolution = tryResolveTokenizerFromConversation(
        conversation = conversation,
        appendTrace = appendTrace,
    )
    if (conversationResolution != null) {
        val sizeMethod = conversationResolution.sizeInTokensMethod
            ?: return TokenizerRecountOutcome(
                status = "conversation-tokenizer-size-method-not-found",
            )
        val promptTokens = invokeSizeInTokens(
            sessionInstance = conversationResolution.sourceObject,
            sizeMethod = sizeMethod,
            input = promptText,
        )
        val responseTokens = invokeSizeInTokens(
            sessionInstance = conversationResolution.sourceObject,
            sizeMethod = sizeMethod,
            input = fullResponseText,
        )
        if (promptTokens != null && responseTokens != null) {
            val sourceTraceSummary = if (BuildConfig.DEBUG) {
                emitTokenizerSessionSourceTrace(
                    appendTrace = appendTrace,
                    tokenizerSessionSource = tokenizerSessionSource,
                    conversation = conversation,
                    existingSessionResolution = null,
                    conversationTokenizerResolution = conversationResolution,
                ).toMeasuredTokenSummary()
            } else {
                null
            }
            return TokenizerRecountOutcome(
                result = TokenizerRecountResult(
                    promptTokens = promptTokens,
                    responseTokens = responseTokens,
                ),
                status = "success(conversation-tokenizer)",
                sourceTraceSummary = sourceTraceSummary,
            )
        }
    }
    val engineSessionAttempt = tryCreateTokenizerSessionFromEngineViaReflection(
        tokenizerSessionSource = tokenizerSessionSource,
        appendTrace = appendTrace,
    )
    val createdSessionResolution = inspectCreatedSessionForTokenizer(
        createdSession = engineSessionAttempt.session,
        createdSessionPath = engineSessionAttempt.createdSessionPath,
    )
    if (engineSessionAttempt.session != null) {
        safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount session-created-but-not-used path=engine-createSession")
        tryCloseTokenizerSession(engineSessionAttempt.session, appendTrace)
    }
    val existingSessionResolution = tryResolveExistingSessionForTokenizer(
        conversation = conversation,
        tokenizerSessionSource = tokenizerSessionSource,
        appendTrace = appendTrace,
    )
    val sourceTraceSummary = if (BuildConfig.DEBUG) {
        emitTokenizerSessionSourceTrace(
            appendTrace = appendTrace,
            tokenizerSessionSource = tokenizerSessionSource,
            conversation = conversation,
            engineCreateSessionStatus = engineSessionAttempt.status,
            sessionConfigProbe = engineSessionAttempt.sessionConfigProbe,
            samplerConfigProbe = engineSessionAttempt.samplerConfigProbe,
            engineCreateSessionFailureSummary = engineSessionAttempt.failureSummary,
            existingSessionResolution = existingSessionResolution,
            createdSessionResolution = createdSessionResolution,
        )
    } else {
        null
    }
    val activeSession = existingSessionResolution?.sessionInstance
        ?: return TokenizerRecountOutcome(
            status = if (engineSessionAttempt.attempted) {
                engineSessionAttempt.status
            } else {
                "fallback-existing-session-not-found"
            },
            sourceTraceSummary = sourceTraceSummary?.toMeasuredTokenSummary(),
        ).also {
            safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped reason=existing-session-not-found")
        }
    return try {
        val sizeMethod = existingSessionResolution.sizeInTokensMethod
            ?: return TokenizerRecountOutcome(
                status = "existing-session-size-method-not-found",
                sourceTraceSummary = sourceTraceSummary?.toMeasuredTokenSummary(),
            ).also {
                safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped reason=size-method-not-found")
            }
        safeAppendTrace(
            appendTrace,
            "UPSTREAM tokenizer-recount existing-session-found path=${existingSessionResolution.path} class=${existingSessionResolution.sessionClassName}",
        )
        val promptTokens = invokeSizeInTokens(activeSession, sizeMethod, promptText)
            ?: return TokenizerRecountOutcome(
                status = "existing-session-prompt-token-failed",
                sourceTraceSummary = sourceTraceSummary?.toMeasuredTokenSummary(),
            ).also {
                safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped reason=prompt-token-failed")
            }
        val responseTokens = invokeSizeInTokens(activeSession, sizeMethod, fullResponseText)
            ?: return TokenizerRecountOutcome(
                status = "existing-session-response-token-failed",
                sourceTraceSummary = sourceTraceSummary?.toMeasuredTokenSummary(),
            ).also {
                safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount skipped reason=response-token-failed")
            }
        TokenizerRecountOutcome(
            result = TokenizerRecountResult(promptTokens = promptTokens, responseTokens = responseTokens),
            status = "success",
            sourceTraceSummary = sourceTraceSummary?.toMeasuredTokenSummary(),
        )
    } catch (throwable: Throwable) {
        val status = "reflection-failed ${throwable.javaClass.simpleName}:${throwable.message}"
        safeAppendTrace(
            appendTrace,
            "UPSTREAM tokenizer-recount $status",
        )
        TokenizerRecountOutcome(
            status = status,
            sourceTraceSummary = sourceTraceSummary?.toMeasuredTokenSummary(),
        )
    }
}

private fun tryReadMediaPipeTokenizerProbeViaReflection(
    tokenizerSessionSource: Any?,
    preferredModelPath: String?,
    mediaPipeProbeContext: Context?,
    promptText: String,
    fullResponseText: String,
): MediaPipeTokenizerProbeOutcome {
    val llmInferenceClassName = "com.google.mediapipe.tasks.genai.llminference.LlmInference"
    val llmSessionClassName = "com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession"
    val llmSessionOptionsClassName =
        "com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession\$LlmInferenceSessionOptions"

    val llmInferenceClass = runCatching { Class.forName(llmInferenceClassName) }.getOrNull()
    val llmSessionClass = runCatching { Class.forName(llmSessionClassName) }.getOrNull()
    val llmSessionOptionsClass = runCatching { Class.forName(llmSessionOptionsClassName) }.getOrNull()
    val classAvailability = listOf(
        "LlmInference=${if (llmInferenceClass != null) "available" else "missing"}",
        "LlmInferenceSession=${if (llmSessionClass != null) "available" else "missing"}",
        "SessionOptions=${if (llmSessionOptionsClass != null) "available" else "missing"}",
    ).joinToString(", ")
    if (llmInferenceClass == null || llmSessionClass == null || llmSessionOptionsClass == null) {
        return MediaPipeTokenizerProbeOutcome(
            attempted = false,
            succeeded = false,
            status = "unavailable(class-not-found)",
            summary = buildString {
                appendLine("MediaPipe tokenizer: unavailable")
                appendLine("MediaPipe class availability: $classAvailability")
            }.trimEnd(),
        )
    }

    val modelPathResolution = resolveMediaPipeTokenizerModelPath(
        preferredModelPath = preferredModelPath,
        tokenizerSessionSource = tokenizerSessionSource,
    )
    val explicitContext = mediaPipeProbeContext
    val fallbackContext = resolveMediaPipeContext(tokenizerSessionSource)
    val mediaPipeContext = explicitContext?.applicationContext ?: explicitContext ?: fallbackContext?.applicationContext ?: fallbackContext
    val mediaPipeContextSource = when {
        explicitContext != null -> "explicit-application-context"
        fallbackContext != null -> "tokenizer-source"
        else -> "none"
    }

    val baseSummary = buildString {
        appendLine("MediaPipe tokenizer: failed")
        appendLine("MediaPipe class availability: $classAvailability")
        appendLine("MediaPipe model path source: ${modelPathResolution.source}")
        appendLine("MediaPipe model path: ${modelPathResolution.adoptedPath ?: "null"}")
        appendLine("MediaPipe model path exists: ${modelPathResolution.exists}")
        appendLine("MediaPipe model path isFile: ${modelPathResolution.isFile}")
        appendLine("MediaPipe model path readable: ${modelPathResolution.readable}")
    }

    if (modelPathResolution.status != "model-path-resolved") {
        return MediaPipeTokenizerProbeOutcome(
            attempted = true,
            succeeded = false,
            status = "failed(${modelPathResolution.status})",
            summary = buildString {
                append(baseSummary)
                appendLine("MediaPipe model path status: ${modelPathResolution.status}")
                appendLine("MediaPipe context source: $mediaPipeContextSource")
                appendLine("MediaPipe context class: ${mediaPipeContext?.javaClass?.name ?: "null"}")
                appendLine("MediaPipe context isNull: ${mediaPipeContext == null}")
                appendLine(
                    "MediaPipe context hasCacheDir: ${
                        runCatching { mediaPipeContext?.cacheDir != null }.getOrElse { false }
                    }",
                )
                appendLine("MediaPipe session create: skipped")
                appendLine("MediaPipe sizeInTokens: not-found")
                append("MediaPipe failure: ${modelPathResolution.status}")
            }.trimEnd(),
        )
    }
    val modelPath = modelPathResolution.adoptedPath ?: return MediaPipeTokenizerProbeOutcome(
        attempted = true,
        succeeded = false,
        status = "failed(model-path-missing)",
        summary = buildString {
            append(baseSummary)
            appendLine("MediaPipe model path status: model-path-missing")
            appendLine("MediaPipe context source: $mediaPipeContextSource")
            appendLine("MediaPipe context class: ${mediaPipeContext?.javaClass?.name ?: "null"}")
            appendLine("MediaPipe context isNull: ${mediaPipeContext == null}")
            appendLine(
                "MediaPipe context hasCacheDir: ${
                    runCatching { mediaPipeContext?.cacheDir != null }.getOrElse { false }
                }",
            )
            appendLine("MediaPipe session create: skipped")
            appendLine("MediaPipe sizeInTokens: not-found")
            append("MediaPipe failure: model-path-missing")
        }.trimEnd(),
    )

    return runCatching {
        val inferenceOutcome = createMediaPipeLlmInferenceInstance(
            llmInferenceClass = llmInferenceClass,
            modelPath = modelPath,
            context = mediaPipeContext,
            contextSource = mediaPipeContextSource,
        )
        if (inferenceOutcome.instance == null) {
            return@runCatching MediaPipeTokenizerProbeOutcome(
                attempted = true,
                succeeded = false,
                status = "failed(createFromOptions)",
                summary = buildString {
                    append(baseSummary)
                    appendLine("MediaPipe model path status: model-path-passed-to-mediapipe")
                    appendLine("MediaPipe context source: $mediaPipeContextSource")
                    appendLine("MediaPipe context class: ${mediaPipeContext?.javaClass?.name ?: "null"}")
                    appendLine("MediaPipe context isNull: ${mediaPipeContext == null}")
                    appendLine(
                        "MediaPipe context hasCacheDir: ${
                            runCatching { mediaPipeContext?.cacheDir != null }.getOrElse { false }
                        }",
                    )
                    inferenceOutcome.debugSummaryLines.forEach { appendLine(it) }
                    appendLine("MediaPipe session create: failed")
                    appendLine("MediaPipe sizeInTokens: not-found")
                    append("MediaPipe failure: ${inferenceOutcome.failureSummary ?: "createFromOptions-failed"}")
                }.trimEnd(),
            )
        }
        var session: Any? = null
        try {
            val sessionCreationOutcome = createMediaPipeLlmSession(
                llmSessionClass = llmSessionClass,
                llmSessionOptionsClass = llmSessionOptionsClass,
                llmInferenceInstance = inferenceOutcome.instance,
            )
            session = sessionCreationOutcome.session
            val sizeMethod = sessionCreationOutcome.sizeInTokensMethod
            if (session == null || sizeMethod == null) {
                return@runCatching MediaPipeTokenizerProbeOutcome(
                    attempted = true,
                    succeeded = false,
                    status = if (session == null) "failed(session-create)" else "failed(sizeInTokens-not-found)",
                    summary = buildString {
                        append(baseSummary)
                        appendLine("MediaPipe model path status: model-path-passed-to-mediapipe")
                        appendLine("MediaPipe context source: $mediaPipeContextSource")
                        appendLine("MediaPipe context class: ${mediaPipeContext?.javaClass?.name ?: "null"}")
                        appendLine("MediaPipe context isNull: ${mediaPipeContext == null}")
                        appendLine(
                            "MediaPipe context hasCacheDir: ${
                                runCatching { mediaPipeContext?.cacheDir != null }.getOrElse { false }
                            }",
                        )
                        sessionCreationOutcome.debugSummaryLines.forEach { appendLine(it) }
                        appendLine("MediaPipe session create: ${if (session != null) "success" else "failed"}")
                        appendLine("MediaPipe sizeInTokens: ${if (sizeMethod != null) "found" else "not-found"}")
                        append(
                            "MediaPipe failure: ${
                                sessionCreationOutcome.failureSummary
                                    ?: if (session == null) "session-create-failed" else "sizeInTokens-not-found"
                            }",
                        )
                    }.trimEnd(),
                )
            }
            val promptTokens = invokeSizeInTokens(session, sizeMethod, promptText)
            val responseTokens = invokeSizeInTokens(session, sizeMethod, fullResponseText)
            val totalTokens = if (promptTokens != null && responseTokens != null) promptTokens + responseTokens else null
            if (promptTokens != null && responseTokens != null && totalTokens != null) {
                MediaPipeTokenizerProbeOutcome(
                    attempted = true,
                    succeeded = true,
                    promptTokens = promptTokens,
                    responseTokens = responseTokens,
                    totalTokens = totalTokens,
                    status = "success",
                    summary = buildString {
                        appendLine("MediaPipe tokenizer: success")
                        appendLine("MediaPipe class availability: $classAvailability")
                        appendLine("MediaPipe model path source: ${modelPathResolution.source}")
                        appendLine("MediaPipe model path: $modelPath")
                        appendLine("MediaPipe model path exists: ${modelPathResolution.exists}")
                        appendLine("MediaPipe model path isFile: ${modelPathResolution.isFile}")
                        appendLine("MediaPipe model path readable: ${modelPathResolution.readable}")
                        appendLine("MediaPipe model path status: model-path-passed-to-mediapipe")
                        appendLine("MediaPipe context source: $mediaPipeContextSource")
                        appendLine("MediaPipe context class: ${mediaPipeContext?.javaClass?.name ?: "null"}")
                        appendLine("MediaPipe context isNull: ${mediaPipeContext == null}")
                        appendLine(
                            "MediaPipe context hasCacheDir: ${
                                runCatching { mediaPipeContext?.cacheDir != null }.getOrElse { false }
                            }",
                        )
                        appendLine("MediaPipe session create: success")
                        appendLine("MediaPipe sizeInTokens: found")
                        appendLine("MediaPipe prompt tokens: $promptTokens")
                        appendLine("MediaPipe response tokens: $responseTokens")
                        append("MediaPipe total tokens: $totalTokens")
                    }.trimEnd(),
                )
            } else {
                MediaPipeTokenizerProbeOutcome(
                    attempted = true,
                    succeeded = false,
                    status = "failed(sizeInTokens-invoke)",
                    summary = buildString {
                        append(baseSummary)
                        appendLine("MediaPipe model path status: model-path-passed-to-mediapipe")
                        appendLine("MediaPipe context source: $mediaPipeContextSource")
                        appendLine("MediaPipe context class: ${mediaPipeContext?.javaClass?.name ?: "null"}")
                        appendLine("MediaPipe context isNull: ${mediaPipeContext == null}")
                        appendLine(
                            "MediaPipe context hasCacheDir: ${
                                runCatching { mediaPipeContext?.cacheDir != null }.getOrElse { false }
                            }",
                        )
                        appendLine("MediaPipe session create: success")
                        appendLine("MediaPipe sizeInTokens: found")
                        append("MediaPipe failure: invoke-sizeInTokens-failed")
                    }.trimEnd(),
                )
            }
        } finally {
            runCatching { tryCloseTokenizerSession(session, appendTrace = {}) }
            runCatching { tryCloseTokenizerSession(inferenceOutcome.instance, appendTrace = {}) }
        }
    }.getOrElse { throwable ->
        MediaPipeTokenizerProbeOutcome(
            attempted = true,
            succeeded = false,
            status = "failed(${throwable.javaClass.simpleName})",
            summary = buildString {
                append(baseSummary)
                appendLine("MediaPipe model path status: model-path-passed-to-mediapipe")
                appendLine("MediaPipe context source: $mediaPipeContextSource")
                appendLine("MediaPipe context class: ${mediaPipeContext?.javaClass?.name ?: "null"}")
                appendLine("MediaPipe context isNull: ${mediaPipeContext == null}")
                appendLine(
                    "MediaPipe context hasCacheDir: ${
                        runCatching { mediaPipeContext?.cacheDir != null }.getOrElse { false }
                    }",
                )
                buildMediaPipeThrowableSummaryLines(throwable).forEach { appendLine(it) }
                appendLine("MediaPipe session create: failed")
                appendLine("MediaPipe sizeInTokens: not-found")
                append("MediaPipe failure: ${throwable.javaClass.simpleName}:${throwable.message}")
            }.trimEnd(),
        )
    }
}

private data class MediaPipeInferenceCreateOutcome(
    val instance: Any? = null,
    val failureSummary: String? = null,
    val debugSummaryLines: List<String> = emptyList(),
)

private data class MediaPipeSessionCreateOutcome(
    val session: Any? = null,
    val sizeInTokensMethod: Method? = null,
    val failureSummary: String? = null,
    val debugSummaryLines: List<String> = emptyList(),
)

private data class MediaPipeSessionOptionsCreateOutcome(
    val options: Any? = null,
    val buildStatus: String,
    val debugSummaryLines: List<String> = emptyList(),
)

private fun createMediaPipeLlmInferenceInstance(
    llmInferenceClass: Class<*>,
    modelPath: String,
    context: android.content.Context?,
    contextSource: String,
): MediaPipeInferenceCreateOutcome {
    val debugLines = mutableListOf<String>()
    val candidateMethods = llmInferenceClass.methods.filter { method ->
        method.name == "createFromOptions" && java.lang.reflect.Modifier.isStatic(method.modifiers)
    }
    if (candidateMethods.isEmpty()) {
        return MediaPipeInferenceCreateOutcome(failureSummary = "createFromOptions-not-found")
    }
    candidateMethods.forEach { method ->
        debugLines += "MediaPipe createFromOptions signature: ${method.toGenericString()}"
        val params = method.parameterTypes
        val optionsClass = params.lastOrNull() ?: return@forEach
        debugLines += "MediaPipe options class: ${optionsClass.name}"
        val options = buildOptionsObject(optionClass = optionsClass, modelPath = modelPath)
        if (options == null) {
            debugLines += "MediaPipe options build: failed"
            return@forEach
        }
        debugLines += "MediaPipe options build: success"
        val args = when {
            params.size == 1 -> arrayOf(options)
            params.size == 2 && isAndroidContextClass(params[0]) -> {
                val safeContext = context?.applicationContext ?: context
                debugLines += "MediaPipe context prepared: ${safeContext != null}"
                debugLines += "MediaPipe context source: $contextSource"
                debugLines += "MediaPipe context type: applicationContext"
                if (safeContext == null) {
                    debugLines += "MediaPipe failure: context-null"
                    return MediaPipeInferenceCreateOutcome(
                        failureSummary = "context-null",
                        debugSummaryLines = debugLines.toList(),
                    )
                }
                arrayOf<Any?>(safeContext, options)
            }
            else -> return@forEach
        }
        val instance = runCatching { method.invoke(null, *args) }
            .onFailure { throwable -> debugLines += buildMediaPipeThrowableSummaryLines(throwable) }
            .getOrNull()
        if (instance != null) {
            return MediaPipeInferenceCreateOutcome(
                instance = instance,
                debugSummaryLines = debugLines.toList(),
            )
        }
    }
    return MediaPipeInferenceCreateOutcome(
        failureSummary = "createFromOptions-invoke-failed",
        debugSummaryLines = debugLines.toList(),
    )
}

private fun createMediaPipeLlmSession(
    llmSessionClass: Class<*>,
    llmSessionOptionsClass: Class<*>,
    llmInferenceInstance: Any,
): MediaPipeSessionCreateOutcome {
    val debugLines = mutableListOf<String>()
    debugLines += "MediaPipe sessionOptions class: ${llmSessionOptionsClass.name}"
    val sessionOptionsOutcome = createMediaPipeLlmSessionOptions(llmSessionOptionsClass)
    debugLines += "MediaPipe sessionOptions build: ${sessionOptionsOutcome.buildStatus}"
    debugLines += sessionOptionsOutcome.debugSummaryLines
    val options = sessionOptionsOutcome.options
        ?: return MediaPipeSessionCreateOutcome(
            failureSummary = "session-options-build-failed",
            debugSummaryLines = debugLines.toList(),
        )
    val createMethods = llmSessionClass.methods.filter { method ->
        method.name == "createFromOptions" && java.lang.reflect.Modifier.isStatic(method.modifiers)
    }
    for (method in createMethods) {
        debugLines += "MediaPipe session createFromOptions signature: ${method.toGenericString()}"
        val params = method.parameterTypes
        if (params.size != 2) continue
        val supportsInference = params[0].isAssignableFrom(llmInferenceInstance.javaClass)
        if (!supportsInference) continue
        val supportsOptions = params[1].isAssignableFrom(options.javaClass)
        if (!supportsOptions) continue
        val session = runCatching { method.invoke(null, llmInferenceInstance, options) }
            .onFailure { throwable -> debugLines += buildMediaPipeThrowableSummaryLines(throwable) }
            .getOrNull()
        if (session != null) {
            return MediaPipeSessionCreateOutcome(
                session = session,
                sizeInTokensMethod = findSizeInTokensMethod(session),
                debugSummaryLines = debugLines.toList(),
            )
        }
    }
    return MediaPipeSessionCreateOutcome(
        failureSummary = "session-createFromOptions-not-found-or-failed",
        debugSummaryLines = debugLines.toList(),
    )
}

private fun createMediaPipeLlmSessionOptions(
    llmSessionOptionsClass: Class<*>,
): MediaPipeSessionOptionsCreateOutcome {
    val builderFactory = llmSessionOptionsClass.methods.firstOrNull { method ->
        method.name == "builder" &&
            method.parameterTypes.isEmpty() &&
            java.lang.reflect.Modifier.isStatic(method.modifiers)
    }
    val builderResult = if (builderFactory != null) {
        runCatching { builderFactory.invoke(null) }
    } else {
        Result.failure(NoSuchMethodException("SessionOptions.builder() not found"))
    }
    val builder = builderResult
        .onFailure { throwable ->
            return MediaPipeSessionOptionsCreateOutcome(
                buildStatus = "failed",
                debugSummaryLines = buildMediaPipeThrowableSummaryLines(throwable),
            )
        }
        .getOrNull()
        ?: return MediaPipeSessionOptionsCreateOutcome(buildStatus = "failed")
    val buildMethod = builder.javaClass.methods.firstOrNull { method ->
        method.name == "build" && method.parameterTypes.isEmpty()
    } ?: return MediaPipeSessionOptionsCreateOutcome(
        buildStatus = "failed",
        debugSummaryLines = listOf("MediaPipe sessionOptions build method: not-found"),
    )
    val optionsResult = runCatching { buildMethod.invoke(builder) }
    return optionsResult
        .map { options ->
            MediaPipeSessionOptionsCreateOutcome(
                options = options,
                buildStatus = if (options != null) "success" else "failed",
            )
        }
        .getOrElse { throwable ->
            MediaPipeSessionOptionsCreateOutcome(
                buildStatus = "failed",
                debugSummaryLines = buildMediaPipeThrowableSummaryLines(throwable),
            )
        }
}

private fun buildMediaPipeThrowableSummaryLines(throwable: Throwable): List<String> {
    val rootCause = throwable.cause?.cause
    val targetThrowable = (throwable as? java.lang.reflect.InvocationTargetException)?.targetException
    return buildList {
        add("MediaPipe exception: ${throwable.javaClass.name}")
        add("MediaPipe exception-message: ${throwable.message ?: "none"}")
        add("MediaPipe cause: ${throwable.cause?.javaClass?.name ?: "none"}")
        add("MediaPipe cause-message: ${throwable.cause?.message ?: "none"}")
        if (targetThrowable != null) {
            add("MediaPipe target-exception: ${targetThrowable.javaClass.name}")
            add("MediaPipe target-exception-message: ${targetThrowable.message ?: "none"}")
        }
        if (rootCause != null) {
            add("MediaPipe root-cause: ${rootCause.javaClass.name}")
            add("MediaPipe root-cause-message: ${rootCause.message ?: "none"}")
        }
    }
}

private fun resolveMediaPipeTokenizerModelPath(
    preferredModelPath: String?,
    tokenizerSessionSource: Any?,
): MediaPipeTokenizerModelPathResolution {
    fun buildResolution(source: String, candidate: String?): MediaPipeTokenizerModelPathResolution {
        val normalizedCandidate = candidate?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedCandidate == null) {
            return MediaPipeTokenizerModelPathResolution(
                rawCandidate = candidate,
                adoptedPath = null,
                source = source,
                exists = false,
                isFile = false,
                readable = false,
                status = "model-path-missing",
            )
        }
        val modelFile = runCatching { File(normalizedCandidate) }.getOrNull()
        val exists = runCatching { modelFile?.exists() == true }.getOrDefault(false)
        val isFile = runCatching { modelFile?.isFile == true }.getOrDefault(false)
        val readable = runCatching { modelFile?.canRead() == true }.getOrDefault(false)
        val status = when {
            !exists -> "model-path-not-found"
            !isFile -> "model-path-not-file"
            !readable -> "model-path-not-readable"
            else -> "model-path-resolved"
        }
        return MediaPipeTokenizerModelPathResolution(
            rawCandidate = candidate,
            adoptedPath = normalizedCandidate,
            source = source,
            exists = exists,
            isFile = isFile,
            readable = readable,
            status = status,
        )
    }

    val candidates = buildList<Pair<String, String?>>() {
        add("trace-resolved-model" to preferredModelPath)
        val source = tokenizerSessionSource
        if (source != null) {
            val heldEngine = readNamedMemberValue(source, "heldEngine")
            val modelPathFromHeldEngine = (heldEngine?.let { readNamedMemberValue(it, "modelPath") }) as? String
            add("held-engine" to modelPathFromHeldEngine)
            val engineKey = readNamedMemberValue(source, "engineKey")
            val modelPathFromEngineKey = (engineKey?.let { readNamedMemberValue(it, "modelPath") }) as? String
            add("engine-key" to modelPathFromEngineKey)
            val modelPathFromSource = readNamedMemberValue(source, "modelPath") as? String
            add("source-model-path" to modelPathFromSource)
            val directModelPath = readNamedMemberValue(source, "getModelPath") as? String
            add("direct-model-path-getter" to directModelPath)
        }
    }

    var lastResolution = MediaPipeTokenizerModelPathResolution(
        rawCandidate = null,
        adoptedPath = null,
        source = "none",
        exists = false,
        isFile = false,
        readable = false,
        status = "model-path-missing",
    )
    for ((source, candidate) in candidates) {
        val resolution = buildResolution(source = source, candidate = candidate)
        lastResolution = resolution
        if (resolution.status == "model-path-resolved") {
            return resolution
        }
    }
    return lastResolution
}

private fun isAndroidContextClass(clazz: Class<*>): Boolean {
    return clazz.name == "android.content.Context"
}

private fun resolveMediaPipeContext(tokenizerSessionSource: Any?): android.content.Context? {
    if (tokenizerSessionSource == null) return null
    val candidates = buildList {
        add(tokenizerSessionSource)
        add(readNamedMemberValue(tokenizerSessionSource, "context"))
        add(readNamedMemberValue(tokenizerSessionSource, "appContext"))
        val heldEngine = readNamedMemberValue(tokenizerSessionSource, "heldEngine")
        add(heldEngine)
        if (heldEngine != null) {
            add(readNamedMemberValue(heldEngine, "context"))
            add(readNamedMemberValue(heldEngine, "appContext"))
            val engineInstance = readNamedMemberValue(heldEngine, "engineInstance")
            add(engineInstance)
            if (engineInstance != null) {
                add(readNamedMemberValue(engineInstance, "context"))
                add(readNamedMemberValue(engineInstance, "appContext"))
            }
        }
    }
    return candidates.firstNotNullOfOrNull { candidate ->
        (candidate as? android.content.Context)?.applicationContext ?: (candidate as? android.content.Context)
    }
}

private fun tryCreateTokenizerSessionFromEngineViaReflection(
    tokenizerSessionSource: Any?,
    appendTrace: (String) -> Unit,
): EngineCreateSessionAttempt {
    val engine = tokenizerSessionSource ?: return EngineCreateSessionAttempt()
    if (!isLiteRtEngineInstance(engine)) return EngineCreateSessionAttempt()
    safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount try=engine-createSession")
    val samplerConfigProbe = probeSamplerConfigCreationPaths()
    samplerConfigProbe.factorySignatures.forEach { signature ->
        safeAppendTrace(appendTrace, "UPSTREAM tokenizer-source SamplerConfig factory signature: $signature")
    }
    samplerConfigProbe.constructorSignatures.forEach { signature ->
        safeAppendTrace(appendTrace, "UPSTREAM tokenizer-source SamplerConfig constructor signature: $signature")
    }
    val sessionConfigProbe = probeSessionConfigCreationPaths(samplerConfigProbe)
    sessionConfigProbe.factorySignatures.forEach { signature ->
        safeAppendTrace(appendTrace, "UPSTREAM tokenizer-source SessionConfig factory signature: $signature")
    }
    sessionConfigProbe.constructorSignatures.forEach { signature ->
        safeAppendTrace(appendTrace, "UPSTREAM tokenizer-source SessionConfig constructor signature: $signature")
    }
    var methodFound = false
    var lastFailureSummary: EngineCreateSessionFailureSummary? = null
    tryInvokeEngineCreateSessionNoArgs(engine)?.let {
        methodFound = true
        return EngineCreateSessionAttempt(
            session = it,
            attempted = true,
            status = "engine-createSession-success",
            createdSessionPath = "engine-createSession",
            sessionConfigProbe = sessionConfigProbe,
            samplerConfigProbe = samplerConfigProbe,
        )
    }
    if (hasEngineCreateSessionNoArgsMethod(engine)) methodFound = true
    val sessionConfigInvokeResult = tryInvokeEngineCreateSessionWithSessionConfig(
        engine = engine,
        sessionConfigProbe = sessionConfigProbe,
        samplerConfigProbe = samplerConfigProbe,
        appendTrace = appendTrace,
    )
    sessionConfigInvokeResult.failureSummary?.let {
        if (lastFailureSummary == null) {
            lastFailureSummary = it
        }
    }
    sessionConfigInvokeResult.session?.let {
        methodFound = true
        return EngineCreateSessionAttempt(
            session = it,
            attempted = true,
            status = "engine-createSession-success",
            createdSessionPath = "engine-createSession",
            sessionConfigProbe = sessionConfigProbe,
            samplerConfigProbe = samplerConfigProbe,
        )
    }
    if (hasEngineCreateSessionSessionConfigMethod(engine)) methodFound = true
    tryInvokeEngineCreateSessionNullableArgs(engine)?.let {
        methodFound = true
        return EngineCreateSessionAttempt(
            session = it,
            attempted = true,
            status = "engine-createSession-success",
            createdSessionPath = "engine-createSession",
            sessionConfigProbe = sessionConfigProbe,
            samplerConfigProbe = samplerConfigProbe,
        )
    }
    if (hasEngineCreateSessionNullableArgsMethod(engine)) methodFound = true
    val defaultBridgeInvokeResult = tryInvokeEngineCreateSessionDefaultBridge(
        engine = engine,
        sessionConfigProbe = sessionConfigProbe,
        appendTrace = appendTrace,
    )
    defaultBridgeInvokeResult.failureSummary?.let {
        if (lastFailureSummary == null) {
            lastFailureSummary = it
        }
    }
    defaultBridgeInvokeResult.session?.let {
        methodFound = true
        return EngineCreateSessionAttempt(
            session = it,
            attempted = true,
            status = "engine-createSession-success",
            createdSessionPath = "engine-createSession",
            sessionConfigProbe = sessionConfigProbe,
            samplerConfigProbe = samplerConfigProbe,
        )
    }
    if (hasEngineCreateSessionDefaultBridgeMethod(engine)) methodFound = true
    val status = if (methodFound) "engine-createSession-failed" else "engine-createSession-method-not-found"
    safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount $status")
    return EngineCreateSessionAttempt(
        attempted = true,
        status = status,
        sessionConfigProbe = sessionConfigProbe,
        samplerConfigProbe = samplerConfigProbe,
        failureSummary = lastFailureSummary,
    )
}

private fun probeSamplerConfigCreationPaths(): SamplerConfigProbeSummary {
    val samplerConfigClass = runCatching {
        Class.forName("com.google.ai.edge.litertlm.SamplerConfig")
    }.getOrNull()
    if (samplerConfigClass == null) {
        return SamplerConfigProbeSummary(classStatus = "not-found")
    }
    val factoryMethods = samplerConfigClass.methods.filter { method ->
        method.parameterTypes.isEmpty() &&
            (method.name == "CreateDefault" || method.name == "createDefault" || method.name == "builder")
    }
    val companionOrObject = samplerConfigClass.declaredFields.firstOrNull { field ->
        field.name == "Companion" || field.name == "INSTANCE"
    }?.let { field ->
        runCatching {
            field.isAccessible = true
            field.get(null)
        }.getOrNull()
    }
    val companionMethods = companionOrObject
        ?.javaClass
        ?.methods
        ?.filter { method ->
            method.parameterTypes.isEmpty() &&
                (method.name == "CreateDefault" || method.name == "createDefault" || method.name == "builder")
        }
        .orEmpty()
    val constructors = samplerConfigClass.declaredConstructors.sortedBy { it.parameterTypes.size }
    val triedPaths = mutableListOf<String>()
    val candidates = mutableListOf<Pair<String, Any>>()

    fun addCandidate(path: String, value: Any?) {
        if (value == null) return
        candidates += path to value
        val buildMethod = value.javaClass.methods.firstOrNull { method ->
            method.name == "build" && method.parameterTypes.isEmpty()
        }
        if (buildMethod != null) {
            triedPaths += "$path.build()"
            runCatching { buildMethod.invoke(value) }.getOrNull()?.let { built ->
                candidates += "$path.build()" to built
            }
        }
    }

    factoryMethods.forEach { method ->
        val path = "SamplerConfig.${method.name}()"
        triedPaths += path
        addCandidate(path, runCatching { method.invoke(null) }.getOrNull())
    }
    companionMethods.forEach { method ->
        val receiver = companionOrObject ?: return@forEach
        val path = "SamplerConfig.${receiver.javaClass.simpleName}.${method.name}()"
        triedPaths += path
        addCandidate(path, runCatching { method.invoke(receiver) }.getOrNull())
    }
    val intDoubleDoubleIntConstructor = constructors.firstOrNull { constructor ->
        val parameterTypes = constructor.parameterTypes
        parameterTypes.size == 4 &&
            parameterTypes[0] == Int::class.javaPrimitiveType &&
            parameterTypes[1] == Double::class.javaPrimitiveType &&
            parameterTypes[2] == Double::class.javaPrimitiveType &&
            parameterTypes[3] == Int::class.javaPrimitiveType
    }
    var defaultAttempt = "not-attempted"
    intDoubleDoubleIntConstructor?.let { constructor ->
        val path = "SamplerConfig.<init>(int,double,double,int)"
        triedPaths += path
        val value = runCatching {
            constructor.isAccessible = true
            constructor.newInstance(10, 0.95, 0.8, 1)
        }.getOrNull()
        defaultAttempt = if (value != null) "success" else "failed"
        addCandidate(path, value)
    }
    constructors.firstOrNull { it.parameterTypes.isEmpty() }?.let { constructor ->
        val path = "SamplerConfig.<init>()"
        triedPaths += path
        addCandidate(path, runCatching {
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrNull())
    }

    val selected = candidates.firstOrNull { (_, value) -> samplerConfigClass.isInstance(value) }
    return SamplerConfigProbeSummary(
        classStatus = "found:${samplerConfigClass.name}",
        factorySignatures = (factoryMethods + companionMethods)
            .distinctBy { it.toGenericString() }
            .sortedBy { it.toGenericString() }
            .map { it.toGenericString() },
        constructorSignatures = constructors.map { constructor ->
            val params = constructor.parameterTypes.joinToString(",") { it.name }.ifBlank { "none" }
            "${constructor.name}(params=$params)"
        },
        triedPaths = triedPaths,
        defaultAttempt = defaultAttempt,
        selectedPath = selected?.first,
    )
}

private fun probeSessionConfigCreationPaths(samplerConfigProbe: SamplerConfigProbeSummary): SessionConfigProbeSummary {
    val sessionConfigClass = runCatching {
        Class.forName("com.google.ai.edge.litertlm.SessionConfig")
    }.getOrNull()
    if (sessionConfigClass == null) {
        return SessionConfigProbeSummary(classStatus = "not-found")
    }
    val factoryMethods = sessionConfigClass.methods.filter { method ->
        method.parameterTypes.isEmpty() &&
            (method.name == "CreateDefault" || method.name == "createDefault" || method.name == "builder")
    }
    val companionOrObject = sessionConfigClass.declaredFields.firstOrNull { field ->
        field.name == "Companion" || field.name == "INSTANCE"
    }?.let { field ->
        runCatching {
            field.isAccessible = true
            field.get(null)
        }.getOrNull()
    }
    val companionMethods = companionOrObject
        ?.javaClass
        ?.methods
        ?.filter { method ->
            method.parameterTypes.isEmpty() &&
                (method.name == "CreateDefault" || method.name == "createDefault" || method.name == "builder")
        }
        .orEmpty()
    val constructors = sessionConfigClass.declaredConstructors.sortedBy { it.parameterTypes.size }

    val triedPaths = mutableListOf<String>()
    val candidates = mutableListOf<Pair<String, Any>>()

    fun addCandidate(path: String, value: Any?) {
        if (value == null) return
        candidates += path to value
        val buildMethod = value.javaClass.methods.firstOrNull { method ->
            method.name == "build" && method.parameterTypes.isEmpty()
        }
        if (buildMethod != null) {
            triedPaths += "$path.build()"
            runCatching { buildMethod.invoke(value) }.getOrNull()?.let { built ->
                candidates += "$path.build()" to built
            }
        }
    }

    factoryMethods.forEach { method ->
        val path = "SessionConfig.${method.name}()"
        triedPaths += path
        addCandidate(path, runCatching { method.invoke(null) }.getOrNull())
    }
    companionMethods.forEach { method ->
        val receiver = companionOrObject ?: return@forEach
        val path = "SessionConfig.${receiver.javaClass.simpleName}.${method.name}()"
        triedPaths += path
        addCandidate(path, runCatching { method.invoke(receiver) }.getOrNull())
    }
    val samplerConfigClass = if (samplerConfigProbe.classStatus.startsWith("found:")) {
        runCatching { Class.forName(samplerConfigProbe.classStatus.removePrefix("found:")) }.getOrNull()
    } else {
        null
    }
    val samplerConfigInstance = samplerConfigClass?.let { samplerClass ->
        samplerConfigProbe.selectedPath?.let { createSamplerConfigFromPath(samplerClass, it) }
            ?: createDefaultSamplerConfig(samplerClass)
    }
    constructors.filter { it.parameterTypes.size == 1 }.forEach { constructor ->
        if (samplerConfigClass == null || samplerConfigInstance == null) return@forEach
        val parameterClass = constructor.parameterTypes.first()
        if (!parameterClass.isAssignableFrom(samplerConfigClass)) return@forEach
        val parameterLabel = parameterClass.simpleName.ifBlank { parameterClass.name.substringAfterLast('.') }
        val path = "SessionConfig.<init>($parameterLabel via ${samplerConfigProbe.selectedPath ?: "SamplerConfig.default"})"
        triedPaths += path
        addCandidate(path, runCatching {
            constructor.isAccessible = true
            constructor.newInstance(samplerConfigInstance)
        }.getOrNull())
    }
    constructors.firstOrNull { it.parameterTypes.isEmpty() }?.let { constructor ->
        val path = "SessionConfig.<init>()"
        triedPaths += path
        addCandidate(path, runCatching {
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrNull())
    }

    val selected = candidates.firstOrNull { (_, value) -> sessionConfigClass.isInstance(value) }
    return SessionConfigProbeSummary(
        classStatus = "found:${sessionConfigClass.name}",
        factorySignatures = (factoryMethods + companionMethods)
            .distinctBy { it.toGenericString() }
            .sortedBy { it.toGenericString() }
            .map { it.toGenericString() },
        constructorSignatures = constructors.map { constructor ->
            val params = constructor.parameterTypes.joinToString(",") { it.name }.ifBlank { "none" }
            "${constructor.name}(params=$params)"
        },
        triedPaths = triedPaths,
        selectedPath = selected?.first,
    )
}

private fun tryInvokeEngineCreateSessionWithSessionConfig(
    engine: Any,
    sessionConfigProbe: SessionConfigProbeSummary,
    samplerConfigProbe: SamplerConfigProbeSummary,
    appendTrace: (String) -> Unit = {},
): EngineCreateSessionInvokeResult {
    val invokePath = "createSession(SessionConfig)"
    val sessionConfigClassName = sessionConfigProbe.classStatus.removePrefix("found:")
    if (!sessionConfigProbe.classStatus.startsWith("found:")) return EngineCreateSessionInvokeResult()
    val sessionConfigClass = runCatching { Class.forName(sessionConfigClassName) }.getOrNull()
        ?: return EngineCreateSessionInvokeResult()
    val sessionConfigInstance = sessionConfigProbe.selectedPath?.let {
        // 生成経路の優先順を維持するため、再度同じルールで生成する。
        createSessionConfigFromPath(sessionConfigClass, samplerConfigProbe, it)
    } ?: createDefaultSessionConfig(
        sessionConfigClass = sessionConfigClass,
        samplerConfigProbe = samplerConfigProbe,
        appendTrace = appendTrace,
    )
    val createMethod = engine.javaClass.methods.firstOrNull { method ->
        method.name == "createSession" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0].isAssignableFrom(sessionConfigClass)
    } ?: return EngineCreateSessionInvokeResult()
    return runCatching { createMethod.invoke(engine, sessionConfigInstance) }
        .fold(
            onSuccess = { session -> EngineCreateSessionInvokeResult(session = session) },
            onFailure = { throwable ->
                safeAppendTrace(
                    appendTrace,
                    "UPSTREAM tokenizer-recount $invokePath invoke-failed ${throwable.javaClass.name}:${throwable.message}",
                )
                EngineCreateSessionInvokeResult(
                    failureSummary = buildEngineCreateSessionFailureSummary(
                        path = invokePath,
                        throwable = throwable,
                    ),
                )
            },
        )
}

private fun createDefaultSamplerConfig(samplerConfigClass: Class<*>): Any? {
    tryCreateDefaultSamplerConfig(samplerConfigClass)?.let { return it }
    val factoryMethods = samplerConfigClass.methods.filter { method ->
        method.parameterTypes.isEmpty() &&
            (method.name == "CreateDefault" || method.name == "createDefault" || method.name == "builder")
    }
    factoryMethods.forEach { method ->
        val value = runCatching { method.invoke(null) }.getOrNull() ?: return@forEach
        if (samplerConfigClass.isInstance(value)) return value
        val buildMethod = value.javaClass.methods.firstOrNull { it.name == "build" && it.parameterTypes.isEmpty() }
        if (buildMethod != null) {
            runCatching { buildMethod.invoke(value) }.getOrNull()?.let { built ->
                if (samplerConfigClass.isInstance(built)) return built
            }
        }
    }
    val constructor = samplerConfigClass.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() } ?: return null
    return runCatching {
        constructor.isAccessible = true
        constructor.newInstance()
    }.getOrNull()
}

private fun tryCreateDefaultSamplerConfig(): Any? {
    val clazz = runCatching {
        Class.forName("com.google.ai.edge.litertlm.SamplerConfig")
    }.getOrNull() ?: return null
    return tryCreateDefaultSamplerConfig(clazz)
}

private fun tryCreateDefaultSamplerConfig(samplerConfigClass: Class<*>): Any? {
    val constructor = samplerConfigClass.declaredConstructors.firstOrNull { target ->
        val parameterTypes = target.parameterTypes
        parameterTypes.size == 4 &&
            parameterTypes[0] == Int::class.javaPrimitiveType &&
            parameterTypes[1] == Double::class.javaPrimitiveType &&
            parameterTypes[2] == Double::class.javaPrimitiveType &&
            parameterTypes[3] == Int::class.javaPrimitiveType
    } ?: return null
    return runCatching {
        constructor.isAccessible = true
        val topK = 10
        val topP = 0.95
        val temperature = 0.8
        val extra = 1
        constructor.newInstance(topK, topP, temperature, extra)
    }.getOrNull()
}

private fun createSamplerConfigFromPath(samplerConfigClass: Class<*>, path: String): Any? {
    if (path == "SamplerConfig.<init>(int,double,double,int)") {
        return tryCreateDefaultSamplerConfig(samplerConfigClass)
    }
    if (path == "SamplerConfig.<init>()") {
        val constructor = samplerConfigClass.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() } ?: return null
        return runCatching {
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrNull()
    }
    if (!path.startsWith("SamplerConfig.")) return null
    val needsBuild = path.endsWith(".build()")
    val methodPath = path.removeSuffix(".build()")
    val methodName = methodPath.removePrefix("SamplerConfig.").substringBefore("(").substringAfterLast('.')
    val staticMethod = samplerConfigClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
    val baseValue = if (staticMethod != null) {
        runCatching { staticMethod.invoke(null) }.getOrNull()
    } else {
        val receiverField = samplerConfigClass.declaredFields.firstOrNull { it.name == "Companion" || it.name == "INSTANCE" }
        val receiver = receiverField?.let {
            runCatching {
                it.isAccessible = true
                it.get(null)
            }.getOrNull()
        }
        val receiverMethod = receiver?.javaClass?.methods?.firstOrNull {
            it.name == methodName && it.parameterTypes.isEmpty()
        }
        if (receiver != null && receiverMethod != null) {
            runCatching { receiverMethod.invoke(receiver) }.getOrNull()
        } else {
            null
        }
    } ?: return null
    if (!needsBuild) return baseValue
    val buildMethod = baseValue.javaClass.methods.firstOrNull { it.name == "build" && it.parameterTypes.isEmpty() } ?: return null
    return runCatching { buildMethod.invoke(baseValue) }.getOrNull()
}

private fun createDefaultSessionConfig(
    sessionConfigClass: Class<*>,
    samplerConfigProbe: SamplerConfigProbeSummary,
    appendTrace: (String) -> Unit = {},
): Any? {
    val samplerConfigClass = if (samplerConfigProbe.classStatus.startsWith("found:")) {
        runCatching { Class.forName(samplerConfigProbe.classStatus.removePrefix("found:")) }.getOrNull()
    } else {
        null
    }
    val samplerConfigInstance = samplerConfigClass?.let { samplerClass ->
        val defaultSamplerConfig = tryCreateDefaultSamplerConfig(samplerClass)
        if (defaultSamplerConfig != null) {
            defaultSamplerConfig
        } else {
            samplerConfigProbe.selectedPath?.let { createSamplerConfigFromPath(samplerClass, it) }
                ?: createDefaultSamplerConfig(samplerClass)
        }
    }
    if (samplerConfigClass != null && samplerConfigInstance != null) {
        val samplerConstructor = sessionConfigClass.declaredConstructors.firstOrNull { constructor ->
            constructor.parameterTypes.size == 1 &&
                constructor.parameterTypes[0].name == "com.google.ai.edge.litertlm.SamplerConfig"
        }
        if (samplerConstructor != null) {
            val instance = runCatching {
                samplerConstructor.isAccessible = true
                samplerConstructor.newInstance(samplerConfigInstance)
            }.getOrNull()
            if (instance != null) {
                safeAppendTrace(appendTrace, "UPSTREAM SessionConfig path=SamplerConfig constructor success")
                return instance
            } else {
                safeAppendTrace(appendTrace, "UPSTREAM SessionConfig path=SamplerConfig constructor failed")
            }
        }
    }
    val factoryMethods = sessionConfigClass.methods.filter { method ->
        method.parameterTypes.isEmpty() &&
            (method.name == "CreateDefault" || method.name == "createDefault" || method.name == "builder")
    }
    factoryMethods.forEach { method ->
        val value = runCatching { method.invoke(null) }.getOrNull() ?: return@forEach
        if (sessionConfigClass.isInstance(value)) return value
        val buildMethod = value.javaClass.methods.firstOrNull { it.name == "build" && it.parameterTypes.isEmpty() }
        if (buildMethod != null) {
            runCatching { buildMethod.invoke(value) }.getOrNull()?.let { built ->
                if (sessionConfigClass.isInstance(built)) return built
            }
        }
    }
    val constructor = sessionConfigClass.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() } ?: return null
    safeAppendTrace(appendTrace, "UPSTREAM SessionConfig path=SessionConfig.<init>() fallback")
    return runCatching {
        constructor.isAccessible = true
        constructor.newInstance()
    }.getOrNull()
}

private fun createSessionConfigFromPath(
    sessionConfigClass: Class<*>,
    samplerConfigProbe: SamplerConfigProbeSummary,
    path: String,
): Any? {
    if (path == "SessionConfig.<init>()") {
        val constructor = sessionConfigClass.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() } ?: return null
        return runCatching {
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrNull()
    }
    if (path.startsWith("SessionConfig.<init>(")) {
        val samplerConfigClass = if (samplerConfigProbe.classStatus.startsWith("found:")) {
            runCatching { Class.forName(samplerConfigProbe.classStatus.removePrefix("found:")) }.getOrNull()
        } else {
            null
        } ?: return null
        val samplerConfigInstance = samplerConfigProbe.selectedPath?.let {
            createSamplerConfigFromPath(samplerConfigClass, it)
        } ?: createDefaultSamplerConfig(samplerConfigClass) ?: return null
        val constructor = sessionConfigClass.declaredConstructors.firstOrNull { target ->
            target.parameterTypes.size == 1 &&
                target.parameterTypes[0].isAssignableFrom(samplerConfigClass)
        } ?: return null
        return runCatching {
            constructor.isAccessible = true
            constructor.newInstance(samplerConfigInstance)
        }.getOrNull()
    }
    if (!path.startsWith("SessionConfig.")) return null
    val needsBuild = path.endsWith(".build()")
    val methodPath = path.removeSuffix(".build()")
    val methodName = methodPath.removePrefix("SessionConfig.").substringBefore("(").substringAfterLast('.')
    val staticMethod = sessionConfigClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
    val baseValue = if (staticMethod != null) {
        runCatching { staticMethod.invoke(null) }.getOrNull()
    } else {
        val receiverField = sessionConfigClass.declaredFields.firstOrNull { it.name == "Companion" || it.name == "INSTANCE" }
        val receiver = receiverField?.let {
            runCatching {
                it.isAccessible = true
                it.get(null)
            }.getOrNull()
        }
        val receiverMethod = receiver?.javaClass?.methods?.firstOrNull {
            it.name == methodName && it.parameterTypes.isEmpty()
        }
        if (receiver != null && receiverMethod != null) {
            runCatching { receiverMethod.invoke(receiver) }.getOrNull()
        } else {
            null
        }
    } ?: return null
    if (!needsBuild) return baseValue
    val buildMethod = baseValue.javaClass.methods.firstOrNull { it.name == "build" && it.parameterTypes.isEmpty() } ?: return null
    return runCatching { buildMethod.invoke(baseValue) }.getOrNull()
}


private fun isLiteRtEngineInstance(instance: Any): Boolean {
    val className = instance.javaClass.name
    return className == "com.google.ai.edge.litertlm.Engine" ||
        className.endsWith(".Engine") && className.contains("litertlm", ignoreCase = true)
}

private fun tryInvokeEngineCreateSessionNoArgs(engine: Any): Any? {
    val createMethod = engine.javaClass.methods.firstOrNull { method ->
        method.name == "createSession" && method.parameterTypes.isEmpty()
    } ?: return null
    return runCatching { createMethod.invoke(engine) }.getOrNull()
}

private fun hasEngineCreateSessionNoArgsMethod(engine: Any): Boolean {
    return engine.javaClass.methods.any { method ->
        method.name == "createSession" && method.parameterTypes.isEmpty()
    }
}

private fun tryInvokeEngineCreateSessionNullableArgs(engine: Any): Any? {
    val createMethods = engine.javaClass.methods.filter { method ->
        method.name == "createSession" && method.parameterTypes.isNotEmpty()
    }
    createMethods.forEach { method ->
        val parameterTypes = method.parameterTypes
        if (parameterTypes.any { it.isPrimitive }) return@forEach
        val args: Array<Any?> = arrayOfNulls(parameterTypes.size)
        runCatching { method.invoke(engine, *args) }.getOrNull()?.let { return it }
    }
    return null
}

private fun hasEngineCreateSessionNullableArgsMethod(engine: Any): Boolean {
    return engine.javaClass.methods.any { method ->
        method.name == "createSession" && method.parameterTypes.isNotEmpty()
    }
}

private fun hasEngineCreateSessionSessionConfigMethod(engine: Any): Boolean {
    return engine.javaClass.methods.any { method ->
        method.name == "createSession" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0].name == "com.google.ai.edge.litertlm.SessionConfig"
    }
}

private fun tryInvokeEngineCreateSessionDefaultBridge(
    engine: Any,
    sessionConfigProbe: SessionConfigProbeSummary,
    appendTrace: (String) -> Unit = {},
): EngineCreateSessionInvokeResult {
    val invokePath = "createSession\$default(...)"
    val defaultMethod = engine.javaClass.methods.firstOrNull { method ->
        method.name == "createSession\$default" && java.lang.reflect.Modifier.isStatic(method.modifiers)
    } ?: return EngineCreateSessionInvokeResult()
    val parameterTypes = defaultMethod.parameterTypes
    val sessionConfigClassName = sessionConfigProbe.classStatus.removePrefix("found:")
    val sessionConfigClass = if (sessionConfigProbe.classStatus.startsWith("found:")) {
        runCatching { Class.forName(sessionConfigClassName) }.getOrNull()
    } else {
        null
    }
    val samplerConfigProbe = probeSamplerConfigCreationPaths()
    val sessionConfigInstance = sessionConfigClass?.let { createDefaultSessionConfig(it, samplerConfigProbe) }
    val args = Array<Any?>(parameterTypes.size) { index ->
        val type = parameterTypes[index]
        when {
            index == 0 && type.isAssignableFrom(engine.javaClass) -> engine
            sessionConfigClass != null && sessionConfigInstance != null && type.isAssignableFrom(sessionConfigClass) -> sessionConfigInstance
            type == Int::class.javaPrimitiveType -> 0
            type == Boolean::class.javaPrimitiveType -> false
            else -> null
        }
    }
    return runCatching { defaultMethod.invoke(null, *args) }
        .fold(
            onSuccess = { session -> EngineCreateSessionInvokeResult(session = session) },
            onFailure = { throwable ->
                safeAppendTrace(
                    appendTrace,
                    "UPSTREAM tokenizer-recount $invokePath invoke-failed ${throwable.javaClass.name}:${throwable.message}",
                )
                EngineCreateSessionInvokeResult(
                    failureSummary = buildEngineCreateSessionFailureSummary(
                        path = invokePath,
                        throwable = throwable,
                    ),
                )
            },
        )
}

private fun buildEngineCreateSessionFailureSummary(
    path: String,
    throwable: Throwable,
): EngineCreateSessionFailureSummary {
    val cause = throwable.cause
    return EngineCreateSessionFailureSummary(
        path = path,
        exceptionClass = throwable.javaClass.name,
        exceptionMessage = throwable.message,
        causeClass = cause?.javaClass?.name,
        causeMessage = cause?.message,
    )
}

private fun hasEngineCreateSessionDefaultBridgeMethod(engine: Any): Boolean {
    return engine.javaClass.methods.any { method ->
        method.name == "createSession\$default" && java.lang.reflect.Modifier.isStatic(method.modifiers)
    }
}

private val TOKENIZER_SOURCE_CANDIDATE_KEYWORDS =
    listOf("inference", "llm", "session", "token", "size", "engine")
private val EXISTING_SESSION_MEMBER_CANDIDATES =
    listOf("getSession", "session", "currentSession", "activeSession", "llmSession")

private fun emitTokenizerSessionSourceTrace(
    appendTrace: (String) -> Unit,
    tokenizerSessionSource: Any?,
    conversation: Conversation,
    engineCreateSessionStatus: String? = null,
    engineCreateSessionFailureSummary: EngineCreateSessionFailureSummary? = null,
    sessionConfigProbe: SessionConfigProbeSummary? = null,
    samplerConfigProbe: SamplerConfigProbeSummary? = null,
    existingSessionResolution: ExistingTokenizerSessionResolution? = null,
    createdSessionResolution: CreatedTokenizerSessionResolution? = null,
    conversationTokenizerResolution: ConversationTokenizerResolution? = null,
): TokenizerSourceTraceSummary {
    val resolvedSourceObject = tokenizerSessionSource ?: conversation
    val sourceClassName = resolvedSourceObject.javaClass.name
    val sourceKind = if (tokenizerSessionSource != null) {
        "engine-backed(unresolved)"
    } else {
        "conversation-fallback(unresolved)"
    }
    safeAppendTrace(appendTrace, "UPSTREAM tokenizer-source kind: $sourceKind")
    safeAppendTrace(appendTrace, "UPSTREAM tokenizer-source class: $sourceClassName")
    val methodCandidates = resolvedSourceObject
        .javaClass
        .methods
        .map { it.name }
        .orEmpty()
    val fieldCandidates = resolvedSourceObject
        .javaClass
        .declaredFields
        .map { it.name }
        .orEmpty()
    safeAppendTrace(
        appendTrace,
        "UPSTREAM tokenizer-source methods: ${summarizeTokenizerSourceCandidates(methodCandidates)}",
    )
    safeAppendTrace(
        appendTrace,
        "UPSTREAM tokenizer-source fields: ${summarizeTokenizerSourceCandidates(fieldCandidates)}",
    )
    val createSessionSignatures = buildCreateSessionMethodSignatures(resolvedSourceObject)
    createSessionSignatures.forEach { signature ->
        safeAppendTrace(appendTrace, "UPSTREAM tokenizer-source createSession signature: $signature")
    }
    return TokenizerSourceTraceSummary(
        kind = sourceKind,
        className = sourceClassName,
        methodsSummary = summarizeTokenizerSourceCandidates(methodCandidates),
        fieldsSummary = summarizeTokenizerSourceCandidates(fieldCandidates),
        createSessionSignatures = createSessionSignatures,
        engineCreateSessionStatus = engineCreateSessionStatus,
        engineCreateSessionFailureSummary = engineCreateSessionFailureSummary,
        sessionConfigProbe = sessionConfigProbe,
        samplerConfigProbe = samplerConfigProbe,
        existingSessionPath = existingSessionResolution?.path,
        existingSessionClass = existingSessionResolution?.sessionClassName,
        existingSessionSizeInTokensStatus = if (existingSessionResolution?.sizeInTokensMethod != null) "found" else "not-found",
        createdSessionPath = createdSessionResolution?.path,
        createdSessionClass = createdSessionResolution?.sessionClassName,
        createdSessionSizeInTokensStatus = if (createdSessionResolution?.sizeInTokensMethod != null) "found" else "not-found",
        conversationTokenizerPath = conversationTokenizerResolution?.path,
        conversationTokenizerClass = conversationTokenizerResolution?.className,
    )
}

private fun inspectCreatedSessionForTokenizer(
    createdSession: Any?,
    createdSessionPath: String?,
): CreatedTokenizerSessionResolution? {
    if (createdSession == null || createdSessionPath.isNullOrBlank()) return null
    return runCatching {
        CreatedTokenizerSessionResolution(
            path = createdSessionPath,
            sessionClassName = createdSession.javaClass.name,
            sizeInTokensMethod = findSizeInTokensMethod(createdSession),
        )
    }.getOrNull()
}

private fun buildCreateSessionMethodSignatures(source: Any?): List<String> {
    val methods = source
        ?.javaClass
        ?.methods
        ?.filter { method ->
            method.name == "createSession" || method.name.startsWith("createSession$")
        }
        .orEmpty()
        .sortedWith(
            compareBy<java.lang.reflect.Method>({ it.name }, { it.parameterTypes.size }, { it.toGenericString() }),
        )
    if (methods.isEmpty()) return listOf("none")
    return methods.map { method ->
        val parameterTypes = method.parameterTypes
            .joinToString(",") { parameterType ->
                parameterType.name
            }
            .ifBlank { "none" }
        val returnType = method.returnType.name
        val staticOrInstance =
            if (java.lang.reflect.Modifier.isStatic(method.modifiers)) "static" else "instance"
        "${method.name}(params=$parameterTypes) : $returnType [$staticOrInstance, paramCount=${method.parameterTypes.size}]"
    }
}

private fun summarizeTokenizerSourceCandidates(names: List<String>): String {
    val candidates = names
        .filter { name ->
            val lower = name.lowercase(Locale.ROOT)
            TOKENIZER_SOURCE_CANDIDATE_KEYWORDS.any { keyword -> lower.contains(keyword) }
        }
        .distinct()
        .sorted()
    if (candidates.isEmpty()) return "none"
    val maxItems = 12
    val visible = candidates.take(maxItems)
    val moreCount = (candidates.size - visible.size).coerceAtLeast(0)
    return buildString {
        append(visible.joinToString(","))
        if (moreCount > 0) {
            append(",...(+")
            append(moreCount)
            append(")")
        }
        append(" [count=")
        append(candidates.size)
        append("]")
    }
}

private fun tryResolveExistingSessionForTokenizer(
    conversation: Conversation,
    tokenizerSessionSource: Any?,
    appendTrace: (String) -> Unit,
): ExistingTokenizerSessionResolution? {
    val sourceCandidates = buildList {
        add(Triple("conversation", conversation as Any, "conversation"))
        tokenizerSessionSource?.let { source ->
            add(Triple("tokenizerSessionSource", source, "tokenizerSessionSource"))
            readNamedMemberValue(source, "heldEngine")?.let { held ->
                add(Triple("held-engine", held, "tokenizerSessionSource.heldEngine"))
                readNamedMemberValue(held, "engineInstance")?.let { engine ->
                    add(Triple("held-engine-engine-instance", engine, "tokenizerSessionSource.heldEngine.engineInstance"))
                }
            }
            readNamedMemberValue(source, "engine")?.let { engine ->
                add(Triple("engine-member", engine, "tokenizerSessionSource.engine"))
            }
            add(Triple("engine", source, "engine"))
        }
    }
    sourceCandidates.forEach { (sourceKind, sourceObject, sourcePath) ->
        resolveExistingSessionFromObject(
            sourceKind = sourceKind,
            sourcePath = sourcePath,
            sourceObject = sourceObject,
        )?.let { resolved ->
            safeAppendTrace(
                appendTrace,
                "UPSTREAM tokenizer-recount existing-session found path=${resolved.path} class=${resolved.sessionClassName}",
            )
            return resolved
        }
    }
    safeAppendTrace(appendTrace, "UPSTREAM tokenizer-recount existing-session path=none")
    return null
}

private fun tryResolveTokenizerFromConversation(
    conversation: Any,
    appendTrace: (String) -> Unit,
): ConversationTokenizerResolution? {
    val visited = mutableSetOf<Any>()
    val queue = ArrayDeque<Pair<String, Any>>()
    queue.add("conversation" to conversation)
    while (queue.isNotEmpty()) {
        val (path, obj) = queue.removeFirst()
        if (obj in visited) continue
        visited.add(obj)
        val clazz = obj.javaClass
        val sizeMethod = findSizeInTokensMethod(obj)
        if (sizeMethod != null) {
            safeAppendTrace(appendTrace, "UPSTREAM tokenizer found at $path class=${clazz.name}")
            return ConversationTokenizerResolution(
                path = path,
                sourceObject = obj,
                className = clazz.name,
                sizeInTokensMethod = sizeMethod,
            )
        }
        clazz.methods
            .filter { it.parameterTypes.isEmpty() }
            .forEach { method ->
                runCatching {
                    val result = method.invoke(obj) ?: return@forEach
                    if (result.javaClass.name.startsWith("java")) return@forEach
                    queue.add("$path.${method.name}()" to result)
                }
            }
        clazz.declaredFields.forEach { field ->
            runCatching {
                field.isAccessible = true
                val value = field.get(obj) ?: return@forEach
                if (value.javaClass.name.startsWith("java")) return@forEach
                queue.add("$path.${field.name}" to value)
            }
        }
    }
    safeAppendTrace(appendTrace, "UPSTREAM tokenizer not found from conversation")
    return null
}

private fun resolveExistingSessionFromObject(
    sourceKind: String,
    sourcePath: String,
    sourceObject: Any,
): ExistingTokenizerSessionResolution? {
    findExistingSessionCandidateFromMembers(
        sourceKind = sourceKind,
        sourcePath = sourcePath,
        sourceObject = sourceObject,
        preferredOnly = true,
    )?.let { return it }
    return findExistingSessionCandidateFromMembers(
        sourceKind = sourceKind,
        sourcePath = sourcePath,
        sourceObject = sourceObject,
        preferredOnly = false,
    )
}

private fun findExistingSessionCandidateFromMembers(
    sourceKind: String,
    sourcePath: String,
    sourceObject: Any,
    preferredOnly: Boolean,
): ExistingTokenizerSessionResolution? {
    val methods = sourceObject.javaClass.methods
        .filter { method -> method.parameterTypes.isEmpty() }
        .sortedBy { it.name }
    methods.forEach { method ->
        val isPreferred = EXISTING_SESSION_MEMBER_CANDIDATES.contains(method.name)
        val isGeneric = method.name.contains("session", ignoreCase = true)
        if ((preferredOnly && !isPreferred) || (!preferredOnly && !isGeneric)) return@forEach
        runCatching { method.invoke(sourceObject) }.getOrNull()?.let { candidate ->
            buildExistingSessionResolution(
                sourceKind = sourceKind,
                path = "$sourcePath.${method.name}()",
                sourceObject = sourceObject,
                candidate = candidate,
            )?.let { return it }
        }
    }
    val fields = sourceObject.javaClass.declaredFields.sortedBy { it.name }
    fields.forEach { field ->
        val isPreferred = EXISTING_SESSION_MEMBER_CANDIDATES.contains(field.name)
        val isGeneric = field.name.contains("session", ignoreCase = true)
        if ((preferredOnly && !isPreferred) || (!preferredOnly && !isGeneric)) return@forEach
        runCatching {
            field.isAccessible = true
            field.get(sourceObject)
        }.getOrNull()?.let { candidate ->
            buildExistingSessionResolution(
                sourceKind = sourceKind,
                path = "$sourcePath.${field.name}",
                sourceObject = sourceObject,
                candidate = candidate,
            )?.let { return it }
        }
    }
    return null
}

private fun buildExistingSessionResolution(
    sourceKind: String,
    path: String,
    sourceObject: Any,
    candidate: Any,
): ExistingTokenizerSessionResolution? {
    val candidateClass = candidate.javaClass
    val looksLikeSession = candidateClass.name.contains("session", ignoreCase = true)
    val sizeMethod = findSizeInTokensMethod(candidate)
    if (!looksLikeSession && sizeMethod == null) return null
    return ExistingTokenizerSessionResolution(
        sourceKind = sourceKind,
        path = path,
        sourceObject = sourceObject,
        sessionInstance = candidate,
        sessionClassName = candidateClass.name,
        sizeInTokensMethod = sizeMethod,
    )
}

private fun readNamedMemberValue(source: Any, memberName: String): Any? {
    val getterName = "get${memberName.replaceFirstChar { it.uppercaseChar() }}"
    val method = source.javaClass.methods.firstOrNull { candidate ->
        candidate.parameterTypes.isEmpty() && (candidate.name == memberName || candidate.name == getterName)
    }
    if (method != null) {
        runCatching { method.invoke(source) }.getOrNull()?.let { return it }
    }
    val field = source.javaClass.declaredFields.firstOrNull { it.name == memberName } ?: return null
    return runCatching {
        field.isAccessible = true
        field.get(source)
    }.getOrNull()
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
    mediaPipeProbeContext: Context? = null,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    onPreferredBackendApplied: (PreferredBackendApplyResult) -> Unit = {},
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
                mediaPipeProbeContext = mediaPipeProbeContext,
                startElapsedMs = startElapsedMs,
                preferredBackendDryRunSetting = preferredBackendDryRunSetting,
                onPreferredBackendApplied = onPreferredBackendApplied,
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
    mediaPipeProbeContext: Context? = null,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    onPreferredBackendApplied: (PreferredBackendApplyResult) -> Unit = {},
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
                mediaPipeProbeContext = mediaPipeProbeContext,
                preferredBackendDryRunSetting = preferredBackendDryRunSetting,
                onPreferredBackendApplied = onPreferredBackendApplied,
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
    mediaPipeProbeContext: Context?,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting,
    onPreferredBackendApplied: (PreferredBackendApplyResult) -> Unit,
    startElapsedMs: Long,
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit,
): LocalOfficialFlowStreamingResult? {
    if (spec.namespace == "com.google.ai.edge.litertlm") {
        return runOfficialLiteRtLmDirect(
            prompt = prompt,
            modelPath = modelPath,
            cacheDirPath = cacheDirPath,
            mediaPipeProbeContext = mediaPipeProbeContext,
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
        createOfficialEngineInstance(engineClass, spec.optionsCandidates, modelPath, preferredBackendDryRunSetting, onPreferredBackendApplied)
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
        val appendContext = StreamingAppendContext()
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
                val extracted = extractedText.orEmpty()
                logLocalStreamingWhitespace(
                    stage = "LocalStreamingRunner#official.flow.extract",
                    raw = extractedText,
                    normalized = extractedText?.trim(),
                )
                if (!isViableStreamingChunk(extracted)) {
                    extractFailureCount += 1
                    return@collect
                }
                if (extracted == lastPartial) return@collect
                lastPartial = extracted
                appendStreamingChunk(
                    builder = builder,
                    extractedRaw = extracted,
                    context = appendContext,
                    appendTrace = appendTrace,
                )
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
        val built = builder.toString()
        val response = built.trim()
        logLocalStreamingWhitespace(
            stage = "LocalStreamingRunner#official.flow.builder",
            raw = built,
            normalized = response,
        )
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
            tokenizerSessionSource = engine,
            mediaPipeProbeModelPath = modelPath,
            mediaPipeProbeContext = mediaPipeProbeContext,
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
    mediaPipeProbeContext: Context?,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    onPreferredBackendApplied: (PreferredBackendApplyResult) -> Unit = {},
    appendTrace: (String) -> Unit,
): LocalOfficialBlockingResult? {
    if (spec.namespace == "com.google.ai.edge.litertlm") {
        val result = runOfficialLiteRtLmBlocking(
            prompt = prompt,
            modelPath = modelPath,
            cacheDirPath = cacheDirPath,
            mediaPipeProbeContext = mediaPipeProbeContext,
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
        createOfficialEngineInstance(engineClass, spec.optionsCandidates, modelPath, preferredBackendDryRunSetting, onPreferredBackendApplied)
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
        val extractedText = extractOfficialMessageTextWithTrace(
            path = "official-blocking",
            value = responseValue,
            appendTrace = appendTrace,
        )
        val responseText = extractedText?.trim()
        logLocalStreamingWhitespace(
            stage = "LocalStreamingRunner#official.blocking.extract",
            raw = extractedText,
            normalized = responseText,
        )
        val nonBlankResponseText = responseText?.takeIf { it.isNotBlank() }
            ?: throw OfficialFlowFallbackException("message_extract_failed")
        measuredCollector.observe(
            timing = "after-response",
            conversation = conversation,
        )
        safeAppendTrace(appendTrace, "UPSTREAM official-blocking success responseLength=${nonBlankResponseText.length}")
        if (BuildConfig.DEBUG) {
            measuredCollector.observe(
                timing = "around-success-reached",
                conversation = conversation,
            )
        }
        successReached = true
        measuredTokenSnapshot = measuredCollector.adoptedSnapshot()
        measuredCollector.emitAdoptedTrace()
        finalResponse = nonBlankResponseText
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
    mediaPipeProbeContext: Context?,
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
            val appendContext = StreamingAppendContext()
            var lastChunk: String? = null
            var partialCount = 0
            var firstPartialMs: Long? = null
            var lastNonEmptyChunkAtMs: Long? = null
            conversation.sendMessageAsync(prompt).collect { message ->
                val rawContents = message.contents.toString()
                val normalizedContents = rawContents.trim()
                val rawMessage = message.toString()
                val normalizedMessage = rawMessage.trim()
                logLocalStreamingWhitespace(
                    stage = "LocalStreamingRunner#official.direct.flow.contents",
                    raw = rawContents,
                    normalized = normalizedContents,
                )
                logLocalStreamingWhitespace(
                    stage = "LocalStreamingRunner#official.direct.flow.message",
                    raw = rawMessage,
                    normalized = normalizedMessage,
                )
                val extractedText = rawContents
                    .takeIf { isViableStreamingChunk(it) }
                    ?: rawMessage.takeIf { isViableStreamingChunk(it) }
                if (!extractedText.isNullOrEmpty()) {
                    if (extractedText == lastChunk) return@collect
                    lastChunk = extractedText
                    appendStreamingChunk(
                        builder = builder,
                        extractedRaw = extractedText,
                        context = appendContext,
                        appendTrace = appendTrace,
                    )
                    if (firstPartialMs == null) {
                        firstPartialMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(0L)
                    }
                    lastNonEmptyChunkAtMs = SystemClock.elapsedRealtime()
                    partialCount += 1
                    onPartial(builder.toString())
                }
            }

            val built = builder.toString()
            val response = built.trim()
            logLocalStreamingWhitespace(
                stage = "LocalStreamingRunner#official.direct.flow.builder",
                raw = built,
                normalized = response,
            )
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
                tokenizerSessionSource = engine,
                mediaPipeProbeModelPath = modelPath,
                mediaPipeProbeContext = mediaPipeProbeContext,
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
    mediaPipeProbeContext: Context?,
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
            val rawContents = message.contents.toString()
            val normalizedContents = rawContents.trim()
            val rawMessage = message.toString()
            val normalizedMessage = rawMessage.trim()
            logLocalStreamingWhitespace(
                stage = "LocalStreamingRunner#official.direct.blocking.contents",
                raw = rawContents,
                normalized = normalizedContents,
            )
            logLocalStreamingWhitespace(
                stage = "LocalStreamingRunner#official.direct.blocking.message",
                raw = rawMessage,
                normalized = normalizedMessage,
            )
            val response = normalizedContents
                .ifBlank { normalizedMessage }

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
                    tokenizerSessionSource = engine,
                    mediaPipeProbeModelPath = modelPath,
                    mediaPipeProbeContext = mediaPipeProbeContext,
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
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    onPreferredBackendApplied: (PreferredBackendApplyResult) -> Unit = {},
): Any? {
    val factoryMethod = engineClass.methods.firstOrNull { method ->
        method.name == "createFromOptions" && method.parameterTypes.size == 1
    } ?: return null
    val options = optionClassNames.firstNotNullOfOrNull { optionClassName ->
        val optionClass = runCatching { Class.forName(optionClassName) }.getOrNull() ?: return@firstNotNullOfOrNull null
        buildOptionsObject(
            optionClass = optionClass,
            modelPath = modelPath,
            preferredBackendDryRunSetting = preferredBackendDryRunSetting,
            onPreferredBackendApplied = onPreferredBackendApplied,
        )
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
    return buildOptionsObject(
        optionClass = optionClass,
        modelPath = modelPath,
        preferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
        onPreferredBackendApplied = {},
    )
}

internal data class PreferredBackendApplyResult(
    val requestedPreferredBackend: String,
    val appliedPreferredBackend: String,
    val preferredBackendApplyResult: String,
    val preferredBackendHookReached: Boolean = false,
    val preferredBackendHookSource: String = "unknown",
    val preferredBackendApplyError: String? = null,
    val preferredBackendApplyBuilderClass: String? = null,
    val preferredBackendApplyMethodCandidates: List<String> = emptyList(),
    val preferredBackendApplyBackendEnumCandidates: List<String> = emptyList(),
    val preferredBackendApplyNotSupportedReason: String? = null,
)

private fun buildOptionsObject(
    optionClass: Class<*>,
    modelPath: String,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting,
    onPreferredBackendApplied: (PreferredBackendApplyResult) -> Unit,
): Any? {
    val builderFactory = optionClass.methods.firstOrNull { method ->
        method.name == "builder" && method.parameterTypes.isEmpty()
    } ?: return null
    val builder = runCatching { builderFactory.invoke(null) }.getOrNull() ?: return null
    onPreferredBackendApplied(
        applyPreferredBackendIfRequested(
            optionsBuilder = builder,
            requested = preferredBackendDryRunSetting,
            source = "buildOptionsObject",
        ),
    )
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

private fun applyPreferredBackendIfRequested(
    optionsBuilder: Any,
    requested: PreferredBackendDryRunSetting,
    source: String,
): PreferredBackendApplyResult {
    val builderClass = optionsBuilder::class.java.name
    val methodCandidates = (optionsBuilder.javaClass.methods.asSequence() + optionsBuilder.javaClass.declaredMethods.asSequence())
        .filter { method ->
            val lower = method.name.lowercase()
            listOf("backend", "preferred", "delegate", "gpu", "cpu").any { keyword -> lower.contains(keyword) }
        }
        .map { method ->
            val params = method.parameterTypes.joinToString(",") { it.simpleName }
            "${method.name}(${params}): ${method.returnType.simpleName}"
        }
        .distinct()
        .take(10)
        .toList()
    val common = PreferredBackendApplyResult(
        requestedPreferredBackend = requested.name,
        appliedPreferredBackend = "not-applied",
        preferredBackendApplyResult = "not-supported",
        preferredBackendHookReached = true,
        preferredBackendHookSource = source,
        preferredBackendApplyBuilderClass = builderClass,
        preferredBackendApplyMethodCandidates = methodCandidates,
    )
    if (!BuildConfig.DEBUG) return common.copy(preferredBackendApplyResult = "not-debug-build", preferredBackendApplyNotSupportedReason = "not-debug-build")
    if (requested == PreferredBackendDryRunSetting.DEFAULT) return common.copy(preferredBackendApplyResult = "skipped-default", preferredBackendApplyNotSupportedReason = "requested-default-skipped")
    val method = optionsBuilder.javaClass.methods.firstOrNull { m ->
        m.name == "setPreferredBackend" && m.parameterTypes.size == 1 && m.parameterTypes[0].isEnum
    } ?: return common.copy(preferredBackendApplyError = "NoSuchMethodException", preferredBackendApplyNotSupportedReason = "no-setPreferredBackend-method")
    val enumType = method.parameterTypes.firstOrNull { it.isEnum }
        ?: return common.copy(preferredBackendApplyNotSupportedReason = "no-backend-parameter")
    val enumCandidates = enumType.enumConstants
        ?.mapNotNull { (it as? Enum<*>)?.name }
        ?.distinct()
        ?.take(10)
        .orEmpty()
    if (enumCandidates.isEmpty()) {
        return common.copy(
            preferredBackendApplyBackendEnumCandidates = enumCandidates,
            preferredBackendApplyNotSupportedReason = "backend-enum-values-empty",
        )
    }
    val enumValue = enumType.enumConstants?.firstOrNull { (it as? Enum<*>)?.name == requested.name }
        ?: return common.copy(
            preferredBackendApplyBackendEnumCandidates = enumCandidates,
            preferredBackendApplyError = "BackendEnumNotFound",
            preferredBackendApplyNotSupportedReason = "enum-value-not-found",
        )
    return runCatching {
        method.invoke(optionsBuilder, enumValue)
        common.copy(
            appliedPreferredBackend = requested.name,
            preferredBackendApplyResult = "applied",
            preferredBackendApplyBackendEnumCandidates = enumCandidates,
            preferredBackendApplyNotSupportedReason = null,
        )
    }.getOrElse {
        common.copy(
            preferredBackendApplyResult = "failed-fallback",
            preferredBackendApplyError = it.javaClass.simpleName,
            preferredBackendApplyBackendEnumCandidates = enumCandidates,
            preferredBackendApplyNotSupportedReason = "unknown",
        )
    }
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
                extractOfficialMessageText(nested)?.takeIf { isViableStreamingChunk(it) }?.let { return it }
            }
            return null
        }
    }
    if (value.javaClass.isArray) {
        (value as? Array<*>)?.forEach { nested ->
            extractOfficialMessageText(nested)?.takeIf { isViableStreamingChunk(it) }?.let { return it }
        }
    }
    val getterNames = OFFICIAL_TEXT_CANDIDATES.filterNot { it == "toString" }
    getterNames.forEach { getterName ->
        val method = value.javaClass.methods.firstOrNull { it.name == getterName && it.parameterTypes.isEmpty() } ?: return@forEach
        val extracted = runCatching { extractOfficialMessageText(method.invoke(value)) }.getOrNull()
        if (!extracted.isNullOrEmpty() && isViableStreamingChunk(extracted)) return extracted
    }
    val loweredMethods = value.javaClass.methods.filter { it.parameterTypes.isEmpty() }.sortedBy { it.name }
    loweredMethods.forEach { method ->
        val lowerName = method.name.lowercase(Locale.ROOT)
        if (!lowerName.contains("text") && !lowerName.contains("content") && !lowerName.contains("part")) return@forEach
        val extracted = runCatching { extractOfficialMessageText(method.invoke(value)) }.getOrNull()
        if (!extracted.isNullOrEmpty() && isViableStreamingChunk(extracted)) return extracted
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
        val extractedRaw = runCatching { extractOfficialMessageText(candidateValue) }.getOrNull()
        val normalizedForBlankCheck = extractedRaw?.trim()
        logLocalStreamingWhitespace(
            stage = "LocalStreamingRunner#extractOfficialMessageTextWithTrace.$candidate",
            raw = extractedRaw,
            normalized = normalizedForBlankCheck,
        )
        if (!extractedRaw.isNullOrEmpty() && isViableStreamingChunk(extractedRaw)) {
            if (candidate == "toString" && value != null && !isMeaningfulToStringFallback(value, extractedRaw)) {
                safeAppendTrace(appendTrace, "UPSTREAM extract-text candidate=$candidate result=blank")
                return@forEach
            }
            val resultType = if (normalizedForBlankCheck.isNullOrBlank()) "whitespaceOnly" else "nonBlank"
            safeAppendTrace(
                appendTrace,
                "UPSTREAM extract-text candidate=$candidate result=$resultType length=${extractedRaw.length}",
            )
            safeAppendTrace(appendTrace, "UPSTREAM $path extracted length=${extractedRaw.length}")
            return extractedRaw
        }
        val resultLabel = if (candidateValue == null) "null" else "blank"
        safeAppendTrace(appendTrace, "UPSTREAM extract-text candidate=$candidate result=$resultLabel")
    }
    safeAppendTrace(appendTrace, "UPSTREAM $path extracted length=0")
    return null
}

private fun logLocalStreamingWhitespace(
    stage: String,
    raw: String?,
    normalized: String? = null,
) {
    if (!BuildConfig.DEBUG) return
    val rawSummary = summarizeWhitespaceForDebug(raw)
    val normalizedSummary = summarizeWhitespaceForDebug(normalized)
    if (normalized == null) {
        Log.d(LOCAL_STREAMING_WHITESPACE_LOG_TAG, "$stage raw=$rawSummary")
    } else {
        Log.d(
            LOCAL_STREAMING_WHITESPACE_LOG_TAG,
            "$stage raw=$rawSummary normalized=$normalizedSummary delta=${buildWhitespaceDeltaForDebug(raw, normalized)}",
        )
    }
}

private fun summarizeWhitespaceForDebug(text: String?): String {
    if (text == null) return "null"
    val spaces = text.count { it == ' ' }
    val newlines = text.count { it == '\n' }
    val tabs = text.count { it == '\t' }
    val carriageReturns = text.count { it == '\r' }
    val visualized = text
        .replace(" ", "␠")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    val head = visualized.take(60)
    val tail = if (visualized.length > 60) visualized.takeLast(60) else visualized
    return "len=${text.length},spaces=$spaces,newlines=$newlines,tabs=$tabs,cr=$carriageReturns,head=\"$head\",tail=\"$tail\""
}

private fun extractMessageContentsForTrace(message: Any?): String? {
    if (message == null) return null
    val contentsValue = runCatching {
        message.javaClass.methods.firstOrNull {
            it.name == "getContents" && it.parameterTypes.isEmpty()
        }?.invoke(message)
    }.getOrNull()
    return when (contentsValue) {
        null -> null
        is String -> contentsValue
        is CharSequence -> contentsValue.toString()
        else -> contentsValue.toString()
    }
}

internal fun shouldPreserveWhitespaceChunk(text: String): Boolean =
    text.isNotEmpty() && text.all { it.isWhitespace() }

internal fun isViableStreamingChunk(text: String): Boolean =
    text.isNotEmpty() && (text.isNotBlank() || shouldPreserveWhitespaceChunk(text))

internal fun shouldInsertMinimalJoinBetween(
    previous: String,
    next: String,
): Boolean {
    if (previous.isEmpty() || next.isEmpty()) return false
    if (next.first().isWhitespace()) return false
    if (previous.last().isWhitespace()) return false
    val previousLast = previous.last()
    val nextFirst = next.first()
    if (!previousLast.isAsciiWordLike() || !nextFirst.isAsciiWordLike()) return false
    if (previousLast in STREAMING_NO_JOIN_PREVIOUS_CHARS) return false
    if (nextFirst in STREAMING_NO_JOIN_NEXT_CHARS) return false
    if (isLikelyCodeJoinContext(previous, next)) return false
    return true
}

internal fun appendStreamingChunk(
    builder: StringBuilder,
    extractedRaw: String,
    context: StreamingAppendContext? = null,
    appendTrace: ((String) -> Unit)? = null,
): String {
    if (extractedRaw.isEmpty()) return ""
    var previousText = builder.toString()
    if (context?.lane == StreamingLane.PROSE &&
        isStandaloneCodeLanguageTag(extractedRaw) &&
        context.pendingCodeLanguageTag == null
    ) {
        context.pendingCodeLanguageTag = extractedRaw.trim()
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "[code.pendingLanguageTag.prose]=${summarizeWhitespaceForUi(context.pendingCodeLanguageTag)}")
            safeAppendTrace(trace, "UPSTREAM append-chunk lane=${StreamingLane.PROSE.label}")
            safeAppendTrace(trace, "UPSTREAM append-chunk join=${summarizeWhitespaceForUi("")}")
        }
        return ""
    }
    val lane = context?.lane ?: StreamingLane.PROSE
    if (lane == StreamingLane.PROSE && shouldEnterCodeLane(extractedRaw, context)) {
        context?.lane = StreamingLane.CODE
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "[lane.switch]=prose->code reason=${codeLaneReason(extractedRaw, context)}")
        }
    }
    if (context?.lane == StreamingLane.PROSE && context.pendingCodeLanguageTag != null && !isStrongCodeLikeChunk(extractedRaw)) {
        flushPendingCodeLanguageTagAsProse(builder, context, appendTrace)
        previousText = builder.toString()
    }
    if (context?.lane == StreamingLane.CODE) {
        if (shouldLeaveCodeLane(extractedRaw, context)) {
            commitPendingCodeLine(builder, context, appendTrace)
            flushPendingCodeLanguageTagAsProse(builder, context, appendTrace)
            context.lane = StreamingLane.PROSE
            appendTrace?.let { trace ->
                safeAppendTrace(trace, "[lane.switch]=code->prose reason=prose_like_chunk")
            }
        } else {
            return appendStreamingChunkForCode(
                builder = builder,
                extractedRaw = extractedRaw,
                context = context,
                appendTrace = appendTrace,
            )
        }
    }
    val join = if (shouldInsertMinimalJoinBetween(previousText, extractedRaw)) " " else ""
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "UPSTREAM append-chunk previousTail=${summarizeWhitespaceForUi(previousText.takeLast(64))}")
        safeAppendTrace(trace, "UPSTREAM append-chunk extracted=${summarizeWhitespaceForUi(extractedRaw.take(64))}")
        safeAppendTrace(trace, "UPSTREAM append-chunk lane=${StreamingLane.PROSE.label}")
        safeAppendTrace(trace, "UPSTREAM append-chunk join=${summarizeWhitespaceForUi(join)}")
    }
    if (join.isNotEmpty()) builder.append(join)
    builder.append(extractedRaw)
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "UPSTREAM append-chunk afterTail=${summarizeWhitespaceForUi(builder.toString().takeLast(64))}")
    }
    return join
}

private fun appendStreamingChunkForCode(
    builder: StringBuilder,
    extractedRaw: String,
    context: StreamingAppendContext,
    appendTrace: ((String) -> Unit)? = null,
): String {
    val wasInFencedCodeBlock = context.inFencedCodeBlock
    val isFenceBoundaryChunk = isFenceBoundaryChunk(extractedRaw)
    if (isFenceBoundaryChunk) {
        if (wasInFencedCodeBlock) {
            commitPendingCodeLine(builder, context, appendTrace)
            appendFenceChunk(builder, extractedRaw)
            context.lane = StreamingLane.PROSE
            clearCodeLanePendingState(context)
            context.inFencedCodeBlock = updateFencedCodeState(wasInFencedCodeBlock, extractedRaw)
            context.fencedCodeLanguageTag = null
            appendTrace?.let { trace ->
                safeAppendTrace(trace, "[lane.switch]=code->prose reason=fence_close")
            }
            return ""
        }
        commitPendingCodeLine(builder, context, appendTrace)
        appendFenceChunk(builder, extractedRaw)
        clearCodeLanePendingState(context)
        context.inFencedCodeBlock = updateFencedCodeState(wasInFencedCodeBlock, extractedRaw)
        context.fencedCodeLanguageTag = extractFencedCodeLanguageTag(extractedRaw)
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "[lane.switch]=code->code reason=fence_open")
        }
        return ""
    }
    context.inFencedCodeBlock = updateFencedCodeState(wasInFencedCodeBlock, extractedRaw)

    if (isStandaloneCodeLanguageTag(extractedRaw)) {
        commitPendingCodeLine(builder, context, appendTrace)
        context.pendingCodeLanguageTag = extractedRaw.trim()
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "UPSTREAM [code.pendingLanguageTag]=${summarizeWhitespaceForUi(context.pendingCodeLanguageTag)}")
            safeAppendTrace(trace, "UPSTREAM [code.insertedNewline]=false")
            safeAppendTrace(trace, "UPSTREAM append-chunk lane=${StreamingLane.CODE.label}")
            safeAppendTrace(trace, "UPSTREAM append-chunk join=${summarizeWhitespaceForUi("")}")
            safeAppendTrace(trace, "UPSTREAM append-chunk afterTail=${summarizeWhitespaceForUi(builder.toString().takeLast(64))}")
        }
        return ""
    }

    val pendingTag = context.pendingCodeLanguageTag
    var insertedNewline = false
    if (pendingTag != null) {
        if (builder.isNotEmpty() && !builder.last().isWhitespace()) {
            builder.append('\n')
            insertedNewline = true
        }
        builder.append(pendingTag)
        context.pendingCodeLanguageTag = null
        if (builder.lastOrNull() != '\n') builder.append('\n')
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "[code.flushLanguageTag]")
            safeAppendTrace(trace, "UPSTREAM [code.pendingLanguageTag]=${summarizeWhitespaceForUi(pendingTag)}")
        }
    }

    preSplitFencedPythonChunk(context, extractedRaw).forEach { chunkPart ->
        appendStreamingCodeChunkBody(
            builder = builder,
            extractedRaw = chunkPart,
            context = context,
            appendTrace = appendTrace,
        )
    }

    appendTrace?.let { trace ->
        safeAppendTrace(trace, "UPSTREAM append-chunk previousTail=${summarizeWhitespaceForUi(builder.toString().takeLast(64))}")
        safeAppendTrace(trace, "UPSTREAM append-chunk extracted=${summarizeWhitespaceForUi(extractedRaw.take(64))}")
        safeAppendTrace(trace, "UPSTREAM append-chunk lane=${StreamingLane.CODE.label}")
        safeAppendTrace(trace, "UPSTREAM append-chunk join=${summarizeWhitespaceForUi("")}")
        safeAppendTrace(trace, "UPSTREAM [code.pendingLanguageTag]=${summarizeWhitespaceForUi(context.pendingCodeLanguageTag)}")
        safeAppendTrace(trace, "[code.pending.after]=${summarizeWhitespaceForUi(context.pendingCodeLineBuffer?.toString())}")
        safeAppendTrace(trace, "UPSTREAM [code.insertedNewline]=$insertedNewline")
    }
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "UPSTREAM append-chunk afterTail=${summarizeWhitespaceForUi(builder.toString().takeLast(64))}")
    }
    return ""
}

private fun appendStreamingCodeChunkBody(
    builder: StringBuilder,
    extractedRaw: String,
    context: StreamingAppendContext,
    appendTrace: ((String) -> Unit)? = null,
) {
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "[code.pending.before]=${summarizeWhitespaceForUi(context.pendingCodeLineBuffer?.toString())}")
    }

    val pendingBuffer = context.pendingCodeLineBuffer ?: StringBuilder().also {
        context.pendingCodeLineBuffer = it
    }
    if (
        pendingBuffer.isNotEmpty() &&
            shouldStartNewFencedPythonLogicalLine(context, pendingBuffer.toString(), extractedRaw)
    ) {
        commitPendingCodeLine(builder, context, appendTrace)
    } else if (pendingBuffer.isNotEmpty() && shouldCommitPendingCodeLine(context, pendingBuffer.toString(), extractedRaw)) {
        commitPendingCodeLine(builder, context, appendTrace)
    }
    context.pendingCodeLineBuffer?.append(extractedRaw)
    if (shouldCommitPendingCodeLine(context, context.pendingCodeLineBuffer?.toString().orEmpty(), null)) {
        commitPendingCodeLine(builder, context, appendTrace)
    }
}

private fun isFenceBoundaryChunk(chunk: String): Boolean {
    val trimmed = chunk.trim()
    return trimmed.startsWith("```")
}

private fun appendFenceChunk(builder: StringBuilder, fenceChunk: String) {
    if (builder.isNotEmpty() && builder.last() != '\n') {
        builder.append('\n')
    }
    builder.append(fenceChunk.trimEnd())
    if (builder.lastOrNull() != '\n') {
        builder.append('\n')
    }
}

private fun clearCodeLanePendingState(context: StreamingAppendContext) {
    context.pendingCodeLanguageTag = null
    context.pendingCodeLineBuffer = null
    context.lastCodeChunkEndedWithNewline = false
}

internal enum class StreamingLane(val label: String) {
    PROSE("prose"),
    CODE("code"),
}

internal data class StreamingAppendContext(
    var lane: StreamingLane = StreamingLane.PROSE,
    var pendingCodeLanguageTag: String? = null,
    var pendingCodeLineBuffer: StringBuilder? = null,
    var lastCodeChunkEndedWithNewline: Boolean = false,
    var inFencedCodeBlock: Boolean = false,
    var fencedCodeLanguageTag: String? = null,
)


private fun updateFencedCodeState(current: Boolean, chunk: String): Boolean {
    val fenceCount = FENCED_MARKER_REGEX.findAll(chunk).count()
    if (fenceCount == 0) return current
    return if (fenceCount % 2 == 0) current else !current
}

private fun shouldEnterCodeLane(next: String, context: StreamingAppendContext?): Boolean {
    if (next.isEmpty()) return false
    if (context?.inFencedCodeBlock == true) return true
    val nextTrimmedStart = next.trimStart()
    if (nextTrimmedStart.startsWith("```")) return true
    if (context?.pendingCodeLanguageTag != null && isStrongCodeLikeChunk(next)) return true
    return isStrongCodeLikeChunk(next)
}

private fun codeLaneReason(next: String, context: StreamingAppendContext?): String = when {
    context?.inFencedCodeBlock == true -> "fenced_block"
    next.trimStart().startsWith("```") -> "fenced_chunk"
    context?.pendingCodeLanguageTag != null && isStrongCodeLikeChunk(next) -> "language_tag_and_strong_code"
    isStrongCodeLikeChunk(next) -> "strong_code_chunk"
    else -> "unknown"
}

private fun shouldLeaveCodeLane(next: String, context: StreamingAppendContext): Boolean {
    if (context.inFencedCodeBlock) return false
    return isProseLikeChunk(next)
}


private fun isProseLikeChunk(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("```")) return false
    if (isStandaloneCodeLanguageTag(trimmed)) return false
    if (isStrongCodeLikeChunk(trimmed)) return false
    if (isCommandLikeCodeChunk(trimmed)) return false
    if (isCodeArtifactLikeChunk(trimmed)) return false

    val hasJapanese = JAPANESE_TEXT_REGEX.containsMatchIn(trimmed)
    val hasSentencePunctuation = JAPANESE_SENTENCE_PUNCTUATION.any { punctuation ->
        trimmed.contains(punctuation)
    }
    val isQuotedNaturalText = trimmed.length >= 3 &&
        ((trimmed.startsWith('"') && trimmed.endsWith('"')) ||
            (trimmed.startsWith('“') && trimmed.endsWith('”'))) &&
        trimmed.any { it.isLetter() }

    return hasJapanese || hasSentencePunctuation || isQuotedNaturalText
}

private fun isCommandLikeCodeChunk(text: String): Boolean =
    CODE_COMMAND_CHUNK_REGEX.containsMatchIn(text)

private fun isCodeArtifactLikeChunk(text: String): Boolean =
    CODE_ARTIFACT_CHUNK_REGEX.containsMatchIn(text)

private fun flushPendingCodeLanguageTagAsProse(
    builder: StringBuilder,
    context: StreamingAppendContext,
    appendTrace: ((String) -> Unit)? = null,
) {
    val pendingTag = context.pendingCodeLanguageTag ?: return
    val join = if (shouldInsertMinimalJoinBetween(builder.toString(), pendingTag)) " " else ""
    if (join.isNotEmpty()) builder.append(join)
    builder.append(pendingTag)
    context.pendingCodeLanguageTag = null
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "[code.flushLanguageTag.asProse]")
    }
}

private val STREAMING_CODE_LANGUAGE_TAGS = setOf("python", "kotlin", "bash", "json")
private val FENCED_MARKER_REGEX = Regex("```")
private val FENCED_PYTHON_ASSIGNMENT_STARTER_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*\\s*(?:[+\\-*/%:]?=)")
private val FENCED_PYTHON_STRONG_STARTERS = listOf(
    "import ",
    "from ",
    "class ",
    "def ",
    "if ",
    "elif ",
    "else:",
    "for ",
    "while ",
    "try:",
    "except",
    "finally:",
    "with ",
    "return ",
    "raise ",
    "yield ",
    "async def ",
    "async for ",
    "async with ",
)

private fun preSplitFencedPythonChunk(context: StreamingAppendContext, raw: String): List<String> {
    if (!isFencedPythonCodeContext(context)) return listOf(raw)
    if (raw.isEmpty() || raw.contains('\n')) return listOf(raw)
    if (isFenceBoundaryChunk(raw)) return listOf(raw)
    val pendingLine = context.pendingCodeLineBuffer?.toString().orEmpty()
    if (pendingLine.isNotEmpty() && (isQuoteOrBracketCarryOverLine(pendingLine) || isFencedPythonCommentCarryOverLine(context, pendingLine))) {
        return listOf(raw)
    }
    return splitFencedPythonChunkSequentially(raw)
}

private fun splitFencedPythonChunkSequentially(raw: String): List<String> {
    val chunks = mutableListOf<String>()
    var remainder = raw
    while (remainder.isNotEmpty()) {
        val splitIndex = findNextFencedPythonSplitIndex(remainder)
        if (splitIndex == null) {
            chunks += remainder
            break
        }
        if (splitIndex !in 1 until remainder.length) {
            chunks += remainder
            break
        }
        chunks += remainder.substring(0, splitIndex)
        remainder = remainder.substring(splitIndex)
    }
    return chunks.filter { it.isNotEmpty() }
}

private fun findNextFencedPythonSplitIndex(text: String): Int? {
    for (index in 1 until text.length) {
        if (shouldSplitAtFencedPythonIndex(text, index)) return index
    }
    return null
}

private fun shouldSplitAtFencedPythonIndex(raw: String, index: Int): Boolean {
    if (index !in 1 until raw.length) return false
    if (isInsideQuotedString(raw, index)) return false
    if (isInsidePythonComment(raw, index)) return isFencedPythonCommentTailBoundaryAt(raw, index)
    if (hasUnclosedBrackets(raw.substring(0, index))) return false
    if (isFencedPythonClosingBracketTailBoundaryAt(raw, index)) return true
    if (isFencedPythonImportTailBoundaryAt(raw, index)) return true
    if (isFencedPythonTailToStrongStarterBoundaryAt(raw, index)) return true
    if (isFencedPythonNumericLiteralTailBoundaryAt(raw, index)) return true
    return isFencedPythonLiteralToAssignmentBoundaryAt(raw, index)
}

private fun isFencedPythonTailToStrongStarterBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isFencedPythonStrongStarterAt(text, index)) return false
    val before = text[index - 1]
    if (before == '\n' || before.isWhitespace() || before == '#') return false
    return true
}

private fun isFencedPythonIdentifierToStrongStarterBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val before = text[index - 1]
    if (!isIdentifierPart(before)) return false
    if (!isIdentifierStart(text[index])) return false
    if (isFencedPythonStrongStarterAt(text, index)) return true
    return isFencedPythonAssignmentTargetListStarterAt(text, index)
}

private fun isFencedPythonLiteralToAssignmentBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isFencedPythonAssignmentTargetListStarterAt(text, index)) return false
    val before = text[index - 1]
    return before.isDigit() || before in listOf(']', ')', '}', '"', '\'')
}

private fun isFencedPythonImportTailBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("import ") && !trimmed.startsWith("from ")) return false

    if (text.regionMatches(index, "import ", 0, "import ".length) ||
        text.regionMatches(index, "from ", 0, "from ".length)
    ) {
        val before = text[index - 1]
        if (!isAsciiIdentifierPart(before) && before != ')' && before != ']' && before != '}') return false
        val previousWord = text.substring(0, index).trimEnd().takeLastWhile { isAsciiIdentifierPart(it) }
        if (previousWord == "as") return false
        return true
    }

    val before = text[index - 1]
    if (!before.isWhitespace()) return false
    if (!isAsciiIdentifierStart(text[index]) && !isFencedPythonStrongStarterAt(text, index)) return false

    if (trimmed.startsWith("import ")) {
        val importTokenEnd = text.indexOf("import ") + "import ".length
        if (index <= importTokenEnd) return false
        val previousWord = text.substring(0, index).trimEnd().takeLastWhile { isAsciiIdentifierPart(it) }
        if (previousWord == "as") return false
        return true
    }

    val importIndex = text.indexOf(" import ")
    if (importIndex < 0 || index <= importIndex + " import ".length) return false
    val previousWord = text.substring(0, index).trimEnd().takeLastWhile { isAsciiIdentifierPart(it) }
    if (previousWord == "as") return false
    return isAsciiIdentifierStart(text[index]) || isFencedPythonStrongStarterAt(text, index)
}

private fun isFencedPythonNumericLiteralTailBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isIdentifierStart(text[index])) return false
    val before = text[index - 1]
    if (!before.isDigit() && before !in listOf(']', ')', '}')) return false
    return true
}

private fun isFencedPythonStrongStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val before = text[index - 1]
    val prevPrev = text.getOrNull(index - 2)
    val hasWordBoundary = before == '\n' || (!isAsciiIdentifierPart(before)) || (before.isLowerCase() && text[index].isUpperCase())
    if (!hasWordBoundary) return false
    if (prevPrev == '#' || before == '#') return false
    return matchesFencedPythonStrongStarterAt(text, index)
}

private fun isFencedPythonAssignmentStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    return isFencedPythonAssignmentTargetListStarterAt(text, index)
}

private fun isFencedPythonAssignmentTargetListStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isIdentifierStart(text[index])) return false
    val before = text[index - 1]
    if (before == '#') return false
    val boundary = before == '\n' || !isIdentifierPart(before) || (text[index].isUpperCase() && (before.isLowerCase() || before.isDigit()))
    if (!boundary) return false
    var cursor = index
    while (true) {
        if (cursor >= text.length || !isIdentifierStart(text[cursor])) return false
        cursor += 1
        while (cursor < text.length && isIdentifierPart(text[cursor])) cursor += 1
        while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
        if (cursor < text.length && text[cursor] == ',') {
            cursor += 1
            while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
            continue
        }
        break
    }
    while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
    if (cursor >= text.length) return false
    if (text[cursor] == '=') return text.getOrNull(cursor + 1) != '='
    if (cursor + 1 >= text.length) return false
    val op = text[cursor]
    val eq = text[cursor + 1]
    return op in charArrayOf('+', '-', '*', '/', '%', ':') && eq == '='
}

private fun isFencedPythonCommentTailBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val before = text[index - 1]
    if (before == '\n') return false
    if (!isInsidePythonComment(text, index)) return false
    if (text[index] == '#') return true
    if (isAsciiAssignmentStarterAt(text, index)) return true
    return isCommentStrongStarterAt(text, index)
}

private fun isFencedPythonClosingBracketTailBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val before = text[index - 1]
    if (before !in listOf(')', ']', '}')) return false
    if (isCommentStarterAt(text, index)) return true
    if (isFencedPythonStrongStarterAt(text, index)) return true
    return isAsciiAssignmentStarterAt(text, index)
}

private fun isFencedPythonClassOrDefStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    return text.regionMatches(index, "class ", 0, "class ".length) ||
        text.regionMatches(index, "def ", 0, "def ".length)
}

private fun isInsideQuotedString(text: String, targetIndex: Int): Boolean {
    if (targetIndex <= 0 || targetIndex >= text.length) return false
    return hasUnclosedQuotedString(text.substring(0, targetIndex))
}

private fun isInsidePythonComment(text: String, targetIndex: Int): Boolean {
    if (targetIndex <= 0 || targetIndex > text.length) return false
    var inSingleQuote = false
    var inDoubleQuote = false
    var escaped = false
    var inComment = false
    for (i in 0 until targetIndex) {
        val ch = text[i]
        if (inComment) {
            if (ch == '\n') inComment = false
            continue
        }
        if (escaped) {
            escaped = false
            continue
        }
        when (ch) {
            '\\' -> if (inSingleQuote || inDoubleQuote) escaped = true
            '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
            '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
            '#' -> if (!inSingleQuote && !inDoubleQuote) inComment = true
        }
    }
    return inComment
}

private fun matchesFencedPythonStrongStarterAt(
    text: String,
    index: Int,
    requireBoundary: Boolean = true,
): Boolean {
    return FENCED_PYTHON_STRONG_STARTERS.any { keyword ->
        if (!text.regionMatches(index, keyword, 0, keyword.length)) return@any false
        if (!requireBoundary) return@any true
        val before = text.getOrNull(index - 1) ?: return@any true
        before == '\n' || !isAsciiIdentifierPart(before) || (before.isLowerCase() && text[index].isUpperCase())
    }
}

private fun isIdentifierStart(ch: Char): Boolean = ch == '_' || ch.isLetter()

private fun isIdentifierPart(ch: Char): Boolean = isIdentifierStart(ch) || ch.isDigit()

private fun isAsciiIdentifierPart(ch: Char): Boolean = ch == '_' || ch.isDigit() || ch in 'a'..'z' || ch in 'A'..'Z'

private fun isAsciiIdentifierStart(ch: Char): Boolean = ch == '_' || ch in 'a'..'z' || ch in 'A'..'Z'

private fun isCommentStrongStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    return text.regionMatches(index, "class ", 0, "class ".length) ||
        text.regionMatches(index, "def ", 0, "def ".length) ||
        text.regionMatches(index, "import ", 0, "import ".length) ||
        text.regionMatches(index, "from ", 0, "from ".length)
}

private fun isCommentStarterAt(text: String, index: Int): Boolean =
    index in 1 until text.length && text[index] == '#'

private fun isUpperSnakeAssignmentStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!text[index].isUpperCase()) return false
    var cursor = index
    while (cursor < text.length && (text[cursor].isUpperCase() || text[cursor].isDigit() || text[cursor] == '_')) {
        cursor += 1
    }
    if (cursor == index) return false
    while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
    if (cursor >= text.length) return false
    if (text[cursor] == '=') return text.getOrNull(cursor + 1) != '='
    if (cursor + 1 >= text.length) return false
    val op = text[cursor]
    val eq = text[cursor + 1]
    return op in charArrayOf('+', '-', '*', '/', '%', ':') && eq == '='
}

private fun isAsciiAssignmentStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isAsciiIdentifierStart(text[index])) return false
    var cursor = index + 1
    while (cursor < text.length && isAsciiIdentifierPart(text[cursor])) cursor += 1
    while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
    if (cursor >= text.length) return false
    if (text[cursor] == '=') return text.getOrNull(cursor + 1) != '='
    if (cursor + 1 >= text.length) return false
    val op = text[cursor]
    val eq = text[cursor + 1]
    return op in charArrayOf('+', '-', '*', '/', '%', ':') && eq == '='
}

private fun isStandaloneLanguageTag(text: String): Boolean {
    val normalized = text.trim()
    return normalized in STREAMING_CODE_LANGUAGE_TAGS
}

private fun isStandaloneCodeLanguageTag(text: String): Boolean = isStandaloneLanguageTag(text)

private fun isStrongCodeLineStart(text: String): Boolean {
    val trimmedStart = text.trimStart()
    if (trimmedStart.isEmpty()) return false
    val lower = trimmedStart.lowercase(Locale.ROOT)
    val keywords = listOf(
        "import ",
        "from ",
        "def ",
        "class ",
        "for ",
        "while ",
        "return ",
        "print(",
        "if ",
        "elif ",
        "else",
        "try",
        "except",
    )
    if (keywords.any { lower.startsWith(it) }) return true
    val assignmentPattern = Regex("^[A-Za-z_][A-Za-z0-9_\\.\\[\\]]*\\s*=.+")
    return assignmentPattern.containsMatchIn(trimmedStart)
}

private fun isStrongCodeLikeChunk(text: String): Boolean =
    isStrongCodeLineStart(text) || text.startsWith("    ")

private fun shouldCommitPendingCodeLine(
    context: StreamingAppendContext,
    pendingLine: String,
    nextChunk: String?,
): Boolean {
    if (pendingLine.isEmpty()) return false
    if (pendingLine.contains('\n')) return true
    if (nextChunk == null) {
        return !shouldHoldPendingCodeLine(pendingLine) &&
            !isFencedPythonCommentCarryOverLine(context, pendingLine)
    }
    if (nextChunk.startsWith("```")) return true
    if (shouldKeepPythonCommentOnSameLogicalLine(context, pendingLine, nextChunk)) return false
    if (shouldAppendToCurrentCodeLine(pendingLine, nextChunk)) return false
    if (!isStrongCodeLikeChunk(nextChunk)) return false
    if (pendingLine.endsWith(" ") || pendingLine.endsWith("(") || pendingLine.endsWith("=")) return false
    return pendingLine.trimStart().startsWith("{") ||
        pendingLine.trimStart().startsWith("}") ||
        pendingLine.trimEnd().endsWith(":") ||
        isStrongCodeLikeChunk(pendingLine)
}

private fun shouldKeepPythonCommentOnSameLogicalLine(
    context: StreamingAppendContext,
    pendingLine: String,
    nextChunk: String,
): Boolean {
    if (!isFencedPythonCommentCarryOverLine(context, pendingLine)) return false
    if (nextChunk.isEmpty() || nextChunk.contains('\n')) return false
    if (nextChunk.trimStart().startsWith("```")) return false
    return !isFencedPythonLogicalLineStarter(nextChunk)
}

private fun isFencedPythonCommentCarryOverLine(
    context: StreamingAppendContext,
    pendingLine: String,
): Boolean {
    if (!isFencedPythonCodeContext(context)) return false
    if (pendingLine.contains('\n')) return false
    if (isQuoteOrBracketCarryOverLine(pendingLine)) return false
    return findPythonCommentHashIndex(pendingLine) >= 0
}

private fun findPythonCommentHashIndex(line: String): Int {
    var inSingleQuote = false
    var inDoubleQuote = false
    var escaped = false
    line.forEachIndexed { index, ch ->
        if (escaped) {
            escaped = false
            return@forEachIndexed
        }
        when (ch) {
            '\\' -> if (inSingleQuote || inDoubleQuote) escaped = true
            '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
            '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
            '#' -> if (!inSingleQuote && !inDoubleQuote) return index
        }
    }
    return -1
}

private fun shouldStartNewFencedPythonLogicalLine(
    context: StreamingAppendContext,
    pendingLine: String,
    nextChunk: String,
): Boolean {
    if (!isFencedPythonCodeContext(context)) return false
    if (pendingLine.isEmpty() || nextChunk.isEmpty()) return false
    if (nextChunk.trimStart().startsWith("```")) return false
    if (shouldKeepPythonCommentOnSameLogicalLine(context, pendingLine, nextChunk)) return false
    if (shouldCommitAfterFencedPythonComment(context, pendingLine, nextChunk)) return true
    if (!isFencedPythonLogicalLineStarter(nextChunk)) return false
    if (shouldAppendToCurrentCodeLine(pendingLine, nextChunk)) return false
    return !isQuoteOrBracketCarryOverLine(pendingLine)
}

private fun shouldCommitAfterFencedPythonComment(
    context: StreamingAppendContext,
    pendingLine: String,
    nextChunk: String,
): Boolean {
    if (!isFencedPythonCodeContext(context)) return false
    if (!isFencedPythonCommentLine(pendingLine)) return false
    if (pendingLine.contains('\n')) return false
    if (nextChunk.isEmpty() || nextChunk.contains('\n')) return false
    if (nextChunk.trimStart().startsWith("```")) return false
    if (isQuoteOrBracketCarryOverLine(pendingLine)) return false
    if (shouldAppendToCurrentCodeLine(pendingLine, nextChunk)) return false
    return isFencedPythonAssignmentStarter(nextChunk) || isFencedPythonClassOrDefStarter(nextChunk)
}

private fun isFencedPythonCommentLine(line: String): Boolean = line.trimStart().startsWith("#")

private fun isFencedPythonAssignmentStarter(chunk: String): Boolean {
    val trimmedStart = chunk.trimStart()
    return FENCED_PYTHON_ASSIGNMENT_STARTER_REGEX.containsMatchIn(trimmedStart)
}

private fun isFencedPythonClassOrDefStarter(chunk: String): Boolean {
    val trimmedStart = chunk.trimStart()
    return trimmedStart.startsWith("class ") || trimmedStart.startsWith("def ")
}

private fun isFencedPythonCodeContext(context: StreamingAppendContext): Boolean {
    if (!context.inFencedCodeBlock) return false
    val normalized = context.fencedCodeLanguageTag?.trim()?.lowercase(Locale.ROOT) ?: return false
    return normalized == "python" || normalized == "py"
}

private fun isStrongFencedPythonLogicalLineStarter(chunk: String): Boolean {
    val trimmed = chunk.trimStart()
    if (trimmed.isEmpty()) return false
    val strongKeywords = listOf(
        "import",
        "from",
        "class",
        "def",
        "if",
        "elif",
        "else",
        "for",
        "while",
        "try",
        "except",
        "finally",
        "with",
        "return",
        "raise",
        "yield",
    )
    return strongKeywords.any { matchesFencedPythonStarterKeyword(trimmed, it) }
}

private fun isFencedPythonLogicalLineStarter(chunk: String): Boolean = isStrongFencedPythonLogicalLineStarter(chunk)

private fun matchesFencedPythonStarterKeyword(text: String, keyword: String): Boolean {
    if (!text.startsWith(keyword)) return false
    if (text.length == keyword.length) return true
    val next = text[keyword.length]
    return next.isWhitespace() || next == ':'
}

private fun isQuoteOrBracketCarryOverLine(pendingLine: String): Boolean {
    val trimmedEnd = pendingLine.trimEnd()
    if (trimmedEnd.isEmpty()) return false
    if (hasUnclosedQuotedString(trimmedEnd)) return true
    if (hasUnclosedBrackets(trimmedEnd)) return true
    return trimmedEnd.endsWith(",") ||
        trimmedEnd.endsWith("(") ||
        trimmedEnd.endsWith("[") ||
        trimmedEnd.endsWith("{")
}

private fun extractFencedCodeLanguageTag(chunk: String): String? {
    val trimmed = chunk.trim()
    if (!trimmed.startsWith("```")) return null
    val markerTail = trimmed.removePrefix("```").trim()
    if (markerTail.isEmpty()) return null
    return markerTail.lineSequence().first().trim().ifEmpty { null }
}

private fun shouldHoldPendingCodeLine(pendingLine: String): Boolean {
    val trimmedEnd = pendingLine.trimEnd()
    if (trimmedEnd.isEmpty()) return false
    if (isQuoteOrBracketContinuationLine(pendingLine)) return true
    return trimmedEnd.endsWith("(") ||
        trimmedEnd.endsWith("=") ||
        trimmedEnd.endsWith(":") ||
        trimmedEnd.matches(Regex(".*[A-Za-z0-9_\\]\"')]$"))
}

private fun shouldAppendToCurrentCodeLine(
    pendingLine: String,
    nextChunk: String,
): Boolean {
    if (nextChunk.isEmpty()) return true
    if (nextChunk.contains('\n')) return false
    if (nextChunk.trimStart().startsWith("```")) return false

    val previous = pendingLine.trimEnd()
    if (previous.isEmpty()) return true
    val nextTrimmedStart = nextChunk.trimStart()
    if (nextTrimmedStart.isEmpty()) return true

    if (nextChunk.first().isWhitespace()) {
        return isQuoteOrBracketContinuationLine(pendingLine)
    }

    val previousLast = previous.last()
    val nextFirst = nextTrimmedStart.first()

    val inlinePair = (previousLast.isLetterOrDigit() || previousLast == '_') &&
        (nextFirst == '(' || nextFirst == ',' || nextFirst == ')' || nextFirst == '!' || nextFirst == ']' || nextFirst == '}')
    if (inlinePair) return true

    if ((previousLast == '(' || previousLast == '[' || previousLast == '{') &&
        (nextFirst.isLetterOrDigit() || nextFirst == '"' || nextFirst == '\'')
    ) return true

    if ((previousLast == '"' || previousLast == '\'') && (nextFirst == '"' || nextFirst == '\'' || nextFirst.isLetterOrDigit())) {
        return true
    }

    if (previousLast == ',' && nextChunk.first().isWhitespace()) return true

    return false
}

private fun isQuoteOrBracketContinuationLine(pendingLine: String): Boolean {
    val trimmedEnd = pendingLine.trimEnd()
    if (trimmedEnd.isEmpty()) return false
    if (hasUnclosedQuotedString(trimmedEnd)) return true
    if (hasUnclosedBrackets(trimmedEnd)) return true
    if (trimmedEnd.endsWith(",")) return true
    if (trimmedEnd.endsWith("(") || trimmedEnd.endsWith("[") || trimmedEnd.endsWith("{")) return true
    return trimmedEnd.endsWith(".") ||
        trimmedEnd.endsWith("=") ||
        trimmedEnd.endsWith(":") ||
        trimmedEnd.matches(Regex(".*[+\\-*/%&|^<>!]$"))
}

private fun hasUnclosedQuotedString(text: String): Boolean {
    var index = 0
    var single = false
    var double = false
    var tripleSingle = false
    var tripleDouble = false
    while (index < text.length) {
        if (tripleSingle) {
            if (text.startsWith("'''", index)) {
                tripleSingle = false
                index += 3
                continue
            }
            index += 1
            continue
        }
        if (tripleDouble) {
            if (text.startsWith("\"\"\"", index)) {
                tripleDouble = false
                index += 3
                continue
            }
            index += 1
            continue
        }
        val ch = text[index]
        val escaped = index > 0 && text[index - 1] == '\\' && (index < 2 || text[index - 2] != '\\')
        if (ch == '\'' && !double && !escaped) {
            if (!single && text.startsWith("'''", index)) {
                tripleSingle = true
                index += 3
                continue
            }
            single = !single
            index += 1
            continue
        }
        if (ch == '"' && !single && !escaped) {
            if (!double && text.startsWith("\"\"\"", index)) {
                tripleDouble = true
                index += 3
                continue
            }
            double = !double
            index += 1
            continue
        }
        index += 1
    }
    return single || double || tripleSingle || tripleDouble
}

private fun hasUnclosedBrackets(text: String): Boolean {
    var round = 0
    var square = 0
    var curly = 0
    var index = 0
    var single = false
    var double = false
    var tripleSingle = false
    var tripleDouble = false
    while (index < text.length) {
        if (tripleSingle) {
            if (text.startsWith("'''", index)) {
                tripleSingle = false
                index += 3
                continue
            }
            index += 1
            continue
        }
        if (tripleDouble) {
            if (text.startsWith("\"\"\"", index)) {
                tripleDouble = false
                index += 3
                continue
            }
            index += 1
            continue
        }
        val ch = text[index]
        val escaped = index > 0 && text[index - 1] == '\\' && (index < 2 || text[index - 2] != '\\')
        if (ch == '\'' && !double && !escaped) {
            if (!single && text.startsWith("'''", index)) {
                tripleSingle = true
                index += 3
                continue
            }
            single = !single
            index += 1
            continue
        }
        if (ch == '"' && !single && !escaped) {
            if (!double && text.startsWith("\"\"\"", index)) {
                tripleDouble = true
                index += 3
                continue
            }
            double = !double
            index += 1
            continue
        }
        if (single || double) {
            index += 1
            continue
        }
        when (ch) {
            '(' -> round += 1
            ')' -> round = (round - 1).coerceAtLeast(0)
            '[' -> square += 1
            ']' -> square = (square - 1).coerceAtLeast(0)
            '{' -> curly += 1
            '}' -> curly = (curly - 1).coerceAtLeast(0)
        }
        index += 1
    }
    return round > 0 || square > 0 || curly > 0
}

private fun commitPendingCodeLine(
    builder: StringBuilder,
    context: StreamingAppendContext,
    appendTrace: ((String) -> Unit)? = null,
) {
    val pending = context.pendingCodeLineBuffer?.toString().orEmpty()
    if (pending.isEmpty()) return
    if (builder.isNotEmpty() && builder.last() != '\n' && !pending.startsWith("\n")) {
        builder.append('\n')
    }
    builder.append(pending)
    context.lastCodeChunkEndedWithNewline = pending.endsWith('\n')
    context.pendingCodeLineBuffer = null
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "[code.commit]=${summarizeWhitespaceForUi(pending)}")
    }
}


private val JAPANESE_TEXT_REGEX = Regex("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]")
private val JAPANESE_SENTENCE_PUNCTUATION = listOf("。", "、", "！", "？")
private val CODE_COMMAND_CHUNK_REGEX = Regex("^(python|python3|bash|sh|node|ruby|java|kotlinc)\\b", RegexOption.IGNORE_CASE)
private val CODE_ARTIFACT_CHUNK_REGEX = Regex("^[A-Za-z0-9_./-]+\\.(py|kt|kts|sh|json|yaml|yml|xml|txt|md)$", RegexOption.IGNORE_CASE)

private val STREAMING_STRONG_CODE_SIGNALS = listOf(
    "import ",
    "from ",
    "def ",
    "class ",
    "return",
    "print(",
    "=",
    "self.",
)

private fun Char.isAsciiWordLike(): Boolean =
    (this in 'a'..'z') || (this in 'A'..'Z') || (this in '0'..'9') || this == '_' || this == '-'

private fun isLikelyCodeJoinContext(previous: String, next: String): Boolean {
    val previousTail = previous.takeLast(48)
    val nextHead = next.take(48)
    val lowerPreviousTail = previousTail.lowercase(Locale.ROOT)
    val lowerNextHead = nextHead.lowercase(Locale.ROOT)
    val codeHints = listOf(
        "print(",
        "def ",
        "class ",
        "import ",
        "from ",
        "return ",
        "if ",
        "for ",
        "while ",
        "grid_",
        "self.",
        "np.",
        "```",
        "=",
        "):",
        "->",
    )
    return codeHints.any { hint ->
        lowerPreviousTail.contains(hint) || lowerNextHead.contains(hint)
    }
}

private fun buildRunnerWhitespaceTraceBlock(
    stages: List<Pair<String, String?>>,
): String? {
    if (!BuildConfig.DEBUG || stages.isEmpty()) return null
    return buildString {
        appendLine("=== RUNNER WS TRACE ===")
        stages.forEach { (stage, text) ->
            append('[').append(stage).appendLine("]")
            appendLine(summarizeWhitespaceForUi(text))
            appendLine()
        }
    }
}

private fun summarizeWhitespaceForUi(text: String?): String {
    if (text == null) return "len=null\nspaces=0\nnewlines=0\ntabs=0\ntext=\"null\""
    val spaces = text.count { it == ' ' }
    val newlines = text.count { it == '\n' }
    val tabs = text.count { it == '\t' }
    val visualized = text
        .replace(" ", "␠")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
    val limited = if (visualized.length > 1200) {
        visualized.take(1200) + "…(truncated)"
    } else {
        visualized
    }
    return "len=${text.length}\nspaces=$spaces\nnewlines=$newlines\ntabs=$tabs\ntext=\"$limited\""
}

private fun buildWhitespaceDeltaForDebug(raw: String?, normalized: String?): String {
    if (raw == null || normalized == null) return "n/a"
    return "len=${raw.length - normalized.length},spaces=${raw.count { it == ' ' } - normalized.count { it == ' ' }},newlines=${raw.count { it == '\n' } - normalized.count { it == '\n' }}"
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
