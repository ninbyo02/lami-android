package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig

internal suspend fun recountLocalInferenceTokensAfterCompletion(
    context: Context,
    modelPath: String?,
    prompt: String,
    response: String,
    trace: LocalInferenceTrace,
    allowStandaloneGpu: Boolean = false,
): LocalInferenceTrace = localTokenRecountCoordinator.execute(fallback = trace) {
    val startedAtMs = trace.localTraceStartElapsedRealtimeMs
        ?: return@execute trace
    val endedAtMs = trace.localTraceCompletedElapsedRealtimeMs
        ?: return@execute trace
    val existingSnapshot = trace.measuredTokenSnapshot
    val recountInput = resolveDeferredTokenizerInput(existingSnapshot, prompt, response)
    val recountedSnapshot = mergeTokenizerRecountSnapshot(
        base = existingSnapshot,
        deferMediaPipeRecount = false,
        allowStandaloneGpu = allowStandaloneGpu,
        conversation = null,
        tokenizerSessionSource = null,
        mediaPipeProbeModelPath = modelPath ?: trace.mediaPipeProbeModelPath,
        mediaPipeProbeContext = context.applicationContext,
        promptText = recountInput.prompt,
        fullResponseText = recountInput.response,
        timing = LocalLiteRtTimingSnapshot(
            startedAtMs = startedAtMs,
            firstNonEmptyChunkAtMs = trace.localTraceFirstResponseElapsedRealtimeMs,
            lastChunkAtMs = trace.localTraceFirstResponseElapsedRealtimeMs?.let { first ->
                existingSnapshot?.decodeDurationMs?.let { first + it }
            } ?: endedAtMs,
            endedAtMs = endedAtMs,
        ),
        appendTrace = { message ->
            if (BuildConfig.DEBUG) Log.d("LocalTokenizerRecount", message)
        },
    ) ?: return@execute trace
    recordStandaloneTokenCountComparison(
        modelPath ?: trace.mediaPipeProbeModelPath, recountInput.prompt, recountInput.response, recountedSnapshot,
    )
    trace.copy(
        mediaPipeProbeModelPath = modelPath ?: trace.mediaPipeProbeModelPath,
        measuredTokenSnapshot = recountedSnapshot,
    )
}
