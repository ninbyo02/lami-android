package io.github.ninbyo02.lami.tts

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class LamiKokoroScratchBundleTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `diagnostic artifact cannot be promoted by editing its manifest flags`() {
        val model = bundle()
        assertThrows(IllegalArgumentException::class.java) { LamiKokoroScratchBundle.read(model) }
        editManifest(model) {
            put("diagnostic_only", false)
            put("quality_verified", true)
            put("android_device_verified", true)
        }
        assertThrows(IllegalArgumentException::class.java) { LamiKokoroScratchBundle.read(model) }
    }

    @Test fun `same length modification fails full payload verification`() {
        val model = bundle()
        model.resolve("text.onnx").writeText("changed")
        val description = LamiKokoroScratchBundle.read(model, diagnostic = true)
        assertThrows(IllegalArgumentException::class.java) { description.verifyAndReadVocabulary() }
    }

    @Test fun `cancelled verification propagates the request cancellation`() {
        val description = LamiKokoroScratchBundle.read(bundle(), diagnostic = true)
        val stopped = CancellationException("Stop during model verification")
        val failure = assertThrows(CancellationException::class.java) {
            description.verifyAndReadVocabulary { throw stopped }
        }
        assertSame(stopped, failure)
    }

    @Test fun `frame budget allows reviewed long export shapes and rejects unbounded declarations`() {
        val model = bundle()
        assertEquals(200, LamiKokoroScratchBundle.read(model, diagnostic = true).maxFrames)
        editManifest(model) { put("max_frames", 600) }
        assertEquals(600, LamiKokoroScratchBundle.read(model, diagnostic = true).maxFrames)
        editManifest(model) { put("max_frames", 601) }
        assertThrows(IllegalArgumentException::class.java) {
            LamiKokoroScratchBundle.read(model, diagnostic = true)
        }
    }

    @Test fun `phoneme split keeps both words and restores native boundary tokens`() {
        val vocabulary = LamiKokoroScratchBundle.read(bundle(), diagnostic = true).verifyAndReadVocabulary()
        val tokens = vocabulary.encode("a🗣 a")
        assertArrayEquals(longArrayOf(0, 1, 2, 16, 1, 0), tokens)
        val (first, second) = checkNotNull(vocabulary.splitAtWordBoundary(tokens))
        assertArrayEquals(longArrayOf(0, 1, 2, 0), first)
        assertArrayEquals(longArrayOf(0, 1, 0), second)
        vocabulary.requireValid(first)
        vocabulary.requireValid(second)
        assertThrows(IllegalArgumentException::class.java) { vocabulary.encode("b") }
    }

    private fun bundle(): File {
        val root = temporary.newFolder()
        val files = JSONObject()
        val vocabulary = JSONObject().put("bos_id", 0).put("eos_id", 0)
            .put("min_tokens", 3).put("max_tokens", 512)
            .put("vocab", JSONObject().put("a", 1).put("🗣", 2).put(" ", 16))
        for ((name, payload) in mapOf("text.onnx" to "payload", "render.onnx" to "payload", "frontend.json" to vocabulary.toString())) {
            val file = root.resolve(name).apply { writeText(payload) }
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            files.put(name, JSONObject().put("bytes", file.length()).put("sha256", hash))
        }
        root.resolve("manifest.json").writeText(JSONObject()
            .put("format_version", 1).put("runtime", "onnxruntime-cpu").put("opset", 17)
            .put("origin", "all-weights-random-initialized").put("strict_state_reload", true)
            .put("teacher_forced", false).put("sample_rate", 24_000).put("channels", 1)
            .put("samples_per_frame", 600).put("max_frames", 200).put("harmonics", 9)
            .put("acoustic_channels", 512).put("prosody_channels", 640)
            .put("checkpoint_sha256", "0".repeat(64)).put("files", files)
            .put("diagnostic_only", true).put("quality_verified", false).toString())
        return root
    }

    private fun editManifest(root: File, edit: JSONObject.() -> Unit) {
        val file = root.resolve("manifest.json")
        file.writeText(JSONObject(file.readText()).apply(edit).toString())
    }
}
