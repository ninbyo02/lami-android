package io.github.ninbyo02.lami.ui.screens.home

internal object NpuStandardRouteS4PseudoStreamingMapper {
    fun map(
        s1Result: NpuStandardRouteS1Result,
        finalText: String,
        sourceDisplayText: String = s1Result.displayText,
        minChunks: Int = NpuStandardRouteS4PseudoStreamingContract.DEFAULT_MIN_CHUNKS,
        maxChunks: Int = NpuStandardRouteS4PseudoStreamingContract.DEFAULT_MAX_CHUNKS,
    ): NpuStandardRouteS4PseudoStreamingMapping {
        if (!s1Result.successCriteriaMet) {
            return NpuStandardRouteS4PseudoStreamingMapping(
                pseudoStreamingCandidate = null,
                failureReason = NpuStandardRouteS4PseudoStreamingContract.FAILURE_S1_NOT_SUCCESS,
            )
        }

        val normalizedFinalText = finalText.trim()
        val normalizedSourceDisplayText = sourceDisplayText.trim()
        if (normalizedFinalText.isBlank() || normalizedSourceDisplayText.isBlank()) {
            return NpuStandardRouteS4PseudoStreamingMapping(
                pseudoStreamingCandidate = null,
                failureReason = NpuStandardRouteS4PseudoStreamingContract.FAILURE_EMPTY_TEXT,
            )
        }

        return NpuStandardRouteS4PseudoStreamingMapping(
            pseudoStreamingCandidate = NpuStandardRouteS4PseudoStreamingCandidate(
                finalText = normalizedFinalText,
                chunks = buildCumulativeChunks(
                    text = normalizedFinalText,
                    minChunks = minChunks,
                    maxChunks = maxChunks,
                ),
                sourceDisplayText = normalizedSourceDisplayText,
            ),
        )
    }

    private fun buildCumulativeChunks(
        text: String,
        minChunks: Int,
        maxChunks: Int,
    ): List<String> {
        val safeMinChunks = minChunks.coerceAtLeast(1)
        val safeMaxChunks = maxChunks.coerceAtLeast(safeMinChunks)
        if (text.length <= SHORT_TEXT_CHARS) return listOf(text)

        val targetChunkCount = safeMaxChunks.coerceAtMost(text.length).coerceAtLeast(safeMinChunks)
        val boundaries = chooseBoundaries(
            text = text,
            targetChunkCount = targetChunkCount,
        )
        return boundaries.map { endIndex -> text.substring(0, endIndex).trimEnd() }
            .filter { it.isNotBlank() }
            .distinct()
            .let { chunks ->
                if (chunks.lastOrNull() == text) chunks else chunks + text
            }
    }

    private fun chooseBoundaries(
        text: String,
        targetChunkCount: Int,
    ): List<Int> {
        val candidateBoundaries = naturalBoundaries(text)
        val wantedIntermediateCount = targetChunkCount - 1
        val intermediate = if (candidateBoundaries.size >= wantedIntermediateCount && wantedIntermediateCount > 0) {
            chooseEvenlySpaced(candidateBoundaries, wantedIntermediateCount)
        } else {
            characterBoundaries(
                textLength = text.length,
                targetChunkCount = targetChunkCount,
            )
        }
        return (intermediate + text.length)
            .map { it.coerceIn(1, text.length) }
            .distinct()
            .sorted()
    }

    private fun naturalBoundaries(text: String): List<Int> {
        val boundaries = mutableListOf<Int>()
        text.forEachIndexed { index, char ->
            val nextIndex = index + 1
            if (nextIndex >= text.length) return@forEachIndexed
            if (char == '\n') {
                boundaries += nextIndex
            } else if (char in SENTENCE_BOUNDARY_CHARS) {
                boundaries += nextIndex
            }
        }
        return boundaries.distinct().filter { it in 1 until text.length }
    }

    private fun chooseEvenlySpaced(
        boundaries: List<Int>,
        count: Int,
    ): List<Int> {
        if (count <= 0) return emptyList()
        if (boundaries.size <= count) return boundaries
        return (1..count).map { step ->
            val targetIndex = ((boundaries.size + 1) * step / (count + 1) - 1).coerceIn(0, boundaries.lastIndex)
            boundaries[targetIndex]
        }.distinct()
    }

    private fun characterBoundaries(
        textLength: Int,
        targetChunkCount: Int,
    ): List<Int> =
        (1 until targetChunkCount)
            .map { step -> (textLength * step / targetChunkCount).coerceIn(1, textLength - 1) }
            .distinct()

    private const val SHORT_TEXT_CHARS = 24
    private val SENTENCE_BOUNDARY_CHARS = setOf('。', '！', '？', '.', '!', '?')
}

internal fun buildNpuStandardRouteS4APseudoStreamingSavedResult(
    s1Result: NpuStandardRouteS1Result,
    finalText: String,
): NpuStandardRouteS1Result {
    val s4Selection = s1Result.selection.copy(
        routeType = NpuStandardRouteS4PseudoStreamingContract.ROUTE_TYPE,
        sideEffects = s1Result.selection.sideEffects.copy(
            db = true,
            conversationHistorySaved = true,
            markdown = true,
            streaming = true,
            tts = false,
        ),
    )
    val normalizedFinalText = finalText.trim()
    return s1Result.copy(
        selection = s4Selection,
        sanitizedOutput = normalizedFinalText.ifBlank { s1Result.sanitizedOutput },
        s2DbReason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        displayText = NpuStandardRouteS1Contract.displayText(
            selection = s4Selection,
            status = s1Result.status,
            reason = s1Result.reason,
            rawOutput = s1Result.rawOutput,
            sanitizedOutput = normalizedFinalText.ifBlank { s1Result.sanitizedOutput },
            qualityClassification = s1Result.qualityClassification,
            runDecodeReached = s1Result.runDecodeReached,
            npuBackendEvidence = s1Result.npuBackendEvidence,
            fallbackUsed = s1Result.fallbackUsed,
            timeout = s1Result.timeout,
            freshCrash = s1Result.freshCrash,
            selectedModelName = s1Result.selectedModelName,
            selectedModelFile = s1Result.selectedModelFile,
            npuModelEligible = s1Result.npuModelEligible,
            timing = s1Result.timing,
            s2DbReason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        ),
    )
}
