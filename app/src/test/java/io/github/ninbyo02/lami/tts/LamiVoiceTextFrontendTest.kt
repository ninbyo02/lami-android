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
    private fun assertPrefillMatchesReference(case: JSONObject, actual: LamiVoiceTextContext) {
        val coordinates = fixture.getJSONArray("coordinates")
        val expected = case.getJSONArray("prefill_coordinates")
        assertEquals(case.getString("text"), expected.length(), actual.prefill.size)
        for (p in 0 until expected.length()) for (j in 0 until coordinates.length()) {
            assertEquals("${case.getString("text")} position=$p coordinate=$j", expected.getJSONArray(p).getDouble(j).toFloat(), actual.prefill[p][coordinates.getInt(j)], 0.0001f)
        }
        for (j in 0 until coordinates.length()) assertEquals(case.getJSONArray("pad_coordinates").getDouble(j).toFloat(), actual.pad[coordinates.getInt(j)], 0.0001f)
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
    @Test fun mergeSelectionMatchesPreviousListBasedAlgorithm() {
        // Every substring is present, so all possible merge outcomes have token IDs.
        val words = (1..7).flatMap { length ->
            (0 until (1 shl length)).map { bits ->
                (0 until length).joinToString("") { if (bits and (1 shl it) == 0) "a" else "b" }
            }
        }
        val vocabulary = words.withIndex().associate { it.value to it.index }
        val pairs = words.flatMap { word ->
            (1 until word.length).map { split -> word.substring(0, split) to word.substring(split) }
        }.distinct()
        val random = java.util.Random(42)
        repeat(12) { round ->
            val ranks = pairs.filter { random.nextBoolean() }.associateWith {
                // Include ties and the largest valid rank, as well as absent pairs.
                if (round == 0) Int.MAX_VALUE else random.nextInt(8)
            }
            val tokenizer = LamiQwenTokenizer(vocabulary, ranks, mapOf("<special>" to -1))
            for (input in words) {
                var word = input.map(Char::toString)
                while (word.size > 1) {
                    val candidate = word.zipWithNext().mapNotNull { pair -> ranks[pair]?.let { pair to it } }
                        .minByOrNull { it.second }?.first ?: break
                    val merged = mutableListOf<String>()
                    var i = 0
                    while (i < word.size) {
                        if (i + 1 < word.size && word[i] == candidate.first && word[i + 1] == candidate.second) {
                            merged += word[i] + word[i + 1]
                            i += 2
                        } else {
                            merged += word[i++]
                        }
                    }
                    word = merged
                }
                assertArrayEquals("round=$round input=$input", word.map(vocabulary::getValue).toIntArray(), tokenizer.encode(input))
            }
        }
    }
    @Test fun japaneseInputEmbeddingsMatchOriginalCustomVoicePreparation() {
        val frontend = LamiVoiceTextFrontend(tokenizer(), { row("text_rows", it) }, { row("codec_rows", it) })
        val cases = fixture.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            assertPrefillMatchesReference(case, frontend.prepare(case.getString("text")))
        }
    }
    @Test fun repeatedSpeechCodecRowIsLoadedOnceWithoutChangingPrefill() {
        val codecCalls = linkedMapOf<Int, Int>()
        val frontend = LamiVoiceTextFrontend(
            tokenizer(),
            { row("text_rows", it) },
            {
                codecCalls[it] = (codecCalls[it] ?: 0) + 1
                row("codec_rows", it)
            }
        )
        val case = fixture.getJSONArray("cases").getJSONObject(0)
        val expectedIds = case.getJSONArray("ids").let { ids -> IntArray(ids.length()) { ids.getInt(it) } }
        val actual = frontend.prepare(case.getString("text"))

        assertArrayEquals(expectedIds, actual.tokenIds)
        assertPrefillMatchesReference(case, actual)
        assertEquals(1, codecCalls.getValue(2148))
        assertEquals(1, codecCalls.getValue(2149))
        assertEquals(setOf(2154, 2156, 2058, 2157, 3000, 2148, 2149), codecCalls.keys)
        assertTrue(codecCalls.values.all { it == 1 })
    }
    @Test fun rejectsBlankSpecialSyntaxAndCacheOverflow() {
        val frontend = LamiVoiceTextFrontend(tokenizer(), { row("text_rows", it) }, { row("codec_rows", it) })
        assertThrows(IllegalArgumentException::class.java) { frontend.prepare(" ") }
        assertThrows(IllegalArgumentException::class.java) { frontend.prepare("<|im_start|>") }
        assertThrows(IllegalArgumentException::class.java) { frontend.prepare("こんにちは。", capacity = 12) }
    }
}
