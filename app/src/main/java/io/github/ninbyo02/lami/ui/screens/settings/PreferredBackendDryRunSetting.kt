package io.github.ninbyo02.lami.ui.screens.settings

enum class PreferredBackendDryRunSetting {
    DEFAULT,
    CPU,
    GPU;

    companion object {
        fun fromStorage(raw: String?): PreferredBackendDryRunSetting =
            when (raw) {
                CPU.name -> CPU
                GPU.name -> GPU
                else -> DEFAULT
            }
    }
}
