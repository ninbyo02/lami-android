package io.github.ninbyo02.lami.npu

class DevOnlyNpuRoutePlanner(
    private val gate: DevOnlyNpuRouteGateEvaluator = DevOnlyNpuRouteGate,
    private val adapter: DevOnlyNpuRouteAdapter = BlockedDevOnlyNpuRouteAdapter(),
) {
    suspend fun runIfAllowed(
        gateInput: DevOnlyNpuRouteGateInput,
        prompt: String,
        maxOutputTokens: Int = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
        timeoutMs: Long = DevOnlyNpuRouteAdapter.DEFAULT_TIMEOUT_MS,
    ): DevOnlyNpuRouteResult {
        val gateResult = gate.evaluate(gateInput)
        if (!gateResult.allowed) {
            return DevOnlyNpuRouteResult(
                success = false,
                output = null,
                reasonCode = "gate_blocked:${gateResult.reason.name}",
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

        return adapter.runOnce(
            prompt = prompt,
            maxOutputTokens = maxOutputTokens,
            timeoutMs = timeoutMs,
        )
    }
}
