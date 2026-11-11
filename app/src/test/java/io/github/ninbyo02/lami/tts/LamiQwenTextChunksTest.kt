package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class LamiQwenTextChunksTest {
    private fun tokenizer(): LamiQwenTokenizer {
        val fixture = JSONObject(checkNotNull(javaClass.getResource("/voice/qwen-text-frontend.json")).readText())
        val vocab = fixture.getJSONObject("vocab")
        // The golden frontend fixture stores only tokens at complete sentence
        // boundaries. Planning also encodes partial candidates, so include byte
        // fallbacks and every intermediate merge token using the real model IDs.
        val partialVocabulary = JSONObject(checkNotNull(javaClass.getResource("/voice/qwen-chunk-vocabulary.json")).readText())
        val vocabulary = vocab.keys().asSequence().associateWith(vocab::getInt).toMutableMap()
        for (token in partialVocabulary.keys()) vocabulary[token] = partialVocabulary.getInt(token)
        val special = fixture.getJSONObject("special")
        val merges = fixture.getJSONArray("merges")
        return LamiQwenTokenizer(vocabulary,
            (0 until merges.length()).associate { index -> merges.getJSONArray(index).let {
                (it.getString(0) to it.getString(1)) to it.getInt(2)
            } }, special.keys().asSequence().associateWith(special::getInt))
    }

    @Test fun multiThousandCharacterTextPreservesEveryCharacterWithinActualTokenAndAudioBudget() = runTest {
        val tokenizer = tokenizer()
        val text = "設定を確認しました。少しお待ちください。".repeat(200)
        assertTrue(text.length > 3_000)
        for (capacity in listOf(128, 256)) {
            val chunks = LamiQwenTextChunks.split(text, capacity) { LamiVoiceTextFrontend.prefillSize(it, tokenizer) }
            assertTrue(chunks.size > 2)
            assertEquals(text, chunks.joinToString(""))
            assertTrue(chunks.all { it.codePointCount(0, it.length) <= LamiQwenTextChunks.MAX_CODE_POINTS })
            assertTrue(chunks.filter { it.isNotBlank() }.all {
                LamiVoiceTextFrontend.prefillSize(it, tokenizer) + LamiQwenTextChunks.audioReserve(it) <= capacity
            })
        }
    }

    @Test fun shortSentenceRetainsExistingModelInput() = runTest {
        val tokenizer = tokenizer()
        val text = "設定を確認しました。少しお待ちください。"
        assertEquals(listOf(text), LamiQwenTextChunks.split(text, 256) { LamiVoiceTextFrontend.prefillSize(it, tokenizer) })
    }

    @Test fun sentencesThenClausesArePreferredBeforeHardLimits() = runTest {
        val first = "最初の説明はここまでです。"
        val rest = "続く説明では必要な項目を一つずつ確かめながら順番に進めます。"
        val chunks = LamiQwenTextChunks.split(first + rest, 256) { 11 + it.length }
        assertEquals(first, chunks.first())
        assertEquals(first + rest, chunks.joinToString(""))
        val clause = "ここでは前半の内容を説明して、"
        val tail = "必要な項目を一つずつ確かめながら次の手順へゆっくり進んでください"
        assertEquals(clause, LamiQwenTextChunks.split(clause + tail, 256) { 11 + it.length }.first())
    }

    @Test fun absentPunctuationStillSplitsAndNoWhitespaceIsLost() = runTest {
        val text = "  " + "長い文章が句読点なしで続きます".repeat(15) + "\t\n　"
        val chunks = LamiQwenTextChunks.split(text, 256) { 11 + it.length }
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.size > 2)
    }

    @Test fun emojisCombiningMarksFlagsAndCrLfStayIntact() = runTest {
        val family = "👩‍👩‍👧‍👦"
        val flag = "🇯🇵"
        val accent = "e\u0301"
        val text = "あ".repeat(31) + "😀" + family + accent + flag + "\r\n" +
            ("あ".repeat(29) + family + accent + flag).repeat(5)
        val chunks = LamiQwenTextChunks.split(text, 256) { 11 + it.codePointCount(0, it.length) }
        assertEquals(text, chunks.joinToString(""))
        assertEquals(6, chunks.sumOf { chunk -> Regex(Regex.escape(family)).findAll(chunk).count() })
        assertEquals(6, chunks.sumOf { chunk -> Regex(Regex.escape(flag)).findAll(chunk).count() })
        assertEquals(6, chunks.sumOf { chunk -> Regex(Regex.escape(accent)).findAll(chunk).count() })
        assertTrue(chunks.any { it.contains("\r\n") })
        assertTrue(chunks.all { chunk -> !Character.isLowSurrogate(chunk.first()) && !Character.isHighSurrogate(chunk.last()) })
    }

    @Test fun expensiveTokenizationShrinksChunksBeforeGeneration() = runTest {
        val text = "あ".repeat(96)
        val chunks = LamiQwenTextChunks.split(text, 128) { 11 + it.length * 5 }
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 10 })
        assertTrue(chunks.all { 11 + it.length * 5 + LamiQwenTextChunks.audioReserve(it) <= 128 })
    }

    @Test fun indivisibleOverBudgetCharacterFailsWithoutSilentlyDroppingIt() = runTest {
        val result = runCatching { LamiQwenTextChunks.split("a\u0301", 128) { 128 } }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
