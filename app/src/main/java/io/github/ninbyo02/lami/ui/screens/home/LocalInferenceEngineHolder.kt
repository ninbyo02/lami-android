package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.local.buildLocalInferenceFailureDiagnosticsText
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_HELD_ENGINE_REUSE_COUNT = 3
private const val ENABLE_HELD_ENGINE_RELOAD_BY_REUSE_LIMIT = false
private const val HELD_ENGINE_BACKGROUND_TIMEOUT_MS = 5 * 60 * 1000L
private const val HELD_ENGINE_IDLE_TIMEOUT_MS = 10 * 60 * 1000L

internal data class HeldLocalEngine(
    val engineKey: HeldEngineKey,
    val modelPath: String,
    val engineInstance: Any,
    val namespace: String?,
    val createdAtElapsedMs: Long,
    var lastUsedAtElapsedMs: Long,
    var useCount: Int,
    val closeEngine: (((String) -> Unit)?) -> Unit,
)

internal data class HeldEngineKey(
    val modelPath: String,
    val backendKey: String,
    val cacheDirPath: String,
)

internal data class HeldEngineAcquireDiagnosticResult(
    val engine: HeldLocalEngine?,
    val failureStage: String?,
    val failureClassName: String?,
    val failureMessage: String?,
    val failureDiagnosticsText: String? = null,
)

internal data class HeldEngineDevDiagnosticSnapshot(
    val holderInstanceHash: Int,
    val heldEngineHash: Int?,
    val appInForeground: Boolean,
    val lastAcquireAction: String?,
    val lastLifecycleEventReason: String?,
    val lastLifecycleDecisionAction: String?,
    val recreateRequestCount: Int,
    val lastRecreateResult: String?,
    val lastRecreateReason: String?,
    val hasHeldEngineBeforeRecreate: Boolean?,
    val hasHeldEngineAfterRecreate: Boolean?,
    val lastHeldEngineCreateReason: String?,
    val lastHeldEngineCreateSource: String?,
    val lastHeldEngineCreateAtElapsedMs: Long?,
    val lastHeldEngineCreateRequestedPreferredBackend: String?,
    val lastHeldEngineCreateStackHint: String?,
    val lastHeldEngineCreateAppliedPreferredBackend: String?,
    val lastHeldEngineCreatePreferredBackendApplyResult: String?,
    val lastHeldEngineCreatePreferredBackendHookReached: Boolean?,
    val lastHeldEngineCreatePreferredBackendHookSource: String?,
    val lastHeldEngineCreatePreferredBackendApplyBuilderClass: String?,
    val lastHeldEngineCreatePreferredBackendApplyBackendEnumCandidates: List<String>,
)

