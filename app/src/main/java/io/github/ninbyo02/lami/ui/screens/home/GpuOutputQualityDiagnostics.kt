package io.github.ninbyo02.lami.ui.screens.home

internal data class GpuOutputQualityDiagnostics(
    val rawLength: String, val rawHead: String, val rawTail: String, val promotedLength: String, val promotedHead: String, val promotedTail: String, val finalLength: String, val finalHead: String, val finalTail: String, val chunkCount: String, val emptyChunkCount: String, val nonEmptyChunkCount: String, val suspiciousDetected: String, val suspiciousReason: String, val suspiciousPosition: String, val suspiciousTailRatio: String, val repeatedMarkdownFragmentDetected: String, val mixedJapaneseFragmentDetected: String, val mixedLanguageFragmentDetected: String, val chunkJoinStrategy: String, val chunkBoundarySuspected: String, val lastChunksSummary: String, val chunkLengthHistogram: String, val matrixMode: String, val samplerMode: String, val streamingMode: String, val effectiveMaxTokens: String, val collectOnlyEnabled: String, val uiIncrementalAppendEnabled: String, val candidateResult: String, val failureBlockReason: String, val recommendation: String, val actualUiAppendedLength: String, val actualUiAppendedHead: String, val actualUiAppendedTail: String, val uiAppendChangedText: String, val sourceCorruptionStage: String, val callbackAverageChunkLength: String, val callbackMedianChunkLength: String, val callbackP50ChunkLength: String, val callbackP90ChunkLength: String, val callbackP95ChunkLength: String, val callbackOneCharChunkCount: String, val callbackTwoCharOrLessChunkCount: String, val callbackOneCharChunkRatio: String, val callbackTwoCharOrLessChunkRatio: String, val callbackLongestChunkLength: String, val callbackShortestNonEmptyChunkLength: String, val callbackFirstChunksArtifact: String, val callbackLastChunksArtifact: String, val fragmentationScore: String, val fragmentationPercentile: String, val fragmentationTailScore: String, val fragmentationMiddleScore: String, val fragmentationHeadScore: String, val chunkSizeDistribution: String, val chunkLengthSequence: String, val fragmentationClusterCount: String, val fragmentationClusterMaxLength: String, val fragmentationClusterAvgLength: String, val callbackQualityClassification: String, val callbackCorruptionEarliestStage: String, val cpuCompareRequested: String, val cpuCompareEnabled: String, val cpuCompareStarted: String, val cpuCompareFinished: String, val cpuCompareSkippedReason: String, val cpuCompareFailureStage: String, val cpuCompareElapsedMs: String, val cpuAverageChunkLength: String, val cpuMedianChunkLength: String, val cpuP90ChunkLength: String, val cpuP95ChunkLength: String, val cpuOneCharChunkCount: String, val cpuTwoCharOrLessChunkCount: String, val cpuOneCharChunkRatio: String, val gpuAverageChunkLength: String, val cpuCallbackCount: String, val cpuEmptyTextCount: String, val cpuNonEmptyTextCount: String, val gpuCallbackCount: String, val cpuTwoCharOrLessRatio: String, val cpuChunkLengthHistogram: String, val cpuCallbackFirstChunks: String, val cpuCallbackLastChunks: String, val cpuCallbackQualityClassification: String, val cpuOutputSuspiciousFragmentDetected: String, val cpuOutputSuspiciousFragmentReason: String, val cpuOutputSourceCorruptionStage: String, val gpuTwoCharOrLessRatio: String, val callbackQualityCompareResult: String, val callbackQualityCompareReason: String, val cpuGpuAvgChunkLengthRatio: String, val cpuGpuTwoCharOrLessRatioDelta: String, val cpuGpuCallbackCountDelta: String, val cpuGpuRawTextSimilarityHint: String, val cpuGpuSamePrompt: String, val cpuGpuSameMaxTokens: String, val cpuGpuSameSamplerConfigHint: String, val samplerRootCauseCandidate: String,
)

