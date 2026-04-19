package io.github.ninbyo02.lami.ui.screens.settings

enum class InferenceStatsDisplayMode {
    SIMPLE,
    DETAILED,
    DEVELOPER;

    companion object {
        fun fromStorage(raw: String?): InferenceStatsDisplayMode {
            return when (raw) {
                DETAILED.name -> DETAILED
                DEVELOPER.name -> DEVELOPER
                else -> SIMPLE
            }
        }
    }
}
