package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiTerminalSpeechReplayTest {
    private data class Terminal(val id: String, val ms: Long, val stage: String)
    private fun records() = File(requireNotNull(System.getProperty("lami.terminal.fixture")))
        .readLines().filter { it.isNotBlank() }.map {
            val f = it.split('\t')
            Terminal(f[0], f[1].toLong(), f[2]).also { row -> require(row.ms >= 0) }
        }

    @Test fun recordedLoadFailureDropsClipAndContinuesQueue() = runTest {
        val record = records().first { it.stage == "load" }
        val played = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline<String?>(backgroundScope, {},
            prepare = { if (it == record.id) { delay(record.ms); null } else it },
            play = { if (it != null) played += it })
        pipeline.enqueue(record.id)
        pipeline.enqueue("next")
        testScheduler.advanceTimeBy(record.ms + 1)
        runCurrent()
        assertEquals(listOf("next"), played)
        pipeline.stop()
    }

    @Test fun recordedCancellationStopsQueueAndAllowsExplicitRestart() = runTest {
        val record = records().first { it.stage == "cancelled" }
        val played = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {},
            prepare = { delay(record.ms + 100); it },
            play = { played += it })
        pipeline.enqueue(record.id)
        pipeline.enqueue("discard")
        backgroundScope.launch { delay(record.ms); pipeline.stop() }
        testScheduler.advanceTimeBy(record.ms + 101)
        runCurrent()
        assertTrue(played.isEmpty())
        pipeline.enqueue("restart")
        testScheduler.advanceTimeBy(record.ms + 101)
        runCurrent()
        assertEquals(listOf("restart"), played)
        pipeline.stop()
    }
}
