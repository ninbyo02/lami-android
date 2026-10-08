package io.github.ninbyo02.lami.tts

import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object LamiVoiceMatrixKernels {
    init {
        System.loadLibrary("lami_voice_matrix")
        // Device-side arithmetic check: sequential float32, no fused multiply-add.
        val weights = ByteBuffer.allocateDirect(7 * 1024 * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val hidden = FloatArray(1024) { (it % 17 - 8) / 13f }
        for (i in 0 until weights.capacity()) weights.put(i, (i % 31 - 15) / 23f)
        val expected = FloatArray(7) { r ->
            var score = 0f
            for (j in 0 until 1024) score += weights.get(r * 1024 + j) * hidden[j]
            score
        }
        check(logits(weights, hidden, 7).map(Float::toBits) == expected.map(Float::toBits)) {
            "Native voice head arithmetic differs from sequential float32"
        }
        val rowMajor = ByteBuffer.allocateDirect(8 * 1024 * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val packed = ByteBuffer.allocateDirect(8 * 1024 * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        for (i in 0 until rowMajor.capacity()) rowMajor.put(i, (i % 31 - 15) / 23f)
        pack4(rowMajor, packed, 8)
        check(logitsPacked4(packed, hidden, 8).map(Float::toBits) == logits(rowMajor, hidden, 8).map(Float::toBits)) {
            "Packed voice head arithmetic differs from row-major float32"
        }
    }
    external fun pack4(source: Buffer, destination: Buffer, rows: Int)
    external fun logitsPacked4(weights: Buffer, hidden: FloatArray, rows: Int): FloatArray
    external fun logits(weights: Buffer, hidden: FloatArray, rows: Int): FloatArray
}
