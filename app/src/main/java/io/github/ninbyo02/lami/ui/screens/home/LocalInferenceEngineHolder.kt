package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.local.buildLocalInferenceFailureDiagnosticsText
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_HELD_ENGINE_REUSE_COUNT = 3
private const val ENABLE_HELD_ENGINE_RELOAD_BY_REUSE_LIMIT = false
private const val HELD_ENGINE_BACKGROUND_TIMEOUT_MS = 5 * 60 * 1000L
private const val HELD_ENGINE_IDLE_TIMEOUT_MS = 10 * 60 * 1000L
private const val HELD_ENGINE_LIFECYCLE_HISTORY_MAX = 24
private const val GPU_TRANSIENT_ONSTOP_AFTER_SUCCESS_SUPPRESS_MS = 5_000L

internal data class HeldLocalEngine(
    val engineKey: HeldEngineKey,
    val modelPath: String,
    val engineInstance: Any,
    val namespace: String?,
    val createdAtElapsedMs: Long,
    var lastUsedAtElapsedMs: Long,
    var useCount: Int,
    val preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
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
    val holderCreated: Boolean = false,
    val holderAcquired: Boolean = false,
    val holderReused: Boolean = false,
    val holderInvalidated: Boolean = false,
    val holderClosed: Boolean = false,
    val holderTimeoutCleanup: Boolean = false,
    val holderFailureCleanup: Boolean = false,
    val holderProcessRestart: Boolean = false,
    val heldEngineLifecycleHistory: String = "unavailable",
    val heldEngineDestroyReason: String? = null,
    val heldEngineLastOwner: String? = null,
    val heldEngineLastFailureStage: String? = null,
    val heldEngineSnapshotBeforeDestroy: String? = null,
    val gpuHolderLifecycleEventAfterSuccess: String? = null,
    val gpuHolderLifecycleLastActivityState: String? = null,
    val gpuHolderLifecycleLastAppVisibility: String? = null,
    val gpuHolderLifecycleClearTriggerElapsedMs: Long? = null,
    val gpuHolderLifecycleClearAfterSuccessMs: Long? = null,
    val gpuHolderLifecycleClearDuringActiveGenerate: Boolean? = null,
    val gpuHolderLifecycleClearAfterUiAppend: Boolean? = null,
    val gpuHolderLifecycleClearReasonDetail: String? = null,
    val gpuHolderLifecycleBackgroundDetectionSource: String? = null,
    val gpuHolderLifecycleOnStopDeferred: Boolean? = null,
    val gpuHolderLifecycleOnStopDeferReason: String? = null,
    val gpuHolderLifecycleClearSuppressedAfterSuccess: Boolean? = null,
    val gpuHolderLifecycleClearSuppressedReason: String? = null,
    val gpuHolderLifecycleActualBackgroundConfirmed: Boolean? = null,
    val gpuHolderLifecycleReuseExpectedNextTurn: Boolean? = null,
)

