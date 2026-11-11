package io.github.ninbyo02.lami.tts

import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Decoder-only shape-boundary evidence: no AR generation and no audio playback. */
class LamiQwenPrefixNumericsInstrumentedTest {
    @Test fun decode24And32ReportsPrefixNumerics() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = context.filesDir.resolve("local_models/lami_tts/prepared_hai")
        assumeTrue(root.resolve("speech-decoder-dynamic-et14.pte").isFile)
        val counts = (InstrumentationRegistry.getArguments().getString("qwen_prefix_frames") ?: "24,32")
            .split(',').map(String::toInt)
        require(counts.size == 2 && counts[0] in 2..255 && counts[1] in counts[0] + 1..256)
        LamiVoiceStreamingProbe.requireStreamingDecoder(root)
        withTimeout(90_000) {
            val fullFrames = counts[1]
            val full = LongArray(fullFrames * 16) { index ->
                val channel = index / fullFrames
                val frame = index % fullFrames
                ((channel * 137 + frame * 61 + 17) % 2048).toLong()
            }
            val shorter = LamiVoiceChunkProbeMath.prefixCodes(full, fullFrames, counts[0])
            val previous = LamiVoiceDecoderProcess.decode(context, root, LamiVoiceCodes(shorter, counts[0])) {}
            val nextCodes = LamiVoiceCodes(full, fullFrames)
            val next = LamiVoiceDecoderProcess.decode(context, root, nextCodes) {}
            assertEquals(counts[0] * 1920, previous.size)
            assertEquals(fullFrames * 1920, next.size)
            assertTrue(previous.all(Float::isFinite) && next.all(Float::isFinite))
            val evidence = LamiVoiceStreamingProbe.capturePrefixDifference(context, nextCodes, previous, next)
            val summary = evidence.resolve("summary.json").readText()
            Log.i("LamiQwenPrefixProbe", "evidence=${evidence.absolutePath} $summary")
            instrumentation.sendStatus(0, Bundle().apply {
                putString("qwen_prefix_evidence", evidence.absolutePath)
                putString("qwen_prefix_summary", summary)
            })
        }
    }
}
