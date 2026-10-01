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
        val codecTags = listOf(2154, 2156, 2058, 2157, 3000, 2148, 2149)
        fun add(a: FloatArray, b: FloatArray): FloatArray {
            require(a.size == 1024 && b.size == 1024)
            return FloatArray(1024) { a[it] + b[it] }.also { require(it.all(Float::isFinite)) }
        }
        val prefill = mutableListOf<FloatArray>()
        ids.take(3).forEach { prefill += projectedText(it) }
        codecTags.dropLast(1).forEachIndexed { index, token -> prefill += add(if (index == codecTags.size - 2) bos else pad, codecEmbedding(token)) }
        ids.slice(3 until ids.size - 5).forEach { prefill += add(projectedText(it), codecEmbedding(2148)) }
        prefill += add(eos, codecEmbedding(2148))
        prefill += add(pad, codecEmbedding(2149))
        require(prefill.size < capacity - 2) { "Speech text does not fit model cache" }
        return LamiVoiceTextContext(ids, prefill, pad)
    }
}
