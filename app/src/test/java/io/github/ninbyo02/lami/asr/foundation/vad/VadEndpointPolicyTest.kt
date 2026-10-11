package io.github.ninbyo02.lami.asr.foundation.vad

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VadEndpointPolicyTest {
    @Test
    fun `default policy durations map to fixed 20 ms frame counts`() {
        val config = VadEndpointConfig()
        assertEquals(listOf(16_000, 320, 15, 20, 13, 40, 500, 1_500), listOf(config.sampleRateHz, config.samplesPerFrame, config.preRollFrames, config.postRollFrames, config.minimumSpeechFrames, config.endSilenceFrames, config.noSpeechTimeoutFrames, config.hardLimitFrames))
    }

    @Test
    fun `policy rejects invalid fixed format positive limits and relationships`() {
        val invalid = listOf<() -> VadEndpointConfig>(
            { VadEndpointConfig(sampleRateHz = 8_000) }, { VadEndpointConfig(samplesPerFrame = 160) },
            { VadEndpointConfig(preRollFrames = 0) }, { VadEndpointConfig(postRollFrames = 0) },
            { VadEndpointConfig(minimumSpeechFrames = 0) }, { VadEndpointConfig(endSilenceFrames = 0) },
            { VadEndpointConfig(noSpeechTimeoutFrames = 0) }, { VadEndpointConfig(hardLimitFrames = 0) },
            { VadEndpointConfig(postRollFrames = 41, endSilenceFrames = 40) },
            { VadEndpointConfig(minimumSpeechFrames = 501, noSpeechTimeoutFrames = 500) },
            { VadEndpointConfig(noSpeechTimeoutFrames = 1_501, hardLimitFrames = 1_500) },
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test
    fun `policy rejects limits above the documented privacy memory ceiling`() {
        val invalid = listOf<() -> VadEndpointConfig>(
            { VadEndpointConfig(preRollFrames = 16) },
            { VadEndpointConfig(postRollFrames = 21) },
            { VadEndpointConfig(minimumSpeechFrames = 14) },
            { VadEndpointConfig(endSilenceFrames = 41) },
            { VadEndpointConfig(noSpeechTimeoutFrames = 501) },
            { VadEndpointConfig(hardLimitFrames = 1_501) },
        )

        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test
    fun `policy rejects threshold combinations that retain more than twenty eight frames`() {
        assertThrows(IllegalArgumentException::class.java) {
            VadEndpointConfig(postRollFrames = 1, endSilenceFrames = 40)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VadEndpointConfig(postRollFrames = 11, endSilenceFrames = 40)
        }

        val boundary = VadEndpointConfig(postRollFrames = 12, endSilenceFrames = 40)
        assertEquals(28, boundary.endSilenceFrames - boundary.postRollFrames)
    }

    @Test
    fun `post roll must leave a distinct end silence frame`() {
        assertThrows(IllegalArgumentException::class.java) {
            VadEndpointConfig(postRollFrames = 20, endSilenceFrames = 20)
        }
    }

    @Test
    fun `pre roll is a bounded fifteen frame FIFO`() {
        val policy = VadEndpointPolicy()
        (1..15).forEach { assertEquals(emptyList<Short>(), ids(policy.onFrame(frame(it), VadEvent.Silence))) }
        assertEquals(15, policy.retainedFrameCountForTest)
        policy.onFrame(frame(16), VadEvent.Silence)
        repeat(12) { index -> policy.onFrame(frame(17 + index), if (index == 0) VadEvent.SpeechStarted else VadEvent.Speech) }
        val committed = policy.onFrame(frame(29), VadEvent.Speech)
        assertEquals((2..29).map(Int::toShort), ids(committed))
    }

    @Test
    fun `speech confirmation emits nothing before thirteen and then emits at most twenty eight ordered frames`() {
        val policy = VadEndpointPolicy()
        (1..15).forEach { policy.onFrame(frame(it), VadEvent.Silence) }
        (16..27).forEach { id -> assertTrue(policy.onFrame(frame(id), if (id == 16) VadEvent.SpeechStarted else VadEvent.Speech).framesForAsr.isEmpty()) }
        assertEquals(27, policy.retainedFrameCountForTest)
        assertEquals((1..28).map(Int::toShort), ids(policy.onFrame(frame(28), VadEvent.Speech)))
        assertEquals(28, policy.maxRetainedFrameCountForTest)
    }

    @Test
    fun `false start below thirteen emits no ASR and rearms with latest pre roll`() {
        val policy = VadEndpointPolicy()
        (1..15).forEach { policy.onFrame(frame(it), VadEvent.Silence) }
        (16..27).forEach { policy.onFrame(frame(it), if (it == 16) VadEvent.SpeechStarted else VadEvent.Speech) }
        assertTrue(policy.onFrame(frame(28), VadEvent.SpeechEnded).framesForAsr.isEmpty())
        (29..40).forEach { policy.onFrame(frame(it), if (it == 29) VadEvent.SpeechStarted else VadEvent.Speech) }
        assertEquals((14..41).map(Int::toShort), ids(policy.onFrame(frame(41), VadEvent.Speech)))
    }

    @Test
    fun `committed speech is emitted once and first twenty tail frames are immediate`() {
        val policy = committedPolicy()
        assertEquals(listOf(29.toShort()), ids(policy.onFrame(frame(29), VadEvent.Speech)))
        (30..49).forEach { id ->
            val event = if (id == 30) VadEvent.SpeechEnded else VadEvent.Silence
            assertEquals(listOf(id.toShort()), ids(policy.onFrame(frame(id), event)))
        }
    }

    @Test
    fun `tail twenty one through thirty nine bridges on resumed speech without duplicates`() {
        val policy = committedPolicy()
        (29..48).forEach { policy.onFrame(frame(it), if (it == 29) VadEvent.SpeechEnded else VadEvent.Silence) }
        (49..66).forEach { assertTrue(policy.onFrame(frame(it), VadEvent.Silence).framesForAsr.isEmpty()) }
        assertEquals(18, policy.retainedFrameCountForTest)
        assertEquals((49..67).map(Int::toShort), ids(policy.onFrame(frame(67), VadEvent.SpeechStarted)))
        assertEquals(listOf(68.toShort()), ids(policy.onFrame(frame(68), VadEvent.Speech)))
    }

    @Test
    fun `tail bridge plus resumed speech snapshot stays within twenty eight frames`() {
        val config = VadEndpointConfig(
            preRollFrames = 1,
            minimumSpeechFrames = 1,
            postRollFrames = 12,
            endSilenceFrames = 40,
        )
        val policy = VadEndpointPolicy(config)
        policy.onFrame(frame(1), VadEvent.Silence)
        policy.onFrame(frame(2), VadEvent.SpeechStarted)
        (3..14).forEach { policy.onFrame(frame(it), if (it == 3) VadEvent.SpeechEnded else VadEvent.Silence) }
        (15..41).forEach { assertTrue(policy.onFrame(frame(it), VadEvent.Silence).framesForAsr.isEmpty()) }
        assertEquals(27, policy.retainedFrameCountForTest)

        assertEquals((15..42).map(Int::toShort), ids(policy.onFrame(frame(42), VadEvent.SpeechStarted)))
        assertEquals(28, policy.maxRetainedFrameCountForTest)
    }

    @Test
    fun `tail forty discards bridge and finalizes end silence`() {
        val policy = committedPolicy()
        var decision = VadEndpointDecision()
        (29..68).forEach { decision = policy.onFrame(frame(it), if (it == 29) VadEvent.SpeechEnded else VadEvent.Silence) }
        assertTrue(decision.framesForAsr.isEmpty())
        assertEquals(VadEndpointTerminal.Finalize(FinalizeReason.END_SILENCE), decision.terminal)
        assertEquals(0, policy.retainedFrameCountForTest)
        assertThrows(IllegalStateException::class.java) { policy.onFrame(frame(69), VadEvent.Silence) }
    }

    @Test
    fun `no speech timeout is exact and false starts do not reset it`() {
        val policy = VadEndpointPolicy()
        (1..487).forEach { assertEquals(null, policy.onFrame(frame(it), VadEvent.Silence).terminal) }
        (488..499).forEach { assertEquals(null, policy.onFrame(frame(it), if (it == 488) VadEvent.SpeechStarted else VadEvent.Speech).terminal) }
        val terminal = policy.onFrame(frame(500), VadEvent.SpeechEnded)
        assertEquals(VadEndpointTerminal.Discard(DiscardReason.NO_SPEECH), terminal.terminal)
        assertTrue(terminal.framesForAsr.isEmpty())
    }

    @Test
    fun `thirteenth speech at frame five hundred beats no speech timeout`() {
        val policy = VadEndpointPolicy()
        (1..487).forEach { policy.onFrame(frame(it), VadEvent.Silence) }
        (488..499).forEach { policy.onFrame(frame(it), if (it == 488) VadEvent.SpeechStarted else VadEvent.Speech) }
        val decision = policy.onFrame(frame(500), VadEvent.Speech)
        assertEquals(null, decision.terminal)
        assertEquals((473..500).map(Int::toShort), ids(decision))
    }

    @Test
    fun `hard limit emits frame fifteen hundred before finalizing`() {
        val policy = committedPolicy()
        var decision = VadEndpointDecision()
        (29..1_499).forEach { decision = policy.onFrame(frame(it), VadEvent.Speech) }
        assertEquals(null, decision.terminal)
        decision = policy.onFrame(frame(1_500), VadEvent.Speech)
        assertEquals(listOf(1_500.toShort()), ids(decision))
        assertEquals(VadEndpointTerminal.Finalize(FinalizeReason.HARD_LIMIT), decision.terminal)
    }

    @Test
    fun `terminal follow up preserves reason without replaying hard limit PCM`() {
        val config = VadEndpointConfig(noSpeechTimeoutFrames = 28, hardLimitFrames = 28)
        val policy = VadEndpointPolicy(config)
        (1..15).forEach { policy.onFrame(frame(it), VadEvent.Silence) }
        var first = VadEndpointDecision()
        (16..28).forEach { id ->
            first = policy.onFrame(frame(id), if (id == 16) VadEvent.SpeechStarted else VadEvent.Speech)
        }

        assertEquals(VadEndpointTerminal.Finalize(FinalizeReason.HARD_LIMIT), first.terminal)
        assertEquals((1..28).map(Int::toShort), ids(first))

        val followUp = policy.stopAndFinalize()
        assertEquals(first.terminal, followUp.terminal)
        assertTrue(followUp.framesForAsr.isEmpty())
    }

    @Test
    fun `end silence has priority when it coincides with hard limit`() {
        val config = VadEndpointConfig(noSpeechTimeoutFrames = 50, hardLimitFrames = 68)
        val policy = committedPolicy(config)
        var decision = VadEndpointDecision()
        (29..68).forEach { decision = policy.onFrame(frame(it), if (it == 29) VadEvent.SpeechEnded else VadEvent.Silence) }
        assertEquals(VadEndpointTerminal.Finalize(FinalizeReason.END_SILENCE), decision.terminal)
    }

    @Test
    fun `stop does not promote candidate or tail bridge while cancel and failure remain distinct`() {
        val waiting = VadEndpointPolicy().also { it.onFrame(frame(1), VadEvent.Silence) }
        assertEquals(VadEndpointTerminal.Finalize(FinalizeReason.USER_STOP), waiting.stopAndFinalize().terminal)
        val candidate = VadEndpointPolicy().also { it.onFrame(frame(1), VadEvent.SpeechStarted) }
        assertTrue(candidate.stopAndFinalize().framesForAsr.isEmpty())
        val tail = committedPolicy().also {
            (29..49).forEach { id -> it.onFrame(frame(id), if (id == 29) VadEvent.SpeechEnded else VadEvent.Silence) }
        }
        assertTrue(tail.stopAndFinalize().framesForAsr.isEmpty())
        assertEquals(VadEndpointTerminal.Finalize(FinalizeReason.USER_STOP), tail.stopAndFinalize().terminal)
        val cancellableStates = listOf(
            VadEndpointPolicy().also { it.onFrame(frame(1), VadEvent.Silence) },
            VadEndpointPolicy().also { it.onFrame(frame(1), VadEvent.SpeechStarted) },
            committedPolicy(),
            committedPolicy().also { it.onFrame(frame(29), VadEvent.SpeechEnded) },
        )
        cancellableStates.forEach { cancel ->
            val result = cancel.cancelAndDiscard()
            assertEquals(VadEndpointTerminal.Discard(DiscardReason.USER_CANCEL), result.terminal)
            assertTrue(result.framesForAsr.isEmpty())
        }
        val overflow = VadEndpointPolicy().also { it.onFrame(frame(1), VadEvent.Silence) }
        assertEquals(VadEndpointTerminal.Failed(FailureReason.BUFFER_OVERFLOW), overflow.failAndDiscard(FailureReason.BUFFER_OVERFLOW).terminal)
    }

    @Test
    fun `invalid PCM and invalid VAD sequences fail closed`() {
        val badPcm = VadEndpointPolicy()
        assertEquals(VadEndpointTerminal.Failed(FailureReason.INVALID_PCM_FORMAT), badPcm.onFrame(Pcm16Frame(8_000, ShortArray(320)), VadEvent.Silence).terminal)
        val badSampleCount = VadEndpointPolicy()
        assertEquals(VadEndpointTerminal.Failed(FailureReason.INVALID_PCM_FORMAT), badSampleCount.onFrame(Pcm16Frame(16_000, ShortArray(319)), VadEvent.Silence).terminal)
        val invalidCases = listOf(
            VadEndpointPolicy() to VadEvent.Speech,
            VadEndpointPolicy() to VadEvent.SpeechEnded,
            VadEndpointPolicy().also { it.onFrame(frame(1), VadEvent.SpeechStarted) } to VadEvent.Silence,
            VadEndpointPolicy().also { it.onFrame(frame(1), VadEvent.SpeechStarted) } to VadEvent.SpeechStarted,
            committedPolicy().also { it.onFrame(frame(29), VadEvent.SpeechEnded) } to VadEvent.Speech,
            committedPolicy().also { it.onFrame(frame(29), VadEvent.SpeechEnded) } to VadEvent.SpeechEnded,
        )
        invalidCases.forEachIndexed { index, (policy, event) ->
            val decision = policy.onFrame(frame(100 + index), event)
            assertEquals(VadEndpointTerminal.Failed(FailureReason.INVALID_VAD_SEQUENCE), decision.terminal)
            assertTrue(decision.framesForAsr.isEmpty())
            assertEquals(0, policy.retainedFrameCountForTest)
        }
    }

    @Test
    fun `first terminal wins idempotently and later input is payload free rejection`() {
        val policy = VadEndpointPolicy()
        val first = policy.cancelAndDiscard()
        assertEquals(first, policy.stopAndFinalize())
        assertEquals(first, policy.failAndDiscard(FailureReason.BUFFER_OVERFLOW))
        val error = assertThrows(IllegalStateException::class.java) { policy.onFrame(frame(9), VadEvent.Silence) }
        assertEquals("VadEndpointPolicy is terminal", error.message)
    }

    @Test
    fun `discard and failure zeroize every owned retained snapshot`() {
        val cancel = VadEndpointPolicy()
        (1..15).forEach { cancel.onFrame(frame(it), VadEvent.Silence) }
        cancel.cancelAndDiscard()
        assertEquals(15, cancel.lastZeroizedFrameCountForTest)
        assertTrue(cancel.lastZeroizeVerifiedForTest)
        val failure = VadEndpointPolicy()
        (1..12).forEach { failure.onFrame(frame(it), if (it == 1) VadEvent.SpeechStarted else VadEvent.Speech) }
        failure.failAndDiscard(FailureReason.BUFFER_OVERFLOW)
        assertEquals(12, failure.lastZeroizedFrameCountForTest)
        assertTrue(failure.lastZeroizeVerifiedForTest)
    }

    @Test
    fun `adversarial fixed sequence stays bounded and emitted frame IDs have no duplicate or gap`() {
        val policy = VadEndpointPolicy(VadEndpointConfig(noSpeechTimeoutFrames = 200, hardLimitFrames = 300))
        val emitted = mutableListOf<Short>()
        (1..15).forEach { emitted += ids(policy.onFrame(frame(it), VadEvent.Silence)) }
        repeat(4) { cycle ->
            val base = 16 + cycle * 13
            policy.onFrame(frame(base), VadEvent.SpeechStarted)
            (1..11).forEach { policy.onFrame(frame(base + it), VadEvent.Speech) }
            policy.onFrame(frame(base + 12), VadEvent.SpeechEnded)
            assertTrue(policy.retainedFrameCountForTest <= 28)
        }
        val start = 68
        emitted += ids(policy.onFrame(frame(start), VadEvent.SpeechStarted))
        (start + 1..start + 12).forEach { emitted += ids(policy.onFrame(frame(it), VadEvent.Speech)) }
        (start + 13..start + 52).forEach { id -> emitted += ids(policy.onFrame(frame(id), if (id == start + 13) VadEvent.SpeechEnded else VadEvent.Silence)) }
        assertEquals(emitted.distinct(), emitted)
        assertEquals((53..100).map(Int::toShort), emitted)
        assertTrue(policy.maxRetainedFrameCountForTest <= 28)
    }

    private fun committedPolicy(config: VadEndpointConfig = VadEndpointConfig()): VadEndpointPolicy {
        val policy = VadEndpointPolicy(config)
        (1..15).forEach { policy.onFrame(frame(it), VadEvent.Silence) }
        (16..28).forEach { policy.onFrame(frame(it), if (it == 16) VadEvent.SpeechStarted else VadEvent.Speech) }
        return policy
    }

    private fun frame(id: Int): Pcm16Frame = Pcm16Frame(16_000, ShortArray(320).also { it[0] = id.toShort() })
    private fun ids(decision: VadEndpointDecision): List<Short> = decision.framesForAsr.map { it.copySamples()[0] }
}
