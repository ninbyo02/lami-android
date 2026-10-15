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
    @Test fun repeatedPairMergesPreserveTokenIdsAcrossPassesAndCalls() {
        val tokenizer = LamiQwenTokenizer(
            mapOf("a" to 1, "aa" to 2, "aaaa" to 3, "aaaaaaaa" to 4),
            mapOf(("a" to "a") to 0, ("aa" to "aa") to 1, ("aaaa" to "aaaa") to 2),
            mapOf("<special>" to -1),
        )
        // Repeated pairs, overlapping pairs, and an unmerged tail at each pass.
        repeat(2) {
            for (length in 1..63) {
                val expected = mutableListOf<Int>()
                repeat(length / 8) { expected += 4 }
                if (length % 8 >= 4) expected += 3
                if (length % 4 >= 2) expected += 2
                if (length % 2 == 1) expected += 1
                assertArrayEquals("length=$length", expected.toIntArray(), tokenizer.encode("a".repeat(length)))
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
    @Test fun repeatedTextRowsAreLoadedOncePerRequestWithBitExactPrefill() {
        val tokenizer = tokenizer()
        val calls = mutableListOf<Int>()
        var request = 0
        fun textRow(id: Int) = FloatArray(1024) { coordinate ->
            (id % 97 - coordinate + request).toFloat() / 128f
        }
        fun codecRow(id: Int) = FloatArray(1024) { coordinate ->
            (id % 31 + coordinate).toFloat() / 256f
        }
        fun add(a: FloatArray, b: FloatArray) = FloatArray(1024) { a[it] + b[it] }
        val frontend = LamiVoiceTextFrontend(tokenizer, { calls += it; textRow(it) }, ::codecRow)
        // Digits tokenize individually, demonstrating repeated body tokens.
        repeat(2) {
            request++
            calls.clear()
            val actual = frontend.prepare("111212111")
            val ids = tokenizer.encode("<|im_start|>assistant\n111212111<|im_end|>\n<|im_start|>assistant\n")
            val body = ids.slice(3 until ids.size - 5)
            assertTrue(body.size > body.distinct().size)
            val pad = textRow(151671)
            val speech = codecRow(2148)
            // Original preparation, including a fresh row for every body token.
            val expected = mutableListOf<FloatArray>()
            for (i in 0 until 3) expected += textRow(ids[i])
            val tags = intArrayOf(2154, 2156, 2058, 2157, 3000, 2148)
            for (i in tags.indices) expected += add(if (i == tags.lastIndex) textRow(151672) else pad, codecRow(tags[i]))
            for (id in body) expected += add(textRow(id), speech)
            expected += add(textRow(151673), speech)
            expected += add(pad, codecRow(2149))

            assertArrayEquals(ids, actual.tokenIds)
            assertArrayEquals(pad.map(Float::toRawBits).toIntArray(), actual.pad.map(Float::toRawBits).toIntArray())
            assertEquals(expected.size, actual.prefill.size)
            expected.indices.forEach { i ->
                assertArrayEquals("request=$request row=$i", expected[i].map(Float::toRawBits).toIntArray(), actual.prefill[i].map(Float::toRawBits).toIntArray())
            }
            assertEquals(listOf(151671, 151672, 151673) + ids.take(3) + body.distinct(), calls)
            // Every body position owns its storage, including nonadjacent repeats.
            for (i in body.indices) for (j in 0 until i) {
                assertNotSame(actual.prefill[9 + i], actual.prefill[9 + j])
            }
            val repeatedIndex = body.indices.drop(1).first { body[it] == body[0] }
            val repeatedRow = actual.prefill[9 + repeatedIndex]
            val original = repeatedRow[0].toRawBits()
            actual.prefill[9][0] = 12345f
            assertEquals(original, repeatedRow[0].toRawBits())
        }
    }
    @Test fun rejectsBlankSpecialSyntaxAndCacheOverflow() {
        val frontend = LamiVoiceTextFrontend(tokenizer(), { row("text_rows", it) }, { row("codec_rows", it) })
        assertThrows(IllegalArgumentException::class.java) { frontend.prepare(" ") }
        assertThrows(IllegalArgumentException::class.java) { frontend.prepare("<|im_start|>") }
        assertThrows(IllegalArgumentException::class.java) { frontend.prepare("こんにちは。", capacity = 12) }
    }
}
