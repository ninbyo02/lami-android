package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Recorded codec durations, virtual clock, placeholder clips. No NPU/audio emulation. */
@OptIn(ExperimentalCoroutinesApi::class)
class LamiSpeechPipelineRecordedReplayTest {
    private data class Row(val id: String, val codecMs: Long)
    private fun rows(): List<Row> {
        val configured = System.getProperty("lami.replay.fixture")
        org.junit.Assume.assumeTrue("Explicit host replay fixture required", configured != null)
        val path = requireNotNull(configured)
        return File(path).readLines().filter { it.isNotBlank() }.map {
            val fields = it.split('\t')
            Row(fields[0], fields[1].toLong()).also { row -> require(row.codecMs > 0) }
        }.also { require(it.size >= 2) }
    }

    @Test fun recordedDurationsPreserveQueuedOrder() = runTest {
        val fixture = rows()
        val delays = fixture.associate { it.id to it.codecMs }
        val played = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {},
            prepare = { delay(delays.getValue(it)); it },
            play = { played += it })
        fixture.forEach { pipeline.enqueue(it.id) }
        testScheduler.advanceTimeBy(fixture.sumOf { it.codecMs } + 1)
        runCurrent()
        assertEquals(fixture.map { it.id }, played)
        pipeline.stop()
    }

    @Test fun stopDuringRecordedDelayDiscardsOldRequest() = runTest {
        val fixture = rows()
        val first = fixture[0]
        val next = fixture[1]
        val delays = fixture.associate { it.id to it.codecMs }
        val played = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {},
            prepare = { delay(delays.getValue(it)); it },
            play = { played += it })
        pipeline.enqueue(first.id)
        testScheduler.advanceTimeBy(first.codecMs / 2)
        pipeline.stop()
        pipeline.enqueue(next.id)
        testScheduler.advanceTimeBy(first.codecMs + next.codecMs + 1)
        runCurrent()
        assertEquals(listOf(next.id), played)
        pipeline.stop()
    }

    @Test fun injectedFailureAtRecordedDurationAllowsNextRequest() = runTest {
        val fixture = rows()
        val first = fixture[0]
        val next = fixture[1]
        val delays = fixture.associate { it.id to it.codecMs }
        val played = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline<String?>(backgroundScope, {},
            prepare = {
                delay(delays.getValue(it))
                if (it == first.id) null else it
            }, play = { if (it != null) played += it })
        pipeline.enqueue(first.id)
        pipeline.enqueue(next.id)
        testScheduler.advanceTimeBy(first.codecMs + next.codecMs + 1)
        runCurrent()
        assertEquals(listOf(next.id), played)
        pipeline.stop()
    }
}
