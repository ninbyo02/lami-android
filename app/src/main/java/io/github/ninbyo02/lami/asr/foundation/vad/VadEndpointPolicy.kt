package io.github.ninbyo02.lami.asr.foundation.vad

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import io.github.ninbyo02.lami.asr.foundation.audio.OwnedPcm16Frame
import java.util.ArrayDeque

private const val MAX_RETAINED_FRAMES = 230

data class VadEndpointConfig(
    val sampleRateHz: Int = 16_000,
    val samplesPerFrame: Int = 320,
    val preRollFrames: Int = 15,
    val postRollFrames: Int = 20,
    val minimumSpeechFrames: Int = 13,
    val endSilenceFrames: Int = 40,
    val noSpeechTimeoutFrames: Int = 500,
    val hardLimitFrames: Int = 1_500,
) {
    init {
        require(sampleRateHz == 16_000)
        require(samplesPerFrame == 320)
        require(preRollFrames > 0)
        require(postRollFrames > 0)
        require(minimumSpeechFrames > 0)
        require(endSilenceFrames > 0)
        require(noSpeechTimeoutFrames > 0)
        require(hardLimitFrames > 0)
        require(preRollFrames <= 15)
        require(postRollFrames <= 20)
        require(minimumSpeechFrames <= 13)
        require(endSilenceFrames <= 250)
        require(noSpeechTimeoutFrames <= 500)
        require(hardLimitFrames <= 1_500)
        require(postRollFrames < endSilenceFrames)
        require(minimumSpeechFrames <= noSpeechTimeoutFrames)
        require(noSpeechTimeoutFrames <= hardLimitFrames)
        require(preRollFrames + minimumSpeechFrames <= MAX_RETAINED_FRAMES)
        require(endSilenceFrames - postRollFrames <= MAX_RETAINED_FRAMES)
    }
}

data class VadEndpointDecision(
    val framesForAsr: List<Pcm16Frame> = emptyList(),
    val terminal: VadEndpointTerminal? = null,
)

sealed interface VadEndpointTerminal {
    data class Finalize(val reason: FinalizeReason) : VadEndpointTerminal
    data class Discard(val reason: DiscardReason) : VadEndpointTerminal
    data class Failed(val reason: FailureReason) : VadEndpointTerminal
}

enum class FinalizeReason { END_SILENCE, HARD_LIMIT, USER_STOP }
enum class DiscardReason { NO_SPEECH, USER_CANCEL }
enum class FailureReason { BUFFER_OVERFLOW, INVALID_PCM_FORMAT, INVALID_VAD_SEQUENCE }

/**
 * Deterministic single-owner endpoint policy. K10 must serialize calls.
 *
 * Retention is bounded to 230 owned PCM snapshots (147,200 bytes) for five-second pauses. Emission transfers each
 * internal snapshot once into an immutable [Pcm16Frame], avoiding simultaneous raw PCM copies.
 */