internal fun buildGpuOutputQualityDiagnostics(flags: LocalRouteDiagnosticFlags): GpuOutputQualityDiagnostics {
    val suspiciousReason = flags.gpuOutputSuspiciousFragmentReason
        ?: classifyGpuOutputSuspiciousFragmentReason(
            rawSample = listOfNotNull(flags.gpuOutputRawCallbackTextHead, flags.gpuOutputRawCallbackTextTail)
                .joinToString(" "),
            promotedSample = listOfNotNull(flags.gpuOutputPromotedTextHead, flags.gpuOutputPromotedTextTail)
                .joinToString(" "),
            finalSample = listOfNotNull(flags.gpuOutputFinalAssistantTextHead, flags.gpuOutputFinalAssistantTextTail)
                .joinToString(" "),
            rawLength = flags.gpuOutputRawCallbackTextLength,
            finalLength = flags.gpuOutputFinalAssistantTextLength,
            nonEmptyChunkCount = flags.gpuOutputNonEmptyChunkCount,
        )
    val suspiciousDetected = flags.gpuOutputSuspiciousFragmentDetected
        ?: (suspiciousReason != "none" && suspiciousReason != "unavailable")
    val repeatedMarkdown = flags.gpuOutputRepeatedMarkdownFragmentDetected
        ?: detectRepeatedMarkdownFragment(
            samples = listOf(
                flags.gpuOutputRawCallbackTextTail,
                flags.gpuOutputPromotedTextTail,
                flags.gpuOutputFinalAssistantTextTail,
            ),
        )
    val mixedJapanese = flags.gpuOutputMixedJapaneseFragmentDetected
        ?: detectMixedJapaneseFragment(
            samples = listOf(
                flags.gpuOutputRawCallbackTextTail,
                flags.gpuOutputPromotedTextTail,
                flags.gpuOutputFinalAssistantTextTail,
            ),
        )
    val mixedLanguage = flags.gpuOutputMixedLanguageFragmentDetected
        ?: (mixedJapanese || detectMixedLanguageFragment(
            samples = listOf(
                flags.gpuOutputRawCallbackTextTail,
                flags.gpuOutputPromotedTextTail,
                flags.gpuOutputFinalAssistantTextTail,
            ),
        ))
    val chunkBoundarySuspected = flags.gpuOutputChunkBoundarySuspected
        ?: (suspiciousReason in setOf(
            "many_tiny_fragments",
            "tail_tiny_chunk_run",
            "tail_markdown_fragment_bias",
            "japanese_particle_or_punctuation_fragment_run",
            "repeated_markdown_or_word_pattern",
            "promoted_text_suspicious_after_stream_join",
            "final_text_only_suspicious_after_ui_or_markdown",
        ))
    val matrixMode = flags.gpuOutputQualityMatrixMode ?: flags.gpuConfigDiagnostics?.outputQualityMatrixMode ?: "unavailable"
    val samplerMode = flags.gpuOutputQualitySamplerMode ?: flags.gpuConfigDiagnostics?.outputQualitySamplerMode ?: "unavailable"
    val streamingMode = flags.gpuOutputQualityStreamingMode
        ?: flags.gpuConfigDiagnostics?.outputQualityStreamingMode
        ?: "unavailable"
    val collectOnlyEnabled = flags.gpuOutputQualityCollectOnlyEnabled
        ?: flags.gpuConfigDiagnostics?.outputQualityCollectOnlyEnabled?.toBooleanStrictOrNull()
    val uiIncrementalAppendEnabled = flags.gpuOutputQualityUiIncrementalAppendEnabled
        ?: flags.gpuConfigDiagnostics?.outputQualityUiIncrementalAppendEnabled?.toBooleanStrictOrNull()
    val candidateResult = flags.gpuOutputQualityCandidateResult
        ?: classifyGpuOutputQualityCandidateResult(
            suspiciousDetected = suspiciousDetected,
            finalLength = flags.gpuOutputFinalAssistantTextLength,
            callbackNonEmptyCount = flags.gpuOutputNonEmptyChunkCount,
        )
    val failureBlockReason = flags.gpuOutputQualityFailureBlockReason
        ?: classifyGpuOutputQualityFailureBlockReason(
            suspiciousDetected = suspiciousDetected,
            suspiciousReason = suspiciousReason,
            collectOnlyEnabled = collectOnlyEnabled == true,
            uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
            sourceCorruptionStage = flags.gpuOutputSourceCorruptionStage,
        )
    return GpuOutputQualityDiagnostics(
        rawLength = flags.gpuOutputRawCallbackTextLength?.toString() ?: "unavailable", rawHead = flags.gpuOutputRawCallbackTextHead.toDiagnosticValue(), rawTail = flags.gpuOutputRawCallbackTextTail.toDiagnosticValue(), promotedLength = flags.gpuOutputPromotedTextLength?.toString() ?: "unavailable", promotedHead = flags.gpuOutputPromotedTextHead.toDiagnosticValue(), promotedTail = flags.gpuOutputPromotedTextTail.toDiagnosticValue(), finalLength = flags.gpuOutputFinalAssistantTextLength?.toString() ?: "unavailable", finalHead = flags.gpuOutputFinalAssistantTextHead.toDiagnosticValue(), finalTail = flags.gpuOutputFinalAssistantTextTail.toDiagnosticValue(), chunkCount = flags.gpuOutputCallbackChunkCount?.toString() ?: "unavailable", emptyChunkCount = flags.gpuOutputEmptyChunkCount?.toString() ?: "unavailable", nonEmptyChunkCount = flags.gpuOutputNonEmptyChunkCount?.toString() ?: "unavailable", suspiciousDetected = suspiciousDetected.toString(), suspiciousReason = suspiciousReason,
        suspiciousPosition = flags.gpuOutputSuspiciousFragmentPosition
            ?: classifyGpuOutputSuspiciousFragmentPosition(
                reason = suspiciousReason,
                headSample = listOfNotNull(
                    flags.gpuOutputRawCallbackTextHead,
                    flags.gpuOutputPromotedTextHead,
                    flags.gpuOutputFinalAssistantTextHead,
                ).joinToString(" "),
                tailSample = listOfNotNull(
                    flags.gpuOutputRawCallbackTextTail,
                    flags.gpuOutputPromotedTextTail,
                    flags.gpuOutputFinalAssistantTextTail,
                ).joinToString(" "),
            ),
        suspiciousTailRatio = flags.gpuOutputSuspiciousFragmentTailRatio
            ?: calculateGpuOutputSuspiciousTailRatio(
                tailSample = listOfNotNull(
                    flags.gpuOutputRawCallbackTextTail,
                    flags.gpuOutputPromotedTextTail,
                    flags.gpuOutputFinalAssistantTextTail,
                ).joinToString(" "),
            ),
        repeatedMarkdownFragmentDetected = repeatedMarkdown.toString(), mixedJapaneseFragmentDetected = mixedJapanese.toString(), mixedLanguageFragmentDetected = mixedLanguage.toString(), chunkJoinStrategy = flags.gpuOutputChunkJoinStrategy ?: "unavailable", chunkBoundarySuspected = chunkBoundarySuspected.toString(), lastChunksSummary = flags.gpuOutputLastChunksSummary.toDiagnosticValue(), chunkLengthHistogram = flags.gpuOutputChunkLengthHistogram.toDiagnosticValue(), matrixMode = matrixMode, samplerMode = samplerMode, streamingMode = streamingMode,
        effectiveMaxTokens = flags.gpuOutputQualityEffectiveMaxTokens
            ?: flags.gpuConfigDiagnostics?.outputQualityEffectiveMaxTokens
            ?: flags.gpuConfigDiagnostics?.maxTokens
            ?: "unavailable",
        collectOnlyEnabled = collectOnlyEnabled.toDiagnosticValue(), uiIncrementalAppendEnabled = uiIncrementalAppendEnabled.toDiagnosticValue(), candidateResult = candidateResult, failureBlockReason = failureBlockReason,
        recommendation = flags.gpuOutputQualityRecommendation
            ?: resolveGpuOutputQualityRecommendation(
                candidateResult = candidateResult,
                failureBlockReason = failureBlockReason,
                collectOnlyEnabled = collectOnlyEnabled == true,
            ),
        actualUiAppendedLength = flags.gpuOutputActualUiAppendedTextLength?.toString() ?: "unavailable", actualUiAppendedHead = flags.gpuOutputActualUiAppendedTextHead.toDiagnosticValue(), actualUiAppendedTail = flags.gpuOutputActualUiAppendedTextTail.toDiagnosticValue(), uiAppendChangedText = flags.gpuOutputUiAppendChangedText.toDiagnosticValue(),
        sourceCorruptionStage = flags.gpuOutputSourceCorruptionStage
            ?: classifyGpuOutputSourceCorruptionStage(
                suspiciousReason = suspiciousReason,
                uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
            ),
        callbackAverageChunkLength = flags.gpuCallbackAverageChunkLength ?: "unavailable", callbackMedianChunkLength = flags.gpuCallbackMedianChunkLength ?: "unavailable", callbackP50ChunkLength = flags.gpuCallbackP50ChunkLength ?: "unavailable", callbackP90ChunkLength = flags.gpuCallbackP90ChunkLength ?: "unavailable", callbackP95ChunkLength = flags.gpuCallbackP95ChunkLength ?: "unavailable", callbackOneCharChunkCount = flags.gpuCallbackOneCharChunkCount?.toString() ?: "unavailable", callbackTwoCharOrLessChunkCount = flags.gpuCallbackTwoCharOrLessChunkCount?.toString() ?: "unavailable", callbackOneCharChunkRatio = flags.gpuCallbackOneCharChunkRatio ?: "unavailable", callbackTwoCharOrLessChunkRatio = flags.gpuCallbackTwoCharOrLessChunkRatio ?: "unavailable", callbackLongestChunkLength = flags.gpuCallbackLongestChunkLength?.toString() ?: "unavailable", callbackShortestNonEmptyChunkLength = flags.gpuCallbackShortestNonEmptyChunkLength?.toString() ?: "unavailable", callbackFirstChunksArtifact = flags.gpuCallbackFirstChunksArtifact.toDiagnosticValue(), callbackLastChunksArtifact = flags.gpuCallbackLastChunksArtifact.toDiagnosticValue(), fragmentationScore = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_score"] ?: "unavailable", fragmentationPercentile = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_percentile"] ?: "unavailable", fragmentationTailScore = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_tail_score"] ?: "unavailable", fragmentationMiddleScore = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_middle_score"] ?: "unavailable", fragmentationHeadScore = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_head_score"] ?: "unavailable",
        chunkSizeDistribution = flags.gpuPrefillProbeDiagnostics["gpu_chunk_size_distribution"]
            ?: flags.gpuOutputChunkLengthHistogram
            ?: "unavailable",
        chunkLengthSequence = flags.gpuPrefillProbeDiagnostics["gpu_chunk_length_sequence"] ?: "unavailable", fragmentationClusterCount = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_cluster_count"] ?: "unavailable", fragmentationClusterMaxLength = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_cluster_max_length"] ?: "unavailable",
        fragmentationClusterAvgLength = flags.gpuPrefillProbeDiagnostics["gpu_fragmentation_cluster_avg_length"]
            ?: "unavailable",
        callbackQualityClassification = flags.callbackQualityClassification
            ?: classifyCallbackQuality(
                callbackCount = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                twoCharOrLessRatio = flags.gpuCallbackTwoCharOrLessChunkRatio,
                averageChunkLength = flags.gpuCallbackAverageChunkLength,
            ),
        callbackCorruptionEarliestStage = flags.callbackCorruptionEarliestStage
            ?: classifyGpuOutputSourceCorruptionStage(
                suspiciousReason = suspiciousReason,
                uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
            ),
        cpuCompareRequested = flags.cpuCompareRequested.toDiagnosticValue(), cpuCompareEnabled = flags.cpuCompareEnabled.toDiagnosticValue(), cpuCompareStarted = flags.cpuCompareStarted.toDiagnosticValue(), cpuCompareFinished = flags.cpuCompareFinished.toDiagnosticValue(), cpuCompareSkippedReason = flags.cpuCompareSkippedReason.toDiagnosticValue(), cpuCompareFailureStage = flags.cpuCompareFailureStage.toDiagnosticValue(), cpuCompareElapsedMs = flags.cpuCompareElapsedMs?.toString() ?: "unavailable", cpuAverageChunkLength = flags.cpuCallbackAverageChunkLength ?: "unavailable", cpuMedianChunkLength = flags.cpuCallbackMedianChunkLength ?: "unavailable", cpuP90ChunkLength = flags.cpuCallbackP90ChunkLength ?: "unavailable", cpuP95ChunkLength = flags.cpuCallbackP95ChunkLength ?: "unavailable", cpuOneCharChunkCount = flags.cpuCallbackOneCharChunkCount?.toString() ?: "unavailable", cpuTwoCharOrLessChunkCount = flags.cpuCallbackTwoCharOrLessChunkCount?.toString() ?: "unavailable", cpuOneCharChunkRatio = flags.cpuCallbackOneCharChunkRatio ?: "unavailable", gpuAverageChunkLength = flags.gpuCallbackAverageChunkLength ?: "unavailable", cpuCallbackCount = flags.cpuCompareCallbackInvokedCount?.toString() ?: "unavailable", cpuEmptyTextCount = flags.cpuCompareEmptyTextCount?.toString() ?: "unavailable", cpuNonEmptyTextCount = flags.cpuCompareNonEmptyTextCount?.toString() ?: "unavailable",
        gpuCallbackCount = (flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount)?.toString()
            ?: "unavailable",
        cpuTwoCharOrLessRatio = flags.cpuCallbackTwoCharOrLessRatio ?: "unavailable", cpuChunkLengthHistogram = flags.cpuCallbackChunkLengthHistogram.toDiagnosticValue(), cpuCallbackFirstChunks = flags.cpuCallbackFirstChunksArtifact.toDiagnosticValue(), cpuCallbackLastChunks = flags.cpuCallbackLastChunksArtifact.toDiagnosticValue(), cpuCallbackQualityClassification = flags.cpuCallbackQualityClassification.toDiagnosticValue(), cpuOutputSuspiciousFragmentDetected = flags.cpuOutputSuspiciousFragmentDetected.toDiagnosticValue(), cpuOutputSuspiciousFragmentReason = flags.cpuOutputSuspiciousFragmentReason.toDiagnosticValue(), cpuOutputSourceCorruptionStage = flags.cpuOutputSourceCorruptionStage.toDiagnosticValue(), gpuTwoCharOrLessRatio = flags.gpuCallbackTwoCharOrLessChunkRatio ?: "unavailable",
        callbackQualityCompareResult = flags.callbackQualityCompareResult
            ?: classifyCallbackQualityCompareResult(
                gpuCandidateResult = candidateResult,
                gpuSuspiciousDetected = suspiciousDetected,
                cpuSuspiciousDetected = flags.cpuOutputSuspiciousFragmentDetected,
                cpuFinished = flags.cpuCompareFinished,
                cpuSkippedReason = flags.cpuCompareSkippedReason,
                cpuExceptionClass = flags.cpuCompareExceptionClass,
                cpuFailureStage = flags.cpuCompareFailureStage,
                cpuCallbackCount = flags.cpuCompareCallbackInvokedCount,
            ),
        callbackQualityCompareReason = flags.callbackQualityCompareReason
            ?: classifyCallbackQualityCompareReason(
                cpuAvg = flags.cpuCallbackAverageChunkLength,
                gpuAvg = flags.gpuCallbackAverageChunkLength,
                cpuTwoCharRatio = flags.cpuCallbackTwoCharOrLessRatio,
                gpuTwoCharRatio = flags.gpuCallbackTwoCharOrLessChunkRatio,
                cpuCount = flags.cpuCompareCallbackInvokedCount,
                gpuCount = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                cpuSkippedReason = flags.cpuCompareSkippedReason,
            ),
        cpuGpuAvgChunkLengthRatio = flags.cpuGpuAvgChunkLengthRatio
            ?: calculateDoubleRatio(
                numerator = flags.gpuCallbackAverageChunkLength,
                denominator = flags.cpuCallbackAverageChunkLength,
            ),
        cpuGpuTwoCharOrLessRatioDelta = flags.cpuGpuTwoCharOrLessRatioDelta
            ?: calculateDoubleDelta(
                lhs = flags.gpuCallbackTwoCharOrLessChunkRatio,
                rhs = flags.cpuCallbackTwoCharOrLessRatio,
            ),
        cpuGpuCallbackCountDelta = flags.cpuGpuCallbackCountDelta
            ?: calculateIntDelta(
                lhs = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                rhs = flags.cpuCompareCallbackInvokedCount,
            ),
        cpuGpuRawTextSimilarityHint = flags.cpuGpuRawTextSimilarityHint
            ?: classifyCpuGpuRawTextSimilarityHint(
                cpuCount = flags.cpuCompareCallbackInvokedCount,
                gpuCount = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                cpuSuspiciousDetected = flags.cpuOutputSuspiciousFragmentDetected,
                gpuSuspiciousDetected = suspiciousDetected,
                cpuAverageChunkLength = flags.cpuCallbackAverageChunkLength,
                gpuAverageChunkLength = flags.gpuCallbackAverageChunkLength,
            ),
        cpuGpuSamePrompt = flags.cpuGpuSamePrompt.toDiagnosticValue(), cpuGpuSameMaxTokens = flags.cpuGpuSameMaxTokens.toDiagnosticValue(), cpuGpuSameSamplerConfigHint = flags.cpuGpuSameSamplerConfigHint.toDiagnosticValue(),
        samplerRootCauseCandidate = flags.gpuPrefillProbeDiagnostics["gpu_sampler_root_cause_candidate"]
            ?: classifyGpuSamplerRootCauseCandidate(
                suspiciousDetected = suspiciousDetected,
                sourceCorruptionStage = flags.gpuOutputSourceCorruptionStage
                    ?: classifyGpuOutputSourceCorruptionStage(
                        suspiciousReason = suspiciousReason,
                        uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
                    ),
                uiAppendChangedText = flags.gpuOutputUiAppendChangedText,
                matrixMode = matrixMode,
                callbackQualityClassification = flags.callbackQualityClassification
                    ?: classifyCallbackQuality(
                        callbackCount = flags.gpuOutputCallbackChunkCount ?: flags.gpuCallbackInvokedCount,
                        twoCharOrLessRatio = flags.gpuCallbackTwoCharOrLessChunkRatio,
                        averageChunkLength = flags.gpuCallbackAverageChunkLength,
                    ),
            ),
    )
}

