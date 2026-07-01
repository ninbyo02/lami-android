package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.NpuStandardRouteSelectionSource
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting

internal const val NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY =
    "debug.lami.npu_standard_route_dev_gate"
internal const val NPU_STANDARD_ROUTE_PHASE_PROPERTY =
    "debug.lami.npu_standard_route_phase"
internal const val NPU_STANDARD_ROUTE_COMPLETED_ROUTE_DISABLED_PROPERTY =
    "debug.lami.npu_standard_route_completed_route_disabled"

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
internal const val NPU_STANDARD_ROUTE_PHASE_6 = "6"
internal const val NPU_STANDARD_ROUTE_PHASE_6_NAME = "6_db_save_gate"
internal const val NPU_STANDARD_ROUTE_PHASE_7 = "7"
internal const val NPU_STANDARD_ROUTE_PHASE_7_NAME = "7_markdown_gate"
internal const val NPU_STANDARD_ROUTE_PHASE_8 = "8"
internal const val NPU_STANDARD_ROUTE_PHASE_8_NAME = "7b_pseudo_streaming_gate"
internal const val NPU_STANDARD_ROUTE_SUPPRESSION_REASON_NONE = "none"
internal const val NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL =
    "quality_candidate_fail"
internal const val NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE = "none"
internal const val NPU_STANDARD_ROUTE_ROLLBACK_REASON_QUALITY_GATE_OUTPUT =
    "quality_gate_output_must_not_reach_ui_tts_db"
internal const val NPU_STANDARD_ROUTE_ROLLBACK_REASON_PHASE3_QUALITY_FAIL =
    "quality_candidate_fail_output_suppressed_before_ui_tts_db"
internal const val NPU_STANDARD_ROUTE_SELECTION_MODE_USER_FACING =
    "user_facing_npu_experimental"
internal const val NPU_STANDARD_ROUTE_SELECTION_MODE_DEVELOPER_OVERRIDE =
    "developer_phase_override"
internal const val NPU_STANDARD_ROUTE_SELECTION_MODE_LOCAL_BACKEND = "local_backend"
internal const val NPU_STANDARD_ROUTE_SELECTION_MODE_LEGACY = "legacy_unspecified"
internal const val NPU_STANDARD_ROUTE_PHASE_SOURCE_COMPLETED_ROUTE_DEFAULT =
    "completed_route_default"
internal const val NPU_STANDARD_ROUTE_PHASE_SOURCE_DEBUG_PROPERTY = "debug_property"
internal const val NPU_STANDARD_ROUTE_PHASE_SOURCE_DEVELOPER_PHASE_SELECTION =
    "developer_phase_selection"
internal const val NPU_STANDARD_ROUTE_PHASE_SOURCE_GATE_DISABLED = "dev_gate_disabled"
internal const val NPU_STANDARD_ROUTE_PHASE_SOURCE_DISABLED_OR_SAFE_DEFAULT =
    "disabled_or_safe_default"
internal const val NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_NONE = "none"
internal const val NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_DEV_GATE_DISABLED =
    "dev_gate_disabled"
internal const val NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_KILL_SWITCH_DISABLED =
    "kill_switch_disabled"
internal const val NPU_STANDARD_ROUTE_DEVELOPER_PHASE_OVERRIDE_BLOCK_NONE = "none"
internal const val NPU_STANDARD_ROUTE_DEVELOPER_PHASE_OVERRIDE_BLOCK_DEV_GATE_DISABLED =
    "dev_gate_disabled"
internal const val NPU_STANDARD_ROUTE_COMPLETED_ROUTE_ROLLOUT_STATE_ENABLED =
    "enabled"
internal const val NPU_STANDARD_ROUTE_COMPLETED_ROUTE_ROLLOUT_STATE_DISABLED_BY_PROPERTY =
    "disabled_by_kill_switch"
internal const val NPU_STANDARD_ROUTE_COMPLETED_ROUTE_ROLLOUT_STATE_NOT_SELECTED =
    "not_selected"
