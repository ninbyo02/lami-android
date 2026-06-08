package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NpuS1PersistentCustomJniDevProbe(
    context: Context,
) : NpuS1PersistentCustomJniProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(
        onUpdate: (NpuS1PersistentCustomJniProbeState) -> Unit,
        isCancelled: () -> Boolean,
    ): NpuS1PersistentCustomJniProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val cacheDir = appContext.cacheDir.absolutePath
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path.orEmpty()
        val modelFile = modelPath.takeIf { it.isNotBlank() }?.let(::File)
        val modelFileSize = modelFile?.takeIf { it.exists() }?.length()?.toString() ?: "unavailable"
        val modelLastModified = modelFile?.takeIf { it.exists() }?.lastModified()?.toString() ?: "unavailable"
        val holderKey = NpuS1PersistentCustomJniHolderKey(
            modelPath = modelPath.ifBlank { modelResolution.reasonCode },
            modelFileLastModified = modelLastModified,
            modelFileSize = modelFileSize,
            backend = NPU_S1_PERSISTENT_CUSTOM_JNI_BACKEND,
            cacheDir = cacheDir,
            maxTokenBudget = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS.toString(),
            engineConfigVersion = NPU_S1_PERSISTENT_CUSTOM_JNI_ENGINE_CONFIG_VERSION,
        )
        var state = NpuS1PersistentCustomJniProbeState(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_RUNNING,
            runCountRequested = NPU_S1_PERSISTENT_CUSTOM_JNI_DEFAULT_COUNT,
            runCountCompletedOverride = 0,
            startedAtElapsedRealtimeMs = startedAt,
            engineCreateCount = "0",
            engineCloseReached = "false",
            engineCloseSuccess = "unavailable",
            holderKey = holderKey,
            holderGeneration = "unavailable",
            holderReusedCount = "0",
            holderInvalidated = "false",
            holderKeyMismatchDetected = "false",
            holderKeyMismatchReason = "unavailable",
            nativeHolderEntrypointAvailable = "false",
            modelPath = holderKey.modelPath,
            modelFileSize = modelFileSize,
            modelFileLastModified = modelLastModified,
            backendEvidence = "custom_jni_persistent_holder_probe_requested",
            persistentCustomJniHypothesisResult = "starting",
        )
        fun update(next: NpuS1PersistentCustomJniProbeState) {
            state = next
            onUpdate(next)
        }
        update(state)

        if (isCancelled()) {
            return@withContext state.copy(
                persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_CANCELLED,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                persistentCustomJniHypothesisResult = "cancelled",
            ).also(::update)
        }

        val failureReason = if (modelPath.isBlank()) {
            "model_resolution_failed:${modelResolution.reasonCode}"
        } else {
            "native_persistent_holder_entrypoint_not_available"
        }
        val failureStage = if (modelPath.isBlank()) {
            "model_resolve"
        } else {
            "native_holder_entrypoint"
        }
        val diagTail = if (modelPath.isBlank()) {
            "persistent_custom_jni_holder_not_started model_resolution=${modelResolution.reasonCode}"
        } else {
            "persistent custom JNI holder requires a native litertlm_jni entrypoint that keeps EngineFactory::CreateDefault result alive; checked-in app C++ exposes only one-shot nativeRunEditablePrompt path"
        }
        state.copy(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED,
            finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            holderInvalidated = "true",
            firstFailureRunIndex = null,
            firstFailureStage = failureStage,
            firstFailureReason = failureReason,
            firstFailureExceptionClass = "unavailable",
            firstFailureDiagTail = diagTail,
            backendEvidence = "custom_jni_persistent_holder_entrypoint_missing",
            persistentCustomJniHypothesisResult = if (modelPath.isBlank()) {
                "model_resolution_failed"
            } else {
                "native_holder_entrypoint_not_available"
            },
        ).also(::update)
    }
}
