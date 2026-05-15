package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import android.util.Log

class NpuExperimentProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NpuExperimentProbeLogger.logSnapshot(applicationContext)
        finish()
    }
}

internal object NpuExperimentProbeLogger {
    fun logSnapshot(context: android.content.Context) {
        val snapshot = AcceleratorProbe.captureSnapshot(
            context = context.applicationContext,
            forceRefresh = true,
        )
        val dispatchLine =
            "Dispatch Runtime Compatibility: " +
                "current flavor=${snapshot.currentFlavor ?: "unknown"}; " +
                "applicationId=${snapshot.applicationId ?: "unknown"}; " +
                "nativeLibraryDir=${snapshot.dispatchNativeLibraryDir ?: "unknown"}; " +
                "nativeLibraryDir exists=${snapshot.dispatchNativeLibraryDirExists ?: "unknown"}; " +
                "dispatch runtime present in nativeLibraryDir=${snapshot.dispatchRuntimePresentInFlavor ?: "unknown"}; " +
                "dispatch runtime file path=${snapshot.dispatchRuntimeFilePath ?: "unknown"}; " +
                "dispatch runtime file length=${snapshot.dispatchRuntimeFileLength ?: "unknown"}; " +
                "dispatch runtime sha256=${snapshot.dispatchRuntimeSha256 ?: "unknown"}; " +
                "expected sha256 match=${snapshot.dispatchRuntimeExpectedSha256Match ?: "unknown"}; " +
                "dispatch runtime build id=${snapshot.dispatchRuntimeBuildId ?: "unknown"}; " +
                "ABI compatibility=${snapshot.dispatchRuntimeAbiCompatibility ?: "unknown"}; " +
                "load policy=diagnostic-only; no System.loadLibrary; no Backend.NPU apply"
        val instantiateLine =
            "Backend.NPU Instantiate Probe: " +
                "enabled=${snapshot.backendNpuInstantiateProbeEnabled ?: "unknown"}; " +
                "reason if skipped=${snapshot.backendNpuInstantiateProbeSkipReason ?: "none"}; " +
                "constructor=${snapshot.backendNpuInstantiateConstructor ?: "Backend.NPU(String)"}; " +
                "nativeLibraryDir argument=${snapshot.backendNpuInstantiateNativeLibraryDirArgument ?: "unknown"}; " +
                "instantiate result=${snapshot.backendNpuInstantiateResult ?: "unknown"}; " +
                "object class=${snapshot.backendNpuInstantiateObjectClass ?: "-"}; " +
                "exception class=${snapshot.backendNpuInstantiateExceptionClass ?: "-"}; " +
                "exception message=${snapshot.backendNpuInstantiateExceptionMessage ?: "-"}; " +
                "root cause=${snapshot.backendNpuInstantiateRootCause ?: "-"}; " +
                "cause chain=${snapshot.backendNpuInstantiateCauseChain ?: "-"}; " +
                "warning=${snapshot.backendNpuInstantiateWarning ?: "instantiate-only; object not passed to engine; no inference"}"
        val attachDryRunLine =
            "Backend.NPU Attach Dry-Run Probe: " +
                "enabled=${snapshot.backendNpuAttachDryRunEnabled ?: "unknown"}; " +
                "skipped reason=${snapshot.backendNpuAttachDryRunSkipReason ?: "none"}; " +
                "npu object class=${snapshot.backendNpuAttachDryRunNpuObjectClass ?: "-"}; " +
                "target builder candidates=${snapshot.backendNpuAttachDryRunTargetBuilderCandidates.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none/unknown"}; " +
                "setter candidates=${snapshot.backendNpuAttachDryRunSetterCandidates.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none/unknown"}; " +
                "selected setter=${snapshot.backendNpuAttachDryRunSelectedSetter ?: "-"}; " +
                "setter invoke result=${snapshot.backendNpuAttachDryRunSetterInvokeResult ?: "unknown"}; " +
                "build invoked=${snapshot.backendNpuAttachDryRunBuildInvoked ?: "no"}; " +
                "build result=${snapshot.backendNpuAttachDryRunBuildResult ?: "skipped"}; " +
                "exception class=${snapshot.backendNpuAttachDryRunExceptionClass ?: "-"}; " +
                "exception message=${snapshot.backendNpuAttachDryRunExceptionMessage ?: "-"}; " +
                "root cause=${snapshot.backendNpuAttachDryRunRootCause ?: "-"}; " +
                "cause chain=${snapshot.backendNpuAttachDryRunCauseChain ?: "-"}; " +
                "warning=${snapshot.backendNpuAttachDryRunWarning ?: "attach-dry-run only; no Engine; no Conversation; no inference"}; " +
                "note=${snapshot.backendNpuAttachDryRunNote ?: "This setter belongs to MediaPipe LlmInference.Backend enum path and is not assignable from LiteRT-LM Backend.NPU."}"
        val apiInventoryLine =
            "LiteRT-LM NPU API Inventory: " +
                "enabled=${snapshot.liteRtLmNpuApiInventoryEnabled ?: "unknown"}; " +
                "skipped reason=${snapshot.liteRtLmNpuApiInventorySkipReason ?: "none"}; " +
                "classes=${snapshot.liteRtLmNpuApiClassInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "EngineConfig constructors=${snapshot.engineConfigConstructorInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "assignability=${snapshot.liteRtLmNpuApiAssignability.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "backend property=${snapshot.engineConfigBackendPropertyInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "copy=${snapshot.engineConfigCopyMethodInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "componentN=${snapshot.engineConfigComponentMethodInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}; " +
                "json=${snapshot.engineConfigJsonMethodInventory.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "none/unknown"}"
        val engineConfigDryBuildLine =
            "EngineConfig NPU Dry-Build Probe: " +
                "enabled=${snapshot.engineConfigNpuDryBuildEnabled ?: "unknown"}; " +
                "skipped reason=${snapshot.engineConfigNpuDryBuildSkipReason ?: "none"}; " +
                "selected constructor=${snapshot.engineConfigNpuDryBuildSelectedConstructor ?: "-"}; " +
                "constructor args summary=${snapshot.engineConfigNpuDryBuildConstructorArgsSummary ?: "-"}; " +
                "npu backend object class=${snapshot.engineConfigNpuDryBuildNpuBackendObjectClass ?: "-"}; " +
                "result=${snapshot.engineConfigNpuDryBuildResult ?: "unknown"}; " +
                "created object class=${snapshot.engineConfigNpuDryBuildCreatedObjectClass ?: "-"}; " +
                "backend getter result class=${snapshot.engineConfigNpuDryBuildBackendGetterResultClass ?: "-"}; " +
                "exception class=${snapshot.engineConfigNpuDryBuildExceptionClass ?: "-"}; " +
                "exception message=${snapshot.engineConfigNpuDryBuildExceptionMessage ?: "-"}; " +
                "root cause=${snapshot.engineConfigNpuDryBuildRootCause ?: "-"}; " +
                "cause chain=${snapshot.engineConfigNpuDryBuildCauseChain ?: "-"}; " +
                "warning=${snapshot.engineConfigNpuDryBuildWarning ?: "config-only; not passed to Engine; no inference"}"
        val connectionCandidateLine =
            "Backend.NPU Connection Candidate: " +
                "preferredBackend enum path=${snapshot.backendNpuConnectionPreferredBackendEnumPath ?: "unknown"}; " +
                "reason=${snapshot.backendNpuConnectionPreferredBackendEnumReason ?: "unknown"}; " +
                "EngineConfig backend path=${snapshot.backendNpuConnectionEngineConfigBackendPath ?: "unknown"}; " +
                "Engine initialize path=${snapshot.backendNpuConnectionEngineInitializePath ?: "not attempted"}; " +
                "recommended next phase=${snapshot.backendNpuConnectionRecommendedNextPhase ?: "unknown"}"
        val safetyLine =
            "NPU safety status: " +
                "selectedPath=${snapshot.qnnNpuSelectedPath ?: "unknown"}; " +
                "QNN/NPU attempted=${if (snapshot.qnnNpuAttempted) "yes" else "no"}; " +
                "fallbackPath=${snapshot.qnnNpuFallbackPath ?: "-"}; " +
                "NPU apply status=disabled / probe-only"

        listOf(dispatchLine, instantiateLine, attachDryRunLine, apiInventoryLine, engineConfigDryBuildLine, connectionCandidateLine, safetyLine).forEach { line ->
            Log.i(LOG_TAG, line)
        }
        runCatching {
            context.filesDir.resolve("npu_experiment_probe.txt").writeText(
                listOf(dispatchLine, instantiateLine, attachDryRunLine, apiInventoryLine, engineConfigDryBuildLine, connectionCandidateLine, safetyLine).joinToString(separator = "\n", postfix = "\n"),
            )
        }.onFailure { throwable ->
            Log.e(LOG_TAG, "Failed to write probe result: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private const val LOG_TAG = "NpuExperimentProbe"
}
