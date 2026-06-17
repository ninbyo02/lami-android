package io.github.ninbyo02.lami.ui.screens.home

internal const val NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY =
    "debug.lami.npu_standard_route_dev_gate"
internal const val NPU_STANDARD_ROUTE_PHASE_PROPERTY =
    "debug.lami.npu_standard_route_phase"

internal const val NPU_STANDARD_ROUTE_PHASE_1 = "1"
internal const val NPU_STANDARD_ROUTE_PHASE_1_NAME = "1_route_entry_diagnostic"
internal const val NPU_STANDARD_ROUTE_PHASE_2 = "2"
internal const val NPU_STANDARD_ROUTE_PHASE_2_NAME = "2_conversation_created_diagnostic"
internal const val NPU_STANDARD_ROUTE_PHASE_3 = "3"
internal const val NPU_STANDARD_ROUTE_PHASE_3_NAME = "3_generate_response_diagnostic"
internal const val NPU_STANDARD_ROUTE_PHASE_4 = "4"
internal const val NPU_STANDARD_ROUTE_PHASE_4_NAME = "4_ui_append_gate"
internal const val NPU_STANDARD_ROUTE_PHASE_5 = "5"
internal const val NPU_STANDARD_ROUTE_PHASE_5_NAME = "5_tts_gate"
internal const val NPU_STANDARD_ROUTE_SUPPRESSION_REASON_NONE = "none"
internal const val NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL =
    "quality_candidate_fail"
internal const val NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE = "none"
internal const val NPU_STANDARD_ROUTE_ROLLBACK_REASON_QUALITY_GATE_OUTPUT =
    "quality_gate_output_must_not_reach_ui_tts_db"
internal const val NPU_STANDARD_ROUTE_ROLLBACK_REASON_PHASE3_QUALITY_FAIL =
    "quality_candidate_fail_output_suppressed_before_ui_tts_db"

internal fun buildNpuStandardRoutePhase1Diagnostics(
    context: LocalRouteDiagnosticContext,
    outputQualityCandidateStatus: String = "unavailable",
    propertyReader: (String) -> String? = ::readNpuStandardRouteDevGateProperty,
): Map<String, String> {
    val enabled = propertyReader(NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY)
        ?.trim()
        ?.equals("true", ignoreCase = true) == true
    if (!enabled) return emptyMap()
    if (!isNpuStandardRoutePhase1Backend(context.preferredBackend)) return emptyMap()

    val connected = context.shouldEnterNpuS1
    val phase = resolveNpuStandardRouteDiagnosticPhase(propertyReader)
    return buildNpuStandardRoutePhaseDiagnosticsMap(
        phase = phase,
        connected = connected,
        outputQualityCandidateStatus = outputQualityCandidateStatus,
    )
}

internal fun buildNpuStandardRoutePhase1DiagnosticsForNpuS1Result(
    result: NpuStandardRouteS1Result,
    backendDiagnostics: NpuS1BackendDiagnostics,
    propertyReader: (String) -> String? = ::readNpuStandardRouteDevGateProperty,
): Map<String, String> {
    val enabled = propertyReader(NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY)
        ?.trim()
        ?.equals("true", ignoreCase = true) == true
    if (!enabled) return emptyMap()
    if (!isNpuStandardRoutePhase1NpuS1DumpEligible(result, backendDiagnostics)) return emptyMap()

    val phase = resolveNpuStandardRouteDiagnosticPhase(propertyReader)
    return buildNpuStandardRoutePhaseDiagnosticsMap(
        phase = phase,
        connected = true,
        outputQualityCandidateStatus = result.outputQualityCandidateStatus,
        outputQualityCandidateReason = result.outputQualityCandidateReason,
        fallbackUsed = result.fallbackUsed,
        timeout = result.timeout,
        freshCrash = result.freshCrash,
        runDecodeReached = result.runDecodeReached,
        nativeCleanupReached = result.nativeDiagnostics.nativeCleanupReached,
        candidateTextPresent = result.actualDisplayText.isNotBlank(),
        candidateTextLength = result.actualDisplayText.length,
        ttsTextPresent = result.ttsText.isNotBlank(),
        ttsTextLength = result.ttsText.length,
    )
}

