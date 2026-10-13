package io.github.ninbyo02.lami.tts

/**
 * CP model lifetime is independent of the upstream LLM execution location.
 * A new generation/session epoch or a CP artifact change invalidates resident state.
 */
internal object ResidentCpRoutingPolicy {
    enum class InferenceRoute { LOCAL_CPU, LOCAL_GPU, LOCAL_NPU, SERVER }
    data class VoiceIdentity(
        val artifactSha256: String,
        val voiceId: String,
        val executor: String,
    ) {
        init {
            require(artifactSha256.length == 64 && artifactSha256.all { it in "0123456789abcdef" })
            require(voiceId.isNotBlank() && executor.isNotBlank())
        }
    }
    fun shouldReload(
        current: VoiceIdentity?,
        requested: VoiceIdentity,
        upstreamRoute: InferenceRoute,
    ): Boolean {
        // Deliberately do not include upstreamRoute in CP's cache identity.
        return current != requested
    }
}
