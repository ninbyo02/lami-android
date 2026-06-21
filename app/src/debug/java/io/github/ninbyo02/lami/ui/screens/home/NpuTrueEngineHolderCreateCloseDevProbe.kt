package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NpuTrueEngineHolderCreateCloseDevProbe(
    context: Context,
) : NpuTrueEngineHolderCreateCloseProbeRunner {
    @Suppress("unused")
    private val appContext = context.applicationContext

    override suspend fun run(): NpuTrueEngineHolderCreateCloseProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val holderId = "true-engine-holder-create-close-dev"
        NpuTrueEngineHolderCreateCloseProbeState(
            status = "blocked",
            reason = NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_BLOCK_REASON,
            startedAtElapsedRealtimeMs = startedAt,
            finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            modelPathOrReason = "not_resolved_startup_safe_block",
            holderId = holderId,
            nativeResult = blockedNpuTrueEngineHolderCreateCloseNativeResult(),
        )
    }
}
