package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.local.buildLocalInferenceFailureDiagnosticsText
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import kotlinx.coroutines.CancellationException

internal suspend fun tryRunOfficialLiteRtFlowStreaming(
    prompt: String,
    modelPath: String,
    cacheDirPath: String,
    mediaPipeProbeContext: Context? = null,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    markdownStreamingMode: MarkdownStreamingMode = MarkdownStreamingMode.DEFAULT,
    initialTurns: List<LocalConversationTurn> = emptyList(),
    onPreferredBackendApplied: (PreferredBackendApplyResult) -> Unit = {},
    onPartial: (String) -> Unit,
    appendTrace: (String) -> Unit = {},
    onFallbackReason: (String) -> Unit = {},
    onFailureDiagnostics: ((String) -> Unit)? = null,
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
                markdownStreamingMode = markdownStreamingMode,
                initialTurns = initialTurns,
                onPreferredBackendApplied = onPreferredBackendApplied,
                onPartial = onPartial,
                appendTrace = appendTrace,
                onFailureDiagnostics = onFailureDiagnostics,
            )
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            val reasonCode = (throwable as? OfficialFlowFallbackException)?.reasonCode ?: "official_exception"
            fallbackReasonReported = true
            runCatching { onFallbackReason(reasonCode) }
            mediaPipeProbeContext?.let { context ->
                val diagnostics = buildLocalInferenceFailureDiagnosticsText(
                    context = context,
                    stage = failureStageForOfficialReason(reasonCode),
                    throwable = throwable,
                    selectedModelName = modelPath,
                    selectedFallbackPath = "gpu",
                )
                safeAppendTrace(appendTrace, "UPSTREAM official-flow failure-diagnostics\n$diagnostics")
                runCatching { onFailureDiagnostics?.invoke(diagnostics) }
            }
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
    onFailureDiagnostics: ((String) -> Unit)? = null,
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
                onFailureDiagnostics = onFailureDiagnostics,
            )
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            val reasonCode = (throwable as? OfficialFlowFallbackException)?.reasonCode ?: "official_blocking_exception"
            runCatching { onFallbackReason(reasonCode) }
            mediaPipeProbeContext?.let { context ->
                val diagnostics = buildLocalInferenceFailureDiagnosticsText(
                    context = context,
                    stage = failureStageForOfficialReason(reasonCode),
                    throwable = throwable,
                    selectedModelName = modelPath,
                    selectedFallbackPath = "gpu",
                )
                safeAppendTrace(appendTrace, "UPSTREAM official-blocking failure-diagnostics\n$diagnostics")
                runCatching { onFailureDiagnostics?.invoke(diagnostics) }
            }
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

internal class OfficialFlowFallbackException(
    val reasonCode: String,
    cause: Throwable? = null,
) : RuntimeException(reasonCode, cause)

internal data class LocalOfficialNamespaceSpec(
    val namespace: String,
    val engineClassName: String,
    val optionsCandidates: List<String>,
)