internal class LocalInferenceEngineHolder(
    private val appContext: Context,
) {
    private enum class HeldEngineLifecycleReason {
        MODEL_CHANGED,
        BACKEND_CHANGED,
        EXPLICIT_RESET,
        FATAL_ERROR,
        LOW_MEMORY,
        APP_BACKGROUNDED,
        TTS_PLAYBACK,
        BACKGROUND_TIMEOUT,
        IDLE_TIMEOUT,
        KEEP_HELD,
    }

    private enum class HeldEngineLifecycleAction {
        KEEP_HELD,
        CLOSE_AND_RECREATE,
        CLEAR_ONLY,
        NO_OP,
    }

    private data class HeldEngineLifecycleDecision(
        val reason: HeldEngineLifecycleReason,
        val action: HeldEngineLifecycleAction,
        val clearReason: String,
    )

    private data class HeldConversation(
        val chatId: Int,
        val engineModelPath: String,
        val engineGeneration: Long,
        val conversation: Any,
        val namespace: String?,
    )

    companion object {
        @Volatile
        private var instance: LocalInferenceEngineHolder? = null

        fun getInstance(appContext: Context): LocalInferenceEngineHolder {
            return instance ?: synchronized(this) {
                instance ?: LocalInferenceEngineHolder(appContext.applicationContext).also { created ->
                    instance = created
                }
            }
        }
    }

    private val mutex = Mutex()
    private var held: HeldLocalEngine? = null
    private val heldConversationsByChatId = mutableMapOf<Int, HeldConversation>()
    private var heldEngineGeneration: Long = 0L
    private var appInForeground: Boolean = true
    private var appBackgroundedAtElapsedMs: Long? = null
    private var lastAcquireAction: String? = null
    private var lastLifecycleEventReason: String? = null
    private var lastLifecycleDecisionAction: String? = null
    private var recreateRequestCount: Int = 0
    private var lastRecreateResult: String? = null
    private var lastRecreateReason: String? = null
    private var hasHeldEngineBeforeRecreate: Boolean? = null
    private var hasHeldEngineAfterRecreate: Boolean? = null
    private var lastHeldEngineCreateReason: String? = null
    private var lastHeldEngineCreateSource: String? = null
    private var lastHeldEngineCreateAtElapsedMs: Long? = null
    private var lastHeldEngineCreateRequestedPreferredBackend: String? = null
    private var lastHeldEngineCreateStackHint: String? = null
    private var lastHeldEngineCreateAppliedPreferredBackend: String? = null
    private var lastHeldEngineCreatePreferredBackendApplyResult: String? = null
    private var lastHeldEngineCreatePreferredBackendHookReached: Boolean? = null
    private var lastHeldEngineCreatePreferredBackendHookSource: String? = null
    private var lastHeldEngineCreatePreferredBackendApplyBuilderClass: String? = null
    private var lastHeldEngineCreatePreferredBackendApplyBackendEnumCandidates: List<String> = emptyList()

    suspend fun acquire(
        engineKey: HeldEngineKey,
        appendTrace: ((String) -> Unit)? = null,
        preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    ): HeldLocalEngine = mutex.withLock {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        maybeReleaseBackgroundTimedOutEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
        maybeReleaseIdleEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
        val modelPath = engineKey.modelPath
        val current = held
        val decision = decideAcquireLifecycle(current = current, requested = engineKey)
        applyLifecycleDecisionLocked(
            current = current,
            decision = decision,
            appendTrace = appendTrace,
        )
        if (decision.action == HeldEngineLifecycleAction.KEEP_HELD && current != null) {
            current.useCount += 1
            current.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
            lastAcquireAction = "reused"
            appendTrace?.invoke(
                "UPSTREAM held-engine reuse-hit modelPathTail=${modelPath.substringAfterLast('/')} useCount=${current.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT",
            )
            return@withLock current
        }

        val createdDiagnostic = createReusableLocalInferenceEngineWithDiagnostic(
            context = appContext,
            engineKey = engineKey,
            appendTrace = appendTrace,
            preferredBackendDryRunSetting = preferredBackendDryRunSetting,
        )
        val created = createdDiagnostic.engine
            ?: throw IllegalStateException(
                "Failed to create local inference engine. modelPath=$modelPath stage=${createdDiagnostic.stage} class=${createdDiagnostic.className} message=${createdDiagnostic.message}",
            )
        created.useCount += 1
        created.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
        lastAcquireAction = "created"
        appendTrace?.invoke(
            "UPSTREAM held-engine create-success modelPathTail=${modelPath.substringAfterLast('/')} useCount=${created.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT",
        )
        heldEngineGeneration += 1
        clearAllConversationsLocked(reason = "engine-recreated", appendTrace = appendTrace)
        held = created
        recordHeldEngineCreateLocked(
            reason = "acquire-create",
            source = "LocalInferenceEngineHolder.acquire",
            createdAtElapsedMs = created.createdAtElapsedMs,
            requestedPreferredBackend = preferredBackendDryRunSetting.name,
            preferredBackendApplyResult = createdDiagnostic.preferredBackendApplyResult,
        )
        created
    }

    suspend fun acquireWithDiagnostic(
        engineKey: HeldEngineKey,
        appendTrace: ((String) -> Unit)? = null,
        preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    ): HeldEngineAcquireDiagnosticResult = mutex.withLock {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        maybeReleaseBackgroundTimedOutEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
        maybeReleaseIdleEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
        val modelPath = engineKey.modelPath
        val modelPathTail = modelPath.substringAfterLast('/')
        appendTrace?.invoke("UPSTREAM held-acquire-diagnostic start modelPathTail=$modelPathTail")
        var failureStage: String? = null
        try {
            val current = held
            val decision = decideAcquireLifecycle(current = current, requested = engineKey)
            if (decision.action == HeldEngineLifecycleAction.CLOSE_AND_RECREATE) {
                failureStage = "recycle-close"
            }
            applyLifecycleDecisionLocked(
                current = current,
                decision = decision,
                appendTrace = appendTrace,
            )
            if (decision.action == HeldEngineLifecycleAction.KEEP_HELD && current != null) {
                current.useCount += 1
                current.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
                lastAcquireAction = "reused"
                appendTrace?.invoke(
                    "UPSTREAM held-engine reuse-hit modelPathTail=$modelPathTail useCount=${current.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT",
                )
                appendTrace?.invoke(
                    "UPSTREAM held-acquire-diagnostic success heldHash=${current.hashCode()} useCount=${current.useCount}",
                )
                return@withLock HeldEngineAcquireDiagnosticResult(
                    engine = current,
                    failureStage = null,
                    failureClassName = null,
                    failureMessage = null,
                )
            }

            failureStage = "create-reusable-engine"
            val createdDiagnostic = createReusableLocalInferenceEngineWithDiagnostic(
                context = appContext,
                engineKey = engineKey,
                appendTrace = appendTrace,
                preferredBackendDryRunSetting = preferredBackendDryRunSetting,
            )
            appendTrace?.invoke(
                "UPSTREAM held-create-diagnostic stage=${createdDiagnostic.stage ?: "unknown"} class=${createdDiagnostic.className ?: "none"} message=${createdDiagnostic.message ?: "none"}",
            )
            val created = createdDiagnostic.engine
            if (created == null) {
                val failStage = createdDiagnostic.stage ?: "create-reusable-engine"
                val failClass = createdDiagnostic.className ?: "ReturnedNull"
                val failMessage = (createdDiagnostic.message ?: "createReusableLocalInferenceEngine returned null").take(200)
                lastAcquireAction = "failed:$failStage"
                appendTrace?.invoke(
                    "UPSTREAM held-acquire-diagnostic fail stage=$failStage class=$failClass message=$failMessage",
                )
                return@withLock HeldEngineAcquireDiagnosticResult(
                    engine = null,
                    failureStage = failStage,
                    failureClassName = failClass,
                    failureMessage = failMessage,
                    failureDiagnosticsText = createdDiagnostic.failureDiagnosticsText,
                )
            }
            created.useCount += 1
            created.lastUsedAtElapsedMs = SystemClock.elapsedRealtime()
            lastAcquireAction = "created"
            appendTrace?.invoke(
                "UPSTREAM held-engine create-success modelPathTail=$modelPathTail useCount=${created.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT",
            )
            failureStage = "store-held"
            heldEngineGeneration += 1
            clearAllConversationsLocked(reason = "engine-recreated", appendTrace = appendTrace)
            held = created
            recordHeldEngineCreateLocked(
                reason = "acquire-with-diagnostic-create",
                source = "LocalInferenceEngineHolder.acquireWithDiagnostic",
                createdAtElapsedMs = created.createdAtElapsedMs,
                requestedPreferredBackend = preferredBackendDryRunSetting.name,
                preferredBackendApplyResult = createdDiagnostic.preferredBackendApplyResult,
            )
            appendTrace?.invoke(
                "UPSTREAM held-acquire-diagnostic success heldHash=${created.hashCode()} useCount=${created.useCount}",
            )
            HeldEngineAcquireDiagnosticResult(
                engine = created,
                failureStage = null,
                failureClassName = null,
                failureMessage = null,
            )
        } catch (e: Exception) {
            val resolvedStage = failureStage ?: "unknown"
            val failureClassName = e::class.java.simpleName.ifBlank { e::class.java.name }
            val failureMessage = (e.message ?: "no message").take(200)
            lastAcquireAction = "failed:$resolvedStage"
            appendTrace?.invoke(
                "UPSTREAM held-acquire-diagnostic fail stage=$resolvedStage class=$failureClassName message=$failureMessage",
            )
            HeldEngineAcquireDiagnosticResult(
                engine = null,
                failureStage = resolvedStage,
                failureClassName = failureClassName,
                failureMessage = failureMessage,
                failureDiagnosticsText = buildLocalInferenceFailureDiagnosticsText(
                    context = appContext,
                    stage = "holder-acquire",
                    throwable = e,
                    selectedModelName = engineKey.modelPath,
                    selectedFallbackPath = "gpu",
                ),
            )
        }
    }

    suspend fun clear(appendTrace: ((String) -> Unit)? = null) {
        mutex.withLock {
            applyLifecycleDecisionLocked(
                current = held,
                decision = HeldEngineLifecycleDecision(
                    reason = HeldEngineLifecycleReason.EXPLICIT_RESET,
                    action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                    clearReason = "clear",
                ),
                appendTrace = appendTrace,
            )
        }
    }

    suspend fun requestRecreateForDev(
        reason: String,
        appendTrace: ((String) -> Unit)? = null,
    ): Boolean = mutex.withLock {
        runCatching {
            val before = held
            recreateRequestCount += 1
            lastRecreateReason = reason
            hasHeldEngineBeforeRecreate = before != null
            appendTrace?.invoke("UPSTREAM held-engine manual-recreate-request reason=$reason")
            appendTrace?.invoke(
                "UPSTREAM held-engine manual-recreate-before holderHash=${this@LocalInferenceEngineHolder.hashCode()} heldHash=${before?.hashCode() ?: -1} heldExists=${before != null} requestCount=$recreateRequestCount",
            )
            applyLifecycleDecisionLocked(
                current = held,
                decision = HeldEngineLifecycleDecision(
                    reason = HeldEngineLifecycleReason.EXPLICIT_RESET,
                    action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                    clearReason = "dev-manual-recreate:$reason",
                ),
                appendTrace = appendTrace,
            )
            val after = held
            hasHeldEngineAfterRecreate = after != null
            lastRecreateResult = "success"
            appendTrace?.invoke(
                "UPSTREAM held-engine manual-recreate-after holderHash=${this@LocalInferenceEngineHolder.hashCode()} heldHash=${after?.hashCode() ?: -1} heldExists=${after != null} result=success",
            )
            appendTrace?.invoke(
                "UPSTREAM held-engine manual-recreate-holder-held-after heldExists=${after != null}",
            )
            true
        }.getOrElse {
            hasHeldEngineAfterRecreate = held != null
            lastRecreateResult = "failed:${it::class.java.simpleName}"
            appendTrace?.invoke(
                "UPSTREAM held-engine manual-recreate-failed reason=$reason error=${it::class.java.simpleName}:${it.message}",
            )
            false
        }
    }

    suspend fun getDevDiagnosticSnapshot(): HeldEngineDevDiagnosticSnapshot = mutex.withLock {
        HeldEngineDevDiagnosticSnapshot(
            holderInstanceHash = this@LocalInferenceEngineHolder.hashCode(),
            heldEngineHash = held?.hashCode(),
            appInForeground = appInForeground,
            lastAcquireAction = lastAcquireAction,
            lastLifecycleEventReason = lastLifecycleEventReason,
            lastLifecycleDecisionAction = lastLifecycleDecisionAction,
            recreateRequestCount = recreateRequestCount,
            lastRecreateResult = lastRecreateResult,
            lastRecreateReason = lastRecreateReason,
            hasHeldEngineBeforeRecreate = hasHeldEngineBeforeRecreate,
            hasHeldEngineAfterRecreate = hasHeldEngineAfterRecreate,
            lastHeldEngineCreateReason = lastHeldEngineCreateReason,
            lastHeldEngineCreateSource = lastHeldEngineCreateSource,
            lastHeldEngineCreateAtElapsedMs = lastHeldEngineCreateAtElapsedMs,
            lastHeldEngineCreateRequestedPreferredBackend = lastHeldEngineCreateRequestedPreferredBackend,
            lastHeldEngineCreateStackHint = lastHeldEngineCreateStackHint,
            lastHeldEngineCreateAppliedPreferredBackend = lastHeldEngineCreateAppliedPreferredBackend,
            lastHeldEngineCreatePreferredBackendApplyResult = lastHeldEngineCreatePreferredBackendApplyResult,
            lastHeldEngineCreatePreferredBackendHookReached = lastHeldEngineCreatePreferredBackendHookReached,
            lastHeldEngineCreatePreferredBackendHookSource = lastHeldEngineCreatePreferredBackendHookSource,
            lastHeldEngineCreatePreferredBackendApplyBuilderClass = lastHeldEngineCreatePreferredBackendApplyBuilderClass,
            lastHeldEngineCreatePreferredBackendApplyBackendEnumCandidates = lastHeldEngineCreatePreferredBackendApplyBackendEnumCandidates,
        )
    }

    private fun recordHeldEngineCreateLocked(
        reason: String,
        source: String,
        createdAtElapsedMs: Long?,
        requestedPreferredBackend: String?,
        preferredBackendApplyResult: PreferredBackendApplyResult?,
    ) {
        lastHeldEngineCreateReason = reason
        lastHeldEngineCreateSource = source
        lastHeldEngineCreateAtElapsedMs = createdAtElapsedMs
        lastHeldEngineCreateRequestedPreferredBackend = requestedPreferredBackend
        lastHeldEngineCreateAppliedPreferredBackend = preferredBackendApplyResult?.appliedPreferredBackend
        lastHeldEngineCreatePreferredBackendApplyResult = preferredBackendApplyResult?.preferredBackendApplyResult
        lastHeldEngineCreatePreferredBackendHookReached = preferredBackendApplyResult?.preferredBackendHookReached
        lastHeldEngineCreatePreferredBackendHookSource = preferredBackendApplyResult?.preferredBackendHookSource
        lastHeldEngineCreatePreferredBackendApplyBuilderClass = preferredBackendApplyResult?.preferredBackendApplyBuilderClass
        lastHeldEngineCreatePreferredBackendApplyBackendEnumCandidates = preferredBackendApplyResult?.preferredBackendApplyBackendEnumCandidates ?: emptyList()
        lastHeldEngineCreateStackHint = Throwable().stackTrace
            .firstOrNull { frame -> frame.className.contains("ChatScreen") || frame.className.contains("LocalInference") }
            ?.let { frame -> "${frame.className}.${frame.methodName}:${frame.lineNumber}" }
    }

    suspend fun clearIfModelChanged(
        newModelPath: String,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        mutex.withLock {
            val current = held
            val decision = if (current != null && current.modelPath != newModelPath) {
                HeldEngineLifecycleDecision(
                    reason = HeldEngineLifecycleReason.MODEL_CHANGED,
                    action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                    clearReason = "clear-model-changed",
                )
            } else {
                HeldEngineLifecycleDecision(
                    reason = HeldEngineLifecycleReason.KEEP_HELD,
                    action = HeldEngineLifecycleAction.NO_OP,
                    clearReason = "keep-held",
                )
            }
            applyLifecycleDecisionLocked(
                current = current,
                decision = decision,
                appendTrace = appendTrace,
            )
        }
    }

    suspend fun notifyLifecycleEvent(
        reason: String,
        chatId: Int? = null,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        mutex.withLock {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            maybeReleaseBackgroundTimedOutEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
            maybeReleaseIdleEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
            val decision = resolveLifecycleDecision(reason = reason)
            lastLifecycleEventReason = reason
            lastLifecycleDecisionAction = decision.action.name
            applyLifecycleDecisionLocked(
                current = held,
                decision = decision,
                chatId = chatId,
                appendTrace = appendTrace,
            )
        }
    }

    suspend fun acquireConversation(
        chatId: Int,
        heldEngine: HeldLocalEngine,
        appendTrace: ((String) -> Unit)? = null,
        createConversation: (engine: Any, namespace: String?) -> Any?,
    ): Any? = mutex.withLock {
        val existing = heldConversationsByChatId[chatId]
        if (existing != null) {
            closeConversationLocked(chatId = chatId, reason = "per-send-recreate", appendTrace = appendTrace)
        }
        val created = createConversation(heldEngine.engineInstance, heldEngine.namespace)
        if (created != null) {
            appendTrace?.invoke("UPSTREAM held-conversation create-ephemeral chatId=$chatId")
        }
        created
    }

    suspend fun resetConversation(
        chatId: Int,
        reason: String,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        notifyLifecycleEvent(reason = reason, chatId = chatId, appendTrace = appendTrace)
    }

    suspend fun notifyAppBackgrounded(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        mutex.withLock {
            appInForeground = false
            appBackgroundedAtElapsedMs = nowElapsedMs
            val decision = resolveLifecycleDecision(reason = "app-backgrounded")
            lastLifecycleEventReason = decision.clearReason
            lastLifecycleDecisionAction = decision.action.name
            applyLifecycleDecisionLocked(
                current = held,
                decision = decision,
            )
        }
    }

    suspend fun notifyAppForegrounded(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        appendTrace: ((String) -> Unit)? = null,
    ) {
        mutex.withLock {
            maybeReleaseBackgroundTimedOutEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
            appInForeground = true
            appBackgroundedAtElapsedMs = null
            lastLifecycleEventReason = "app-foregrounded"
            lastLifecycleDecisionAction = HeldEngineLifecycleAction.KEEP_HELD.name
            maybeReleaseIdleEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
        }
    }

    suspend fun maybeReleaseIdleEngine(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        appendTrace: ((String) -> Unit)? = null,
    ) {
        mutex.withLock {
            maybeReleaseIdleEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
        }
    }

    suspend fun maybeReleaseBackgroundTimedOutEngine(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        appendTrace: ((String) -> Unit)? = null,
    ) {
        mutex.withLock {
            maybeReleaseBackgroundTimedOutEngineLocked(nowElapsedMs = nowElapsedMs, appendTrace = appendTrace)
        }
    }

    private fun decideAcquireLifecycle(
        current: HeldLocalEngine?,
        requested: HeldEngineKey,
    ): HeldEngineLifecycleDecision {
        if (current == null) {
            return HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.KEEP_HELD,
                action = HeldEngineLifecycleAction.NO_OP,
                clearReason = "keep-held",
            )
        }
        if (current.engineKey == requested) {
            if (ENABLE_HELD_ENGINE_RELOAD_BY_REUSE_LIMIT && current.useCount >= MAX_HELD_ENGINE_REUSE_COUNT) {
                return HeldEngineLifecycleDecision(
                    reason = HeldEngineLifecycleReason.EXPLICIT_RESET,
                    action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                    clearReason = "reuse-limit",
                )
            }
            return HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.KEEP_HELD,
                action = HeldEngineLifecycleAction.KEEP_HELD,
                clearReason = "keep-held",
            )
        }
        val reason = if (current.modelPath != requested.modelPath) {
            HeldEngineLifecycleReason.MODEL_CHANGED
        } else {
            HeldEngineLifecycleReason.BACKEND_CHANGED
        }
        val clearReason = if (reason == HeldEngineLifecycleReason.MODEL_CHANGED) "model-changed" else "backend-changed"
        return HeldEngineLifecycleDecision(
            reason = reason,
            action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
            clearReason = clearReason,
        )
    }

    private fun resolveLifecycleDecision(reason: String): HeldEngineLifecycleDecision {
        return when (reason) {
            "backend-changed" -> HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.BACKEND_CHANGED,
                action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                clearReason = reason,
            )

            "explicit-reset" -> HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.EXPLICIT_RESET,
                action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                clearReason = reason,
            )

            "fatal-error" -> HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.FATAL_ERROR,
                action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                clearReason = reason,
            )

            "low-memory" -> HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.LOW_MEMORY,
                action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                clearReason = reason,
            )

            "app-backgrounded" -> HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.APP_BACKGROUNDED,
                action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                clearReason = reason,
            )

            "tts-playback" -> HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.TTS_PLAYBACK,
                action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                clearReason = reason,
            )

            "background-timeout" -> HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.BACKGROUND_TIMEOUT,
                action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                clearReason = reason,
            )

            "idle-timeout" -> HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.IDLE_TIMEOUT,
                action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                clearReason = reason,
            )

            else -> HeldEngineLifecycleDecision(
                reason = HeldEngineLifecycleReason.KEEP_HELD,
                action = HeldEngineLifecycleAction.CLEAR_ONLY,
                clearReason = reason,
            )
        }
    }

    private fun maybeReleaseIdleEngineLocked(
        nowElapsedMs: Long,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        if (appInForeground) return
        val current = held ?: return
        if (nowElapsedMs - current.lastUsedAtElapsedMs < HELD_ENGINE_IDLE_TIMEOUT_MS) return
        applyLifecycleDecisionLocked(
            current = current,
            decision = resolveLifecycleDecision(reason = "idle-timeout"),
            appendTrace = appendTrace,
        )
    }

    private fun maybeReleaseBackgroundTimedOutEngineLocked(
        nowElapsedMs: Long,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        val backgroundedAt = appBackgroundedAtElapsedMs ?: return
        if (nowElapsedMs - backgroundedAt < HELD_ENGINE_BACKGROUND_TIMEOUT_MS) return
        applyLifecycleDecisionLocked(
            current = held,
            decision = resolveLifecycleDecision(reason = "background-timeout"),
            appendTrace = appendTrace,
        )
        appBackgroundedAtElapsedMs = null
    }

    private fun applyLifecycleDecisionLocked(
        current: HeldLocalEngine?,
        decision: HeldEngineLifecycleDecision,
        chatId: Int? = null,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        when (decision.action) {
            HeldEngineLifecycleAction.KEEP_HELD -> Unit
            HeldEngineLifecycleAction.CLOSE_AND_RECREATE -> {
                val target = current ?: return
                val engineClassName = target.engineInstance.javaClass.name
                appendTrace?.invoke(
                    "UPSTREAM held-engine close-start reason=${decision.clearReason} class=$engineClassName modelPathTail=${target.modelPath.substringAfterLast('/')}",
                )
                runCatching { target.closeEngine(appendTrace) }
                held = null
                appBackgroundedAtElapsedMs = null
                clearAllConversationsLocked(reason = decision.clearReason, appendTrace = appendTrace)
                if (decision.reason == HeldEngineLifecycleReason.MODEL_CHANGED) {
                    appendTrace?.invoke("UPSTREAM held-engine cleared reason=model-changed")
                }
                if (decision.clearReason == "reuse-limit") {
                    appendTrace?.invoke(
                        "UPSTREAM held-engine recycle reason=reuse-limit reached useCount=${target.useCount}/$MAX_HELD_ENGINE_REUSE_COUNT modelPathTail=${target.modelPath.substringAfterLast('/')}",
                    )
                }
            }

            HeldEngineLifecycleAction.CLEAR_ONLY -> {
                if (chatId != null) {
                    closeConversationLocked(chatId = chatId, reason = decision.clearReason, appendTrace = appendTrace)
                } else {
                    clearAllConversationsLocked(reason = decision.clearReason, appendTrace = appendTrace)
                }
            }

            HeldEngineLifecycleAction.NO_OP -> Unit
        }
    }

    private fun clearAllConversationsLocked(
        reason: String,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        val chatIds = heldConversationsByChatId.keys.toList()
        chatIds.forEach { chatId ->
            closeConversationLocked(chatId = chatId, reason = reason, appendTrace = appendTrace)
        }
    }

    private fun closeConversationLocked(
        chatId: Int,
        reason: String,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        val session = heldConversationsByChatId.remove(chatId) ?: return
        appendTrace?.invoke("UPSTREAM held-conversation close-start chatId=$chatId reason=$reason class=${session.conversation.javaClass.name}")
        closeTargetQuietly(session.conversation)
        appendTrace?.invoke("UPSTREAM held-conversation cleared chatId=$chatId reason=$reason")
    }

    private fun closeTargetQuietly(target: Any?) {
        if (target == null) return
        runCatching {
            val closeMethod = target.javaClass.methods.firstOrNull { method ->
                method.name == "close" && method.parameterTypes.isEmpty()
            } ?: target.javaClass.methods.firstOrNull { method ->
                method.name == "shutdown" && method.parameterTypes.isEmpty()
            }
            closeMethod?.invoke(target)
        }
    }
}