internal const val NPU_STANDARD_ROUTE_ROLLBACK_REASON_KILL_SWITCH_DISABLED =
    "kill_switch_disabled_before_generation"
internal const val NPU_STANDARD_ROUTE_USER_FACING_BACKEND_NPU_EXPERIMENTAL =
    "NPU プレビュー"
internal const val NPU_STANDARD_ROUTE_COMPLETED_ROUTE_FAMILY =
    "npu_standard_route_completed"
internal const val NPU_STANDARD_ROUTE_INTERNAL_LEGACY_BACKEND_NPU_S5 = "NPU_S5"
internal const val NPU_STANDARD_ROUTE_INTERNAL_LEGACY_ROUTE_FAMILY_NPU_S5 = "npu_s5"

internal data class NpuStandardRouteRolloutSelection(
    val devGateEnabled: Boolean,
    val devGateRequired: Boolean,
    val rolloutGateEnabled: Boolean,
    val selectionMode: String,
    val userFacingBackend: String,
    val completedPhaseDefault: String,
    val completedRouteSelected: Boolean,
    val developerPhaseOverride: Boolean,
    val completedRouteBlockReason: String,
    val completedRouteKillSwitchEnabled: Boolean,
    val completedRouteDisabledByProperty: Boolean,
    val developerPhaseOverrideBlockReason: String,
    val completedRouteRolloutState: String,
    val effectivePhaseSource: String,
    val effectivePhase: String,
    val effectiveMode: NpuStandardRouteMode,
)

