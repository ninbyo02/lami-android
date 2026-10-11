package io.github.ninbyo02.lami.asr.foundation.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AsrStateMachineTest {
    private val machine = AsrStateMachine()

    @Test
    fun `stop finalizes listening session into editable review`() {
        val loading = machine.reduce(AsrUiState(), AsrAction.RequestModelLoad)
        val ready = machine.reduce(loading, AsrAction.ModelReady)
        val listening = machine.reduce(ready, AsrAction.StartListening)
        val partial = machine.reduce(listening, AsrAction.PartialTranscript("こん"))
        val finalizing = machine.reduce(partial, AsrAction.StopAndFinalize)
        val reviewing = machine.reduce(finalizing, AsrAction.FinalTranscript("こんにちは"))

        assertEquals(AsrPhase.REVIEWING, reviewing.phase)
        assertEquals("", reviewing.partialText)
        assertEquals("こんにちは", reviewing.finalText)
    }

    @Test
    fun `cancel discards active transcript and returns ready`() {
        val listening = AsrUiState(
            phase = AsrPhase.LISTENING,
            partialText = "破棄する途中結果",
        )

        val cancelling = machine.reduce(listening, AsrAction.CancelAndDiscard)
        val ready = machine.reduce(cancelling, AsrAction.Cancelled)

        assertEquals(AsrPhase.READY, ready.phase)
        assertEquals("", ready.partialText)
        assertEquals("", ready.finalText)
    }

    @Test
    fun `invalid transition fails closed without changing source state`() {
        val source = AsrUiState(phase = AsrPhase.READY)

        assertThrows(IllegalStateException::class.java) {
            machine.reduce(source, AsrAction.FinalTranscript("unexpected"))
        }
        assertEquals(AsrPhase.READY, source.phase)
    }

    @Test
    fun `invalid transition exception never contains transcript text`() {
        val sensitiveText = "private transcript must not enter crash logs"

        val exception = assertThrows(IllegalStateException::class.java) {
            machine.reduce(
                AsrUiState(phase = AsrPhase.READY),
                AsrAction.PartialTranscript(sensitiveText),
            )
        }

        assertFalse(exception.message.orEmpty().contains(sensitiveText))
    }

    @Test
    fun `failure hides partial transcript and reset requires model reload`() {
        val failed = machine.reduce(
            AsrUiState(phase = AsrPhase.LISTENING, partialText = "機密な途中結果"),
            AsrAction.Failed("decode failed"),
        )
        val ready = machine.reduce(failed, AsrAction.Reset)

        assertEquals(AsrPhase.ERROR, failed.phase)
        assertEquals("", failed.partialText)
        assertEquals("decode failed", failed.errorMessage)
        assertEquals(AsrUiState(phase = AsrPhase.IDLE), ready)
    }

    @Test
    fun `model load failure cannot bypass model ready gate`() {
        val failed = machine.reduce(
            AsrUiState(phase = AsrPhase.LOADING_MODEL),
            AsrAction.Failed("model verification failed"),
        )

        val reset = machine.reduce(failed, AsrAction.Reset)

        assertEquals(AsrUiState(phase = AsrPhase.IDLE), reset)
        assertThrows(IllegalStateException::class.java) {
            machine.reduce(reset, AsrAction.StartListening)
        }
    }

    @Test
    fun `discard review clears final transcript`() {
        val ready = machine.reduce(
            AsrUiState(phase = AsrPhase.REVIEWING, finalText = "破棄する確定結果"),
            AsrAction.DiscardReview,
        )

        assertEquals(AsrUiState(phase = AsrPhase.READY), ready)
    }
}
