package io.github.ninbyo02.lami.tts

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiUnifiedSpeechReplayTest {
    private data class Record(val id: String, val prepareMs: Long, val bytes: ByteArray, val sha: String)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun record(): Record {
        val fixture = File(requireNotNull(System.getProperty("lami.unified.fixture")))
        val fields = fixture.readLines().single().split('\t')
        val bytes = File(fixture.parentFile, fields[2]).readBytes()
        require(fields[2] == "output.f32")
        require(bytes.size.toLong() == fields[4].toLong() * 4)
        require(hash(bytes) == fields[3])
        return Record(fields[0], fields[1].toLong(), bytes, fields[3]).also { require(it.prepareMs > 0) }
    }

    @Test fun sameRequestDelayAndPcmReachRealQueueTogether() = runTest {
        val record = record()
        val played = mutableListOf<Pair<Long, String>>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {},
            prepare = { delay(record.prepareMs); record.bytes },
            play = { played += testScheduler.currentTime to hash(it) })
        pipeline.enqueue(record.id)
        runCurrent()
        testScheduler.advanceTimeBy(record.prepareMs - 1)
        runCurrent()
        assertTrue(played.isEmpty())
        testScheduler.advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(record.prepareMs to record.sha), played)
        pipeline.stop()
    }

    @Test fun stopDuringRecordedPreparationDiscardsPcmAndAllowsReuse() = runTest {
        val record = record()
        val played = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {},
            prepare = { delay(record.prepareMs); record.bytes },
            play = { played += hash(it) })
        pipeline.enqueue(record.id)
        testScheduler.advanceTimeBy(record.prepareMs / 2)
        pipeline.stop()
        testScheduler.advanceTimeBy(record.prepareMs + 1)
        runCurrent()
        assertTrue(played.isEmpty())
        pipeline.enqueue(record.id)
        testScheduler.advanceTimeBy(record.prepareMs + 1)
        runCurrent()
        assertEquals(listOf(record.sha), played)
        pipeline.stop()
    }
}