internal fun classifyGpuOutputSuspiciousFragmentReason(
    rawSample: String,
    promotedSample: String,
    finalSample: String,
    rawLength: Int?,
    finalLength: Int?,
    nonEmptyChunkCount: Int?,
): String {
    val samplePattern = Regex(":\\*\\*|ml2|g）に）：：|[：:]{3,}|[)）]{4,}|[*＊]{4,}|[{}\\[\\]]{8,}")
    val tailPattern = Regex("([：:）。、・*_＊#`\\-]){5,}|([ぁ-んァ-ヶ一-龠][：:）)]){3,}|([はがをにでとへもやの、。]){7,}")
    val markdownTailBias = Regex("(\\*\\*|###|__|[_*＊#`\\-]).*(\\*\\*|###|__|[_*＊#`\\-])")
    val repeatedPattern = Regex("([*＊#`_\\-]{2,}|[A-Za-z]{2,8}\\W?).*\\1.*\\1")
    val rawSuspicious = samplePattern.containsMatchIn(rawSample)
    val promotedSuspicious = samplePattern.containsMatchIn(promotedSample)
    val finalSuspicious = samplePattern.containsMatchIn(finalSample)
    val tailSample = listOf(rawSample.takeLast(80), promotedSample.takeLast(80), finalSample.takeLast(80)).joinToString(" ")
    val repeatedSuspicious = repeatedPattern.containsMatchIn(tailSample)
    val safeNonEmptyChunkCount = nonEmptyChunkCount ?: 0
    val averageRawChunkLength = if (safeNonEmptyChunkCount > 0 && rawLength != null) {
        rawLength.toDouble() / safeNonEmptyChunkCount
    } else {
        null
    }
    return when {
        safeNonEmptyChunkCount >= 24 && averageRawChunkLength != null && averageRawChunkLength <= 2.0 ->
            "many_tiny_fragments"
        safeNonEmptyChunkCount >= 16 && averageRawChunkLength != null && averageRawChunkLength <= 2.5 ->
            "tail_tiny_chunk_run"
        finalSuspicious && !rawSuspicious && !promotedSuspicious -> "final_text_only_suspicious_after_ui_or_markdown"
        promotedSuspicious && !rawSuspicious -> "promoted_text_suspicious_after_stream_join"
        rawSuspicious -> "raw_callback_suspicious_fragment"
        markdownTailBias.containsMatchIn(tailSample) -> "tail_markdown_fragment_bias"
        tailPattern.containsMatchIn(tailSample) -> "japanese_particle_or_punctuation_fragment_run"
        repeatedSuspicious -> "repeated_markdown_or_word_pattern"
        finalLength != null && rawLength != null && finalLength > rawLength * 3 && rawLength > 0 ->
            "final_text_expanded_unexpectedly"
        else -> "none"
    }
}

