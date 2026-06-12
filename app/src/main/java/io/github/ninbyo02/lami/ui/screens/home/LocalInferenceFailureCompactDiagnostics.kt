package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.io.File
import java.lang.reflect.InvocationTargetException

internal data class LocalInferenceFailureCompactInput(
    val inputPrompt: String,
    val preferredBackendSetting: PreferredBackendDryRunSetting,
    val npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    val status: String = "failure",
    val reason: String = "local_inference_failure",
    val failureStage: String = "unknown",
    val failureExceptionClass: String = "unavailable",
    val failureExceptionMessage: String = "unavailable",
    val failureCauseClass: String = "unavailable",
    val failureCauseMessage: String = "unavailable",
    val failureRootCauseClass: String = "unavailable",
    val failureRootCauseMessage: String = "unavailable",
    val reflectionTargetExceptionClass: String = "unavailable",
    val reflectionTargetExceptionMessage: String = "unavailable",
    val reflectionTargetExceptionRootCauseClass: String = "unavailable",
    val reflectionTargetExceptionRootCauseMessage: String = "unavailable",
    val exceptionChain: String = "unavailable",
    val engineConfigBackend: String = "unavailable",
    val normalChatNativeRouteBlocked: Boolean = false,
    val blockedReason: String = "none",
    val localRouteStarted: Boolean = true,
    val localEngineCreateStarted: Boolean = false,
    val localEngineCreateFinished: Boolean = false,
    val localGenerateStarted: Boolean = false,
    val localGenerateFinished: Boolean = false,
    val fallbackUsed: Boolean = false,
    val timeout: Boolean = false,
    val freshCrash: Boolean = false,
    val ttsRequested: Boolean = false,
    val dbRequested: Boolean = false,
    val markdownRequested: Boolean = false,
    val streamingRequested: Boolean = false,
    val modelName: String = "unavailable",
    val modelFile: String = "unavailable",
    val threadName: String = Thread.currentThread().name.ifBlank { "unavailable" },
    val processPid: String = "unavailable",
    val memoryBefore: MemorySnapshot? = null,
    val memoryAfter: MemorySnapshot? = null,
    val javaHeapUsedMb: Long? = memoryAfter?.javaHeapUsedMb ?: memoryBefore?.javaHeapUsedMb,
    val nativeHeapAllocMb: Long? = memoryAfter?.nativeHeapAllocatedMb ?: memoryBefore?.nativeHeapAllocatedMb,
)