internal fun resolveNpuStandardRouteRolloutSelection(
    preferredBackend: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode,
    selectionSource: NpuStandardRouteSelectionSource,
    propertyReader: (String) -> String? = ::readNpuStandardRouteDevGateProperty,
): NpuStandardRouteRolloutSelection {
    val devGateEnabled = propertyReader(NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY)
        ?.trim()
        ?.equals("true", ignoreCase = true) == true
    val completedRouteDisabled = propertyReader(NPU_STANDARD_ROUTE_COMPLETED_ROUTE_DISABLED_PROPERTY)
        ?.trim()
        ?.equals("true", ignoreCase = true) == true
    val explicitPhase = resolveExplicitNpuStandardRouteDiagnosticPhase(propertyReader)
    val npuExperimentalSettingsShape = preferredBackend == PreferredBackendDryRunSetting.DEFAULT &&
        npuStandardRouteMode == NpuStandardRouteMode.FULL
    val userFacingNpu = npuExperimentalSettingsShape &&
        (
            selectionSource == NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL ||
                selectionSource == NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED
            )
    val completedRouteDisabledForUserFacingNpu = userFacingNpu && completedRouteDisabled
    val explicitDeveloperOverrideAllowed = explicitPhase != null && devGateEnabled
    val explicitDeveloperOverrideBlocked = explicitPhase != null && !devGateEnabled
    val developerSelectionSourceOverride =
        selectionSource == NpuStandardRouteSelectionSource.DEVELOPER_PHASE_OVERRIDE
    val developerPhaseOverride = explicitDeveloperOverrideAllowed ||
        (developerSelectionSourceOverride && devGateEnabled)
    val selectionMode = when {
        explicitDeveloperOverrideAllowed -> NPU_STANDARD_ROUTE_SELECTION_MODE_DEVELOPER_OVERRIDE
        userFacingNpu -> NPU_STANDARD_ROUTE_SELECTION_MODE_USER_FACING
        explicitDeveloperOverrideBlocked -> NPU_STANDARD_ROUTE_SELECTION_MODE_DEVELOPER_OVERRIDE
        developerSelectionSourceOverride ->
            NPU_STANDARD_ROUTE_SELECTION_MODE_DEVELOPER_OVERRIDE
        selectionSource == NpuStandardRouteSelectionSource.LOCAL_BACKEND ->
            NPU_STANDARD_ROUTE_SELECTION_MODE_LOCAL_BACKEND
        else -> NPU_STANDARD_ROUTE_SELECTION_MODE_LEGACY
    }
    val completedRouteSelected = userFacingNpu &&
        !completedRouteDisabled &&
        !explicitDeveloperOverrideAllowed
    val completedRouteBlockedByKillSwitch = completedRouteDisabledForUserFacingNpu
    val effectivePhase = when {
        explicitDeveloperOverrideAllowed -> requireNotNull(explicitPhase)
        completedRouteSelected -> NPU_STANDARD_ROUTE_PHASE_8
        completedRouteBlockedByKillSwitch -> NPU_STANDARD_ROUTE_PHASE_8
        else -> NPU_STANDARD_ROUTE_PHASE_1
    }
    val effectivePhaseSource = when {
        explicitDeveloperOverrideAllowed -> NPU_STANDARD_ROUTE_PHASE_SOURCE_DEBUG_PROPERTY
        completedRouteSelected -> NPU_STANDARD_ROUTE_PHASE_SOURCE_COMPLETED_ROUTE_DEFAULT
        completedRouteBlockedByKillSwitch -> NPU_STANDARD_ROUTE_PHASE_SOURCE_COMPLETED_ROUTE_DEFAULT
        else -> NPU_STANDARD_ROUTE_PHASE_SOURCE_DEVELOPER_PHASE_SELECTION
    }
    val completedRouteBlockReason = when {
        completedRouteDisabledForUserFacingNpu -> NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_KILL_SWITCH_DISABLED
        else -> NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_NONE
    }
    val completedRouteRolloutState = when {
        completedRouteSelected -> NPU_STANDARD_ROUTE_COMPLETED_ROUTE_ROLLOUT_STATE_ENABLED
        completedRouteDisabledForUserFacingNpu ->
            NPU_STANDARD_ROUTE_COMPLETED_ROUTE_ROLLOUT_STATE_DISABLED_BY_PROPERTY
        else -> NPU_STANDARD_ROUTE_COMPLETED_ROUTE_ROLLOUT_STATE_NOT_SELECTED
    }
    val developerPhaseOverrideBlockReason = if (explicitDeveloperOverrideBlocked) {
        NPU_STANDARD_ROUTE_DEVELOPER_PHASE_OVERRIDE_BLOCK_DEV_GATE_DISABLED
    } else {
        NPU_STANDARD_ROUTE_DEVELOPER_PHASE_OVERRIDE_BLOCK_NONE
    }
    return NpuStandardRouteRolloutSelection(
        devGateEnabled = devGateEnabled,
        devGateRequired = developerPhaseOverride,
        rolloutGateEnabled = completedRouteSelected || completedRouteBlockedByKillSwitch || developerPhaseOverride,
        selectionMode = selectionMode,
        userFacingBackend = if (userFacingNpu) {
            NPU_STANDARD_ROUTE_USER_FACING_BACKEND_NPU_EXPERIMENTAL
        } else {
            "unavailable"
        },
        completedPhaseDefault = NPU_STANDARD_ROUTE_PHASE_8,
        completedRouteSelected = completedRouteSelected,
        developerPhaseOverride = developerPhaseOverride,
        completedRouteBlockReason = completedRouteBlockReason,
        completedRouteKillSwitchEnabled = completedRouteDisabled,
        completedRouteDisabledByProperty = completedRouteDisabledForUserFacingNpu,
        developerPhaseOverrideBlockReason = developerPhaseOverrideBlockReason,
        completedRouteRolloutState = completedRouteRolloutState,
        effectivePhaseSource = effectivePhaseSource,
        effectivePhase = effectivePhase,
        effectiveMode = when {
            completedRouteBlockedByKillSwitch -> NpuStandardRouteMode.FULL
            developerSelectionSourceOverride && !devGateEnabled -> NpuStandardRouteMode.OFF
            else -> npuStandardRouteMode
        },
    )
}

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
        devGateEnabled = enabled,
    )
}

