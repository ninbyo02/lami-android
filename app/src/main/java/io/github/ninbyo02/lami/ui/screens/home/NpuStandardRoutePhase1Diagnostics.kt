package io.github.ninbyo02.lami.ui.screens.home

internal const val NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY =
    "debug.lami.npu_standard_route_dev_gate"
internal const val NPU_STANDARD_ROUTE_PHASE_PROPERTY =
    "debug.lami.npu_standard_route_phase"

internal const val NPU_STANDARD_ROUTE_PHASE_1 = "1"
internal const val NPU_STANDARD_ROUTE_PHASE_1_NAME = "1_route_entry_diagnostic"
internal const val NPU_STANDARD_ROUTE_PHASE_2 = "2"
internal const val NPU_STANDARD_ROUTE_PHASE_2_NAME = "2_conversation_created_diagnostic"
internal const val NPU_STANDARD_ROUTE_SUPPRESSION_REASON_NONE = "none"
internal const val NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL =
    "quality_candidate_fail"
internal const val NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE = "none"
internal const val NPU_STANDARD_ROUTE_ROLLBACK_REASON_QUALITY_GATE_OUTPUT =
    "quality_gate_output_must_not_reach_ui_tts_db"

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
    )
}

private fun buildNpuStandardRoutePhaseDiagnosticsMap(
    phase: String,
    connected: Boolean,
    outputQualityCandidateStatus: String,
): Map<String, String> {
    val phaseName = when (phase) {
        NPU_STANDARD_ROUTE_PHASE_2 -> NPU_STANDARD_ROUTE_PHASE_2_NAME
        else -> NPU_STANDARD_ROUTE_PHASE_1_NAME
    }
    val conversationCreated = phase == NPU_STANDARD_ROUTE_PHASE_2 && connected
    val qualityGatePassed = when (outputQualityCandidateStatus) {
        NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS -> "true"
        NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL -> "false"
        else -> "unavailable"
    }
    val outputSuppressed = outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL
    val suppressionReason = if (outputSuppressed) {
        NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL
    } else {
        NPU_STANDARD_ROUTE_SUPPRESSION_REASON_NONE
    }
    val rollbackRequired = outputSuppressed
    val rollbackReason = if (rollbackRequired) {
        NPU_STANDARD_ROUTE_ROLLBACK_REASON_QUALITY_GATE_OUTPUT
    } else {
        NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE
    }

    return linkedMapOf(
        "npu_standard_route_dev_gate_enabled" to "true",
        "npu_standard_route_phase" to phase,
        "npu_standard_route_phase_name" to phaseName,
        "npu_standard_route_connected" to connected.toString(),
        "conversation_created" to conversationCreated.toString(),
        "generate_response" to "false",
        "npu_standard_route_quality_gate_passed" to qualityGatePassed,
        "npu_standard_route_output_suppressed" to outputSuppressed.toString(),
        "npu_standard_route_suppression_reason" to suppressionReason,
        "npu_standard_route_ui_append_allowed" to "false",
        "npu_standard_route_tts_allowed" to "false",
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

private fun isNpuStandardRoutePhase1Backend(preferredBackend: String): Boolean {
    val normalized = preferredBackend.trim().uppercase()
    return normalized == "NPU" || normalized == "NPU_S1"
}

private fun resolveNpuStandardRouteDiagnosticPhase(
    propertyReader: (String) -> String?,
): String =
    when (propertyReader(NPU_STANDARD_ROUTE_PHASE_PROPERTY)?.trim()) {
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
