package io.github.ninbyo02.lami.tts

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class LamiVoiceTextFrontendTest {
    private val fixture = JSONObject(requireNotNull(javaClass.getResource("/voice/qwen-text-frontend.json")).readText())
    private fun tokenizer(): LamiQwenTokenizer {
        val vocab = fixture.getJSONObject("vocab")
        val special = fixture.getJSONObject("special")
        val merges = fixture.getJSONArray("merges")
        return LamiQwenTokenizer(vocab.keys().asSequence().associateWith { vocab.getInt(it) },
            (0 until merges.length()).associate { i -> merges.getJSONArray(i).let { (it.getString(0) to it.getString(1)) to it.getInt(2) } },
            special.keys().asSequence().associateWith { special.getInt(it) })
    }
    private fun row(table: String, id: Int): FloatArray {
        val sparse = fixture.getJSONObject(table).getJSONArray(id.toString())
        val coordinates = fixture.getJSONArray("coordinates")
        return FloatArray(1024).also { vector ->
            for (i in 0 until coordinates.length()) vector[coordinates.getInt(i)] = sparse.getDouble(i).toFloat()
        }
    }
    @Test fun multilingualTokenIdsMatchHuggingFaceReference() {
        val tokenizer = tokenizer()
        val cases = fixture.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val expected = case.getJSONArray("ids").let { ids -> IntArray(ids.length()) { ids.getInt(it) } }
            assertArrayEquals(case.getString("text"), expected, tokenizer.encode(case.getString("wrapped")))
        }
    }
    @Test fun japaneseInputEmbeddingsMatchOriginalCustomVoicePreparation() {
        val frontend = LamiVoiceTextFrontend(tokenizer(), { row("text_rows", it) }, { row("codec_rows", it) })
        val cases = fixture.getJSONArray("cases")
        val coordinates = fixture.getJSONArray("coordinates")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val actual = frontend.prepare(case.getString("text"))
            val expected = case.getJSONArray("prefill_coordinates")
            assertEquals(case.getString("text"), expected.length(), actual.prefill.size)
            for (p in 0 until expected.length()) for (j in 0 until coordinates.length()) {
                assertEquals("${case.getString("text")} position=$p coordinate=$j", expected.getJSONArray(p).getDouble(j).toFloat(), actual.prefill[p][coordinates.getInt(j)], 0.0001f)
            }
            for (j in 0 until coordinates.length()) assertEquals(case.getJSONArray("pad_coordinates").getDouble(j).toFloat(), actual.pad[coordinates.getInt(j)], 0.0001f)
        }
    }
    @Test fun rejectsBlankSpecialSyntaxAndCacheOverflow() {
        val frontend = LamiVoiceTextFrontend(tokenizer(), { row("text_rows", it) }, { row("codec_rows", it) })
        assertThrows(IllegalArgumentException::class.java) { frontend.prepare(" ") }
        assertThrows(IllegalArgumentException::class.java) { frontend.prepare("<|im_start|>") }
        assertThrows(IllegalArgumentException::class.java) { frontend.prepare("こんにちは。", capacity = 12) }
    }
}
