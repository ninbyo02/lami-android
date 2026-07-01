package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteMode

enum class NpuStandardRouteSelectionSource {
    LOCAL_BACKEND,
    USER_FACING_NPU_EXPERIMENTAL,
    DEVELOPER_PHASE_OVERRIDE,
    LEGACY_UNSPECIFIED;

    companion object {
        fun fromStorage(raw: String?): NpuStandardRouteSelectionSource =
            entries.firstOrNull { it.name == raw } ?: LEGACY_UNSPECIFIED

        fun forSelection(selection: InferenceBackendSelection): NpuStandardRouteSelectionSource =
            when {
                selection == InferenceBackendSelection.NPU -> USER_FACING_NPU_EXPERIMENTAL
                isDeveloperNpuPhaseSelection(selection) -> DEVELOPER_PHASE_OVERRIDE
                else -> LOCAL_BACKEND
            }
    }
}

enum class InferenceBackendSelection(
    val displayLabel: String,
    val preferredBackend: PreferredBackendDryRunSetting,
    val npuStandardRouteMode: NpuStandardRouteMode,
) {
    AUTOMATIC(
        displayLabel = "Automatic（推奨）",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.OFF,
    ),
    CPU(
        displayLabel = "CPU",
        preferredBackend = PreferredBackendDryRunSetting.CPU,
        npuStandardRouteMode = NpuStandardRouteMode.OFF,
    ),
    GPU(
        displayLabel = "GPU（Experimental / 非推奨）",
        preferredBackend = PreferredBackendDryRunSetting.GPU,
        npuStandardRouteMode = NpuStandardRouteMode.OFF,
    ),
    NPU(
        displayLabel = "NPU プレビュー",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.FULL,
    ),
    NPU_S1(
        displayLabel = "DEV: NPU S1 response only",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
    ),
    NPU_S2(
        displayLabel = "DEV: NPU S2 DB save",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.S2_DB,
    ),
    NPU_S3(
        displayLabel = "DEV: NPU S3 Markdown",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.S3_MARKDOWN,
    ),
    NPU_S4(
        displayLabel = "DEV: NPU S4 Streaming",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.S4A_PSEUDO_STREAMING,
    ),
    NPU_S5(
        displayLabel = "DEV: NPU S5 TTS",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.FULL,
    );

    companion object {
        val selectableEntries: List<InferenceBackendSelection> = entries
        val userFacingEntries: List<InferenceBackendSelection> = listOf(AUTOMATIC, CPU, GPU, NPU)
        val localEntries: List<InferenceBackendSelection> = listOf(AUTOMATIC, CPU, GPU)
        val developerNpuPhaseEntries: List<InferenceBackendSelection> =
            listOf(NPU_S1, NPU_S2, NPU_S3, NPU_S4, NPU_S5)
        val npuEntries: List<InferenceBackendSelection> = developerNpuPhaseEntries

        fun fromSettings(
            preferredBackend: PreferredBackendDryRunSetting,
            npuStandardRouteMode: NpuStandardRouteMode,
        ): InferenceBackendSelection =
            when (preferredBackend) {
                PreferredBackendDryRunSetting.CPU -> CPU
                PreferredBackendDryRunSetting.GPU -> GPU
                PreferredBackendDryRunSetting.DEFAULT,
                PreferredBackendDryRunSetting.NPU,
                PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU -> when (npuStandardRouteMode) {
                    NpuStandardRouteMode.OFF -> AUTOMATIC
                    NpuStandardRouteMode.S1_ONLY -> NPU_S1
                    NpuStandardRouteMode.S2_DB -> NPU_S2
                    NpuStandardRouteMode.S3_MARKDOWN -> NPU_S3
                    NpuStandardRouteMode.S4A_PSEUDO_STREAMING -> NPU_S4
                    NpuStandardRouteMode.FULL -> NPU_S5
                }
            }

        fun userFacingFromSettings(
            preferredBackend: PreferredBackendDryRunSetting,
            npuStandardRouteMode: NpuStandardRouteMode,
        ): InferenceBackendSelection =
            when (preferredBackend) {
                PreferredBackendDryRunSetting.CPU -> CPU
                PreferredBackendDryRunSetting.GPU -> GPU
                PreferredBackendDryRunSetting.DEFAULT,
                PreferredBackendDryRunSetting.NPU,
                PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU ->
                    if (npuStandardRouteMode == NpuStandardRouteMode.OFF) AUTOMATIC else NPU
            }
    }
}

internal fun isDeveloperNpuPhaseSelection(selection: InferenceBackendSelection): Boolean =
    selection in InferenceBackendSelection.developerNpuPhaseEntries

internal fun effectiveNpuStandardRouteModeForBackendSelection(
    preferredBackend: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode,
): NpuStandardRouteMode =
    InferenceBackendSelection.fromSettings(
        preferredBackend = preferredBackend,
        npuStandardRouteMode = npuStandardRouteMode,
    ).npuStandardRouteMode
