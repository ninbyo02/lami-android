package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NpuTrueEngineHolderCreateCloseDevProbe(
    context: Context,
) : NpuTrueEngineHolderCreateCloseProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(): NpuTrueEngineHolderCreateCloseProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path ?: modelResolution.reasonCode
        val holderId = "true-engine-holder-create-close-dev"
        val nativeResult = runCatching {
            Qairt244ShortMultitokenSmoke.runTrueEngineHolderCreateCloseProbe(
                context = appContext,
                modelPath = modelPath,
                runId = "true_engine_holder_create_close",
                maxOutputTokens = NPU_PERSISTENT_HOLDER_RUN_ONCE_MAX_OUTPUT_TOKENS,
                holderKey = holderId,
            )
        }.getOrElse { throwable ->
            NpuTrueEngineHolderNativeResult(
                throwableClass = throwable.javaClass.name,
                throwableMessage = throwable.message ?: "unavailable",
            )
        }
        val state = NpuTrueEngineHolderCreateCloseProbeState(
            status = if (nativeResult.throwableClass == "unavailable") "completed" else "failed",
            reason = resolveReason(nativeResult),
            startedAtElapsedRealtimeMs = startedAt,
            finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            modelPathOrReason = modelPath,
            holderId = holderId,
            nativeResult = nativeResult,
        )
        state
    }

    private fun resolveReason(result: NpuTrueEngineHolderNativeResult): String {
        if (result.throwableClass != "unavailable") {
            return result.throwableMessage.ifBlank { result.throwableClass }
        }
        val values = result.resultText.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index > 0) line.substring(0, index) to line.substring(index + 1) else null
            }
            .toMap()
        return values["persistent_custom_jni_hypothesis_result"]
            ?: values["persistent_custom_jni_status"]
            ?: result.nativeReturn.ifBlank { "unavailable" }
    }
}
