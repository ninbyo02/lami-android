package io.github.ninbyo02.lami.ui.screens.settings

enum class PreferredBackendDryRunSetting {
    DEFAULT,
    CPU,
    GPU,
    NPU,
    QUALCOMM_QNN_NPU;

    companion object {
        val selectableEntries: List<PreferredBackendDryRunSetting> = listOf(
            DEFAULT,
            CPU,
            GPU,
            QUALCOMM_QNN_NPU,
        )

        fun fromStorage(raw: String?): PreferredBackendDryRunSetting =
            when (raw) {
                CPU.name -> CPU
                GPU.name -> GPU
                NPU.name -> QUALCOMM_QNN_NPU
                QUALCOMM_QNN_NPU.name -> QUALCOMM_QNN_NPU
                else -> DEFAULT
            }
    }
}