class VadEndpointPolicy(
    private val config: VadEndpointConfig = VadEndpointConfig(),
    private val onTerminal: (VadEndpointTerminal) -> Unit = {},
) {
    private enum class State { WAITING, CANDIDATE, ACTIVE, TAIL }
    private val preRoll = ArrayDeque<OwnedPcm16Frame>()
    private val candidate = ArrayDeque<OwnedPcm16Frame>()
    private val tailBridge = ArrayDeque<OwnedPcm16Frame>()
    private var state = State.WAITING
    private var observedFrames = 0
    private var tailFrames = 0
    private var terminalDecision: VadEndpointDecision? = null

    internal var maxRetainedFrameCountForTest: Int = 0
        private set
    internal var lastZeroizedFrameCountForTest: Int = 0
        private set
    internal var lastZeroizeVerifiedForTest: Boolean = true
        private set
    internal val retainedFrameCountForTest: Int
        get() = preRoll.size + candidate.size + tailBridge.size

    fun onFrame(frame: Pcm16Frame, event: VadEvent): VadEndpointDecision {
        check(terminalDecision == null) { "VadEndpointPolicy is terminal" }
        if (frame.sampleRateHz != config.sampleRateHz || frame.sampleCount != config.samplesPerFrame) {
            return terminate(VadEndpointTerminal.Failed(FailureReason.INVALID_PCM_FORMAT))
        }

        observedFrames += 1
        val owned = frame.copyForOwnershipTransfer()
        val decision = when (state) {
            State.WAITING -> handleWaiting(owned, event)
            State.CANDIDATE -> handleCandidate(owned, event)
            State.ACTIVE -> handleActive(owned, event)
            State.TAIL -> handleTail(owned, event)
        }
        if (decision.terminal != null) return decision

        if ((state == State.WAITING || state == State.CANDIDATE) && observedFrames >= config.noSpeechTimeoutFrames) {
            return terminate(VadEndpointTerminal.Discard(DiscardReason.NO_SPEECH))
        }
        if ((state == State.ACTIVE || state == State.TAIL) && observedFrames >= config.hardLimitFrames) {
            return terminate(VadEndpointTerminal.Finalize(FinalizeReason.HARD_LIMIT), decision.framesForAsr)
        }
        return decision
    }

    fun stopAndFinalize(): VadEndpointDecision =
        terminalWithoutFrames() ?: terminate(VadEndpointTerminal.Finalize(FinalizeReason.USER_STOP))

    fun cancelAndDiscard(): VadEndpointDecision =
        terminalWithoutFrames() ?: terminate(VadEndpointTerminal.Discard(DiscardReason.USER_CANCEL))

    fun failAndDiscard(reason: FailureReason): VadEndpointDecision =
        terminalWithoutFrames() ?: terminate(VadEndpointTerminal.Failed(reason))

    private fun terminalWithoutFrames(): VadEndpointDecision? =
        terminalDecision?.terminal?.let { terminal -> VadEndpointDecision(terminal = terminal) }

    private fun handleWaiting(frame: OwnedPcm16Frame, event: VadEvent): VadEndpointDecision = when (event) {
        VadEvent.Silence -> {
            appendPreRoll(frame)
            VadEndpointDecision()
        }
        VadEvent.SpeechStarted -> {
            state = State.CANDIDATE
            candidate.addLast(frame)
            updateMaximum()
            maybeCommitCandidate()
        }
        VadEvent.Speech, VadEvent.SpeechEnded -> invalidSequence(frame)
    }

    private fun handleCandidate(frame: OwnedPcm16Frame, event: VadEvent): VadEndpointDecision = when (event) {
        VadEvent.Speech -> {
            candidate.addLast(frame)
            updateMaximum()
            maybeCommitCandidate()
        }
        VadEvent.SpeechEnded -> {
            val recent = ArrayList<OwnedPcm16Frame>(preRoll.size + candidate.size + 1)
            recent.addAll(preRoll)
            recent.addAll(candidate)
            recent.add(frame)
            preRoll.clear()
            candidate.clear()
            recent.takeLast(config.preRollFrames).forEach(preRoll::addLast)
            zeroize(recent.dropLast(config.preRollFrames))
            state = State.WAITING
            updateMaximum()
            VadEndpointDecision()
        }
        VadEvent.Silence, VadEvent.SpeechStarted -> invalidSequence(frame)
    }

    private fun maybeCommitCandidate(): VadEndpointDecision {
        if (candidate.size < config.minimumSpeechFrames) return VadEndpointDecision()
        val frames = ArrayList<OwnedPcm16Frame>(preRoll.size + candidate.size)
        frames.addAll(preRoll)
        frames.addAll(candidate)
        preRoll.clear()
        candidate.clear()
        state = State.ACTIVE
        return emit(frames)
    }

    private fun handleActive(frame: OwnedPcm16Frame, event: VadEvent): VadEndpointDecision = when (event) {
        VadEvent.Speech -> emit(listOf(frame))
        VadEvent.SpeechEnded -> {
            state = State.TAIL
            tailFrames = 1
            emit(listOf(frame))
        }
        VadEvent.Silence, VadEvent.SpeechStarted -> invalidSequence(frame)
    }

    private fun handleTail(frame: OwnedPcm16Frame, event: VadEvent): VadEndpointDecision = when (event) {
        VadEvent.Silence -> {
            tailFrames += 1
            when {
                tailFrames <= config.postRollFrames -> emit(listOf(frame))
                tailFrames < config.endSilenceFrames -> {
                    tailBridge.addLast(frame)
                    updateMaximum()
                    VadEndpointDecision()
                }
                else -> {
                    zeroize(listOf(frame))
                    terminate(VadEndpointTerminal.Finalize(FinalizeReason.END_SILENCE))
                }
            }
        }
        VadEvent.SpeechStarted -> {
            updateMaximum(additionalFrames = 1)
            val frames = ArrayList<OwnedPcm16Frame>(tailBridge.size + 1)
            frames.addAll(tailBridge)
            frames.add(frame)
            tailBridge.clear()
            tailFrames = 0
            state = State.ACTIVE
            emit(frames)
        }
        VadEvent.Speech, VadEvent.SpeechEnded -> invalidSequence(frame)
    }

    private fun appendPreRoll(frame: OwnedPcm16Frame) {
        if (preRoll.size == config.preRollFrames) zeroize(listOf(preRoll.removeFirst()))
        preRoll.addLast(frame)
        updateMaximum()
    }

    private fun invalidSequence(frame: OwnedPcm16Frame): VadEndpointDecision {
        zeroize(listOf(frame))
        return terminate(VadEndpointTerminal.Failed(FailureReason.INVALID_VAD_SEQUENCE))
    }

    private fun emit(frames: List<OwnedPcm16Frame>): VadEndpointDecision {
        val result = frames.map(Pcm16Frame::takeOwnership)
        return VadEndpointDecision(framesForAsr = result)
    }

    private fun terminate(terminal: VadEndpointTerminal, frames: List<Pcm16Frame> = emptyList()): VadEndpointDecision {
        clearRetained()
        terminalDecision = VadEndpointDecision(terminal = terminal)
        runCatching { onTerminal(terminal) }
        return VadEndpointDecision(frames, terminal)
    }

    private fun clearRetained() {
        val retained = ArrayList<OwnedPcm16Frame>(retainedFrameCountForTest)
        retained.addAll(preRoll)
        retained.addAll(candidate)
        retained.addAll(tailBridge)
        preRoll.clear()
        candidate.clear()
        tailBridge.clear()
        zeroize(retained)
    }

    private fun zeroize(frames: List<OwnedPcm16Frame>) {
        if (frames.isEmpty()) return
        lastZeroizedFrameCountForTest = frames.size
        var verified = true
        frames.forEach { frame -> verified = frame.zeroizeAndRelease() && verified }
        lastZeroizeVerifiedForTest = verified
    }

    private fun updateMaximum(additionalFrames: Int = 0) {
        maxRetainedFrameCountForTest = maxOf(
            maxRetainedFrameCountForTest,
            retainedFrameCountForTest + additionalFrames,
        )
    }

}