internal class LocalInferenceEngineHolder(
    private val appContext: Context,
    private val gpuTransientOnStopProtectionOverrideForTest: Boolean? = null,
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
    private val heldEngineLifecycleHistory = ArrayDeque<String>()
    private val heldEngineLifecycleEvents = linkedSetOf<String>()
    private var heldEngineDestroyReason: String? = null
    private var heldEngineLastOwner: String? = null
    private var heldEngineLastFailureStage: String? = null
    private var heldEngineSnapshotBeforeDestroy: String? = null
    private var gpuGenerateActive: Boolean = false
    private var lastGpuGenerateStartedAtElapsedMs: Long? = null
    private var lastGpuUiAppendFinishedAtElapsedMs: Long? = null
    private var lastGpuGenerationSuccessAtElapsedMs: Long? = null
    private var lastGpuHolderClearTriggerElapsedMs: Long? = null
    private var lastGpuHolderClearAfterSuccessMs: Long? = null
    private var lastGpuHolderClearDuringActiveGenerate: Boolean? = null
    private var lastGpuHolderClearAfterUiAppend: Boolean? = null
    private var lastGpuHolderLifecycleEventAfterSuccess: String? = null
    private var lastGpuHolderLifecycleBackgroundDetectionSource: String? = null
    private var lastGpuHolderLifecycleOnStopDeferred: Boolean? = null
    private var lastGpuHolderLifecycleOnStopDeferReason: String? = null
    private var lastGpuHolderLifecycleClearSuppressedAfterSuccess: Boolean? = null
    private var lastGpuHolderLifecycleClearSuppressedReason: String? = null
    private var lastGpuHolderLifecycleActualBackgroundConfirmed: Boolean? = null
    private var lastGpuHolderLifecycleReuseExpectedNextTurn: Boolean? = null

    init {
        recordHeldEngineLifecycleEventLocked(
            event = "holder_process_restart",
            reason = "holder_init",
            owner = "LocalInferenceEngineHolder.init",
            failureStage = "none",
            target = null,
        )
        recordHeldEngineLifecycleEventLocked(
            event = "holder_created",
            reason = "holder_init",
            owner = "LocalInferenceEngineHolder.init",
            failureStage = "none",
            target = null,
        )
    }

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
            recordHeldEngineLifecycleEventLocked(
                event = "holder_reused",
                reason = "acquire-reuse",
                owner = "LocalInferenceEngineHolder.acquire",
                failureStage = "none",
                target = current,
            )
            recordHeldEngineLifecycleEventLocked(
                event = "holder_acquired",
                reason = "acquire-reuse",
                owner = "LocalInferenceEngineHolder.acquire",
                failureStage = "none",
                target = current,
            )
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
        recordHeldEngineLifecycleEventLocked(
            event = "held_engine_created",
            reason = "acquire-create",
            owner = "LocalInferenceEngineHolder.acquire",
            failureStage = "none",
            target = created,
        )
        recordHeldEngineLifecycleEventLocked(
            event = "holder_acquired",
            reason = "acquire-create",
            owner = "LocalInferenceEngineHolder.acquire",
            failureStage = "none",
            target = created,
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
                recordHeldEngineLifecycleEventLocked(
                    event = "holder_reused",
                    reason = "acquire-with-diagnostic-reuse",
                    owner = "LocalInferenceEngineHolder.acquireWithDiagnostic",
                    failureStage = "none",
                    target = current,
                )
                recordHeldEngineLifecycleEventLocked(
                    event = "holder_acquired",
                    reason = "acquire-with-diagnostic-reuse",
                    owner = "LocalInferenceEngineHolder.acquireWithDiagnostic",
                    failureStage = "none",
                    target = current,
                )
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
            recordHeldEngineLifecycleEventLocked(
                event = "held_engine_created",
                reason = "acquire-with-diagnostic-create",
                owner = "LocalInferenceEngineHolder.acquireWithDiagnostic",
                failureStage = "none",
                target = created,
            )
            recordHeldEngineLifecycleEventLocked(
                event = "holder_acquired",
                reason = "acquire-with-diagnostic-create",
                owner = "LocalInferenceEngineHolder.acquireWithDiagnostic",
                failureStage = "none",
                target = created,
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
            recordHeldEngineLifecycleEventLocked(
                event = "holder_failure_cleanup",
                reason = "acquire-with-diagnostic-failed",
                owner = "LocalInferenceEngineHolder.acquireWithDiagnostic",
                failureStage = resolvedStage,
                target = held,
            )
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

    suspend fun clear(
        reason: String = "clear",
        failureStage: String? = null,
        owner: String = "LocalInferenceEngineHolder.clear",
        appendTrace: ((String) -> Unit)? = null,
    ) {
        mutex.withLock {
            applyLifecycleDecisionLocked(
                current = held,
                decision = HeldEngineLifecycleDecision(
                    reason = HeldEngineLifecycleReason.EXPLICIT_RESET,
                    action = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                    clearReason = reason,
                ),
                owner = owner,
                failureStage = failureStage,
                appendTrace = appendTrace,
            )
        }
    }

    suspend fun requestRecreateForDev(
        reason: String,
        failureStage: String? = null,
        owner: String = "LocalInferenceEngineHolder.requestRecreateForDev",
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
                owner = owner,
                failureStage = failureStage,
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
            holderCreated = "holder_created" in heldEngineLifecycleEvents,
            holderAcquired = "holder_acquired" in heldEngineLifecycleEvents,
            holderReused = "holder_reused" in heldEngineLifecycleEvents,
            holderInvalidated = "holder_invalidated" in heldEngineLifecycleEvents,
            holderClosed = "holder_closed" in heldEngineLifecycleEvents,
            holderTimeoutCleanup = "holder_timeout_cleanup" in heldEngineLifecycleEvents,
            holderFailureCleanup = "holder_failure_cleanup" in heldEngineLifecycleEvents,
            holderProcessRestart = "holder_process_restart" in heldEngineLifecycleEvents,
            heldEngineLifecycleHistory = heldEngineLifecycleHistory.joinToString("|").ifBlank { "unavailable" },
            heldEngineDestroyReason = heldEngineDestroyReason,
            heldEngineLastOwner = heldEngineLastOwner,
            heldEngineLastFailureStage = heldEngineLastFailureStage,
            heldEngineSnapshotBeforeDestroy = heldEngineSnapshotBeforeDestroy,
            gpuHolderLifecycleEventAfterSuccess = lastGpuHolderLifecycleEventAfterSuccess,
            gpuHolderLifecycleLastActivityState = if (appInForeground) "foreground" else "background",
            gpuHolderLifecycleLastAppVisibility = if (appInForeground) "foreground" else "background",
            gpuHolderLifecycleClearTriggerElapsedMs = lastGpuHolderClearTriggerElapsedMs,
            gpuHolderLifecycleClearAfterSuccessMs = lastGpuHolderClearAfterSuccessMs,
            gpuHolderLifecycleClearDuringActiveGenerate = lastGpuHolderClearDuringActiveGenerate,
            gpuHolderLifecycleClearAfterUiAppend = lastGpuHolderClearAfterUiAppend,
            gpuHolderLifecycleClearReasonDetail = heldEngineDestroyReason,
            gpuHolderLifecycleBackgroundDetectionSource = lastGpuHolderLifecycleBackgroundDetectionSource,
            gpuHolderLifecycleOnStopDeferred = lastGpuHolderLifecycleOnStopDeferred,
            gpuHolderLifecycleOnStopDeferReason = lastGpuHolderLifecycleOnStopDeferReason,
            gpuHolderLifecycleClearSuppressedAfterSuccess = lastGpuHolderLifecycleClearSuppressedAfterSuccess,
            gpuHolderLifecycleClearSuppressedReason = lastGpuHolderLifecycleClearSuppressedReason,
            gpuHolderLifecycleActualBackgroundConfirmed = lastGpuHolderLifecycleActualBackgroundConfirmed,
            gpuHolderLifecycleReuseExpectedNextTurn = lastGpuHolderLifecycleReuseExpectedNextTurn,
        )
    }

    suspend fun recordGpuGenerationStartedForDiagnostics(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        mutex.withLock {
            gpuGenerateActive = true
            lastGpuGenerateStartedAtElapsedMs = nowElapsedMs
        }
    }

    suspend fun recordGpuUiAppendFinishedForDiagnostics(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        mutex.withLock {
            lastGpuUiAppendFinishedAtElapsedMs = nowElapsedMs
        }
    }

    suspend fun recordGpuGenerationFinishedForDiagnostics(
        success: Boolean,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        mutex.withLock {
            gpuGenerateActive = false
            if (success) {
                lastGpuGenerationSuccessAtElapsedMs = nowElapsedMs
            }
        }
    }

    suspend fun hasReusableHeldEngineForKey(engineKey: HeldEngineKey): Boolean = mutex.withLock {
        held?.engineKey == engineKey
    }

    private fun resolveGpuTransientOnStopDeferReasonLocked(
        nowElapsedMs: Long,
    ): String? {
        if (!isGpuTransientOnStopProtectionEnabledForDebug()) return null
        val current = held ?: return null
        if (current.preferredBackendDryRunSetting != PreferredBackendDryRunSetting.GPU) return null
        if (gpuGenerateActive) return "active_generate"
        val successAt = lastGpuGenerationSuccessAtElapsedMs ?: return null
        val clearAfterSuccessMs = (nowElapsedMs - successAt).coerceAtLeast(0L)
        if (clearAfterSuccessMs > GPU_TRANSIENT_ONSTOP_AFTER_SUCCESS_SUPPRESS_MS) return null
        val uiAppendFinished = lastGpuUiAppendFinishedAtElapsedMs?.let { uiAt ->
            uiAt <= nowElapsedMs && uiAt >= (lastGpuGenerateStartedAtElapsedMs ?: 0L)
        } == true
        return if (uiAppendFinished) {
            "transient_onstop_after_success_ui_append"
        } else {
            "transient_onstop_after_success"
        }
    }

    private fun recordGpuOnStopDeferredLocked(
        target: HeldLocalEngine,
        nowElapsedMs: Long,
        deferReason: String,
    ) {
        val clearAfterSuccessMs = lastGpuGenerationSuccessAtElapsedMs?.let { successAt ->
            (nowElapsedMs - successAt).coerceAtLeast(0L)
        }
        lastGpuHolderClearTriggerElapsedMs = nowElapsedMs
        lastGpuHolderClearAfterSuccessMs = clearAfterSuccessMs
        lastGpuHolderClearDuringActiveGenerate = gpuGenerateActive
        lastGpuHolderClearAfterUiAppend = lastGpuUiAppendFinishedAtElapsedMs?.let { uiAt ->
            uiAt <= nowElapsedMs && uiAt >= (lastGpuGenerateStartedAtElapsedMs ?: 0L)
        }
        lastGpuHolderLifecycleEventAfterSuccess = if (gpuGenerateActive) {
            "onstop_deferred_during_active_generate"
        } else {
            "onstop_deferred_after_success"
        }
        lastGpuHolderLifecycleBackgroundDetectionSource = "HeldEngineLifecycleBridge.onStop"
        lastGpuHolderLifecycleOnStopDeferred = true
        lastGpuHolderLifecycleOnStopDeferReason = deferReason
        lastGpuHolderLifecycleClearSuppressedAfterSuccess = clearAfterSuccessMs != null
        lastGpuHolderLifecycleClearSuppressedReason = deferReason
        lastGpuHolderLifecycleActualBackgroundConfirmed = false
        lastGpuHolderLifecycleReuseExpectedNextTurn = true
        recordHeldEngineLifecycleEventLocked(
            event = "holder_onstop_deferred",
            reason = deferReason,
            owner = "HeldEngineLifecycleBridge.onStop",
            failureStage = "none",
            target = target,
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
            val current = held
            val deferReason = resolveGpuTransientOnStopDeferReasonLocked(nowElapsedMs = nowElapsedMs)
            if (current != null && deferReason != null) {
                lastLifecycleDecisionAction = HeldEngineLifecycleAction.KEEP_HELD.name
                recordGpuOnStopDeferredLocked(
                    target = current,
                    nowElapsedMs = nowElapsedMs,
                    deferReason = deferReason,
                )
                return@withLock
            }
            applyLifecycleDecisionLocked(
                current = current,
                decision = decision,
                nowElapsedMs = nowElapsedMs,
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
        if (gpuGenerateActive && isGpuTransientOnStopProtectionEnabledForDebug()) {
            recordGpuOnStopDeferredLocked(
                target = current,
                nowElapsedMs = nowElapsedMs,
                deferReason = "active_generate_idle_timeout_deferred",
            )
            return
        }
            applyLifecycleDecisionLocked(
                current = current,
                decision = resolveLifecycleDecision(reason = "idle-timeout"),
                nowElapsedMs = nowElapsedMs,
                appendTrace = appendTrace,
            )
    }

    private fun maybeReleaseBackgroundTimedOutEngineLocked(
        nowElapsedMs: Long,
        appendTrace: ((String) -> Unit)? = null,
    ) {
        val backgroundedAt = appBackgroundedAtElapsedMs ?: return
        if (nowElapsedMs - backgroundedAt < HELD_ENGINE_BACKGROUND_TIMEOUT_MS) return
        val current = held
        if (current != null && gpuGenerateActive && isGpuTransientOnStopProtectionEnabledForDebug()) {
            recordGpuOnStopDeferredLocked(
                target = current,
                nowElapsedMs = nowElapsedMs,
                deferReason = "active_generate_background_timeout_deferred",
            )
            return
        }
        applyLifecycleDecisionLocked(
            current = current,
            decision = resolveLifecycleDecision(reason = "background-timeout"),
            nowElapsedMs = nowElapsedMs,
            appendTrace = appendTrace,
        )
        appBackgroundedAtElapsedMs = null
    }

    private fun applyLifecycleDecisionLocked(
        current: HeldLocalEngine?,
        decision: HeldEngineLifecycleDecision,
        chatId: Int? = null,
        owner: String = detectHeldEngineLifecycleOwner(),
        failureStage: String? = null,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        appendTrace: ((String) -> Unit)? = null,
    ) {
        when (decision.action) {
            HeldEngineLifecycleAction.KEEP_HELD -> Unit
            HeldEngineLifecycleAction.CLOSE_AND_RECREATE -> {
                val target = current ?: return
                val clearAtElapsedMs = nowElapsedMs
                val engineClassName = target.engineInstance.javaClass.name
                val resolvedFailureStage = failureStage ?: resolveHeldEngineFailureStage(decision.clearReason)
                heldEngineDestroyReason = decision.clearReason
                heldEngineLastOwner = owner
                heldEngineLastFailureStage = resolvedFailureStage
                lastGpuHolderClearTriggerElapsedMs = clearAtElapsedMs
                lastGpuHolderClearAfterSuccessMs = lastGpuGenerationSuccessAtElapsedMs?.let { successAt ->
                    (clearAtElapsedMs - successAt).coerceAtLeast(0L)
                }
                lastGpuHolderClearDuringActiveGenerate = gpuGenerateActive
                lastGpuHolderClearAfterUiAppend = lastGpuUiAppendFinishedAtElapsedMs?.let { uiAt ->
                    lastGpuGenerateStartedAtElapsedMs?.let { startedAt ->
                        uiAt >= startedAt && uiAt <= clearAtElapsedMs
                    } ?: (uiAt <= clearAtElapsedMs)
                }
                lastGpuHolderLifecycleEventAfterSuccess = when {
                    gpuGenerateActive -> "clear_during_active_generate"
                    lastGpuHolderClearAfterSuccessMs != null -> "clear_after_success"
                    else -> "clear_without_recorded_success"
                }
                lastGpuHolderLifecycleBackgroundDetectionSource = when (decision.clearReason) {
                    "app-backgrounded" -> "HeldEngineLifecycleBridge.onStop"
                    "background-timeout" -> "LocalInferenceEngineHolder.maybeReleaseBackgroundTimedOutEngine"
                    else -> owner
                }
                lastGpuHolderLifecycleOnStopDeferred = false
                lastGpuHolderLifecycleOnStopDeferReason = "none"
                lastGpuHolderLifecycleClearSuppressedAfterSuccess = false
                lastGpuHolderLifecycleClearSuppressedReason = "none"
                lastGpuHolderLifecycleActualBackgroundConfirmed =
                    decision.clearReason == "app-backgrounded" || decision.clearReason == "background-timeout"
                lastGpuHolderLifecycleReuseExpectedNextTurn = false
                heldEngineSnapshotBeforeDestroy = buildHeldEngineSnapshotBeforeDestroyLocked(
                    target = target,
                    reason = decision.clearReason,
                    owner = owner,
                    failureStage = resolvedFailureStage,
                )
                recordHeldEngineLifecycleEventLocked(
                    event = "holder_invalidated",
                    reason = decision.clearReason,
                    owner = owner,
                    failureStage = resolvedFailureStage,
                    target = target,
                )
                if (decision.clearReason.contains("timeout", ignoreCase = true)) {
                    recordHeldEngineLifecycleEventLocked(
                        event = "holder_timeout_cleanup",
                        reason = decision.clearReason,
                        owner = owner,
                        failureStage = resolvedFailureStage,
                        target = target,
                    )
                }
                if (
                    decision.reason == HeldEngineLifecycleReason.FATAL_ERROR ||
                    decision.clearReason.contains("failure", ignoreCase = true) ||
                    decision.clearReason.contains("failed", ignoreCase = true) ||
                    decision.clearReason.contains("error", ignoreCase = true)
                ) {
                    recordHeldEngineLifecycleEventLocked(
                        event = "holder_failure_cleanup",
                        reason = decision.clearReason,
                        owner = owner,
                        failureStage = resolvedFailureStage,
                        target = target,
                    )
                }
                appendTrace?.invoke(
                    "UPSTREAM held-engine close-start reason=${decision.clearReason} class=$engineClassName modelPathTail=${target.modelPath.substringAfterLast('/')}",
                )
                clearAllConversationsLocked(reason = decision.clearReason, appendTrace = appendTrace)
                runCatching { target.closeEngine(appendTrace) }
                recordHeldEngineLifecycleEventLocked(
                    event = "holder_closed",
                    reason = decision.clearReason,
                    owner = owner,
                    failureStage = resolvedFailureStage,
                    target = target,
                )
                held = null
                appBackgroundedAtElapsedMs = null
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
        recordHeldEngineLifecycleEventLocked(
            event = "conversation_closed",
            reason = reason,
            owner = "LocalInferenceEngineHolder.closeConversationLocked",
            failureStage = resolveHeldEngineFailureStage(reason),
            target = held,
            extra = "chatId=$chatId",
        )
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

    private fun isGpuTransientOnStopProtectionEnabledForDebug(): Boolean {
        gpuTransientOnStopProtectionOverrideForTest?.let { return it }
        val explicit = readHolderDebugProperty("debug.lami.gpu_holder_lifecycle_defer_transient_onstop")
            ?: readHolderDebugProperty("lami.gpu_holder_lifecycle_defer_transient_onstop")
        if (explicit != null) return explicit.equals("true", ignoreCase = true) || explicit == "1"
        val callbackStreamingGate = readHolderDebugProperty("debug.lami.gpu_normal_route_use_callback_streaming")
            ?: readHolderDebugProperty("lami.gpu_normal_route_use_callback_streaming")
        return callbackStreamingGate.equals("true", ignoreCase = true) || callbackStreamingGate == "1"
    }

    private fun readHolderDebugProperty(key: String): String? {
        val jvmProperty = runCatching {
            System.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
        if (jvmProperty != null) return jvmProperty
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            (method.invoke(null, key, "") as? String)?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun recordHeldEngineLifecycleEventLocked(
        event: String,
        reason: String,
        owner: String,
        failureStage: String?,
        target: HeldLocalEngine?,
        extra: String? = null,
    ) {
        heldEngineLifecycleEvents += event
        val entry = buildString {
            append(event)
            append("@elapsed_ms=").append(SystemClock.elapsedRealtime())
            append(":reason=").append(reason.toHeldEngineDiagnosticValue())
            append(":owner=").append(owner.toHeldEngineDiagnosticValue())
            append(":heldHash=").append(target?.hashCode() ?: -1)
            append(":backend=").append(target?.preferredBackendDryRunSetting?.name ?: "none")
            append(":model=").append(target?.modelPath?.substringAfterLast('/') ?: "none")
            append(":failureStage=").append((failureStage ?: "none").toHeldEngineDiagnosticValue())
            extra?.takeIf { it.isNotBlank() }?.let {
                append(":extra=").append(it.toHeldEngineDiagnosticValue())
            }
        }
        heldEngineLifecycleHistory += entry
        while (heldEngineLifecycleHistory.size > HELD_ENGINE_LIFECYCLE_HISTORY_MAX) {
            heldEngineLifecycleHistory.removeFirst()
        }
    }

    private fun buildHeldEngineSnapshotBeforeDestroyLocked(
        target: HeldLocalEngine,
        reason: String,
        owner: String,
        failureStage: String?,
    ): String =
        listOf(
            "holder_hash=${this@LocalInferenceEngineHolder.hashCode()}",
            "engine_hash=${target.hashCode()}",
            "backend=${target.preferredBackendDryRunSetting.name}",
            "model_path=${target.modelPath}",
            "model_path_tail=${target.modelPath.substringAfterLast('/')}",
            "created_at_elapsed_ms=${target.createdAtElapsedMs}",
            "last_used_at_elapsed_ms=${target.lastUsedAtElapsedMs}",
            "use_count=${target.useCount}",
            "namespace=${target.namespace ?: "none"}",
            "destroy_reason=$reason",
            "destroy_owner=$owner",
            "last_failure_stage=${failureStage ?: "none"}",
            "initialize_state=see_gpu_engine_initialize_finished",
            "conversation_state=see_gpu_conversation_create_finished",
            "generate_state=see_gpu_generate_started",
        ).joinToString(";") { it.toHeldEngineDiagnosticValue() }

    private fun resolveHeldEngineFailureStage(reason: String): String =
        when {
            reason.contains("gpu_watchdog_timeout", ignoreCase = true) -> "gpu_watchdog_timeout"
            reason.contains("engine_create_timeout", ignoreCase = true) -> "engine_create_timeout"
            reason.contains("timeout", ignoreCase = true) -> reason
            reason.contains("fatal", ignoreCase = true) -> "fatal-error"
            reason.contains("failure", ignoreCase = true) -> reason
            reason.contains("failed", ignoreCase = true) -> reason
            reason.contains("error", ignoreCase = true) -> reason
            else -> "none"
        }

    private fun detectHeldEngineLifecycleOwner(): String =
        Throwable().stackTrace
            .firstOrNull { frame ->
                frame.className.contains("ChatScreen") ||
                    frame.className.contains("LocalInferenceEngineHolder") ||
                    frame.className.contains("LocalStreamingRunner")
            }
            ?.let { frame -> "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}" }
            ?: "unknown"

    private fun String.toHeldEngineDiagnosticValue(): String =
        replace('\n', ' ')
            .replace('\r', ' ')
            .replace('|', '/')
            .replace(';', ',')
            .trim()
            .ifBlank { "none" }
}
