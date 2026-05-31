package io.github.ninbyo02.lami.ui.screens.home

import java.io.File

internal data class LocalRouteDiagnosticContext(
    val selectedModelName: String,
    val selectedModelFile: String,
    val preferredBackend: String,
    val npuStandardRouteMode: String,
    val shouldEnterNpuS1: Boolean,
    val localRouteEntered: Boolean,
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
)

internal fun buildLocalRouteDiagnosticContext(
    selectedModelName: String?,
    selectedModelFile: String?,
    preferredBackend: String,
    npuStandardRouteMode: String,
    shouldEnterNpuS1: Boolean,
    localRouteEntered: Boolean,
): LocalRouteDiagnosticContext =
    LocalRouteDiagnosticContext(
        selectedModelName = selectedModelName?.trim()?.takeIf { it.isNotBlank() } ?: "unknown",
        selectedModelFile = selectedModelFile
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> File(path).name.ifBlank { path } }
            ?: "unknown",
        preferredBackend = preferredBackend,
        npuStandardRouteMode = npuStandardRouteMode,
        shouldEnterNpuS1 = shouldEnterNpuS1,
        localRouteEntered = localRouteEntered,
    )

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
    "preferred_backend=${context.preferredBackend}",
    "npu_standard_route_mode=${context.npuStandardRouteMode}",
    "should_enter_npu_s1=${context.shouldEnterNpuS1}",
    "local_route_entered=${context.localRouteEntered}",
    "held_engine_exists=${flags.heldEngineExists.toDiagnosticValue()}",
    "held_engine_reused=${flags.heldEngineReused.toDiagnosticValue()}",
    "engine_create_started=${flags.engineCreateStarted.toDiagnosticValue()}",
    "engine_create_finished=${flags.engineCreateFinished.toDiagnosticValue()}",
    "conversation_create_started=${flags.conversationCreateStarted.toDiagnosticValue()}",
    "conversation_create_finished=${flags.conversationCreateFinished.toDiagnosticValue()}",
    "generate_started=${flags.generateStarted.toDiagnosticValue()}",
    "first_token_received=${flags.firstTokenReceived.toDiagnosticValue()}",
    "failure_stage=${flags.failureStage?.takeIf { it.isNotBlank() } ?: "none"}",
    "elapsed_ms=${elapsedMs.coerceAtLeast(0L)}",
).joinToString(" ")

private fun Boolean?.toDiagnosticValue(): String = this?.toString() ?: "unknown"