private fun buildNpuStandardRoutePhaseDiagnosticsMap(
    phase: String,
    connected: Boolean,
    outputQualityCandidateStatus: String,
    outputQualityCandidateReason: String = "",
    fallbackUsed: Boolean = false,
    timeout: Boolean = false,
    freshCrash: Boolean = false,
    runDecodeReached: Boolean? = null,
    nativeCleanupReached: String = "unavailable",
    candidateTextPresent: Boolean? = null,
    candidateTextLength: Int? = null,
    ttsTextPresent: Boolean? = null,
    ttsTextLength: Int? = null,
): Map<String, String> {
    val phaseName = when (phase) {
        NPU_STANDARD_ROUTE_PHASE_5 -> NPU_STANDARD_ROUTE_PHASE_5_NAME
        NPU_STANDARD_ROUTE_PHASE_4 -> NPU_STANDARD_ROUTE_PHASE_4_NAME
        NPU_STANDARD_ROUTE_PHASE_3 -> NPU_STANDARD_ROUTE_PHASE_3_NAME
        NPU_STANDARD_ROUTE_PHASE_2 -> NPU_STANDARD_ROUTE_PHASE_2_NAME
        else -> NPU_STANDARD_ROUTE_PHASE_1_NAME
    }
    val conversationCreated = phase in setOf(
        NPU_STANDARD_ROUTE_PHASE_2,
        NPU_STANDARD_ROUTE_PHASE_3,
        NPU_STANDARD_ROUTE_PHASE_4,
        NPU_STANDARD_ROUTE_PHASE_5,
    ) && connected
    val generateResponse = phase in setOf(
        NPU_STANDARD_ROUTE_PHASE_3,
        NPU_STANDARD_ROUTE_PHASE_4,
        NPU_STANDARD_ROUTE_PHASE_5,
    ) && connected
    val generateDiagnosticOnly = phase == NPU_STANDARD_ROUTE_PHASE_3 && generateResponse
    val qualityGatePassed = when (outputQualityCandidateStatus) {
        NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS -> "true"
        NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL -> "false"
        else -> "unavailable"
    }
    val qualityCandidatePassed = outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS
    val outputSuppressed = outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL
    val generateOrDeliveryPhase = phase in setOf(
        NPU_STANDARD_ROUTE_PHASE_3,
        NPU_STANDARD_ROUTE_PHASE_4,
        NPU_STANDARD_ROUTE_PHASE_5,
    )
    val suppressionReason = if (outputSuppressed) {
        if (generateOrDeliveryPhase) {
            outputQualityCandidateReason.ifBlank {
                NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL
            }
        } else {
            NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL
        }
    } else {
        NPU_STANDARD_ROUTE_SUPPRESSION_REASON_NONE
    }
    val rollbackReasons = buildList {
        if (outputSuppressed) {
            add(
                if (generateOrDeliveryPhase) {
                    NPU_STANDARD_ROUTE_ROLLBACK_REASON_PHASE3_QUALITY_FAIL
                } else {
                    NPU_STANDARD_ROUTE_ROLLBACK_REASON_QUALITY_GATE_OUTPUT
                },
            )
        }
        if (fallbackUsed) add("fallback_used")
        if (timeout) add("timeout")
        if (freshCrash) add("fresh_crash")
        if (runDecodeReached == false) add("decode_not_reached")
        if (nativeCleanupReached.equals("false", ignoreCase = true)) add("native_cleanup_not_reached")
    }
    val candidatePresent = candidateTextPresent == true
    val ttsPresent = ttsTextPresent == true
    val baseRunHealthy = connected &&
        !fallbackUsed &&
        !timeout &&
        !freshCrash &&
        runDecodeReached != false &&
        !nativeCleanupReached.equals("false", ignoreCase = true)
    val uiAppendAllowed = phase in setOf(NPU_STANDARD_ROUTE_PHASE_4, NPU_STANDARD_ROUTE_PHASE_5) &&
        baseRunHealthy &&
        qualityCandidatePassed &&
        candidatePresent
    val outputDeliveryAllowed = uiAppendAllowed
    val ttsAllowed = phase == NPU_STANDARD_ROUTE_PHASE_5 &&
        uiAppendAllowed &&
        ttsPresent
    val uiAppendSource = when {
        uiAppendAllowed -> "actual_display_text"
        phase !in setOf(NPU_STANDARD_ROUTE_PHASE_4, NPU_STANDARD_ROUTE_PHASE_5) -> "not_allowed_before_phase4"
        outputSuppressed -> "blocked_quality_candidate_fail"
        !candidatePresent -> "candidate_text_absent"
        fallbackUsed -> "blocked_fallback_used"
        timeout -> "blocked_timeout"
        freshCrash -> "blocked_fresh_crash"
        runDecodeReached == false -> "blocked_decode_not_reached"
        nativeCleanupReached.equals("false", ignoreCase = true) -> "blocked_native_cleanup_not_reached"
        else -> "blocked_quality_gate_unavailable"
    }
    val uiAppendBlockReason = when {
        uiAppendAllowed -> "none"
        phase !in setOf(NPU_STANDARD_ROUTE_PHASE_4, NPU_STANDARD_ROUTE_PHASE_5) -> "phase_not_ui_append"
        outputSuppressed -> NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL
        !candidatePresent -> "candidate_text_absent"
        fallbackUsed -> "fallback_used"
        timeout -> "timeout"
        freshCrash -> "fresh_crash"
        runDecodeReached == false -> "decode_not_reached"
        nativeCleanupReached.equals("false", ignoreCase = true) -> "native_cleanup_not_reached"
        else -> "quality_gate_unavailable"
    }
    val ttsSource = when {
        ttsAllowed -> "tts_text"
        phase != NPU_STANDARD_ROUTE_PHASE_5 -> "not_allowed_before_phase5"
        outputSuppressed -> "blocked_quality_candidate_fail"
        !uiAppendAllowed -> "blocked_ui_append_not_allowed"
        !ttsPresent -> "tts_text_absent"
        else -> "blocked_quality_gate_unavailable"
    }
    val ttsBlockReason = when {
        ttsAllowed -> "none"
        phase != NPU_STANDARD_ROUTE_PHASE_5 -> "phase_not_tts"
        outputSuppressed -> NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL
        !uiAppendAllowed -> uiAppendBlockReason
        !ttsPresent -> "tts_text_absent"
        else -> "quality_gate_unavailable"
    }
    val rollbackRequired = rollbackReasons.isNotEmpty()
    val rollbackReason = rollbackReasons.joinToString("+").ifBlank { NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE }

    return linkedMapOf(
        "npu_standard_route_dev_gate_enabled" to "true",
        "npu_standard_route_phase" to phase,
        "npu_standard_route_phase_name" to phaseName,
        "npu_standard_route_connected" to connected.toString(),
        "conversation_created" to conversationCreated.toString(),
        "generate_response" to generateResponse.toString(),
        "npu_standard_route_generate_diagnostic_only" to generateDiagnosticOnly.toString(),
        "npu_standard_route_quality_gate_passed" to qualityGatePassed,
        "npu_standard_route_output_suppressed" to outputSuppressed.toString(),
        "npu_standard_route_suppression_reason" to suppressionReason,
        "npu_standard_route_output_delivery_allowed" to outputDeliveryAllowed.toString(),
        "npu_standard_route_candidate_text_present" to (candidateTextPresent?.toString() ?: "unavailable"),
        "npu_standard_route_ui_append_allowed" to uiAppendAllowed.toString(),
        "npu_standard_route_ui_append_source" to uiAppendSource,
        "npu_standard_route_ui_appended_text_length" to if (uiAppendAllowed) {
            (candidateTextLength ?: 0).toString()
        } else {
            "0"
        },
        "npu_standard_route_ui_append_block_reason" to uiAppendBlockReason,
        "npu_standard_route_tts_allowed" to ttsAllowed.toString(),
        "npu_standard_route_tts_source" to ttsSource,
        "npu_standard_route_tts_text_length" to if (ttsAllowed) {
            (ttsTextLength ?: 0).toString()
        } else {
            "0"
        },
        "npu_standard_route_tts_block_reason" to ttsBlockReason,
        "npu_standard_route_db_save_allowed" to "false",
        "npu_standard_route_markdown_allowed" to "false",
        "npu_standard_route_streaming_allowed" to "false",
        "npu_standard_route_rollback_required" to rollbackRequired.toString(),
        "npu_standard_route_rollback_reason" to rollbackReason,
    )
}

