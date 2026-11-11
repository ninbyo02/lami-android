package io.github.ninbyo02.lami.tts

internal data class LamiVoiceTextContext(val tokenIds: IntArray, val prefill: List<FloatArray>, val pad: FloatArray)

/** Qwen CustomVoice non-streaming input layout, with Japanese language and LAMI speaker. */
internal class LamiVoiceTextFrontend(
    private val tokenizer: LamiQwenTokenizer,
    private val projectedText: (Int) -> FloatArray,
    private val codecEmbedding: (Int) -> FloatArray,
) {
    companion object {
        private fun tokenIds(text: String, tokenizer: LamiQwenTokenizer): IntArray {
            require(text.isNotBlank()) { "Empty speech text" }
            require(!text.contains("<|")) { "Special-token syntax is not spoken text" }
            return tokenizer.encode("<|im_start|>assistant\n$text<|im_end|>\n<|im_start|>assistant\n")
                .also { require(it.size >= 9) }
        }

        /** Count the real wrapped text tokens without mapping/loading embedding weights. */
        fun prefillSize(text: String, tokenizer: LamiQwenTokenizer): Int = tokenIds(text, tokenizer).size + 3
    }

    fun prepare(text: String, capacity: Int = 256): LamiVoiceTextContext {
        val ids = tokenIds(text, tokenizer)
        // Three header rows, six codec tags, the body (ids - eight wrapper
        // tokens), then text EOS and codec-end rows: ids.size + three in total.
        require(ids.size + 3 < capacity - 2) { "Speech text does not fit model cache" }
        val pad = projectedText(151671)
        val bos = projectedText(151672)
        val eos = projectedText(151673)
        val codecSpeech = codecEmbedding(2148)
        val codecEnd = codecEmbedding(2149)
        val codecTags = intArrayOf(2154, 2156, 2058, 2157, 3000, 2148)
        fun add(a: FloatArray, b: FloatArray): FloatArray {
            require(a.size == 1024 && b.size == 1024)
            // Validate each sum as it is written, avoiding a second traversal of the row.
            return FloatArray(1024) { (a[it] + b[it]).also { sum -> require(sum.isFinite()) } }
        }
        val prefill = mutableListOf<FloatArray>()
        for (index in 0 until 3) prefill += projectedText(ids[index])
        for (index in codecTags.indices) {
            val token = codecTags[index]
            prefill += add(if (index == codecTags.lastIndex) bos else pad, if (token == 2148) codecSpeech else codecEmbedding(token))
        }
        // Reuse the sum for repeated speech tokens, retaining independent prefill storage.
        val speechRows = mutableMapOf<Int, FloatArray>()
        for (index in 3 until ids.size - 5) {
            val token = ids[index]
            val cached = speechRows[token]
            prefill += if (cached != null) cached.copyOf() else {
                add(projectedText(token), codecSpeech).also { speechRows[token] = it }
            }
        }
        prefill += add(eos, codecSpeech)
        prefill += add(pad, codecEnd)
        require(prefill.size < capacity - 2) { "Speech text does not fit model cache" }
        return LamiVoiceTextContext(ids, prefill, pad)
    }
}
