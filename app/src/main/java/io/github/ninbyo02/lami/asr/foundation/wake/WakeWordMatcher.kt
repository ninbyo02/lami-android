package io.github.ninbyo02.lami.asr.foundation.wake

import java.text.Normalizer
import java.util.Locale

/** Exact whole-utterance matching, not fuzzy matching or a substring trigger. */
class WakeWordMatcher(phrase: String) {
    private val normalizedPhrase = normalize(phrase)

    init {
        require(phrase.length <= 64)
        require(normalizedPhrase.length in 2..24)
        require(normalizedPhrase.all { it.isLetterOrDigit() || it == '\u30fc' })
    }

    fun matches(transcript: String): Boolean = normalize(transcript) == normalizedPhrase

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .filterNot { it.isWhitespace() || Character.getType(it) in punctuationTypes }
        .map { character ->
            if (character in '\u30a1'..'\u30f6') (character.code - 0x60).toChar() else character
        }
        .joinToString("")

    private companion object {
        val punctuationTypes = setOf(
            Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
        )
    }
}
