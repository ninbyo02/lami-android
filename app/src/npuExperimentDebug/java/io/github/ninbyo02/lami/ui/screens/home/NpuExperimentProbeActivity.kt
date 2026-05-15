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
        val safetyLine =
            "NPU safety status: " +
                "selectedPath=${snapshot.qnnNpuSelectedPath ?: "unknown"}; " +
                "QNN/NPU attempted=${if (snapshot.qnnNpuAttempted) "yes" else "no"}; " +
                "fallbackPath=${snapshot.qnnNpuFallbackPath ?: "-"}; " +
                "NPU apply status=disabled / probe-only"

        listOf(dispatchLine, instantiateLine, safetyLine).forEach { line ->
            Log.i(LOG_TAG, line)
        }
        runCatching {
            context.filesDir.resolve("npu_experiment_probe.txt").writeText(
                listOf(dispatchLine, instantiateLine, safetyLine).joinToString(separator = "\n", postfix = "\n"),
            )
        }.onFailure { throwable ->
            Log.e(LOG_TAG, "Failed to write probe result: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private const val LOG_TAG = "NpuExperimentProbe"
}
