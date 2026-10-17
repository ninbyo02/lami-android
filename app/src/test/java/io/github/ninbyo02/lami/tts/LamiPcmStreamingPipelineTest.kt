package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LamiPcmStreamingPipelineTest {
    @Test fun slowPlaybackBackpressuresGenerationAndFlushesEos() = runTest {
        val drain = CompletableDeferred<Unit>()
        var emitted = 0
        val heard = mutableListOf<Float>()
        val result = async {
            LamiPcmStreamingPipeline.run(
                frames = { count: Int -> count },
                generate = { emit ->
                    for (count in 2..20) { emit(count); emitted++ }
                    21
                },
                decode = { count -> FloatArray(count) { 0.1f } },
                play = { chunks -> drain.await(); for (chunk in chunks) heard.addAll(chunk.toList()) },
            )
        }
        runCurrent()
        assertTrue(emitted in 1..5)
        assertTrue(heard.isEmpty())
        drain.complete(Unit)
        val completed = result.await()
        assertEquals(21, completed.codes)
        assertEquals(21, heard.size)
        assertEquals(completed.pcm.toList(), heard)
    }
}
