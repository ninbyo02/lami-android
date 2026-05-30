package io.github.ninbyo02.lami.npu

data class DevOnlyNpuLifecycleArtifactParserInput(
    val runId: String,
    val stateText: String,
    val resultText: String,
    val nativeDiagText: String,
    val cleanupText: String,
    val artifactTimestampMs: Long,
    val files: DevOnlyNpuLifecycleFiles = DevOnlyNpuLifecycleWrapper.buildRunFiles(runId),
)

data class DevOnlyNpuLifecycleArtifactParserResult(
    val evidence: DevOnlyNpuLifecycleEvidence,
    val decision: DevOnlyNpuLifecycleDecision,
) {
    val classification: DevOnlyNpuLifecycleClassification
        get() = decision.classification
}

object DevOnlyNpuLifecycleArtifactParser {
    private val keyValueRegex = Regex("""(?:^|\s)([A-Za-z0-9_.-]+)=([^ \n]+)""")
    private val runIdRegex = Regex("""(?:^|\s)runId=([^ \n]+)""")

    fun parse(input: DevOnlyNpuLifecycleArtifactParserInput): DevOnlyNpuLifecycleArtifactParserResult {
        val stateValues = parseValues(input.stateText)
        val resultValues = parseValues(input.resultText)
        val nativeDiagValues = parseValues(input.nativeDiagText)
        val cleanupValues = parseValues(input.cleanupText)
        val resultWrittenAtMs = firstLong(
            resultValues,
            "result_written_at_ms",
            "written_at_ms",
            "timestamp_ms",
            "created_at_ms",
        ) ?: input.artifactTimestampMs
        val cleanupElapsedMs = firstLong(
            resultValues,
            "cleanup_elapsed_ms",
        ) ?: firstLong(
            nativeDiagValues,
            "cleanup_elapsed_ms",
        ) ?: firstLong(
            cleanupValues,
            "cleanup_elapsed_ms",
        )
        val timeout = hasTimeout(stateValues, resultValues, input.stateText, input.resultText)
        val resultCompleted = hasTerminalState(input.resultText) || completedResult(resultValues)
        val nativeCompleted = nativeCompleted(input.nativeDiagText, nativeDiagValues)
        val cleanupMarker = cleanupElapsedMs != null && nativeCompleted
        val evidence = DevOnlyNpuLifecycleEvidence(
            runId = input.runId,
            files = input.files,
            callbackRunId = observedRunId(input.runId, input.resultText),
            stateRunId = observedRunId(input.runId, input.stateText),
            resultRunId = observedRunId(input.runId, input.resultText),
            nativeDiagRunId = observedRunId(input.runId, input.nativeDiagText),
            cleanupRunId = observedRunId(input.runId, input.cleanupText),
            runStartedAtMs = input.artifactTimestampMs,
            resultWrittenAtMs = resultWrittenAtMs,
            startedMarker = hasStartedMarker(input.stateText, input.resultText),
            terminalMarker = resultCompleted,
            cleanupMarker = cleanupMarker,
            resultSuccess = resultSucceeded(resultValues, input.resultText),
            timeout = timeout,
            cleanupElapsedMs = cleanupElapsedMs,
            engineCloseUniquePtrCleanup = engineCloseEvidence(
                input.resultText,
                input.nativeDiagText,
                input.cleanupText,
            ),
            staleResultDetected = staleResultDetected(input.resultText, resultValues),
            sideEffects = parseSideEffects(stateValues + resultValues + cleanupValues),
        )
        return DevOnlyNpuLifecycleArtifactParserResult(
            evidence = evidence,
            decision = DevOnlyNpuLifecycleWrapper.evaluate(evidence),
        )
    }

    private fun parseValues(text: String): Map<String, String> =
        keyValueRegex.findAll(text).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }

    private fun observedRunId(expectedRunId: String, text: String): String? {
        if (text.isBlank()) return null
        val runIds = runIdRegex.findAll(text).map { it.groupValues[1] }.toSet()
        if (runIds.isEmpty()) return null
        return if (runIds.size == 1 && runIds.first() == expectedRunId) {
            expectedRunId
        } else {
            runIds.first { it != expectedRunId }
        }
    }

    private fun hasStartedMarker(stateText: String, resultText: String): Boolean =
        stateText.contains("state=started") ||
            resultText.contains("state=started") ||
            stateText.contains("status=started") ||
            resultText.contains("status=started")

    private fun hasTerminalState(resultText: String): Boolean =
        resultText.contains("state=success") ||
            resultText.contains("state=failure") ||
            resultText.contains("status=success") ||
            resultText.contains("status=failure")

    private fun completedResult(resultValues: Map<String, String>): Boolean =
        resultValues["result"] in setOf("success", "failure") ||
            resultValues["receiver_result_success"] in setOf("true", "false")

    private fun resultSucceeded(resultValues: Map<String, String>, resultText: String): Boolean =
        resultValues["result"] == "success" || resultText.contains("state=success")

    private fun hasTimeout(
        stateValues: Map<String, String>,
        resultValues: Map<String, String>,
        stateText: String,
        resultText: String,
    ): Boolean =
        stateValues["timeout"] == "true" ||
            resultValues["timeout"] == "true" ||
            stateText.contains("state=timeout") ||
            resultText.contains("state=timeout")

    private fun nativeCompleted(nativeDiagText: String, nativeDiagValues: Map<String, String>): Boolean =
        nativeDiagText.contains(" success ") ||
            nativeDiagText.contains(" result=success") ||
            nativeDiagValues["npu_backend_evidence"] == "QNN_HTP_V79_FastRPC_native_diag" ||
            nativeDiagText.contains("Engine.close=unique_ptr_cleanup")

    private fun engineCloseEvidence(vararg texts: String): Boolean =
        texts.any { it.contains("Engine.close=unique_ptr_cleanup") }

    private fun staleResultDetected(resultText: String, resultValues: Map<String, String>): Boolean =
        resultText.contains("stale_result=true") ||
            resultValues["stale_result"] == "true" ||
            resultValues["stale_result_detected"] == "true"

    private fun firstLong(values: Map<String, String>, vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { key -> values[key]?.toLongOrNull() }

    private fun parseSideEffects(values: Map<String, String>): DevOnlyNpuLifecycleSideEffects =
        DevOnlyNpuLifecycleSideEffects(
            assistantMessageListInserted = bool(values, "assistant_message_list_inserted"),
            selectedPathSaved = bool(values, "selected_path_npu_saved") ||
                bool(values, "selectedPathSaved") ||
                bool(values, "selected_path_saved"),
            db = bool(values, "db"),
            tts = bool(values, "tts"),
            markdown = bool(values, "markdown"),
            streaming = bool(values, "streaming") || bool(values, "stream"),
        )

    private fun bool(values: Map<String, String>, key: String): Boolean =
        values[key]?.equals("true", ignoreCase = true) == true
}
