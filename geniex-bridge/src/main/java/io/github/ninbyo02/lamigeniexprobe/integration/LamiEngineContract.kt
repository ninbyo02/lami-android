package io.github.ninbyo02.lamigeniexprobe.integration

import kotlinx.coroutines.flow.Flow

/**
 * The LAMI client uses this contract to stream from the GenieX companion.
 */
interface LamiTextEngine {
    fun stream(request: LamiInferenceRequest): Flow<LamiEngineEvent>
}

enum class LamiComputeChoice(val wireName: String) {
    CPU("cpu"), GPU("gpu"), NPU("npu"), HYBRID("hybrid"),
}

data class LamiChatTurn(val role: String, val content: String) {
    init {
        require(role in listOf("system", "user", "assistant"))
        require(content.isNotBlank() && content.length <= LamiInferenceRequest.MAX_TURN_CHARS)
    }
}

data class LamiInferenceRequest(
    val id: Int,
    val compute: LamiComputeChoice,
    val turns: List<LamiChatTurn>,
    val maxTokens: Int = 256,
) {
    init {
        require(id >= 0)
        require(maxTokens in 1..MAX_OUTPUT_TOKENS)
        require(turns.size in 1..MAX_TURNS)
        require(turns.sumOf { it.content.length } <= MAX_HISTORY_CHARS)
        require(turns.last().role == "user")
    }

    companion object {
        const val MAX_OUTPUT_TOKENS = 512
        const val MAX_TURNS = 48
        const val MAX_TURN_CHARS = 10_000
        const val MAX_HISTORY_CHARS = 12_000
        const val MAX_WIRE_CHARS = 6 * MAX_HISTORY_CHARS + 2_048
        const val PROTOCOL_VERSION = 2
    }
}

sealed interface LamiEngineEvent {
    data class Token(val text: String) : LamiEngineEvent
    data class Complete(val metricsJson: String) : LamiEngineEvent
    data class Failure(val code: String, val message: String) : LamiEngineEvent
}
