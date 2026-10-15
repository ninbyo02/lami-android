package io.github.ninbyo02.lami.tts

import java.io.File
import java.util.UUID

/** Fixed record slots also bound abandoned captures. Existing data is never removed. */
internal class LamiSpeechTraceStore(
    private val root: File,
    private val modelHash: String,
    private val runtimeHash: String,
    private val clockMs: () -> Long,
) {
    fun capture(requestId: Long, text: String): LamiSpeechTraceCapture = synchronized(lock) {
        require(!root.exists() || root.isDirectory)
        require(root.exists() || root.mkdirs())
        val entries = requireNotNull(root.listFiles())
        require(entries.size < MAX_RECORDS) { "Trace store full" }
        val id = "r" + requestId + "-" + UUID.randomUUID().toString()
        LamiSpeechTraceCapture(root.resolve(id), id, text, modelHash, runtimeHash, clockMs)
    }
    companion object {
        private val lock = Any()
        const val MAX_RECORDS = 8
    }
}