internal fun buildNpuStandardRoutePhase1DiagnosticsForNpuS1Result(
    result: NpuStandardRouteS1Result,
    backendDiagnostics: NpuS1BackendDiagnostics,
    rolloutSelection: NpuStandardRouteRolloutSelection? = null,
    propertyReader: (String) -> String? = ::readNpuStandardRouteDevGateProperty,
): Map<String, String> {
    val enabled = propertyReader(NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY)
        ?.trim()
        ?.equals("true", ignoreCase = true) == true
    if (
        !enabled &&
        rolloutSelection?.completedRouteSelected != true &&
        rolloutSelection?.completedRouteDisabledByProperty != true
    ) return emptyMap()
    if (!isNpuStandardRoutePhase1NpuS1DumpEligible(result, backendDiagnostics)) return emptyMap()

    val phase = rolloutSelection?.effectivePhase ?: resolveNpuStandardRouteDiagnosticPhase(propertyReader)
    return rolloutSelection.toDiagnosticsMap() + buildNpuStandardRoutePhaseDiagnosticsMap(
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
        devGateEnabled = enabled,
        completedRouteKillSwitchBlocked = rolloutSelection?.completedRouteDisabledByProperty == true,
    )
}

internal fun NpuStandardRouteRolloutSelection?.toDiagnosticsMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return linkedMapOf(
        "npu_standard_route_rollout_gate_enabled" to rolloutGateEnabled.toString(),
        "npu_standard_route_dev_gate_required" to devGateRequired.toString(),
        "npu_standard_route_selection_mode" to selectionMode,
        "npu_standard_route_user_facing_backend" to userFacingBackend,
        "npu_standard_route_completed_phase_default" to completedPhaseDefault,
        "npu_standard_route_completed_route_selected" to completedRouteSelected.toString(),
        "npu_standard_route_developer_phase_override" to developerPhaseOverride.toString(),
        "npu_standard_route_completed_route_block_reason" to completedRouteBlockReason,
        "npu_standard_route_completed_route_kill_switch_enabled" to completedRouteKillSwitchEnabled.toString(),
        "npu_standard_route_completed_route_disabled_by_property" to completedRouteDisabledByProperty.toString(),
        "npu_standard_route_developer_phase_override_block_reason" to developerPhaseOverrideBlockReason,
        "npu_standard_route_completed_route_rollout_state" to completedRouteRolloutState,
        "npu_standard_route_effective_phase_source" to effectivePhaseSource,
        "npu_standard_route_effective_phase" to effectivePhase,
        "npu_standard_route_user_facing_selected_backend" to userFacingBackend,
        "npu_standard_route_completed_route_family" to if (
            completedRouteSelected ||
            completedRouteDisabledByProperty
        ) {
            NPU_STANDARD_ROUTE_COMPLETED_ROUTE_FAMILY
        } else {
            "unavailable"
        },
        "npu_standard_route_internal_legacy_backend" to if (
            completedRouteSelected ||
            completedRouteDisabledByProperty
        ) {
            NPU_STANDARD_ROUTE_INTERNAL_LEGACY_BACKEND_NPU_S5
        } else {
            "unavailable"
        },
        "npu_standard_route_internal_legacy_route_family" to if (
            completedRouteSelected ||
            completedRouteDisabledByProperty
        ) {
            NPU_STANDARD_ROUTE_INTERNAL_LEGACY_ROUTE_FAMILY_NPU_S5
        } else {
            "unavailable"
        },
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
    devGateEnabled: Boolean = true,
    completedRouteKillSwitchBlocked: Boolean = false,
): Map<String, String> {
    val phaseName = when (phase) {
        NPU_STANDARD_ROUTE_PHASE_8 -> NPU_STANDARD_ROUTE_PHASE_8_NAME
        NPU_STANDARD_ROUTE_PHASE_7 -> NPU_STANDARD_ROUTE_PHASE_7_NAME
        NPU_STANDARD_ROUTE_PHASE_6 -> NPU_STANDARD_ROUTE_PHASE_6_NAME
        NPU_STANDARD_ROUTE_PHASE_5 -> NPU_STANDARD_ROUTE_PHASE_5_NAME
        NPU_STANDARD_ROUTE_PHASE_4 -> NPU_STANDARD_ROUTE_PHASE_4_NAME
        NPU_STANDARD_ROUTE_PHASE_3 -> NPU_STANDARD_ROUTE_PHASE_3_NAME
        NPU_STANDARD_ROUTE_PHASE_2 -> NPU_STANDARD_ROUTE_PHASE_2_NAME
        else -> NPU_STANDARD_ROUTE_PHASE_1_NAME
    }
    val conversationCreated = !completedRouteKillSwitchBlocked && phase in setOf(
        NPU_STANDARD_ROUTE_PHASE_2,
        NPU_STANDARD_ROUTE_PHASE_3,
        NPU_STANDARD_ROUTE_PHASE_4,
        NPU_STANDARD_ROUTE_PHASE_5,
        NPU_STANDARD_ROUTE_PHASE_6,
        NPU_STANDARD_ROUTE_PHASE_7,
        NPU_STANDARD_ROUTE_PHASE_8,
    ) && connected
    val generateResponse = !completedRouteKillSwitchBlocked && phase in setOf(
        NPU_STANDARD_ROUTE_PHASE_3,
        NPU_STANDARD_ROUTE_PHASE_4,
        NPU_STANDARD_ROUTE_PHASE_5,
        NPU_STANDARD_ROUTE_PHASE_6,
        NPU_STANDARD_ROUTE_PHASE_7,
        NPU_STANDARD_ROUTE_PHASE_8,
    ) && connected
    val generateDiagnosticOnly = phase == NPU_STANDARD_ROUTE_PHASE_3 && generateResponse
    val qualityGatePassed = if (completedRouteKillSwitchBlocked) {
        "unavailable"
    } else {
        when (outputQualityCandidateStatus) {
            NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS -> "true"
            NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL -> "false"
            else -> "unavailable"
        }
    }
    val qualityCandidatePassed = outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS
    val outputSuppressed = !completedRouteKillSwitchBlocked &&
        outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL
    val generateOrDeliveryPhase = phase in setOf(
        NPU_STANDARD_ROUTE_PHASE_3,
        NPU_STANDARD_ROUTE_PHASE_4,
        NPU_STANDARD_ROUTE_PHASE_5,
        NPU_STANDARD_ROUTE_PHASE_6,
        NPU_STANDARD_ROUTE_PHASE_7,
        NPU_STANDARD_ROUTE_PHASE_8,
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
        if (completedRouteKillSwitchBlocked) {
            add(NPU_STANDARD_ROUTE_ROLLBACK_REASON_KILL_SWITCH_DISABLED)
        } else {
            if (runDecodeReached == false) add("decode_not_reached")
            if (nativeCleanupReached.equals("false", ignoreCase = true)) add("native_cleanup_not_reached")
        }
    }
    val candidatePresent = candidateTextPresent == true
    val ttsPresent = ttsTextPresent == true
    val baseRunHealthy = connected &&
        !fallbackUsed &&
        !timeout &&
        !freshCrash &&
        runDecodeReached != false &&
        !nativeCleanupReached.equals("false", ignoreCase = true)
    val uiAppendAllowed = phase in setOf(
        NPU_STANDARD_ROUTE_PHASE_4,
        NPU_STANDARD_ROUTE_PHASE_5,
        NPU_STANDARD_ROUTE_PHASE_6,
        NPU_STANDARD_ROUTE_PHASE_7,
        NPU_STANDARD_ROUTE_PHASE_8,
    ) &&
        baseRunHealthy &&
        qualityCandidatePassed &&
        candidatePresent
    val outputDeliveryAllowed = uiAppendAllowed
    val ttsAllowed = phase in setOf(
        NPU_STANDARD_ROUTE_PHASE_5,
        NPU_STANDARD_ROUTE_PHASE_6,
        NPU_STANDARD_ROUTE_PHASE_7,
        NPU_STANDARD_ROUTE_PHASE_8,
    ) &&
        uiAppendAllowed &&
        ttsPresent
    val dbSaveAllowed = phase in setOf(
        NPU_STANDARD_ROUTE_PHASE_6,
        NPU_STANDARD_ROUTE_PHASE_7,
        NPU_STANDARD_ROUTE_PHASE_8,
    ) &&
        uiAppendAllowed
    val markdownAllowed = phase in setOf(NPU_STANDARD_ROUTE_PHASE_7, NPU_STANDARD_ROUTE_PHASE_8) &&
        dbSaveAllowed
    val streamingAllowed = phase == NPU_STANDARD_ROUTE_PHASE_8 && markdownAllowed
    val uiAppendSource = when {
        uiAppendAllowed -> "actual_display_text"
        completedRouteKillSwitchBlocked -> "blocked_kill_switch_disabled"
        phase !in setOf(
            NPU_STANDARD_ROUTE_PHASE_4,
            NPU_STANDARD_ROUTE_PHASE_5,
            NPU_STANDARD_ROUTE_PHASE_6,
            NPU_STANDARD_ROUTE_PHASE_7,
            NPU_STANDARD_ROUTE_PHASE_8,
        ) -> "not_allowed_before_phase4"
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
        completedRouteKillSwitchBlocked -> NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_KILL_SWITCH_DISABLED
        phase !in setOf(
            NPU_STANDARD_ROUTE_PHASE_4,
            NPU_STANDARD_ROUTE_PHASE_5,
            NPU_STANDARD_ROUTE_PHASE_6,
            NPU_STANDARD_ROUTE_PHASE_7,
            NPU_STANDARD_ROUTE_PHASE_8,
        ) -> "phase_not_ui_append"
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
        completedRouteKillSwitchBlocked -> "blocked_kill_switch_disabled"
        phase !in setOf(
            NPU_STANDARD_ROUTE_PHASE_5,
            NPU_STANDARD_ROUTE_PHASE_6,
            NPU_STANDARD_ROUTE_PHASE_7,
            NPU_STANDARD_ROUTE_PHASE_8,
        ) -> "not_allowed_before_phase5"
        outputSuppressed -> "blocked_quality_candidate_fail"
        !uiAppendAllowed -> "blocked_ui_append_not_allowed"
        !ttsPresent -> "tts_text_absent"
        else -> "blocked_quality_gate_unavailable"
    }
    val ttsBlockReason = when {
        ttsAllowed -> "none"
        completedRouteKillSwitchBlocked -> NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_KILL_SWITCH_DISABLED
        phase !in setOf(
            NPU_STANDARD_ROUTE_PHASE_5,
            NPU_STANDARD_ROUTE_PHASE_6,
            NPU_STANDARD_ROUTE_PHASE_7,
            NPU_STANDARD_ROUTE_PHASE_8,
        ) -> "phase_not_tts"
        outputSuppressed -> NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL
        !uiAppendAllowed -> uiAppendBlockReason
        !ttsPresent -> "tts_text_absent"
        else -> "quality_gate_unavailable"
    }
    val dbSaveBlockReason = when {
        dbSaveAllowed -> "none"
        completedRouteKillSwitchBlocked -> NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_KILL_SWITCH_DISABLED
        phase !in setOf(
            NPU_STANDARD_ROUTE_PHASE_6,
            NPU_STANDARD_ROUTE_PHASE_7,
            NPU_STANDARD_ROUTE_PHASE_8,
        ) -> "phase_not_db_save"
        outputSuppressed -> NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL
        !uiAppendAllowed -> uiAppendBlockReason
        else -> "quality_gate_unavailable"
    }
    val markdownBlockReason = when {
        markdownAllowed -> "none"
        completedRouteKillSwitchBlocked -> NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_KILL_SWITCH_DISABLED
        phase !in setOf(NPU_STANDARD_ROUTE_PHASE_7, NPU_STANDARD_ROUTE_PHASE_8) -> "phase_not_markdown"
        outputSuppressed -> NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL
        !dbSaveAllowed -> dbSaveBlockReason
        else -> "quality_gate_unavailable"
    }
    val streamingBlockReason = when {
        streamingAllowed -> "none"
        completedRouteKillSwitchBlocked -> NPU_STANDARD_ROUTE_COMPLETED_ROUTE_BLOCK_KILL_SWITCH_DISABLED
        phase != NPU_STANDARD_ROUTE_PHASE_8 -> "phase_not_streaming"
        outputSuppressed -> NPU_STANDARD_ROUTE_SUPPRESSION_REASON_QUALITY_CANDIDATE_FAIL
        !markdownAllowed -> markdownBlockReason
        else -> "quality_gate_unavailable"
    }
    val rollbackRequired = rollbackReasons.isNotEmpty()
    val rollbackReason = rollbackReasons.joinToString("+").ifBlank { NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE }

    return linkedMapOf(
        "npu_standard_route_dev_gate_enabled" to devGateEnabled.toString(),
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
        "npu_standard_route_db_save_allowed" to dbSaveAllowed.toString(),
        "npu_standard_route_db_save_block_reason" to dbSaveBlockReason,
        "npu_standard_route_markdown_allowed" to markdownAllowed.toString(),
        "npu_standard_route_markdown_block_reason" to markdownBlockReason,
        "npu_standard_route_streaming_allowed" to streamingAllowed.toString(),
        "npu_standard_route_streaming_block_reason" to streamingBlockReason,
        "npu_standard_route_native_streaming_used" to "false",
        "npu_standard_route_rollback_required" to rollbackRequired.toString(),
        "npu_standard_route_rollback_reason" to rollbackReason,
    ).also { diagnostics ->
        if (completedRouteKillSwitchBlocked) {
            diagnostics["npu_standard_route_ui_append_executed"] = "false"
            diagnostics["npu_standard_route_tts_requested"] = "false"
            diagnostics["npu_standard_route_tts_started"] = "false"
            diagnostics["npu_standard_route_db_save_executed"] = "false"
            diagnostics["npu_standard_route_markdown_executed"] = "false"
            diagnostics["npu_standard_route_streaming_executed"] = "false"
            diagnostics["npu_standard_route_output_delivery_executed"] = "false"
            diagnostics["npu_standard_route_delivery_path"] = "kill_switch_safe_block"
        }
    }
}

