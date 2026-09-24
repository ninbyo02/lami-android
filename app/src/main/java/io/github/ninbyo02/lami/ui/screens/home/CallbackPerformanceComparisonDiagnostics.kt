package io.github.ninbyo02.lami.ui.screens.home

internal fun classifyCallbackQuality(callbackCount: Int?, twoCharOrLessRatio: String?, averageChunkLength: String?): String {
    val count = callbackCount ?: 0
    val smallRatio = twoCharOrLessRatio?.toDoubleOrNull()
    val average = averageChunkLength?.toDoubleOrNull()
    return when {
        count == 0 -> "unavailable"
        smallRatio != null && smallRatio >= 0.85 && count >= 20 -> "pathological_single_char_stream"
        smallRatio != null && smallRatio >= 0.65 && count >= 16 -> "severe_fragmentation"
        average != null && average < 4.0 && count >= 12 -> "moderate_fragmentation"
        average != null && average >= 8.0 -> "healthy_large_chunks"
        else -> "moderate_fragmentation"
    }
}

internal fun classifyCallbackQualityCompareResult(
    gpuCandidateResult: String?,
    gpuSuspiciousDetected: Boolean?,
    cpuSuspiciousDetected: Boolean?,
    cpuFinished: Boolean?,
    cpuSkippedReason: String?,
    cpuExceptionClass: String? = null,
    cpuFailureStage: String? = null,
    cpuCallbackCount: Int? = null,
): String {
    val gpuCorrupt = gpuCandidateResult == "quality_candidate_fail" || gpuSuspiciousDetected == true
    if (
        isCpuCallbackCompareUnavailable(
            cpuFinished = cpuFinished,
            cpuSkippedReason = cpuSkippedReason,
            cpuExceptionClass = cpuExceptionClass,
            cpuFailureStage = cpuFailureStage,
            cpuCallbackCount = cpuCallbackCount,
        )
    ) {
        return if (gpuCorrupt) "gpu_corrupt_cpu_unavailable" else "comparison_unavailable"
    }
    val cpuCorrupt = cpuSuspiciousDetected == true
    return when {
        gpuCorrupt && cpuCorrupt -> "cpu_and_gpu_corrupt"
        gpuCorrupt -> "gpu_only_corrupt"
        cpuCorrupt -> "cpu_only_corrupt"
        else -> "both_pass"
    }
}

private fun isCpuCallbackCompareUnavailable(
    cpuFinished: Boolean?,
    cpuSkippedReason: String?,
    cpuExceptionClass: String?,
    cpuFailureStage: String?,
    cpuCallbackCount: Int?,
): Boolean {
    if (cpuFinished != true) return true
    if (!cpuSkippedReason.isNullOrBlank() && cpuSkippedReason != "none") return true
    if (!cpuExceptionClass.isNullOrBlank() && cpuExceptionClass !in setOf("none", "unavailable")) return true
    if (cpuFailureStage in setOf("timeout", "engine_initialize", "conversation_create", "generate_start", "generate_collect")) {
        return true
    }
    if (cpuCallbackCount == null || cpuCallbackCount <= 0) return true
    return false
}


internal fun classifyCallbackQualityCompareReason(
    cpuAvg: String?,
    gpuAvg: String?,
    cpuTwoCharRatio: String?,
    gpuTwoCharRatio: String?,
    cpuCount: Int?,
    gpuCount: Int?,
    cpuSkippedReason: String?,
): String {
    if (!cpuSkippedReason.isNullOrBlank() && cpuSkippedReason != "none") {
        return "cpu_compare_skipped:$cpuSkippedReason"
    }
    val cpuAverage = cpuAvg?.toDoubleOrNull()
    val gpuAverage = gpuAvg?.toDoubleOrNull()
    val cpuSmall = cpuTwoCharRatio?.toDoubleOrNull()
    val gpuSmall = gpuTwoCharRatio?.toDoubleOrNull()
    return when {
        cpuCount == null || gpuCount == null || cpuCount <= 0 || gpuCount <= 0 -> "comparison_unavailable"
        cpuAverage != null && gpuAverage != null &&
            cpuAverage >= 6.0 && gpuAverage <= 3.0 -> "gpu_chunks_much_smaller_than_cpu"
        cpuSmall != null && gpuSmall != null &&
            gpuSmall - cpuSmall >= 0.40 -> "gpu_two_char_ratio_much_higher_than_cpu"
        cpuAverage != null && gpuAverage != null &&
            kotlin.math.abs(cpuAverage - gpuAverage) <= 2.0 -> "cpu_gpu_callback_chunks_similar"
        else -> "cpu_gpu_callback_quality_recorded"
    }
}

