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
class LamiSpeechPipelinePcmReplayTest {
    private data class Clip(val id: String, val bytes: ByteArray, val hash: String, val audioMs: Long)
    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun clips(): List<Clip> {
        val configured = System.getProperty("lami.pcm.fixture")
        org.junit.Assume.assumeTrue("Explicit PCM fixture required", configured != null)
        val fixture = File(requireNotNull(configured))
        return fixture.readLines().filter { it.isNotBlank() }.map {
            val fields = it.split('\t')
            val file = File(fixture.parentFile, fields[1])
            val bytes = file.readBytes()
            require(hash(bytes) == fields[2]) { "PCM hash mismatch" }
            val samples = fields[3].toLong()
            val rate = fields[4].toLong()
            require(samples > 0 && rate == 24000L && bytes.size.toLong() == samples * 2)
            Clip(fields[0], bytes, fields[2], samples * 1000 / rate)
        }.also { require(it.size >= 2) }
    }

    @Test fun verifiedPcmTravelsThroughRealQueueUnchanged() = runTest {
        val fixture = clips()
        val byId = fixture.associateBy { it.id }
        val consumed = mutableListOf<Pair<String, String>>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {},
            prepare = { byId.getValue(it) },
            play = {
                consumed += it.id to hash(it.bytes)
                delay(it.audioMs)
            })
        fixture.forEach { pipeline.enqueue(it.id) }
        testScheduler.advanceTimeBy(fixture.sumOf { it.audioMs } + 1)
        runCurrent()
        assertEquals(fixture.map { it.id to it.hash }, consumed)
        pipeline.stop()
    }

    @Test fun stoppingPcmPlaybackDiscardsPrefetchedClip() = runTest {
        val fixture = clips()
        val byId = fixture.associateBy { it.id }
        val consumed = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {},
            prepare = { byId.getValue(it) },
            play = { consumed += it.id; delay(it.audioMs) })
        fixture.forEach { pipeline.enqueue(it.id) }
        runCurrent()
        pipeline.stop()
        testScheduler.advanceTimeBy(fixture.sumOf { it.audioMs } + 1)
        runCurrent()
        assertEquals(listOf(fixture[0].id), consumed)
        pipeline.enqueue(fixture[1].id)
        testScheduler.advanceTimeBy(fixture[1].audioMs + 1)
        runCurrent()
        assertEquals(listOf(fixture[0].id, fixture[1].id), consumed)
        pipeline.stop()
    }
}
