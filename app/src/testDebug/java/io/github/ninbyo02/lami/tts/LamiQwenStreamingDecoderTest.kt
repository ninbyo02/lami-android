package io.github.ninbyo02.lami.tts

import java.io.File
import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class LamiQwenStreamingDecoderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun knownCausalDecoderContractIsEligibleForLivePrefixes() {
        val root = writeManifest()
        LamiVoiceStreamingProbe.requireStreamingDecoder(root)
    }

    @Test fun replacementDecoderCannotReleaseUnverifiedPartialAudio() {
        val root = writeManifest()
        val file = root.resolve("voice-text-bundle.json")
        val manifest = JSONObject(file.readText())
        manifest.getJSONObject("sha256").put("speech-decoder-dynamic-et14.pte", "0".repeat(64))
        file.writeText(manifest.toString())
        assertTrue(runCatching { LamiVoiceStreamingProbe.requireStreamingDecoder(root) }.isFailure)
    }

    @Test fun differentSampleOrFrameContractIsRejectedBeforeLiveStreaming() {
        for ((field, value) in listOf("sample_rate" to 44_100, "samples_per_frame" to 960,
            "decoder_frames_min" to 8, "decoder_frames_max" to 128)) {
            val root = writeManifest()
            val file = root.resolve("voice-text-bundle.json")
            file.writeText(JSONObject(file.readText()).put(field, value).toString())
            assertTrue(field, runCatching { LamiVoiceStreamingProbe.requireStreamingDecoder(root) }.isFailure)
        }
    }

    @Test fun capturedAndroidShapeRoundoffPassesBoundedLivePolicyButExactDiagnosticRejectsIt() {
        val resource = checkNotNull(javaClass.getResourceAsStream("/voice/qwen-prefix24-32-roundoff.f32pairs.gz"))
        DataInputStream(GZIPInputStream(resource)).use { input ->
            val samples = input.readInt()
            val changed = input.readInt()
            val previous = FloatArray(samples)
            val next = FloatArray(samples + 1).apply { this[samples] = 0.1f }
            repeat(changed) {
                val index = input.readInt()
                previous[index] = input.readFloat()
                next[index] = input.readFloat()
            }
            val difference = LamiVoiceChunkProbeMath.prefixDifference(previous, next)
            org.junit.Assert.assertEquals(15_098, difference.changedSamples)
            org.junit.Assert.assertEquals(3.7848949432373047e-6, difference.maxAbsolute, 0.0)
            org.junit.Assert.assertEquals(1.788268561204451e-7, difference.rms, 1e-15)
            assertTrue(runCatching { LamiVoiceChunkProbeMath.appendedPcm(previous, next) }.isFailure)
            org.junit.Assert.assertArrayEquals(floatArrayOf(0.1f),
                LamiVoiceChunkProbeMath.appendedPcm(previous, next, LamiVoiceStreamingProbe.LIVE_PREFIX_TOLERANCE), 0f)
        }
    }

    private fun writeManifest(): File = temporary.newFolder().apply {
        resolve("voice-text-bundle.json").writeText(JSONObject()
            .put("decoder_frames_min", 2).put("decoder_frames_max", 256)
            .put("samples_per_frame", 1920).put("sample_rate", 24_000)
            .put("sha256", JSONObject().put("speech-decoder-dynamic-et14.pte",
                "05288ee2c763f80067139019ca66915bad150de705acc940200735f653c1b7c5"))
            .toString())
    }
}