internal fun buildLocalInferenceFailureCompactDiagnosticsText(
    input: LocalInferenceFailureCompactInput,
): String {
    val backendDiagnostics = npuS1BackendDiagnosticsForPreferredSetting(
        setting = input.preferredBackendSetting,
        npuStandardRouteMode = input.npuStandardRouteMode,
    )
    val modelFile = input.modelFile.takeIf { it.isNotBlank() && it != "unavailable" }
    val model = modelFile?.let(::File)
    return listOf(
        "[DEV診断: Local inference failure compact]",
        "input_prompt=${escapeLocalInferenceFailureValue(input.inputPrompt)}",
        "selected_backend=${backendDiagnostics.selectedBackend}",
        "requested_backend=${backendDiagnostics.requestedBackend}",
        "effective_backend=${backendDiagnostics.effectiveBackend}",
        "route_family=${backendDiagnostics.routeFamily}",
        "backend_evidence=${backendDiagnostics.backendEvidence}",
        "status=${input.status}",
        "reason=${input.reason}",
        "failure_stage=${input.failureStage.ifBlank { "unknown" }}",
        "failure_exception_class=${input.failureExceptionClass.ifBlank { "unavailable" }}",
        "failure_exception_message=${escapeLocalInferenceFailureValue(input.failureExceptionMessage.ifBlank { "unavailable" })}",
        "failure_cause_class=${input.failureCauseClass.ifBlank { "unavailable" }}",
        "failure_cause_message=${escapeLocalInferenceFailureValue(input.failureCauseMessage.ifBlank { "unavailable" })}",
        "failure_root_cause_class=${input.failureRootCauseClass.ifBlank { "unavailable" }}",
        "failure_root_cause_message=${escapeLocalInferenceFailureValue(input.failureRootCauseMessage.ifBlank { "unavailable" })}",
        "reflection_target_exception_class=${input.reflectionTargetExceptionClass.ifBlank { "unavailable" }}",
        "reflection_target_exception_message=${escapeLocalInferenceFailureValue(input.reflectionTargetExceptionMessage.ifBlank { "unavailable" })}",
        "reflection_target_exception_root_cause_class=${input.reflectionTargetExceptionRootCauseClass.ifBlank { "unavailable" }}",
        "reflection_target_exception_root_cause_message=${escapeLocalInferenceFailureValue(input.reflectionTargetExceptionRootCauseMessage.ifBlank { "unavailable" })}",
        "exception_chain=${escapeLocalInferenceFailureValue(input.exceptionChain.ifBlank { "unavailable" })}",
        "engine_config_backend=${input.engineConfigBackend.ifBlank { "unavailable" }}",
        "preferred_backend_setting=${input.preferredBackendSetting.name}",
        "npu_standard_route_setting=${input.npuStandardRouteMode.name}",
        "normal_chat_native_route_blocked=${input.normalChatNativeRouteBlocked}",
        "blocked_reason=${input.blockedReason.ifBlank { "none" }}",
        "guard_recommendation=unavailable",
        "local_route_started=${input.localRouteStarted}",
        "local_engine_create_started=${input.localEngineCreateStarted}",
        "local_engine_create_finished=${input.localEngineCreateFinished}",
        "local_generate_started=${input.localGenerateStarted}",
        "local_generate_finished=${input.localGenerateFinished}",
        "fallback_used=${input.fallbackUsed}",
        "timeout=${input.timeout}",
        "fresh_crash=${input.freshCrash}",
        "tts_requested=${input.ttsRequested}",
        "db_requested=${input.dbRequested}",
        "markdown_requested=${input.markdownRequested}",
        "streaming_requested=${input.streamingRequested}",
        "model_name=${escapeLocalInferenceFailureValue(input.modelName.ifBlank { "unavailable" })}",
        "model_file=${escapeLocalInferenceFailureValue(input.modelFile.ifBlank { "unavailable" })}",
        "model_exists=${model?.isFile ?: false}",
        "model_size_bytes=${model?.takeIf { it.isFile }?.length()?.toString() ?: "unavailable"}",
        "thread_name=${escapeLocalInferenceFailureValue(input.threadName)}",
        "process_pid=${input.processPid.ifBlank { "unavailable" }}",
        "memory_before_total_pss_mb=${formatLocalFailureNullableLong(input.memoryBefore?.totalPssMb)}",
        "memory_after_total_pss_mb=${formatLocalFailureNullableLong(input.memoryAfter?.totalPssMb)}",
        "java_heap_used_mb=${formatLocalFailureNullableLong(input.javaHeapUsedMb)}",
        "native_heap_alloc_mb=${formatLocalFailureNullableLong(input.nativeHeapAllocMb)}",
    ).joinToString("\n")
}

