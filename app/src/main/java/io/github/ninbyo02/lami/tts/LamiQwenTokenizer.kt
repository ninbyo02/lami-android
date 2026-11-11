package io.github.ninbyo02.lami.tts

import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.regex.Pattern

/** Qwen2 byte-level BPE. The model's own vocabulary and merge ranks are required. */
internal class LamiQwenTokenizer(
    private val vocabulary: Map<String, Int>,
    private val ranks: Map<Pair<String, String>, Int>,
    private val specialTokens: Map<String, Int>,
) {
    companion object {
        // Explicit Unicode White_Space keeps JVM and Android token boundaries equal.
        // Android rejects UNICODE_CHARACTER_CLASS; it already uses Unicode classes.
        private const val whitespace = """\x{0009}-\x{000D}\x{0020}\x{0085}\x{00A0}\x{1680}\x{2000}-\x{200A}\x{2028}\x{2029}\x{202F}\x{205F}\x{3000}"""
        private val pretoken = Pattern.compile(
            """(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\r\n\p{L}\p{N}]?\p{L}+|\p{N}| ?[^${whitespace}\p{L}\p{N}]+[\r\n]*|[$whitespace]*[\r\n]+|[$whitespace]+(?![^$whitespace])|[$whitespace]+""",
        )
        private val byteCharacters: Array<String> = run {
            val visible = ((33..126) + (161..172) + (174..255)).toSet()
            var extra = 256
            Array(256) { b -> String(Character.toChars(if (b in visible) b else extra++)) }
        }
        fun load(root: File): LamiQwenTokenizer {
            val vocab = JSONObject(root.resolve("vocab.json").readText())
            val config = JSONObject(root.resolve("tokenizer_config.json").readText()).getJSONObject("added_tokens_decoder")
            val ranks = linkedMapOf<Pair<String, String>, Int>()
            root.resolve("merges.txt").useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#version:") }.forEachIndexed { rank, line ->
                    val pair = line.split(' ')
                    require(pair.size == 2)
                    ranks[pair[0] to pair[1]] = rank
                }
            }
            return LamiQwenTokenizer(vocab.keys().asSequence().associateWith { vocab.getInt(it) }, ranks,
                config.keys().asSequence().associate { key -> config.getJSONObject(key).getString("content") to key.toInt() })
        }
    }

    private val specialPattern = Pattern.compile(specialTokens.keys.sortedByDescending(String::length).joinToString("|") { Pattern.quote(it) })

    fun encode(input: String): IntArray {
        val text = Normalizer.normalize(input, Normalizer.Form.NFC)
        require(text.length <= 4096) { "TTS tokenizer input exceeds diagnostic limit" }
        val result = mutableListOf<Int>()
        val words = mutableMapOf<String, List<Int>>()
        val special = specialPattern.matcher(text)
        var start = 0
        while (special.find()) {
            ordinary(text, start, special.start(), result, words)
            result += specialTokens.getValue(special.group())
            start = special.end()
        }
        ordinary(text, start, text.length, result, words)
        return result.toIntArray()
    }

    private fun ordinary(text: String, start: Int, limit: Int, output: MutableList<Int>, words: MutableMap<String, List<Int>>) {
        // Opaque region bounds preserve substring lookahead semantics without copying the segment.
        val matcher = pretoken.matcher(text).region(start, limit)
        var end = start
        while (matcher.find()) {
            check(matcher.start() == end) { "Unmatched tokenizer input" }
            end = matcher.end()
            // Repeated pretokens have identical BPE results within this encode call.
            val word = matcher.group()
            output += words.getOrPut(word) { encodeWord(word) }
        }
        check(end == limit) { "Unmatched tokenizer suffix" }
    }

    private fun encodeWord(text: String): List<Int> {
        var word = text.toByteArray(Charsets.UTF_8).map { byteCharacters[it.toInt() and 255] }
        while (word.size > 1) {
            var candidate: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            for (index in 0 until word.lastIndex) {
                val pair = word[index] to word[index + 1]
                val rank = ranks[pair] ?: continue
                // Strict comparison keeps the first adjacent pair on rank ties.
                if (candidate == null || rank < bestRank) {
                    candidate = pair
                    bestRank = rank
                }
            }
            if (candidate == null) break
            val mergedToken = candidate.first + candidate.second
            val merged = ArrayList<String>(word.size)
            var i = 0
            while (i < word.size) {
                if (i + 1 < word.size && word[i] == candidate.first && word[i + 1] == candidate.second) {
                    merged += mergedToken
                    i += 2
                } else { merged += word[i]; i++ }
            }
            word = merged
        }
        return word.map { requireNotNull(vocabulary[it]) { "Token missing from model vocabulary" } }
    }
}