private fun classifyGpuOutputSuspiciousFragmentPosition(reason: String, headSample: String, tailSample: String): String {
    if (reason == "none" || reason == "unavailable") return "none"
    val samplePattern = Regex(":\\*\\*|ml2|g）に）：：|[：:]{3,}|[)）]{4,}|[*＊]{4,}|[{}\\[\\]]{8,}")
    val headSuspicious = samplePattern.containsMatchIn(headSample)
    val tailSuspicious = samplePattern.containsMatchIn(tailSample)
    return when {
        tailSuspicious && !headSuspicious -> "tail"
        headSuspicious && !tailSuspicious -> "head"
        tailSuspicious && headSuspicious -> "middle"
        reason == "many_tiny_fragments" -> "tail"
        else -> "middle"
    }
}

private fun calculateGpuOutputSuspiciousTailRatio(tailSample: String): String {
    if (tailSample.isBlank()) return "unavailable"
    val suspiciousChars = tailSample.count { ch ->
        ch in listOf('*', '＊', ':', '：', ')', '）', '(', '（', '}', '{', '[', ']') ||
            ch.isDigit() ||
            ch.code in 0x3040..0x309F && tailSample.contains("ml", ignoreCase = true)
    }
    return "%.3f".format(java.util.Locale.US, suspiciousChars.toDouble() / tailSample.length.coerceAtLeast(1))
}