internal fun buildLocalInferenceFailureCompactInputFromTrace(
    inputPrompt: String,
    preferredBackendSetting: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode,
    trace: LocalInferenceTrace?,
    status: String = "failure",
    reason: String = "local_inference_failure",
    failureDiagnosticsText: String? = trace?.localFailureDiagnosticsText,
    failureStage: String? = null,
    exceptionClass: String? = null,
    exceptionMessage: String? = null,
    throwable: Throwable? = null,
    routeContext: LocalRouteDiagnosticContext? = null,
    timeout: Boolean = false,
    modelName: String? = null,
    modelFile: String? = null,
    ttsRequested: Boolean = false,
    dbRequested: Boolean = false,
    markdownRequested: Boolean = false,
    streamingRequested: Boolean = false,
    processPid: String = "unavailable",
): LocalInferenceFailureCompactInput {
    val parsed = parseLocalInferenceFailureDiagnosticsText(failureDiagnosticsText)
    val snapshots = trace?.memorySnapshots.orEmpty()
    val before = snapshots.firstOrNull { it.stage == MEMORY_STAGE_BEFORE_GENERATE } ?: snapshots.firstOrNull()
    val after = snapshots.lastOrNull { it.stage == MEMORY_STAGE_GENERATION_FAILED } ?: snapshots.lastOrNull()
    val resolvedFailureExceptionClass = exceptionClass
        ?: parsed["failure_exception_class"]
        ?: parsed["exception class"]
        ?: trace?.sessionAsyncPocErrorClassName
        ?: trace?.sessionTokenProbeErrorClassName
        ?: "unavailable"
    val resolvedFailureExceptionMessage = exceptionMessage
        ?: parsed["failure_exception_message"]
        ?: parsed["exception message"]
        ?: trace?.sessionAsyncPocErrorMessage
        ?: trace?.preferredBackendApplyError
        ?: "unavailable"
    val exceptionExpansion = buildLocalFailureExceptionExpansion(
        throwable = throwable,
        parsed = parsed,
        failureExceptionClass = resolvedFailureExceptionClass,
        failureExceptionMessage = resolvedFailureExceptionMessage,
    )
    return LocalInferenceFailureCompactInput(
        inputPrompt = inputPrompt,
        preferredBackendSetting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
        status = status,
        reason = reason,
        failureStage = failureStage
            ?: parsed["failure_stage"]
            ?: parsed["failure stage"]
            ?: trace?.sessionAsyncPocErrorStage
            ?: trace?.sessionTokenProbeErrorStage
            ?: if (timeout) "timeout" else "unknown",
        failureExceptionClass = resolvedFailureExceptionClass,
        failureExceptionMessage = resolvedFailureExceptionMessage,
        failureCauseClass = exceptionExpansion.failureCauseClass,
        failureCauseMessage = exceptionExpansion.failureCauseMessage,
        failureRootCauseClass = exceptionExpansion.failureRootCauseClass,
        failureRootCauseMessage = exceptionExpansion.failureRootCauseMessage,
        reflectionTargetExceptionClass = exceptionExpansion.reflectionTargetExceptionClass,
        reflectionTargetExceptionMessage = exceptionExpansion.reflectionTargetExceptionMessage,
        reflectionTargetExceptionRootCauseClass = exceptionExpansion.reflectionTargetExceptionRootCauseClass,
        reflectionTargetExceptionRootCauseMessage = exceptionExpansion.reflectionTargetExceptionRootCauseMessage,
        exceptionChain = exceptionExpansion.exceptionChain,
        engineConfigBackend = trace?.appliedPreferredBackend
            ?: trace?.requestedPreferredBackend
            ?: when (preferredBackendSetting) {
                PreferredBackendDryRunSetting.DEFAULT -> "Automatic"
                else -> preferredBackendSetting.name
            },
        normalChatNativeRouteBlocked = routeContext?.normalChatNativeRouteBlocked ?: false,
        blockedReason = routeContext?.blockedReason ?: "none",
        localRouteStarted = true,
        localEngineCreateStarted = trace?.localTraceStartElapsedRealtimeMs != null ||
            trace?.heldEngineCreatePath != null ||
            trace?.llmInferenceCreateMethod != null ||
            trace?.createMethodSignature != null,
        localEngineCreateFinished = trace?.heldEngineHash != null ||
            trace?.llmInferenceCreateMethod != null ||
            trace?.createMethodSignature != null,
        localGenerateStarted = trace?.generateMethodSignature != null ||
            trace?.officialFlowAttempted == true ||
            trace?.sessionAsyncPocAttempted == true,
        localGenerateFinished = trace?.localTraceCompletedElapsedRealtimeMs != null,
        fallbackUsed = trace?.officialFlowFallbackReason != null,
        timeout = timeout || trace?.preferredBackendApplyResult == "timeout",
        freshCrash = false,
        ttsRequested = ttsRequested,
        dbRequested = dbRequested,
        markdownRequested = markdownRequested,
        streamingRequested = streamingRequested,
        modelName = modelName
            ?: trace?.localModelDisplayName
            ?: "unavailable",
        modelFile = modelFile
            ?: trace?.mediaPipeProbeModelPath
            ?: "unavailable",
        processPid = processPid,
        memoryBefore = before,
        memoryAfter = after,
    )
}

private data class LocalFailureExceptionExpansion(
    val failureCauseClass: String = "unavailable",
    val failureCauseMessage: String = "unavailable",
    val failureRootCauseClass: String = "unavailable",
    val failureRootCauseMessage: String = "unavailable",
    val reflectionTargetExceptionClass: String = "unavailable",
    val reflectionTargetExceptionMessage: String = "unavailable",
    val reflectionTargetExceptionRootCauseClass: String = "unavailable",
    val reflectionTargetExceptionRootCauseMessage: String = "unavailable",
    val exceptionChain: String = "unavailable",
)

private fun buildLocalFailureExceptionExpansion(
    throwable: Throwable?,
    parsed: Map<String, String>,
    failureExceptionClass: String,
    failureExceptionMessage: String,
): LocalFailureExceptionExpansion =
    throwable?.let(::buildLocalFailureExceptionExpansionFromThrowable)
        ?: buildLocalFailureExceptionExpansionFromParsed(
            parsed = parsed,
            failureExceptionClass = failureExceptionClass,
            failureExceptionMessage = failureExceptionMessage,
        )

