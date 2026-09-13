package io.github.ninbyo02.lami.ui.screens.home

/** Release has no native tokenizer bridge and cannot opt into comparison. */
@Suppress("UNUSED_PARAMETER")
internal suspend fun recordStandaloneTokenCountComparison(
    modelPath: String?, input: String, output: String, snapshot: LocalInferenceMeasuredTokenSnapshot,
) = Unit

@Suppress("UNUSED_PARAMETER")
internal fun tryStandaloneGpuTokenCount(modelPath: String?, input: String, output: String): StandaloneGpuCountAttempt =
    StandaloneGpuCountAttempt(status = "disabled")