internal fun buildNpuStandardRoutePhase1DiagnosticLines(
    diagnostics: Map<String, String>,
): List<String> =
    diagnostics.map { (key, value) -> "$key=$value" }

internal fun buildNpuStandardRouteDeliveryExecutionDiagnostics(
    uiAppendExecuted: Boolean,
    ttsRequested: Boolean,
    ttsStarted: Boolean,
    outputDeliveryExecuted: Boolean = uiAppendExecuted || ttsStarted,
    deliveryPath: String,
    ttsExecutionBlockReason: String = NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE,
): Map<String, String> = linkedMapOf(
    "npu_standard_route_ui_append_executed" to uiAppendExecuted.toString(),
    "npu_standard_route_tts_requested" to ttsRequested.toString(),
    "npu_standard_route_tts_started" to ttsStarted.toString(),
    "npu_standard_route_output_delivery_executed" to outputDeliveryExecuted.toString(),
    "npu_standard_route_delivery_path" to deliveryPath,
    "npu_standard_route_tts_execution_block_reason" to ttsExecutionBlockReason,
)

private fun isNpuStandardRoutePhase1Backend(preferredBackend: String): Boolean {
    val normalized = preferredBackend.trim().uppercase()
    return normalized == "NPU" || normalized == "NPU_S1"
}