private fun detectRepeatedMarkdownFragment(samples: List<String?>): Boolean {
    val combined = samples.joinToString(" ")
    if (combined.isBlank()) return false
    return Regex("([*＊#`_\\-]{2,}).*\\1").containsMatchIn(combined) || Regex("(:\\*\\*|\\*\\*)").findAll(combined).count() >= 2
}

private fun detectMixedJapaneseFragment(samples: List<String?>): Boolean {
    val combined = samples.joinToString(" ")
    if (combined.isBlank()) return false
    val hasJapanese = combined.any { ch -> ch.code in 0x3040..0x30FF || ch.code in 0x4E00..0x9FFF }
    val hasAsciiNoise = Regex("(ml\\d+|[a-zA-Z]\\)|[a-zA-Z]）|\\d+[ぁ-んァ-ヶ一-龠])").containsMatchIn(combined)
    val manyJapanesePunctuation = Regex("[：）。、]{4,}").containsMatchIn(combined)
    return hasJapanese && (hasAsciiNoise || manyJapanesePunctuation)
}

private fun detectMixedLanguageFragment(samples: List<String?>): Boolean {
    val combined = samples.joinToString(" ")
    if (combined.isBlank()) return false
    val hasJapanese = combined.any { ch -> ch.code in 0x3040..0x30FF || ch.code in 0x4E00..0x9FFF }
    val hasDevanagari = combined.any { ch -> ch.code in 0x0900..0x097F }
    val hasArabic = combined.any { ch -> ch.code in 0x0600..0x06FF }
    val hasLatinNoiseNearJapanese = Regex("[ぁ-んァ-ヶ一-龠][A-Za-z]{2,}|[A-Za-z]{2,}[ぁ-んァ-ヶ一-龠]").containsMatchIn(combined)
    return hasJapanese && (hasDevanagari || hasArabic || hasLatinNoiseNearJapanese)
}

