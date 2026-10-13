package io.github.ninbyo02.lami.tts

internal data class LamiVoiceTextContext(val tokenIds: IntArray, val prefill: List<FloatArray>, val pad: FloatArray)

/** Qwen CustomVoice non-streaming input layout, with Japanese language and LAMI speaker. */
internal class LamiVoiceTextFrontend(
    private val tokenizer: LamiQwenTokenizer,
    private val projectedText: (Int) -> FloatArray,
    private val codecEmbedding: (Int) -> FloatArray,
) {
    fun prepare(text: String, capacity: Int = 256): LamiVoiceTextContext {
        require(text.isNotBlank()) { "Empty speech text" }
        require(!text.contains("<|")) { "Special-token syntax is not spoken text" }
        val ids = tokenizer.encode("<|im_start|>assistant\n$text<|im_end|>\n<|im_start|>assistant\n")
        require(ids.size >= 9)
        val pad = projectedText(151671)
        val bos = projectedText(151672)
        val eos = projectedText(151673)
        val codecSpeech = codecEmbedding(2148)
        val codecEnd = codecEmbedding(2149)
        val codecTags = intArrayOf(2154, 2156, 2058, 2157, 3000, 2148)
        fun add(a: FloatArray, b: FloatArray): FloatArray {
            require(a.size == 1024 && b.size == 1024)
            return FloatArray(1024) { a[it] + b[it] }.also { require(it.all(Float::isFinite)) }
        }
        val prefill = mutableListOf<FloatArray>()
        for (index in 0 until 3) prefill += projectedText(ids[index])
        for (index in codecTags.indices) {
            val token = codecTags[index]
            prefill += add(if (index == codecTags.lastIndex) bos else pad, if (token == 2148) codecSpeech else codecEmbedding(token))
        }
        for (index in 3 until ids.size - 5) prefill += add(projectedText(ids[index]), codecSpeech)
        prefill += add(eos, codecSpeech)
        prefill += add(pad, codecEnd)
        require(prefill.size < capacity - 2) { "Speech text does not fit model cache" }
        return LamiVoiceTextContext(ids, prefill, pad)
    }
}
