package io.github.ninbyo02.lami.asr.foundation.transcript

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptContractsTest {
    @Test
    fun `review revision becomes the only committable transcript`() {
        val origin = TranscriptOrigin("sherpa-onnx", "reazonspeech", "v2")
        val initial = TranscriptRevision.final(
            text = "暫定結果",
            timing = TranscriptTiming(100, 900),
            confidence = 0.9f,
            origin = origin,
        )

        val committed = initial.revise("確認済み結果").commit()

        assertEquals(TranscriptStage.COMMITTED, committed.stage)
        assertEquals(1, committed.revision)
        assertEquals("確認済み結果", committed.text)
        assertEquals(origin, committed.origin)
    }
}