internal fun buildNpuStandardRoutePhase1DiagnosticLines(
    diagnostics: Map<String, String>,
): List<String> =
    diagnostics.map { (key, value) -> "$key=$value" }

internal fun buildNpuStandardRouteDeliveryExecutionDiagnostics(
    uiAppendExecuted: Boolean,
    uiAppendVisibleCandidate: Boolean,
    ttsRequested: Boolean,
    ttsStarted: Boolean,
    outputDeliveryExecuted: Boolean = uiAppendExecuted || ttsStarted,
    deliveryPath: String,
    uiAppendTarget: String = if (uiAppendExecuted) "transient_chat_message" else "none",
    uiAppendFailureReason: String = NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE,
    ttsExecutionBlockReason: String = NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE,
    dbSaveExecuted: Boolean = false,
    dbSaveTarget: String = if (dbSaveExecuted) "assistant_message" else "none",
    dbSavedTextLength: Int = 0,
    dbAssistantIdPresent: Boolean = false,
    dbSaveBlockReason: String = NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE,
    dbMessageReplacedTransient: Boolean = false,
    dbConversationIdPresent: Boolean = false,
    markdownExecuted: Boolean = false,
    markdownMode: String = if (markdownExecuted) "default" else "none",
    markdownBlockReason: String = NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE,
    streamingExecuted: Boolean = false,
    streamingMode: String = if (streamingExecuted) "pseudo_final_text" else "none",
    streamingSource: String = "none",
    streamingChunkCount: Int = 0,
    streamingFinalTextLength: Int = 0,
    streamingBlockReason: String = NPU_STANDARD_ROUTE_ROLLBACK_REASON_NONE,
    nativeStreamingUsed: Boolean = false,
    streamingTextMatchesDb: Boolean = false,
    streamingTextMatchesMarkdown: Boolean = false,
): Map<String, String> = linkedMapOf(
    "npu_standard_route_ui_append_executed" to uiAppendExecuted.toString(),
    "npu_standard_route_ui_append_visible_candidate" to uiAppendVisibleCandidate.toString(),
    "npu_standard_route_ui_append_target" to uiAppendTarget,
    "npu_standard_route_ui_append_failure_reason" to uiAppendFailureReason,
    "npu_standard_route_tts_requested" to ttsRequested.toString(),
    "npu_standard_route_tts_started" to ttsStarted.toString(),
    "npu_standard_route_output_delivery_executed" to outputDeliveryExecuted.toString(),
    "npu_standard_route_delivery_path" to deliveryPath,
    "npu_standard_route_tts_execution_block_reason" to ttsExecutionBlockReason,
    "npu_standard_route_db_save_executed" to dbSaveExecuted.toString(),
    "npu_standard_route_db_save_target" to dbSaveTarget,
    "npu_standard_route_db_saved_text_length" to dbSavedTextLength.coerceAtLeast(0).toString(),
    "npu_standard_route_db_assistant_id_present" to dbAssistantIdPresent.toString(),
    "npu_standard_route_db_save_block_reason" to dbSaveBlockReason,
    "npu_standard_route_db_message_replaced_transient" to dbMessageReplacedTransient.toString(),
    "npu_standard_route_db_conversation_id_present" to dbConversationIdPresent.toString(),
    "npu_standard_route_markdown_executed" to markdownExecuted.toString(),
    "npu_standard_route_markdown_mode" to markdownMode,
    "npu_standard_route_markdown_block_reason" to markdownBlockReason,
    "npu_standard_route_streaming_executed" to streamingExecuted.toString(),
    "npu_standard_route_streaming_mode" to streamingMode,
    "npu_standard_route_streaming_source" to streamingSource,
    "npu_standard_route_streaming_chunk_count" to streamingChunkCount.coerceAtLeast(0).toString(),
    "npu_standard_route_streaming_final_text_length" to streamingFinalTextLength.coerceAtLeast(0).toString(),
    "npu_standard_route_streaming_block_reason" to streamingBlockReason,
    "npu_standard_route_native_streaming_used" to nativeStreamingUsed.toString(),
    "npu_standard_route_streaming_text_matches_db" to streamingTextMatchesDb.toString(),
    "npu_standard_route_streaming_text_matches_markdown" to streamingTextMatchesMarkdown.toString(),
)

