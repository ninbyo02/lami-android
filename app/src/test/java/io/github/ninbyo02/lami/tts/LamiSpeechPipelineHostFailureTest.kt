package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiSpeechPipelineHostFailureTest {
    @Test fun delayedFailedClipDoesNotBlockNextRequest() = runTest {
        val played = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val pipeline = LamiSpeechPipeline<String?>(backgroundScope, busy::add,
            prepare = { text ->
                delay(250)
                if (text == "failed") null else text
            },
            play = { clip -> if (clip != null) played += clip })
        pipeline.enqueue("failed")
        pipeline.enqueue("recovered")
        testScheduler.advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf("recovered"), played)
        assertEquals(listOf(true, false), busy)
        pipeline.enqueue("reuse")
        testScheduler.advanceTimeBy(251)
        runCurrent()
        assertEquals(listOf("recovered", "reuse"), played)
        pipeline.stop()
    }
}
