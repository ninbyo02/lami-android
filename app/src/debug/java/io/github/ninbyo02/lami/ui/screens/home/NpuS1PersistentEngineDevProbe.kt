package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
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
        var state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_RUNNING,
            runCountRequested = requestedRunCount,
            startedAtElapsedRealtimeMs = startedAtElapsedRealtimeMs,
            modelPathOrName = modelPath ?: modelResolution.reasonCode,
            cacheDir = cacheDir,
            backendEvidence = "official_litertlm_backend_npu_requested",
            persistentEngineHypothesisResult = "running",
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
                    maxNumTokens = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
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
            for (runIndex in 1..requestedRunCount) {
                if (isCancelled()) {
                    return@withContext state.copy(
                        persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_CANCELLED,
                        finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        records = records.toList(),
                        conversationCreateCount = conversationCreateCount,
                        conversationCloseCount = conversationCloseCount,
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
                    )
                    return@withContext state.copy(
                        persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
                        finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        records = records.toList(),
                        conversationCreateCount = conversationCreateCount,
                        conversationCloseCount = conversationCloseCount,
                        firstFailureRunIndex = runIndex,
                        firstFailureStage = "low_memory",
                        firstFailureReason = "low_memory_before_run",
                        persistentEngineHypothesisResult = "low_memory_before_run",
                    ).also(::update)
                }
                val record = runOneConversation(
                    engine = engine,
                    runIndex = runIndex,
                    backendEvidence = state.backendEvidence,
                    onConversationCreated = { conversationCreateCount += 1 },
                    onConversationClosed = { conversationCloseCount += 1 },
                )
                records += record
                val failed = record.status != NpuStandardRouteS1Contract.STATUS_SUCCESS
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
                        firstFailureRunIndex = if (failed) record.runIndex else state.firstFailureRunIndex,
                        firstFailureStage = if (failed) record.failureStage else state.firstFailureStage,
                        firstFailureReason = if (failed) record.reason else state.firstFailureReason,
                        firstFailureExceptionClass = if (failed) record.failureExceptionClass else state.firstFailureExceptionClass,
                        firstFailureExceptionMessage = if (failed) record.failureExceptionMessage else state.firstFailureExceptionMessage,
                        persistentEngineHypothesisResult = if (failed) {
                            when (record.failureStage) {
                                "conversation_create" -> "conversation_create_failed"
                                "decode" -> "decode_failed"
                                else -> "decode_failed"
                            }
                        } else {
                            "running"
                        },
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
            val prompt = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
                contextText = "",
                userPrompt = NPU_S1_REPEATED_RUN_DEFAULT_PROMPT,
                promptTailVariant = NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT,
            )
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
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException && throwable !is TimeoutCancellationException) {
                throw throwable
            }
            failureRecord(
                runIndex = runIndex,
                stage = if (conversationCreated == "true" && decodeStarted == "true") {
                    "decode"
                } else {
                    "conversation_create"
                },
                reason = if (decodeStarted == "true") {
                    "decode_failed:${throwable.javaClass.simpleName}"
                } else {
                    "conversation_create_failed:${throwable.javaClass.simpleName}"
                },
                throwable = throwable,
                backendEvidence = backendEvidence,
                conversationCreated = conversationCreated,
                conversationClosed = conversationClosed,
                decodeStarted = decodeStarted,
                decodeFinished = decodeFinished,
                totalMs = SystemClock.elapsedRealtime() - runStartedAt,
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
            persistentEngineHypothesisResult = hypothesisResult,
        )

    private fun failureRecord(
        runIndex: Int,
        stage: String,
        reason: String,
        throwable: Throwable?,
        backendEvidence: String,
        conversationCreated: String = "unavailable",
        conversationClosed: String = "unavailable",
        decodeStarted: String = "unavailable",
        decodeFinished: String = "unavailable",
        totalMs: Long? = null,
    ): NpuS1PersistentEngineRunRecord =
        NpuS1PersistentEngineRunRecord(
            runIndex = runIndex,
            status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
            reason = reason,
            conversationCreated = conversationCreated,
            conversationClosed = conversationClosed,
            sessionCreated = "unavailable",
            sessionClosed = "unavailable",
            decodeStarted = decodeStarted,
            decodeFinished = decodeFinished,
            totalMs = totalMs,
            failureStage = stage,
            failureExceptionClass = throwable?.javaClass?.simpleName ?: inferPersistentFailureExceptionClass(reason),
            failureExceptionMessage = throwable?.message ?: "unavailable",
            nativeOrEngineDiagTail = throwable?.causeChainTail() ?: reason,
            backendEvidence = backendEvidence,
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
