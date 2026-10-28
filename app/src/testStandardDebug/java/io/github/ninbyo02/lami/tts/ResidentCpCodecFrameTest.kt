package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class ResidentCpCodecFrameTest {
    @Test fun createsSixteenCodesOnlyWhenComplete() {
        val frame = ResidentCpCodecFrame.assemble(125, (0 until 15).toList())
        assertEquals(16, frame.size)
        assertEquals(125, frame[0])
        assertEquals(14, frame.last())
    }

    @Test fun refusesPartialOrOutOfRangeFrames() {
        assertTrue(runCatching { ResidentCpCodecFrame.assemble(125, List(14) { 0 }) }.isFailure)
        assertTrue(runCatching { ResidentCpCodecFrame.assemble(125, List(15) { 2048 }) }.isFailure)
        assertTrue(runCatching { ResidentCpCodecFrame.assemble(-1, List(15) { 0 }) }.isFailure)
    }
}
