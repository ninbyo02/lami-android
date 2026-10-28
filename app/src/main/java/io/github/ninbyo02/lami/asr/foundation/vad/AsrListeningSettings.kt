package io.github.ninbyo02.lami.asr.foundation.vad

/** Settings are snapshotted before capture; defaults preserve the verified headset path. */
data class AsrListeningSettings(
    val stableDetection: Boolean = false,
    val startThresholdRms: Int = 650,
    val endSilenceMillis: Int = 800,
    val noSpeechTimeoutMillis: Int = 10_000,
) {
    init {
        require(startThresholdRms in 100..4_000)
        require(endSilenceMillis in 420..5_000 && endSilenceMillis % 20 == 0)
        require(noSpeechTimeoutMillis in 3_000..10_000 && noSpeechTimeoutMillis % 20 == 0)
    }

    fun createVad(): VadEngine = EnergyVadEngine(
        startThresholdRms = startThresholdRms.toDouble(),
        continueThresholdRms = startThresholdRms * (350.0 / 650.0),
        startConfirmationFrames = if (stableDetection) 3 else 1,
        endConfirmationFrames = if (stableDetection) 5 else 1,
    )

    fun endpointConfig(): VadEndpointConfig = VadEndpointConfig(
        endSilenceFrames = endSilenceMillis / 20,
        noSpeechTimeoutFrames = noSpeechTimeoutMillis / 20,
    )

    fun wakeEndpointConfig(): VadEndpointConfig = endpointConfig().copy(minimumSpeechFrames = 6)
}
