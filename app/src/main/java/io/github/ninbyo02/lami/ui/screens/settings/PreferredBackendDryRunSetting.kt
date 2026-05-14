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
        )

        fun fromStorage(raw: String?): PreferredBackendDryRunSetting =
            when (raw) {
                CPU.name -> CPU
                GPU.name -> GPU
                NPU.name -> GPU
                QUALCOMM_QNN_NPU.name -> GPU
                else -> DEFAULT
            }
    }
}
