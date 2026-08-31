package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NpuPersistentHolderCreateCloseDevProbe(
    context: Context,
) : NpuPersistentHolderCreateCloseProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(): NpuPersistentHolderCreateCloseProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path ?: modelResolution.reasonCode
        val api = NativeStubNpuPersistentHolderApi
        var createResult: NpuPersistentHolderApiResult? = null
        var diagnosticsAfterCreate: NpuPersistentHolderApiDiagnostics? = null
        var closeResult: NpuPersistentHolderApiResult? = null
        var diagnosticsAfterClose: NpuPersistentHolderApiDiagnostics? = null
        var secondCloseResult: NpuPersistentHolderApiResult? = null
        var diagnosticsAfterSecondClose: NpuPersistentHolderApiDiagnostics? = null
        try {
            createResult = api.createHolder(
                NpuPersistentHolderCreateRequest(
                    modelPath = modelPath,
                    nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
                    cacheDir = appContext.cacheDir.absolutePath,
                    maxTokens = NPU_S1_PERSISTENT_ENGINE_REQUESTED_MAX_OUTPUT_TOKENS,
                ),
            )
            val holderId = createResult.holderId.ifBlank { "unavailable" }
            diagnosticsAfterCreate = api.getDiagnostics(holderId)
            closeResult = api.closeHolder(
                NpuPersistentHolderCloseRequest(
                    holderId = holderId,
                    reason = "dev_ui_create_close_probe",
                ),
            )
            diagnosticsAfterClose = api.getDiagnostics(holderId)
            secondCloseResult = api.closeHolder(
                NpuPersistentHolderCloseRequest(
                    holderId = holderId,
                    reason = "dev_ui_double_close_safety_probe",
                ),
            )
            diagnosticsAfterSecondClose = api.getDiagnostics(holderId)
            NpuPersistentHolderCreateCloseProbeState(
                status = "completed",
                reason = diagnosticsAfterSecondClose.reason,
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = modelPath,
                createResult = createResult,
                diagnosticsAfterCreate = diagnosticsAfterCreate,
                closeResult = closeResult,
                diagnosticsAfterClose = diagnosticsAfterClose,
                secondCloseResult = secondCloseResult,
                diagnosticsAfterSecondClose = diagnosticsAfterSecondClose,
            )
        } catch (throwable: Throwable) {
            NpuPersistentHolderCreateCloseProbeState(
                status = "failed",
                reason = throwable.message ?: "holder_create_close_probe_failed",
                startedAtElapsedRealtimeMs = startedAt,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                modelPathOrReason = modelPath,
                createResult = createResult,
                diagnosticsAfterCreate = diagnosticsAfterCreate,
                closeResult = closeResult,
                diagnosticsAfterClose = diagnosticsAfterClose,
                secondCloseResult = secondCloseResult,
                diagnosticsAfterSecondClose = diagnosticsAfterSecondClose,
                throwableClass = throwable.javaClass.name,
                throwableMessage = throwable.message ?: "unavailable",
            )
        }
    }
}