private fun isNpuStandardRoutePhase1Backend(preferredBackend: String): Boolean {
    val normalized = preferredBackend.trim().uppercase()
    return normalized == "NPU" || normalized == "NPU_S1"
}

private fun resolveNpuStandardRouteDiagnosticPhase(
    propertyReader: (String) -> String?,
): String =
    resolveExplicitNpuStandardRouteDiagnosticPhase(propertyReader) ?: NPU_STANDARD_ROUTE_PHASE_1

private fun resolveExplicitNpuStandardRouteDiagnosticPhase(
    propertyReader: (String) -> String?,
): String? =
    when (propertyReader(NPU_STANDARD_ROUTE_PHASE_PROPERTY)?.trim()) {
        null, "", "0" -> null
        NPU_STANDARD_ROUTE_PHASE_8 -> NPU_STANDARD_ROUTE_PHASE_8
        NPU_STANDARD_ROUTE_PHASE_7 -> NPU_STANDARD_ROUTE_PHASE_7
        NPU_STANDARD_ROUTE_PHASE_6 -> NPU_STANDARD_ROUTE_PHASE_6
        NPU_STANDARD_ROUTE_PHASE_5 -> NPU_STANDARD_ROUTE_PHASE_5
        NPU_STANDARD_ROUTE_PHASE_4 -> NPU_STANDARD_ROUTE_PHASE_4
        NPU_STANDARD_ROUTE_PHASE_3 -> NPU_STANDARD_ROUTE_PHASE_3
        NPU_STANDARD_ROUTE_PHASE_2 -> NPU_STANDARD_ROUTE_PHASE_2
        NPU_STANDARD_ROUTE_PHASE_1 -> NPU_STANDARD_ROUTE_PHASE_1
        else -> null
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