internal fun calculateDoubleRatio(
    numerator: String?,
    denominator: String?,
): String {
    val n = numerator?.toDoubleOrNull()
    val d = denominator?.toDoubleOrNull()
    if (n == null || d == null || d == 0.0) return "unavailable"
    return "%.3f".format(java.util.Locale.US, n / d)
}

internal fun calculateDoubleDelta(
    lhs: String?,
    rhs: String?,
): String {
    val left = lhs?.toDoubleOrNull()
    val right = rhs?.toDoubleOrNull()
    if (left == null || right == null) return "unavailable"
    return "%.3f".format(java.util.Locale.US, left - right)
}

internal fun calculateIntDelta(
    lhs: Int?,
    rhs: Int?,
): String =
    if (lhs == null || rhs == null) {
        "unavailable"
    } else {
        (lhs - rhs).toString()
    }

internal fun classifyCpuGpuRawTextSimilarityHint(
    cpuCount: Int?,
    gpuCount: Int?,
    cpuSuspiciousDetected: Boolean?,
    gpuSuspiciousDetected: Boolean?,
    cpuAverageChunkLength: String?,
    gpuAverageChunkLength: String?,
): String {
    if (cpuCount == null || gpuCount == null || cpuCount <= 0 || gpuCount <= 0) return "comparison_unavailable"
    if (gpuSuspiciousDetected == true && cpuSuspiciousDetected != true) return "gpu_raw_callback_suspicious_cpu_clean"
    if (gpuSuspiciousDetected == true && cpuSuspiciousDetected == true) return "both_raw_callbacks_suspicious"
    val cpuAverage = cpuAverageChunkLength?.toDoubleOrNull()
    val gpuAverage = gpuAverageChunkLength?.toDoubleOrNull()
    return when {
        cpuAverage != null && gpuAverage != null && kotlin.math.abs(cpuAverage - gpuAverage) <= 2.0 ->
            "chunk_shape_similar"
        cpuAverage != null && gpuAverage != null && gpuAverage < cpuAverage ->
            "gpu_chunks_smaller"
        cpuAverage != null && gpuAverage != null && gpuAverage > cpuAverage ->
            "gpu_chunks_larger"
        else -> "raw_text_not_directly_compared"
    }
}

internal fun classifyGpuSamplerRootCauseCandidate(
    suspiciousDetected: Boolean,
    sourceCorruptionStage: String?,
    uiAppendChangedText: Boolean?,
    matrixMode: String?,
    callbackQualityClassification: String?,
): String =
    when {
        !suspiciousDetected -> "unknown"
        uiAppendChangedText == true -> "streaming_join_issue"
        sourceCorruptionStage == "raw_callback" &&
            matrixMode in setOf(
                GPU_OUTPUT_QUALITY_MATRIX_MODE_NO_SAMPLING_ACCELERATION,
                GPU_OUTPUT_QUALITY_MATRIX_MODE_DISABLE_TOPK_GPU_SAMPLER_CANDIDATE,
            ) -> "not_sampler_related"
        sourceCorruptionStage == "raw_callback" &&
            callbackQualityClassification in setOf("severe_fragmentation", "pathological_single_char_stream") ->
            "runtime_decode_fragmentation"
        sourceCorruptionStage == "raw_callback" -> "callback_source_corruption"
        matrixMode == GPU_OUTPUT_QUALITY_MATRIX_MODE_SAMPLER_MINIMAL -> "sampler_related"
        else -> "unknown"
    }

internal fun estimateVisibleTokensPerSecond(
    charLength: Int?,
    elapsedMs: Long?,
): String {
    if (charLength == null || elapsedMs == null || elapsedMs <= 0L) return "unavailable"
    val estimatedTokens = (charLength / 4.0).coerceAtLeast(1.0)
    return "%.1f".format(java.util.Locale.US, estimatedTokens * 1000.0 / elapsedMs)
}

internal fun classifyGpuPerfSlowPathReason(
    engineCreateOrReuse: String,
    engineAcquireElapsedMs: Long?,
    generateToFirstTokenMs: Long?,
    callbackTotalElapsedMs: Long?,
    visibleTokensPerSecond: String,
    tokenizerCountDurationMs: Long?,
): String {
    val visibleTps = visibleTokensPerSecond.toDoubleOrNull()
    return when {
        generateToFirstTokenMs != null && generateToFirstTokenMs > 2_000L -> "slow_first_token"
        tokenizerCountDurationMs != null && tokenizerCountDurationMs > 1_000L -> "slow_tokenizer_count"
        engineCreateOrReuse == "create" && engineAcquireElapsedMs != null && engineAcquireElapsedMs > 2_000L ->
            "cold_engine_load"
        callbackTotalElapsedMs != null && visibleTps != null && visibleTps < 8.0 -> "slow_callback_stream"
        engineCreateOrReuse == "reuse" && visibleTps != null && visibleTps < 8.0 -> "runtime_or_backend_slow"
        else -> "none"
    }
}