private fun resolveNpuStandardRouteDiagnosticPhase(
    propertyReader: (String) -> String?,
): String =
    when (propertyReader(NPU_STANDARD_ROUTE_PHASE_PROPERTY)?.trim()) {
        NPU_STANDARD_ROUTE_PHASE_5 -> NPU_STANDARD_ROUTE_PHASE_5
        NPU_STANDARD_ROUTE_PHASE_4 -> NPU_STANDARD_ROUTE_PHASE_4
        NPU_STANDARD_ROUTE_PHASE_3 -> NPU_STANDARD_ROUTE_PHASE_3
        NPU_STANDARD_ROUTE_PHASE_2 -> NPU_STANDARD_ROUTE_PHASE_2
        else -> NPU_STANDARD_ROUTE_PHASE_1
    }

private fun isNpuStandardRoutePhase1NpuS1DumpEligible(
    result: NpuStandardRouteS1Result,
    backendDiagnostics: NpuS1BackendDiagnostics,
): Boolean {
    val explicitCpuOrGpu = backendDiagnostics.selectedBackend.equals("CPU", ignoreCase = true) ||
        backendDiagnostics.selectedBackend.equals("GPU", ignoreCase = true) ||
        backendDiagnostics.effectiveBackend.equals("CPU", ignoreCase = true) ||
        backendDiagnostics.effectiveBackend.equals("GPU", ignoreCase = true)
    if (explicitCpuOrGpu) return false
    val routeLooksNpu = backendDiagnostics.routeFamily.contains("npu", ignoreCase = true)
    val backendLooksNpu = backendDiagnostics.selectedBackend.contains("NPU", ignoreCase = true) ||
        backendDiagnostics.requestedBackend.contains("NPU", ignoreCase = true) ||
        backendDiagnostics.effectiveBackend.contains("NPU", ignoreCase = true)
    val evidenceLooksNpu = result.npuBackendEvidence.contains("QNN", ignoreCase = true) ||
        result.npuBackendEvidence.contains("HTP", ignoreCase = true) ||
        result.npuBackendEvidence.contains("FastRPC", ignoreCase = true) ||
        result.npuBackendEvidence.contains("NPU", ignoreCase = true) ||
        backendDiagnostics.backendEvidence.contains("QNN", ignoreCase = true) ||
        backendDiagnostics.backendEvidence.contains("HTP", ignoreCase = true) ||
        backendDiagnostics.backendEvidence.contains("FastRPC", ignoreCase = true) ||
        backendDiagnostics.backendEvidence.contains("NPU", ignoreCase = true)
    return routeLooksNpu || backendLooksNpu || evidenceLooksNpu
}

internal fun readNpuStandardRouteDevGateProperty(key: String): String? {
    if (key.isBlank()) return null
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        method.invoke(null, key, "") as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
