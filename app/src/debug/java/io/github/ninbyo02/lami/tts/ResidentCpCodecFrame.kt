package io.github.ninbyo02.lami.tts

/** Debug-only codec frame: one main code plus all fifteen CP codes. */
internal object ResidentCpCodecFrame {
    fun assemble(mainCode: Int, cpCodes: List<Int>): IntArray {
        require(mainCode in 0..2154) { "Invalid main codec token" }
        require(cpCodes.size == 15 && cpCodes.all { it in 0..2047 }) {
            "Incomplete or invalid CP code frame"
        }
        return intArrayOf(mainCode, *cpCodes.toIntArray())
    }
}