private fun classifyGpuOutputQualityCandidateResult(suspiciousDetected: Boolean, finalLength: Int?, callbackNonEmptyCount: Int?): String =
    when {
        suspiciousDetected -> "quality_candidate_fail"
        (finalLength ?: 0) > 0 && (callbackNonEmptyCount ?: 0) > 0 -> "quality_candidate_pass"
        else -> "quality_candidate_unknown"
    }

private fun classifyGpuOutputQualityFailureBlockReason(
    suspiciousDetected: Boolean, suspiciousReason: String, collectOnlyEnabled: Boolean, uiAppendChangedText: Boolean?, sourceCorruptionStage: String?,
): String =
    when {
        !suspiciousDetected -> "none"
        uiAppendChangedText == true -> "ui_append_changed_callback_text"
        sourceCorruptionStage == "raw_callback" -> "callback_source_already_suspicious"
        collectOnlyEnabled -> "collect_only_still_suspicious"
        suspiciousReason in setOf("many_tiny_fragments", "tail_tiny_chunk_run") -> "chunk_boundary_or_sampler_fragmentation"
        else -> suspiciousReason
    }

private fun resolveGpuOutputQualityRecommendation(candidateResult: String, failureBlockReason: String, collectOnlyEnabled: Boolean): String =
    when {
        candidateResult == "quality_candidate_pass" -> "none"
        failureBlockReason == "ui_append_changed_callback_text" -> "inspect_ui_append_or_markdown_path"
        failureBlockReason == "callback_source_already_suspicious" -> "compare_sampler_modes_and_max_tokens"
        collectOnlyEnabled -> "compare_sampler_modes_or_runtime_stack"
        else -> "run_collect_only_and_sampler_matrix"
    }

private fun classifyGpuOutputSourceCorruptionStage(suspiciousReason: String, uiAppendChangedText: Boolean?): String =
    when {
        suspiciousReason == "none" -> "none"
        uiAppendChangedText == true -> "ui_append_or_final_commit"
        suspiciousReason == "final_text_only_suspicious_after_ui_or_markdown" -> "final_assistant_text"
        suspiciousReason == "promoted_text_suspicious_after_stream_join" -> "promoted_or_chunk_join"
        else -> "raw_callback"
    }
