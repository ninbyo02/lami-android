package io.github.ninbyo02.lami.tts

/**
 * Fail-closed contract for a compiled CP context before QNN JNI binding.
 * Caller must verify SHA-256 against the actual file bytes before constructing a native driver.
 */
internal data class ResidentCpContextSpec(
    val sha256: String,
    val hiddenWidth: Int,
    val positionsPerFrame: Int,
    val kvCacheCapacity: Int,
    val modelIdentity: String,
) {
    init {
        require(sha256.length == 64 && sha256.all { it in "0123456789abcdef" })
        require(hiddenWidth > 0 && hiddenWidth <= 16384)
        require(positionsPerFrame == 16)
        require(kvCacheCapacity >= positionsPerFrame)
        require(modelIdentity.isNotBlank())
    }
    fun requireCompatible(other: ResidentCpContextSpec) {
        require(this == other) { "Resident CP compiled context or tensor layout changed" }
    }
}
