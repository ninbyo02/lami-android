package io.github.ninbyo02.lami.npu

interface DevOnlyNpuRouteAdapter {
    suspend fun runOnce(
        prompt: String,
        maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): DevOnlyNpuRouteResult

    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 128
        const val QAIRT244_MAX_OUTPUT_TOKENS_COMPARE_LIMIT = 512
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}

data class DevOnlyNpuRouteResult(
    val success: Boolean,
    val output: String?,
    val reasonCode: String,
    val elapsedMs: Long?,
    val decodeElapsedMs: Long?,
    val prompt: String,
    val maxOutputTokens: Int,
    val backendEvidence: String?,
    val artifactPath: String?,
    val freshCrash: Boolean,
    val timeout: Boolean,
)
