package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteMode

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
        displayLabel = "GPU",
        preferredBackend = PreferredBackendDryRunSetting.GPU,
        npuStandardRouteMode = NpuStandardRouteMode.OFF,
    ),
    NPU_S1(
        displayLabel = "NPU S1 応答表示",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
    ),
    NPU_S2(
        displayLabel = "NPU S2 DB保存",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.S2_DB,
    ),
    NPU_S3(
        displayLabel = "NPU S3 Markdown",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.S3_MARKDOWN,
    ),
    NPU_S4(
        displayLabel = "NPU S4 Streaming",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.S4A_PSEUDO_STREAMING,
    ),
    NPU_S5(
        displayLabel = "NPU S5 TTS",
        preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
        npuStandardRouteMode = NpuStandardRouteMode.FULL,
    );

    companion object {
        val selectableEntries: List<InferenceBackendSelection> = entries
        val localEntries: List<InferenceBackendSelection> = listOf(AUTOMATIC, CPU, GPU)
        val npuEntries: List<InferenceBackendSelection> = listOf(NPU_S1, NPU_S2, NPU_S3, NPU_S4, NPU_S5)

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
    }
}

internal fun effectiveNpuStandardRouteModeForBackendSelection(
    preferredBackend: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode,
): NpuStandardRouteMode =
    InferenceBackendSelection.fromSettings(
        preferredBackend = preferredBackend,
        npuStandardRouteMode = npuStandardRouteMode,
    ).npuStandardRouteMode
