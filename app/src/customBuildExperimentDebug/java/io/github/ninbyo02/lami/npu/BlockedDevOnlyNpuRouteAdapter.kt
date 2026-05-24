package io.github.ninbyo02.lami.npu

import io.github.ninbyo02.lami.BuildConfig

class BlockedDevOnlyNpuRouteAdapter : DevOnlyNpuRouteAdapter {
    override suspend fun runOnce(
        prompt: String,
        maxOutputTokens: Int,
        timeoutMs: Long,
    ): DevOnlyNpuRouteResult {
        check(BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
            "DEV-only NPU route adapter is customBuildExperimentDebug-only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
        }

        return DevOnlyNpuRouteResult(
            success = false,
            output = null,
            reasonCode = REASON_ADAPTER_NOT_CONNECTED,
            elapsedMs = null,
            decodeElapsedMs = null,
            prompt = prompt,
            maxOutputTokens = maxOutputTokens,
            backendEvidence = null,
            artifactPath = null,
            freshCrash = false,
            timeout = false,
        )
    }

    companion object {
        const val REASON_ADAPTER_NOT_CONNECTED = "adapter_not_connected"
    }
}
