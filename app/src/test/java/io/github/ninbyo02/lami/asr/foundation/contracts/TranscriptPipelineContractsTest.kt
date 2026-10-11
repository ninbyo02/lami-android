package io.github.ninbyo02.lami.asr.foundation.contracts

import io.github.ninbyo02.lami.asr.foundation.meeting.TwoPassTranscriptReconciler
import io.github.ninbyo02.lami.asr.foundation.realtime.CommittedTranscriptSink
import io.github.ninbyo02.lami.asr.foundation.realtime.TranscriptCommitController
import io.github.ninbyo02.lami.asr.foundation.realtime.TranscriptRealtimeEvent
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptOrigin
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptRevision
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptStage
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptTiming
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TranscriptPipelineContractsTest {
    private val origin = TranscriptOrigin(
        backend = "test",
        modelId = "test-model",
        modelVersion = "1",
    )

    @Test
    fun `two pass replaces provisional text and advances revision`() {
        val provisional = finalTranscript("速報", revision = 4)
        val offline = finalTranscript("確定した文字起こし", revision = 0)

        val replaced = TwoPassTranscriptReconciler().replace(provisional, offline)

        assertEquals("確定した文字起こし", replaced.text)
        assertEquals(5, replaced.revision)
        assertEquals(TranscriptStage.FINAL, replaced.stage)
        assertEquals(offline.timing, replaced.timing)
        assertEquals(offline.origin, replaced.origin)
    }

    @Test
    fun `commit controller submits only committed transcript`() = runBlocking {
        val submitted = mutableListOf<TranscriptRevision>()
        val controller = TranscriptCommitController(
            CommittedTranscriptSink { submitted += it },
        )

        val committed = controller.commit(finalTranscript("送信対象"))

        assertEquals(TranscriptStage.COMMITTED, committed.stage)
        assertEquals(listOf(committed), submitted)
    }

    @Test
    fun `realtime event rejects mismatched transcript stage`() {
        val final = finalTranscript("確定")

        assertThrows(IllegalArgumentException::class.java) {
            TranscriptRealtimeEvent.Partial(final)
        }
        TranscriptRealtimeEvent.Final(final)
        TranscriptRealtimeEvent.Committed(final.commit())
    }

    private fun finalTranscript(
        text: String,
        revision: Long = 0,
    ): TranscriptRevision = TranscriptRevision.final(
        text = text,
        timing = TranscriptTiming(
            startedAtMillis = 100,
            endedAtMillis = 200,
        ),
        confidence = 0.9f,
        origin = origin,
        revision = revision,
    )
}
