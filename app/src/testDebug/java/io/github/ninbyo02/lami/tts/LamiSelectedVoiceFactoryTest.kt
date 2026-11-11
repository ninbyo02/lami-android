package io.github.ninbyo02.lami.tts

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class LamiSelectedVoiceFactoryTest {
    private lateinit var context: Context
    private lateinit var qwen: File
    private lateinit var learned: File
    private lateinit var stock: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        qwen = context.filesDir.resolve("local_models/lami_tts/prepared_hai")
        learned = context.filesDir.resolve("supertonic3-light5")
        stock = context.filesDir.resolve("factory-test-stock")
        clearModels()
    }

    @After
    fun tearDown() = clearModels()

    @Test
    fun `Android remains available when local models are absent`() {
        val state = LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.ANDROID)
        assertEquals(TtsEngineChoice.ANDROID, state.engine)
        assertTrue(state.available)
    }

    @Test
    fun `Qwen is unavailable when manifest is absent`() {
        val state = LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.LAMI_QWEN3)
        assertEquals(TtsEngineChoice.LAMI_QWEN3, state.engine)
        assertFalse(state.available)
        assertTrue(state.message.isNotBlank())
    }

    @Test
    fun `Qwen availability requires every referenced file to be nonempty`() {
        writeQwenBundle()
        assertTrue(qwenAvailability())

        qwen.resolve("payload.bin").delete()
        assertFalse(qwenAvailability())
        qwen.resolve("payload.bin").writeBytes(byteArrayOf())
        assertFalse(qwenAvailability())
    }

    @Test
    fun `Qwen rejects old manifest and empty references`() {
        writeQwenBundle(version = 1)
        assertFalse(qwenAvailability())
        writeQwenBundle()
        val manifest = qwen.resolve("voice-text-bundle.json")
        manifest.writeText(JSONObject(manifest.readText()).put("sha256", JSONObject()).toString())
        assertFalse(qwenAvailability())
    }

    @Test
    fun `Qwen rejects manifest references outside model directory`() {
        writeQwenBundle(reference = "../escaped.bin")
        assertFalse(qwenAvailability())
    }

    @Test
    fun `Qwen rejects a different named speaker`() {
        writeQwenBundle()
        val manifest = qwen.resolve("voice-text-bundle.json")
        manifest.writeText(JSONObject(manifest.readText()).put("speaker", "different_voice").toString())
        assertFalse(qwenAvailability())
    }

    @Test
    fun `Qwen rejects model-free metadata even when every listed file exists`() {
        writeQwenBundle()
        updateQwen { it.put("sha256", JSONObject().put("payload.bin", "0".repeat(64))) }
        assertFalse(qwenAvailability())
    }

    @Test
    fun `Qwen requires programs matrices and tokenizer references`() {
        for (name in listOf(LamiQwenBundleContract.DEFAULT_MAIN, LamiQwenBundleContract.DEFAULT_CP,
            "main.head.f32", "main.embedding.f32", "cp.head.14.f32", "cp.embedding.14.f32",
            "text_frontend/vocab.json", "text_frontend/merges.txt", "text_frontend/tokenizer_config.json",
            "text_frontend/projected-text.f32")) {
            writeQwenBundle()
            updateQwen { it.getJSONObject("sha256").remove(name) }
            assertFalse(name, qwenAvailability())
        }
    }

    @Test
    fun `Qwen availability checks causal decoder identity and sample contract`() {
        writeQwenBundle()
        updateQwen { it.getJSONObject("sha256").put(LamiQwenBundleContract.DECODER, "0".repeat(64)) }
        assertFalse(qwenAvailability())
        for ((field, value) in listOf("sample_rate" to 44_100, "samples_per_frame" to 960,
            "decoder_frames_min" to 8, "decoder_frames_max" to 128)) {
            writeQwenBundle()
            updateQwen { it.put(field, value) }
            assertFalse(field, qwenAvailability())
        }
    }

    @Test
    fun `Qwen selected programs and packed heads determine required files`() {
        writeQwenBundle()
        updateQwen { it.put("main_program", "unknown.pte") }
        assertFalse(qwenAvailability())
        writeQwenBundle()
        updateQwen { it.put("cp_program", "unknown.pte") }
        assertFalse(qwenAvailability())

        writeQwenBundle()
        val main = "main-int8-grouped-cache128.pte"
        val cp = "cp-int8-grouped-delta-cache16.pte"
        qwen.resolve(main).writeText("payload")
        qwen.resolve(cp).writeText("payload")
        updateQwen {
            it.put("main_program", main).put("cp_program", cp)
            it.getJSONObject("sha256")
                .put(main, "83ef62e9ac80c6db43d532b43c7e85d0586e5860e6718c552054f321d8da5dca")
                .put(cp, "0f801eac3fbdd26db5e972d95b79e57362e75582ff09efe24a249da62ee046cf")
                .remove(LamiQwenBundleContract.DEFAULT_MAIN)
            it.getJSONObject("sha256").remove(LamiQwenBundleContract.DEFAULT_CP)
        }
        assertTrue(qwenAvailability())
        updateQwen { it.getJSONObject("sha256").put(cp, "0".repeat(64)) }
        assertFalse(qwenAvailability())

        writeQwenBundle()
        updateQwen { it.put("cp_head_prepacked4", true) }
        assertFalse(qwenAvailability())
        for (index in 0..14) qwen.resolve("cp.head.$index.packed4.f32").writeText("payload")
        updateQwen {
            for (index in 0..14) {
                val hashes = it.getJSONObject("sha256")
                hashes.put("cp.head.$index.packed4.f32", hashes.remove("cp.head.$index.f32"))
            }
        }
        assertTrue(qwenAvailability())
        updateQwen { it.put("cp_head_fixed_benchmark", true) }
        assertFalse(qwenAvailability())
    }

    @Test
    fun `Qwen requires complete finite rotary position tables`() {
        writeQwenBundle()
        updateQwen { it.remove("rope_cos") }
        assertFalse(qwenAvailability())
        writeQwenBundle()
        updateQwen { it.put("rope_sin", JSONArray().put(JSONArray().put(0))) }
        assertFalse(qwenAvailability())
        writeQwenBundle()
        updateQwen { it.getJSONArray("rope_cos").getJSONArray(0).put(0, "not a number") }
        assertFalse(qwenAvailability())
    }

    @Test
    fun `Qwen retains reviewed FP32 and INT8 cache256 main variants`() {
        val programs = mapOf(
            "stateless-28-fp32-cache256-et14.pte" to "0813bb92405360aa285560f16270319491f7757480a98cb882e897a0e4d7531a",
            "main-int8-permute-cache256.pte" to "5a03abc2481cc050ea37950247b73450fd3be64d6a4b7854dee4251b39db9bba",
            "main-int8-cache256.pte" to "6a00b9912080e057db86fe29e0c67bb3a8fa6c0851c8f5daea04175578d5c338",
        )
        for ((program, pin) in programs) {
            writeQwenBundle()
            qwen.resolve(program).writeText("payload")
            updateQwen {
                it.put("main_program", program)
                it.getJSONObject("sha256").put(program, pin).remove(LamiQwenBundleContract.DEFAULT_MAIN)
            }
            assertTrue(program, qwenAvailability())
            updateQwen {
                it.put("rope_cos", JSONArray().apply { repeat(128) { put(JSONArray().apply { repeat(128) { put(1) } }) } })
            }
            assertFalse("$program needs 256 positions", qwenAvailability())
            writeQwenBundle()
            updateQwen {
                it.put("main_program", program)
                it.getJSONObject("sha256").put(program, "0".repeat(64))
            }
            assertFalse("$program changed pin", qwenAvailability())
        }
    }

    @Test
    fun `Supertonic learned directory takes precedence and labels LAMI voice`() {
        writeSupertonic(learned, learnedVoice = true)
        writeSupertonic(stock)
        configureStock()

        assertEquals(learned, LamiSupertonicVoiceFactory.modelDirectory(context))
        val state = LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.SUPERTONIC3)
        assertTrue(state.available)
        assertTrue(state.message.contains("LAMI"))
    }

    @Test
    fun `configured Supertonic stock directory is labelled separately`() {
        writeSupertonic(stock)
        configureStock()

        assertEquals(stock, LamiSupertonicVoiceFactory.modelDirectory(context))
        val state = LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.SUPERTONIC3)
        assertTrue(state.available)
        assertTrue(state.message.contains("既成"))
    }

    @Test
    fun `stock bundle in light directory is not labelled as learned LAMI`() {
        writeSupertonic(learned)

        val state = LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.SUPERTONIC3)

        assertTrue(state.available)
        assertTrue(state.message.contains("既成"))
        assertFalse(state.message.contains("LAMI"))
    }

    @Test
    fun `unknown Supertonic metadata uses generic speaker label`() {
        writeSupertonic(learned)
        learned.resolve("manifest.json").delete()

        val state = LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.SUPERTONIC3)

        assertTrue(state.available)
        assertTrue(state.message.contains("話者1"))
        assertFalse(state.message.contains("LAMI"))
        assertFalse(state.message.contains("既成"))
    }

    @Test
    fun `learned runtime alias metadata identifies LAMI without relying on directory name`() {
        writeSupertonic(stock)
        stock.resolve("manifest.json").writeText(JSONObject()
            .put("recommended_runtime_options", JSONObject().put("voice_alias", "F1/sid0 = learned LAMI1850"))
            .toString())
        configureStock()

        val state = LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.SUPERTONIC3)

        assertTrue(state.available)
        assertTrue(state.message.contains("LAMI"))
        assertFalse(state.message.contains("既成"))
    }

    @Test
    fun `incomplete learned Supertonic directory is not silently replaced by stock`() {
        writeSupertonic(stock)
        configureStock()
        learned.mkdirs()

        val state = LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.SUPERTONIC3)
        assertFalse(state.available)
        writeSupertonic(learned, learnedVoice = true)
        learned.resolve(LamiSupertonicSession.FILES.first()).writeBytes(byteArrayOf())
        assertFalse(LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.SUPERTONIC3).available)
    }

    private fun qwenAvailability() =
        LamiNeuralChatVoiceFactory.availability(context, TtsEngineChoice.LAMI_QWEN3).available

    private fun writeQwenBundle(version: Int = 2, reference: String = "payload.bin") {
        qwen.mkdirs()
        val names = listOf(LamiQwenBundleContract.DEFAULT_MAIN, LamiQwenBundleContract.DEFAULT_CP,
            LamiQwenBundleContract.DECODER, "main.head.f32", "main.embedding.f32",
            "text_frontend/vocab.json", "text_frontend/merges.txt", "text_frontend/tokenizer_config.json",
            "text_frontend/projected-text.f32", reference) +
            (0..14).map { "cp.head.$it.f32" } + (0..14).map { "cp.embedding.$it.f32" }
        val hashes = JSONObject()
        for (name in names) {
            qwen.resolve(name).apply { parentFile!!.mkdirs(); writeText("payload") }
            hashes.put(name, if (name == LamiQwenBundleContract.DECODER) LamiQwenBundleContract.DECODER_SHA256 else "0".repeat(64))
        }
        fun rope(value: Int) = JSONArray().apply {
            repeat(256) { put(JSONArray().apply { repeat(128) { put(value) } }) }
        }
        val manifest = JSONObject().put("version", version).put("capacity", 256).put("speaker", "lami_cute")
            .put("sha256", hashes).put("decoder_frames_min", 2).put("decoder_frames_max", 256)
            .put("samples_per_frame", 1920).put("sample_rate", 24_000)
            .put("rope_cos", rope(1)).put("rope_sin", rope(0))
        qwen.resolve("voice-text-bundle.json").writeText(manifest.toString())
    }

    private fun updateQwen(change: (JSONObject) -> Unit) {
        val file = qwen.resolve("voice-text-bundle.json")
        file.writeText(JSONObject(file.readText()).also(change).toString())
    }

    private fun writeSupertonic(directory: File, learnedVoice: Boolean = false) {
        directory.mkdirs()
        LamiSupertonicSession.FILES.forEach { directory.resolve(it).writeText("payload") }
        val metadata = if (learnedVoice) JSONObject()
            .put("bundle_sha256", "c88ef7f782d4f4ab9b409e3d1422748099f53fc0266e34025b3b3ddc2e1236db")
            .put("kind", "trained LAMI style candidate; identity quality requires evaluation")
        else JSONObject().put("bundle_sha256", "6197735e54412143817dde72a3fb0a5eaa6884360bc94c3afb2950a913dfc201")
        directory.resolve("manifest.json").writeText(metadata.toString())
    }

    private fun configureStock() {
        context.getSharedPreferences(LamiSupertonicVoiceFactory.PREFS, Context.MODE_PRIVATE)
            .edit().putString("model_path", stock.absolutePath).apply()
    }

    private fun clearModels() {
        qwen.parentFile.deleteRecursively()
        learned.deleteRecursively()
        stock.deleteRecursively()
        context.filesDir.resolve("supertonic3").deleteRecursively()
        context.getSharedPreferences(LamiSupertonicVoiceFactory.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
