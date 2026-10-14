package io.github.ninbyo02.lami.tts

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Debug-only first feedback edge. Uses greedy argmax, not production sampling. */
internal object ResidentCpGreedyFeedback {
    private const val WIDTH = 1024
    private const val CODES = 2048
    private const val ROW_BYTES = WIDTH * 4
    const val HEAD_SHA256 = "1bcf9112dde57e7c40fa31b455b2e60bdba0dbae75f3edfeed9f83594d3782af"
    const val EMBEDDING_SHA256 = "deec193c5d8824dc20b47eb71c4c59f6a56cbd9585d22a5f029ca31172df1958"

    fun select(head: File, embedding: File, hidden: FloatArray, headIndex: Int): Pair<Int, FloatArray> {
        require(hidden.size == WIDTH && hidden.all(Float::isFinite))
        require(headIndex in 0..14)
        val hashes = File(head.parentFile, "resident-cp-heads-embeddings.sha256").readLines()
        val pairHashes = hashes[headIndex].split(" ")
        require(pairHashes.size == 2 && pairHashes.all { it.matches(Regex("[0-9a-f]{64}")) })
        for ((file, hash) in listOf(head to pairHashes[0], embedding to pairHashes[1])) {
            require(file.isFile && file.length() == CODES.toLong() * ROW_BYTES &&
                ResidentCpSingleStepFixture.sha256(file) == hash) { "Incorrect feedback fixture" }
        }
        var best = -1
        var bestScore = Double.NEGATIVE_INFINITY
        RandomAccessFile(head, "r").use { reader ->
            val bytes = ByteArray(ROW_BYTES)
            for (code in 0 until CODES) {
                reader.readFully(bytes)
                val row = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                var score = 0.0
                for (i in 0 until WIDTH) score += row.get(i).toDouble() * hidden[i]
                require(score.isFinite()) { "Non-finite head score" }
                if (score > bestScore) { bestScore = score; best = code }
            }
        }
        require(best in 0 until CODES)
        val bytes = ByteArray(ROW_BYTES)
        RandomAccessFile(embedding, "r").use {
            it.seek(best.toLong() * ROW_BYTES)
            it.readFully(bytes)
        }
        val row = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return best to FloatArray(WIDTH) { row.get(it) }.also {
            require(it.all(Float::isFinite)) { "Invalid selected embedding" }
        }
    }
}