private fun buildLocalFailureExceptionExpansionFromThrowable(
    throwable: Throwable,
): LocalFailureExceptionExpansion {
    val chain = localFailureThrowableChain(throwable)
    val root = chain.lastOrNull() ?: throwable
    val target = (throwable as? InvocationTargetException)?.targetException
    val targetChain = target?.let(::localFailureThrowableChain).orEmpty()
    val targetRoot = targetChain.lastOrNull() ?: target
    val failureCause = target ?: throwable.cause
    return LocalFailureExceptionExpansion(
        failureCauseClass = failureCause.localFailureClassNameOrNone(),
        failureCauseMessage = failureCause.localFailureMessageOrNone(),
        failureRootCauseClass = root.javaClass.name,
        failureRootCauseMessage = root.localFailureMessageOrNone(),
        reflectionTargetExceptionClass = target.localFailureClassNameOrNone(),
        reflectionTargetExceptionMessage = target.localFailureMessageOrNone(),
        reflectionTargetExceptionRootCauseClass = targetRoot.localFailureClassNameOrNone(),
        reflectionTargetExceptionRootCauseMessage = targetRoot.localFailureMessageOrNone(),
        exceptionChain = chain.joinToString(" -> ") { cause ->
            "${cause.javaClass.name}:${cause.localFailureMessageOrNone()}"
        }.ifBlank { "none" },
    )
}

private fun buildLocalFailureExceptionExpansionFromParsed(
    parsed: Map<String, String>,
    failureExceptionClass: String,
    failureExceptionMessage: String,
): LocalFailureExceptionExpansion {
    val rootCauseClass = parsed["failure_root_cause_class"]
        ?: parsed["root cause class"]
        ?: parsed["root-cause"]
    val rootCauseMessage = parsed["failure_root_cause_message"]
        ?: parsed["root cause message"]
        ?: parsed["root-cause-message"]
    val parsedTargetClass = parsed["reflection_target_exception_class"]
        ?: parsed["target-exception"]
        ?: parsed["MediaPipe target-exception"]
    val parsedTargetMessage = parsed["reflection_target_exception_message"]
        ?: parsed["target-exception-message"]
        ?: parsed["MediaPipe target-exception-message"]
    val inferredTargetClass = parsedTargetClass
        ?: rootCauseClass.takeIf { failureExceptionClass.endsWith("InvocationTargetException") }
    val inferredTargetMessage = parsedTargetMessage
        ?: rootCauseMessage.takeIf { failureExceptionClass.endsWith("InvocationTargetException") }
    val chain = parsed["exception_chain"]
        ?: parsed["cause chain summary"]
        ?: listOf(failureExceptionClass, failureExceptionMessage)
            .takeIf { failureExceptionClass != "unavailable" }
            ?.joinToString(":")
    return LocalFailureExceptionExpansion(
        failureCauseClass = inferredTargetClass ?: parsed["failure_cause_class"] ?: parsed["cause class"] ?: "unavailable",
        failureCauseMessage = normalizeLocalFailureMessage(
            inferredTargetMessage ?: parsed["failure_cause_message"] ?: parsed["cause message"] ?: "unavailable",
        ),
        failureRootCauseClass = rootCauseClass ?: "unavailable",
        failureRootCauseMessage = normalizeLocalFailureMessage(rootCauseMessage ?: "unavailable"),
        reflectionTargetExceptionClass = inferredTargetClass ?: "none",
        reflectionTargetExceptionMessage = normalizeLocalFailureMessage(inferredTargetMessage ?: "none"),
        reflectionTargetExceptionRootCauseClass = rootCauseClass ?: inferredTargetClass ?: "none",
        reflectionTargetExceptionRootCauseMessage = normalizeLocalFailureMessage(rootCauseMessage ?: inferredTargetMessage ?: "none"),
        exceptionChain = chain ?: "unavailable",
    )
}

private fun localFailureThrowableChain(throwable: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = throwable
    while (current != null && chain.size < LOCAL_FAILURE_EXCEPTION_CHAIN_MAX_DEPTH && current !in chain) {
        chain += current
        current = if (current is InvocationTargetException && current.targetException != null) {
            current.targetException
        } else {
            current.cause
        }
    }
    return chain
}

private fun parseLocalInferenceFailureDiagnosticsText(text: String?): Map<String, String> =
    text
        ?.lineSequence()
        ?.mapNotNull { line ->
            val separatorIndex = line.indexOf('=').takeIf { it > 0 }
                ?: line.indexOf(':').takeIf { it > 0 }
            separatorIndex?.let { index ->
                line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
        }
        ?.toMap()
        .orEmpty()

private fun Throwable?.localFailureClassNameOrNone(): String = this?.javaClass?.name ?: "none"

private fun Throwable?.localFailureMessageOrNone(): String =
    normalizeLocalFailureMessage(this?.message ?: "none")

private fun normalizeLocalFailureMessage(value: String): String =
    value.ifBlank { "none" }

private fun escapeLocalInferenceFailureValue(value: String): String =
    value
        .replace("\r", "\\r")
        .replace("\n", "\\n")

private fun formatLocalFailureNullableLong(value: Long?): String = value?.toString() ?: "unavailable"

private const val LOCAL_FAILURE_EXCEPTION_CHAIN_MAX_DEPTH = 5
