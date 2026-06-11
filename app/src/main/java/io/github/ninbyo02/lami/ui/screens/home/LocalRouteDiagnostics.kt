package io.github.ninbyo02.lami.ui.screens.home

import java.io.File

internal data class LocalRouteDiagnosticContext(
    val selectedModelName: String,
    val selectedModelFile: String,
    val preferredBackend: String,
    val npuStandardRouteMode: String,
    val effectiveNpuStandardRouteMode: String,
    val shouldEnterNpuS1: Boolean,
    val localRouteEntered: Boolean,
    val normalChatNativeRouteBlocked: Boolean,
    val blockedReason: String,
    val modelKind: String,
    val baselineRole: String,
    val genericModelCpuBaseline: Boolean,
)

internal data class LocalRouteDiagnosticFlags(
    val heldEngineExists: Boolean? = null,
    val heldEngineReused: Boolean? = null,
    val engineCreateStarted: Boolean? = null,
    val engineCreateFinished: Boolean? = null,
    val conversationCreateStarted: Boolean? = null,
    val conversationCreateFinished: Boolean? = null,
    val generateStarted: Boolean? = null,
    val firstTokenReceived: Boolean? = null,
    val failureStage: String? = null,
    val fallbackUsed: Boolean? = null,
    val staleCallbackIgnored: Boolean? = null,
)

internal fun buildLocalRouteDiagnosticContext(
    selectedModelName: String?,
    selectedModelFile: String?,
    preferredBackend: String,
    npuStandardRouteMode: String,
    effectiveNpuStandardRouteMode: String = npuStandardRouteMode,
    shouldEnterNpuS1: Boolean,
    localRouteEntered: Boolean,
    normalChatNativeRouteBlocked: Boolean = false,
    blockedReason: String = "none",
): LocalRouteDiagnosticContext {
    val modelName = selectedModelName?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
    val modelFile = selectedModelFile
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { path -> File(path).name.ifBlank { path } }
        ?: "unknown"
    val modelKind = classifyLiteRtLmModelKindForBaseline(
        selectedModelFile?.trim()?.takeIf { it.isNotBlank() } ?: modelName,
    )
    val baselineRole = resolveLiteRtLmBaselineRole(
        modelKind = modelKind,
        preferredBackend = preferredBackend,
    )
    return LocalRouteDiagnosticContext(
        selectedModelName = modelName,
        selectedModelFile = modelFile,
        preferredBackend = preferredBackend,
        npuStandardRouteMode = npuStandardRouteMode,
        effectiveNpuStandardRouteMode = effectiveNpuStandardRouteMode,
        shouldEnterNpuS1 = shouldEnterNpuS1,
        localRouteEntered = localRouteEntered,
        normalChatNativeRouteBlocked = normalChatNativeRouteBlocked,
        blockedReason = blockedReason,
        modelKind = modelKind,
        baselineRole = baselineRole,
        genericModelCpuBaseline = isGenericLiteRtLmCpuStableBaseline(
            modelKind = modelKind,
            baselineRole = baselineRole,
        ),
    )
}

internal fun buildLocalRouteDiagnosticTrace(
    stage: String,
    context: LocalRouteDiagnosticContext,
    flags: LocalRouteDiagnosticFlags = LocalRouteDiagnosticFlags(),
    elapsedMs: Long = 0L,
): String = listOf(
    "LOCAL_ROUTE_DIAG",
    "stage=$stage",
    "selected_model_name=${context.selectedModelName}",
    "selected_model_file=${context.selectedModelFile}",
    "model_kind=${context.modelKind}",
    "preferred_backend=${context.preferredBackend}",
    "baseline_role=${context.baselineRole}",
    "generic_model_cpu_baseline=${context.genericModelCpuBaseline}",
    "npu_standard_route_mode=${context.npuStandardRouteMode}",
    "effective_npu_standard_route_mode=${context.effectiveNpuStandardRouteMode}",
    "should_enter_npu_s1=${context.shouldEnterNpuS1}",
    "local_route_entered=${context.localRouteEntered}",
    "normal_chat_native_route_blocked=${context.normalChatNativeRouteBlocked}",
    "blocked_reason=${context.blockedReason}",
    "held_engine_exists=${flags.heldEngineExists.toDiagnosticValue()}",
    "held_engine_reused=${flags.heldEngineReused.toDiagnosticValue()}",
    "engine_create_started=${flags.engineCreateStarted.toDiagnosticValue()}",
    "engine_create_finished=${flags.engineCreateFinished.toDiagnosticValue()}",
    "conversation_create_started=${flags.conversationCreateStarted.toDiagnosticValue()}",
    "conversation_create_finished=${flags.conversationCreateFinished.toDiagnosticValue()}",
    "generate_started=${flags.generateStarted.toDiagnosticValue()}",
    "first_token_received=${flags.firstTokenReceived.toDiagnosticValue()}",
    "failure_stage=${flags.failureStage?.takeIf { it.isNotBlank() } ?: "none"}",
    "fallback_used=${flags.fallbackUsed.toDiagnosticValue()}",
    "stale_callback_ignored=${flags.staleCallbackIgnored.toDiagnosticValue()}",
    "elapsed_ms=${elapsedMs.coerceAtLeast(0L)}",
).joinToString(" ")

private fun Boolean?.toDiagnosticValue(): String = this?.toString() ?: "unknown"

internal const val GPU_EXPERIMENTAL_STAGE_TIMEOUT_MS = 20_000L
internal const val GPU_EXPERIMENTAL_TIMEOUT_MESSAGE =
    "GPU backend の初期化または生成開始がタイムアウトしました。Generic LiteRT-LMモデルではCPU backendを選択してください。"

internal fun shouldApplyGpuExperimentalStageTimeout(
    context: LocalRouteDiagnosticContext,
): Boolean =
    context.localRouteEntered &&
        context.baselineRole == LITERT_LM_BASELINE_GPU_EXPERIMENTAL

internal fun resolveGpuExperimentalTimeoutFailureStage(
    lastStage: String?,
): String =
    when (lastStage) {
        "engine_create_started" -> "engine_create_timeout"
        "conversation_create_started" -> "conversation_create_timeout"
        "conversation_create_finished" -> "generate_start_timeout"
        "generate_started" -> "first_token_timeout"
        else -> "engine_create_timeout"
    }
