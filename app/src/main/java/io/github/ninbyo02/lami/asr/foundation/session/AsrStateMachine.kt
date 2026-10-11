package io.github.ninbyo02.lami.asr.foundation.session

import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptRevision

enum class AsrPhase {
    IDLE,
    LOADING_MODEL,
    READY,
    LISTENING,
    FINALIZING,
    REVIEWING,
    CANCELLING,
    ERROR,
}

data class AsrUiState(
    val phase: AsrPhase = AsrPhase.IDLE,
    val partialText: String = "",
    val finalText: String = "",
    val transcript: TranscriptRevision? = null,
    val errorMessage: String? = null,
)

sealed interface AsrAction {
    data object RequestModelLoad : AsrAction

    data object ModelReady : AsrAction

    data object StartListening : AsrAction

    data class PartialTranscript(val text: String) : AsrAction

    data object StopAndFinalize : AsrAction

    data object CancelAndDiscard : AsrAction

    data class FinalTranscript(
        val text: String,
        val transcript: TranscriptRevision? = null,
    ) : AsrAction

    data object Cancelled : AsrAction

    data object DiscardReview : AsrAction

    data class Failed(val message: String) : AsrAction

    data object Reset : AsrAction
}

/** Pure fail-closed reducer. Resource ownership belongs to the future session coordinator. */
class AsrStateMachine {
    fun reduce(state: AsrUiState, action: AsrAction): AsrUiState = when {
        state.phase == AsrPhase.IDLE && action == AsrAction.RequestModelLoad ->
            AsrUiState(phase = AsrPhase.LOADING_MODEL)

        state.phase == AsrPhase.LOADING_MODEL && action == AsrAction.ModelReady ->
            AsrUiState(phase = AsrPhase.READY)

        state.phase == AsrPhase.READY && action == AsrAction.StartListening ->
            AsrUiState(phase = AsrPhase.LISTENING)

        state.phase == AsrPhase.LISTENING && action is AsrAction.PartialTranscript ->
            state.copy(partialText = action.text, errorMessage = null)

        state.phase == AsrPhase.LISTENING && action == AsrAction.StopAndFinalize ->
            state.copy(phase = AsrPhase.FINALIZING)

        state.phase in setOf(AsrPhase.LISTENING, AsrPhase.FINALIZING) &&
            action == AsrAction.CancelAndDiscard ->
            AsrUiState(phase = AsrPhase.CANCELLING)

        state.phase == AsrPhase.FINALIZING && action is AsrAction.FinalTranscript ->
            AsrUiState(
                phase = AsrPhase.REVIEWING,
                finalText = action.text,
                transcript = action.transcript,
            )

        state.phase == AsrPhase.CANCELLING && action == AsrAction.Cancelled ->
            AsrUiState(phase = AsrPhase.READY)

        state.phase == AsrPhase.REVIEWING && action == AsrAction.DiscardReview ->
            AsrUiState(phase = AsrPhase.READY)

        state.phase in ACTIVE_OR_READY_PHASES && action is AsrAction.Failed ->
            AsrUiState(phase = AsrPhase.ERROR, errorMessage = action.message)

        state.phase == AsrPhase.ERROR && action == AsrAction.Reset ->
            AsrUiState(phase = AsrPhase.IDLE)

        else -> throw IllegalStateException(
            "Invalid ASR transition: ${state.phase} + ${action.javaClass.simpleName}",
        )
    }

    private companion object {
        val ACTIVE_OR_READY_PHASES = setOf(
            AsrPhase.LOADING_MODEL,
            AsrPhase.READY,
            AsrPhase.LISTENING,
            AsrPhase.FINALIZING,
            AsrPhase.CANCELLING,
        )
    }
}
