package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import io.github.ninbyo02.lami.npu.Qairt244NpuOutputSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalApi::class)
internal class NpuS1PersistentEngineDevProbe(
    context: Context,
) : NpuS1PersistentEngineProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(
        onUpdate: (NpuS1PersistentEngineProbeState) -> Unit,
        isCancelled: () -> Boolean,
    ): NpuS1PersistentEngineProbeState = withContext(Dispatchers.Default) {
        val requestedRunCount = NPU_S1_PERSISTENT_ENGINE_DEFAULT_COUNT
        val cacheDir = appContext.cacheDir.absolutePath
        val startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path
        val prompt = buildPersistentPrompt()
        var state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_RUNNING,
            runCountRequested = requestedRunCount,
            startedAtElapsedRealtimeMs = startedAtElapsedRealtimeMs,
            modelPathOrName = modelPath ?: modelResolution.reasonCode,
            cacheDir = cacheDir,
            backendEvidence = "official_litertlm_backend_npu_requested",
            persistentEngineHypothesisResult = "running",
            promptTextLengthChars = prompt.length,
            requestedMaxOutputTokens = NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS,
            officialTotalTokenLimit = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
            officialOutputTokenLimit = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_OUTPUT_TOKEN_LIMIT,
            tokenLimitSource = NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_SOURCE,
            persistentEngineApiMode = NPU_S1_PERSISTENT_ENGINE_API_MODE_AUTO,
            attemptedApiModes = NPU_S1_PERSISTENT_ENGINE_API_MODE_SESSION,
            selectedApiMode = NPU_S1_PERSISTENT_ENGINE_API_MODE_SESSION,
            apiModeSelectionReason = "session_api_available_prefers_generate_content",
            sessionApiAvailable = "true",
            sessionApiUsed = "true",
            conversationApiUsed = "false",
            streamingApiUsed = "false",
        )
        fun update(next: NpuS1PersistentEngineProbeState) {
            state = next
            onUpdate(next)
        }
        update(state)

        if (modelPath.isNullOrBlank()) {
            return@withContext stopBeforeRun(
                state = state,
                stage = "model_resolve",
                reason = modelResolution.reasonCode,
                throwable = null,
                hypothesisResult = "engine_initialize_once_failed",
            ).also(::update)
        }

        var engine: Engine? = null
        var engineCloseReached = "false"
        var engineCloseSuccess = "unavailable"
        try {
            val engineInitializeStartedAt = SystemClock.elapsedRealtime()
            update(
                state.copy(
                    engineInitializeCount = 1,
                    engineInitializeStartedAtElapsedRealtimeMs = engineInitializeStartedAt,
                    backendEvidence = "official_litertlm_backend_npu_before_initialize",
                ),
            )
            engine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.NPU(appContext.applicationInfo.nativeLibraryDir),
                    visionBackend = Backend.GPU(),
                    audioBackend = Backend.CPU(),
                    maxNumTokens = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
                    cacheDir = cacheDir,
                ),
            )
            engine.initialize()
            val engineInitializeFinishedAt = SystemClock.elapsedRealtime()
            update(
                state.copy(
                    engineInitializeFinishedAtElapsedRealtimeMs = engineInitializeFinishedAt,
                    engineInitializeDurationMs = engineInitializeFinishedAt - engineInitializeStartedAt,
                    backendEvidence = "official_litertlm_backend_npu_engine_initialized",
                ),
            )

            val records = mutableListOf<NpuS1PersistentEngineRunRecord>()
            var conversationCreateCount = 0
            var conversationCloseCount = 0
            var sessionCreateCount = 0
            var sessionCloseCount = 0
            for (runIndex in 1..requestedRunCount) {
                if (isCancelled()) {
                    return@withContext state.copy(
                        persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_CANCELLED,
                        finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        records = records.toList(),
                        conversationCreateCount = conversationCreateCount,
                        conversationCloseCount = conversationCloseCount,
                        sessionCreateCount = sessionCreateCount.toString(),
                        sessionCloseCount = sessionCloseCount.toString(),
                        persistentEngineHypothesisResult = "cancelled",
                    ).also(::update)
                }
                val memoryBefore = captureLocalMemorySnapshot(
                    context = appContext,
                    stage = "npu_s1_persistent_engine_run_${runIndex}_before",
                )
                if (memoryBefore.lowMemory == true) {
                    records += failureRecord(
                        runIndex = runIndex,
                        stage = "low_memory",
                        reason = "low_memory_before_run",
                        throwable = null,
                        backendEvidence = state.backendEvidence,
                        promptLength = prompt.length,
                    )
                    return@withContext state.copy(
                        persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
                        finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        records = records.toList(),
                        conversationCreateCount = conversationCreateCount,
                        conversationCloseCount = conversationCloseCount,
                        sessionCreateCount = sessionCreateCount.toString(),
                        sessionCloseCount = sessionCloseCount.toString(),
                        firstFailureRunIndex = runIndex,
                        firstFailureStage = "low_memory",
                        firstFailureReason = "low_memory_before_run",
                        persistentEngineHypothesisResult = "low_memory_before_run",
                    ).also(::update)
                }
                val record = runOneSessionGenerateContent(
                    engine = engine,
                    runIndex = runIndex,
                    prompt = prompt,
                    backendEvidence = state.backendEvidence,
                    onSessionCreated = { sessionCreateCount += 1 },
                    onSessionClosed = { sessionCloseCount += 1 },
                )
                records += record
                val failed = record.status != NpuStandardRouteS1Contract.STATUS_SUCCESS
                val hypothesisResult = if (failed) {
                    npuS1PersistentHypothesisResultForFailureMessage(
                        stage = record.failureStage,
                        message = record.failureExceptionMessage,
                    )
                } else {
                    "running"
                }
                update(
                    state.copy(
                        persistentProbeStatus = if (failed) {
                            NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED
                        } else {
                            NPU_S1_PERSISTENT_ENGINE_STATUS_RUNNING
                        },
                        records = records.toList(),
                        conversationCreateCount = conversationCreateCount,
                        conversationCloseCount = conversationCloseCount,
                        sessionCreateCount = sessionCreateCount.toString(),
                        sessionCloseCount = sessionCloseCount.toString(),
                        firstFailureRunIndex = if (failed) record.runIndex else state.firstFailureRunIndex,
                        firstFailureStage = if (failed) record.failureStage else state.firstFailureStage,
                        firstFailureReason = if (failed) record.reason else state.firstFailureReason,
                        firstFailureExceptionClass = if (failed) record.failureExceptionClass else state.firstFailureExceptionClass,
                        firstFailureExceptionMessage = if (failed) record.failureExceptionMessage else state.firstFailureExceptionMessage,
                        firstFailureTokenLimitMessage = if (failed && record.tokenLimitFailureDetected == "true") {
                            record.tokenLimitFailureMessage
                        } else {
                            state.firstFailureTokenLimitMessage
                        },
                        logitsOutputRequired = if (failed && record.logitsFailureDetected == "true") {
                            "true"
                        } else {
                            state.logitsOutputRequired
                        },
                        logitsOutputBackendSupported = if (failed && record.logitsFailureDetected == "true") {
                            "false"
                        } else {
                            state.logitsOutputBackendSupported
                        },
                        logitsFailureDetected = if (failed && record.logitsFailureDetected == "true") {
                            "true"
                        } else {
                            state.logitsFailureDetected
                        },
                        logitsFailureMessage = if (failed && record.logitsFailureDetected == "true") {
                            record.logitsFailureMessage
                        } else {
                            state.logitsFailureMessage
                        },
                        persistentEngineHypothesisResult = hypothesisResult,
                    ),
                )
                if (failed) return@withContext state
            }
            state.copy(
                persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_COMPLETED,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                records = records.toList(),
                conversationCreateCount = conversationCreateCount,
                conversationCloseCount = conversationCloseCount,
                sessionCreateCount = sessionCreateCount.toString(),
                sessionCloseCount = sessionCloseCount.toString(),
                persistentEngineHypothesisResult = "engine_initialize_once_20_runs_success",
            ).also(::update)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException && throwable !is TimeoutCancellationException) {
                throw throwable
            }
            stopBeforeRun(
                state = state,
                stage = "engine_initialize",
                reason = "engine_initialize_failed:${throwable.javaClass.simpleName}",
                throwable = throwable,
                hypothesisResult = "engine_initialize_once_failed",
            ).also(::update)
        } finally {
            engine?.let { closeTarget ->
                engineCloseReached = "true"
                engineCloseSuccess = runCatching {
                    closeTarget.close()
                    "true"
                }.getOrElse { throwable ->
                    val failedCloseState = state.copy(
                        persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
                        finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        engineCloseReached = "true",
                        engineCloseSuccess = "false",
                        firstFailureStage = if (state.firstFailureStage == "unavailable") {
                            "engine_close"
                        } else {
                            state.firstFailureStage
                        },
                        firstFailureReason = if (state.firstFailureReason == "unavailable") {
                            "engine_close_failed:${throwable.javaClass.simpleName}"
                        } else {
                            state.firstFailureReason
                        },
                        firstFailureExceptionClass = if (state.firstFailureExceptionClass == "unavailable") {
                            throwable.javaClass.simpleName
                        } else {
                            state.firstFailureExceptionClass
                        },
                        firstFailureExceptionMessage = if (state.firstFailureExceptionMessage == "unavailable") {
                            throwable.message ?: "unavailable"
                        } else {
                            state.firstFailureExceptionMessage
                        },
                        persistentEngineHypothesisResult = "engine_close_failed",
                    )
                    update(failedCloseState)
                    "false"
                }
            }
            if (state.engineCloseReached != engineCloseReached || state.engineCloseSuccess != engineCloseSuccess) {
                update(
                    state.copy(
                        finishedAtElapsedRealtimeMs = state.finishedAtElapsedRealtimeMs ?: SystemClock.elapsedRealtime(),
                        engineCloseReached = engineCloseReached,
                        engineCloseSuccess = engineCloseSuccess,
                    ),
                )
            }
        }
    }

    private suspend fun runOneConversation(
        engine: Engine,
        runIndex: Int,
        prompt: String,
        backendEvidence: String,
        onConversationCreated: () -> Unit,
        onConversationClosed: () -> Unit,
    ): NpuS1PersistentEngineRunRecord {
        val runStartedAt = SystemClock.elapsedRealtime()
        var conversation: Conversation? = null
        var conversationCreated = "false"
        var conversationClosed = "unavailable"
        var decodeStarted = "false"
        var decodeFinished = "false"
        return try {
            conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = PERSISTENT_SAMPLER_TOP_K,
                        topP = PERSISTENT_SAMPLER_TOP_P,
                        temperature = PERSISTENT_SAMPLER_TEMPERATURE,
                    ),
                ),
            )
            conversationCreated = "true"
            val activeConversation = conversation
            onConversationCreated()
            val rawOutputBuilder = StringBuilder()
            val decodeStartedAt = SystemClock.elapsedRealtime()
            decodeStarted = "true"
            withTimeout(DevOnlyNpuOneTurnConversationContract.TIMEOUT_MS) {
                activeConversation.sendMessageAsync(prompt).collect { message ->
                    val chunk = message.contents.toString()
                        .takeIf { it.isNotBlank() }
                        ?: message.toString().takeIf { it.isNotBlank() }
                    if (!chunk.isNullOrBlank()) {
                        rawOutputBuilder.append(chunk)
                    }
                }
            }
            decodeFinished = "true"
            val decodeFinishedAt = SystemClock.elapsedRealtime()
            val rawOutput = rawOutputBuilder.toString()
            val sanitized = Qairt244NpuOutputSanitizer.sanitize(
                rawOutput = rawOutput,
                prompt = NPU_S1_REPEATED_RUN_DEFAULT_PROMPT,
            ).sanitizedOutput.trim()
            NpuS1PersistentEngineRunRecord(
                runIndex = runIndex,
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                reason = "success",
                conversationCreated = conversationCreated,
                conversationClosed = conversationClosed,
                sessionCreated = "unavailable",
                sessionClosed = "unavailable",
                decodeStarted = decodeStarted,
                decodeFinished = decodeFinished,
                rawOutput = rawOutput,
                sanitizedOutput = sanitized,
                qualityClassification = if (sanitized.isBlank()) {
                    NpuStandardRouteS1Contract.REASON_EMPTY_AFTER_SANITIZE
                } else {
                    NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE
                },
                totalMs = decodeFinishedAt - runStartedAt,
                decodeMs = decodeFinishedAt - decodeStartedAt,
                failureStage = "unavailable",
                failureExceptionClass = "unavailable",
                failureExceptionMessage = "unavailable",
                nativeOrEngineDiagTail = "unavailable",
                backendEvidence = backendEvidence,
                promptTextLengthChars = prompt.length,
                requestedMaxOutputTokens = NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS,
                officialTotalTokenLimit = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
                officialOutputTokenLimit = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_OUTPUT_TOKEN_LIMIT,
                tokenLimitSource = NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_SOURCE,
                apiModeUsed = NPU_S1_PERSISTENT_ENGINE_API_MODE_CONVERSATION,
                streamingStarted = "false",
                streamingFinished = "false",
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException && throwable !is TimeoutCancellationException) {
                throw throwable
            }
            val failureMessage = throwable.message ?: ""
            val failureStage = npuS1PersistentFailureStage(
                conversationCreated = conversationCreated == "true",
                decodeStarted = decodeStarted == "true",
                message = failureMessage,
            )
            failureRecord(
                runIndex = runIndex,
                stage = failureStage,
                reason = "${npuS1PersistentHypothesisResultForFailureStage(failureStage)}:${throwable.javaClass.simpleName}",
                throwable = throwable,
                backendEvidence = backendEvidence,
                promptLength = prompt.length,
                conversationCreated = conversationCreated,
                conversationClosed = conversationClosed,
                decodeStarted = decodeStarted,
                decodeFinished = decodeFinished,
                totalMs = SystemClock.elapsedRealtime() - runStartedAt,
                apiModeUsed = NPU_S1_PERSISTENT_ENGINE_API_MODE_CONVERSATION,
                streamingStarted = "false",
                streamingFinished = "false",
            )
        } finally {
            conversation?.let { closeTarget ->
                conversationClosed = runCatching {
                    closeTarget.close()
                    onConversationClosed()
                    "true"
                }.getOrElse { "false" }
            }
        }.let { record ->
            if (record.conversationClosed == "unavailable" && conversationCreated == "true") {
                record.copy(conversationClosed = conversationClosed)
            } else {
                record
            }
        }
    }

    private suspend fun runOneSessionGenerateContent(
        engine: Engine,
        runIndex: Int,
        prompt: String,
        backendEvidence: String,
        onSessionCreated: () -> Unit,
        onSessionClosed: () -> Unit,
    ): NpuS1PersistentEngineRunRecord {
        val runStartedAt = SystemClock.elapsedRealtime()
        var session: Session? = null
        var sessionCreated = "false"
        var sessionClosed = "unavailable"
        var decodeStarted = "false"
        var decodeFinished = "false"
        return try {
            session = engine.createSession(
                SessionConfig(
                    samplerConfig = SamplerConfig(
                        topK = PERSISTENT_SAMPLER_TOP_K,
                        topP = PERSISTENT_SAMPLER_TOP_P,
                        temperature = PERSISTENT_SAMPLER_TEMPERATURE,
                    ),
                ),
            )
            sessionCreated = "true"
            val activeSession = session
            onSessionCreated()
            val decodeStartedAt = SystemClock.elapsedRealtime()
            decodeStarted = "true"
            val rawOutput = withTimeout(DevOnlyNpuOneTurnConversationContract.TIMEOUT_MS) {
                activeSession.generateContent(listOf(InputData.Text(prompt)))
            }
            decodeFinished = "true"
            val decodeFinishedAt = SystemClock.elapsedRealtime()
            val sanitized = Qairt244NpuOutputSanitizer.sanitize(
                rawOutput = rawOutput,
                prompt = NPU_S1_REPEATED_RUN_DEFAULT_PROMPT,
            ).sanitizedOutput.trim()
            NpuS1PersistentEngineRunRecord(
                runIndex = runIndex,
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                reason = "success",
                conversationCreated = "false",
                conversationClosed = "unavailable",
                sessionCreated = sessionCreated,
                sessionClosed = sessionClosed,
                decodeStarted = decodeStarted,
                decodeFinished = decodeFinished,
                rawOutput = rawOutput,
                sanitizedOutput = sanitized,
                qualityClassification = if (sanitized.isBlank()) {
                    NpuStandardRouteS1Contract.REASON_EMPTY_AFTER_SANITIZE
                } else {
                    NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE
                },
                totalMs = decodeFinishedAt - runStartedAt,
                decodeMs = decodeFinishedAt - decodeStartedAt,
                failureStage = "unavailable",
                failureExceptionClass = "unavailable",
                failureExceptionMessage = "unavailable",
                nativeOrEngineDiagTail = "unavailable",
                backendEvidence = backendEvidence,
                promptTextLengthChars = prompt.length,
                requestedMaxOutputTokens = NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS,
                officialTotalTokenLimit = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
                officialOutputTokenLimit = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_OUTPUT_TOKEN_LIMIT,
                tokenLimitSource = NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_SOURCE,
                apiModeUsed = NPU_S1_PERSISTENT_ENGINE_API_MODE_SESSION,
                streamingStarted = "false",
                streamingFinished = "false",
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException && throwable !is TimeoutCancellationException) {
                throw throwable
            }
            val failureMessage = throwable.message ?: ""
            val failureStage = npuS1PersistentFailureStage(
                conversationCreated = true,
                decodeStarted = decodeStarted == "true",
                message = failureMessage,
            )
            failureRecord(
                runIndex = runIndex,
                stage = failureStage,
                reason = "${npuS1PersistentHypothesisResultForFailureMessage(failureStage, failureMessage)}:${throwable.javaClass.simpleName}",
                throwable = throwable,
                backendEvidence = backendEvidence,
                promptLength = prompt.length,
                sessionCreated = sessionCreated,
                sessionClosed = sessionClosed,
                decodeStarted = decodeStarted,
                decodeFinished = decodeFinished,
                totalMs = SystemClock.elapsedRealtime() - runStartedAt,
                apiModeUsed = NPU_S1_PERSISTENT_ENGINE_API_MODE_SESSION,
                streamingStarted = "false",
                streamingFinished = "false",
            )
        } finally {
            session?.let { closeTarget ->
                sessionClosed = runCatching {
                    closeTarget.close()
                    onSessionClosed()
                    "true"
                }.getOrElse { "false" }
            }
        }.let { record ->
            if (record.sessionClosed == "unavailable" && sessionCreated == "true") {
                record.copy(sessionClosed = sessionClosed)
            } else {
                record
            }
        }
    }

    private fun stopBeforeRun(
        state: NpuS1PersistentEngineProbeState,
        stage: String,
        reason: String,
        throwable: Throwable?,
        hypothesisResult: String,
    ): NpuS1PersistentEngineProbeState =
        state.copy(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
            finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            firstFailureStage = stage,
            firstFailureReason = reason,
            firstFailureExceptionClass = throwable?.javaClass?.simpleName ?: "unavailable",
            firstFailureExceptionMessage = throwable?.message ?: "unavailable",
            backendEvidence = throwable?.causeChainTail() ?: state.backendEvidence,
            firstFailureTokenLimitMessage = throwable?.message
                ?.takeIf(::isNpuS1PersistentTokenLimitFailure)
                ?: state.firstFailureTokenLimitMessage,
            persistentEngineHypothesisResult = hypothesisResult,
        )

    private fun failureRecord(
        runIndex: Int,
        stage: String,
        reason: String,
        throwable: Throwable?,
        backendEvidence: String,
        promptLength: Int? = null,
        conversationCreated: String = "unavailable",
        conversationClosed: String = "unavailable",
        sessionCreated: String = "unavailable",
        sessionClosed: String = "unavailable",
        decodeStarted: String = "unavailable",
        decodeFinished: String = "unavailable",
        totalMs: Long? = null,
        apiModeUsed: String = "unavailable",
        streamingStarted: String = "unavailable",
        streamingFinished: String = "unavailable",
    ): NpuS1PersistentEngineRunRecord {
        val failureMessage = throwable?.message ?: reason
        val tokenLimitFailureDetected = isNpuS1PersistentTokenLimitFailure(failureMessage)
        val logitsFailureDetected = isNpuS1PersistentLogitsFailure(failureMessage)
        return NpuS1PersistentEngineRunRecord(
            runIndex = runIndex,
            status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
            reason = reason,
            conversationCreated = conversationCreated,
            conversationClosed = conversationClosed,
            sessionCreated = sessionCreated,
            sessionClosed = sessionClosed,
            decodeStarted = decodeStarted,
            decodeFinished = decodeFinished,
            totalMs = totalMs,
            failureStage = stage,
            failureExceptionClass = throwable?.javaClass?.simpleName ?: inferPersistentFailureExceptionClass(reason),
            failureExceptionMessage = throwable?.message ?: "unavailable",
            nativeOrEngineDiagTail = throwable?.causeChainTail() ?: reason,
            backendEvidence = backendEvidence,
            promptTextLengthChars = promptLength,
            requestedMaxOutputTokens = NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS,
            officialTotalTokenLimit = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
            officialOutputTokenLimit = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_OUTPUT_TOKEN_LIMIT,
            tokenLimitSource = NPU_S1_PERSISTENT_ENGINE_TOKEN_LIMIT_SOURCE,
            tokenLimitFailureDetected = tokenLimitFailureDetected.toString(),
            tokenLimitFailureMessage = if (tokenLimitFailureDetected) failureMessage else "unavailable",
            apiModeUsed = apiModeUsed,
            logitsFailureDetected = logitsFailureDetected.toString(),
            logitsFailureMessage = if (logitsFailureDetected) failureMessage else "unavailable",
            streamingStarted = streamingStarted,
            streamingFinished = streamingFinished,
        )
    }

    private fun buildPersistentPrompt(): String =
        DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = "",
            userPrompt = NPU_S1_REPEATED_RUN_DEFAULT_PROMPT,
            promptTailVariant = NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT,
        )

    private fun Throwable.causeChainTail(): String =
        generateSequence(this) { it.cause }
            .joinToString(" <- ") { throwable ->
                "${throwable.javaClass.simpleName}:${throwable.message ?: "no_message"}"
            }
            .takeLast(PERSISTENT_DIAG_TAIL_LIMIT)

    private fun inferPersistentFailureExceptionClass(reason: String): String =
        reason.substringAfter(':', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.takeWhile { !it.isWhitespace() && it != ':' }
            ?: "unavailable"

    private companion object {
        private const val PERSISTENT_SAMPLER_TOP_K = 40
        private const val PERSISTENT_SAMPLER_TOP_P = 0.95
        private const val PERSISTENT_SAMPLER_TEMPERATURE = 1.0
        private const val PERSISTENT_DIAG_TAIL_LIMIT = 1000
    }
}
