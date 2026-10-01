package io.github.ninbyo02.lami.tts

import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object LamiVoiceMatrixKernels {
    init {
        System.loadLibrary("lami_voice_matrix")
        // Device-side arithmetic check: sequential float32, no fused multiply-add.
        val weights = ByteBuffer.allocateDirect(3 * 1024 * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val hidden = FloatArray(1024) { (it % 17 - 8) / 13f }
        for (i in 0 until weights.capacity()) weights.put(i, (i % 31 - 15) / 23f)
        val expected = FloatArray(3) { r ->
            var score = 0f
            for (j in 0 until 1024) score += weights.get(r * 1024 + j) * hidden[j]
            score
        }
        check(logits(weights, hidden, 3).map(Float::toBits) == expected.map(Float::toBits)) {
            "Native voice head arithmetic differs from sequential float32"
        }
    }
    external fun logits(weights: Buffer, hidden: FloatArray, rows: Int): FloatArray
}
