package io.github.ninbyo02.lami.ui.screens.home

import java.security.MessageDigest

internal data class ExactTokenCountKey(
    val modelPath: String,
    val modelSize: Long,
    val modelModified: Long,
    val promptDigest: String,
    val responseDigest: String,
)

internal fun tokenTextDigest(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Stores counts, never text, tokenizer objects, sessions or engines. */
internal class ExactTokenCountCache<V>(private val capacity: Int) {
    init { require(capacity > 0) }
    private val values = LinkedHashMap<ExactTokenCountKey, V>(capacity, 0.75f, true)

    @Synchronized fun get(key: ExactTokenCountKey): V? = values[key]

    @Synchronized fun put(key: ExactTokenCountKey, value: V) {
        values[key] = value
        while (values.size > capacity) values.remove(values.keys.first())
    }
}
