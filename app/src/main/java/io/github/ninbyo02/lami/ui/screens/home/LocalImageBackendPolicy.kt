package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting

internal enum class LocalImageLanguageBackend { CPU, GPU, NPU }
internal enum class LocalImageModelKind { GENERIC, QUALCOMM_SM8750 }

internal data class LocalImageBackendPlan(
    val languageBackend: LocalImageLanguageBackend,
    val visionBackend: LocalVisionBackend,
) {
    val preferredBackend: PreferredBackendDryRunSetting
        get() = when (languageBackend) {
            LocalImageLanguageBackend.CPU -> PreferredBackendDryRunSetting.CPU
            LocalImageLanguageBackend.GPU -> PreferredBackendDryRunSetting.GPU
            LocalImageLanguageBackend.NPU -> PreferredBackendDryRunSetting.NPU
        }

    fun diagnostics(imageCount: Int): String =
        "image_input_count=$imageCount\nlanguage_backend=$languageBackend\nvision_backend=$visionBackend\nvision_adapter_backend=CPU"
}

internal fun localImageBackendPlan(
    modelKind: LocalImageModelKind,
    preferredBackend: PreferredBackendDryRunSetting,
): LocalImageBackendPlan? = when (modelKind) {
    LocalImageModelKind.GENERIC -> when (preferredBackend) {
        PreferredBackendDryRunSetting.CPU -> LocalImageBackendPlan(LocalImageLanguageBackend.CPU, LocalVisionBackend.CPU)
        PreferredBackendDryRunSetting.GPU,
        PreferredBackendDryRunSetting.DEFAULT -> LocalImageBackendPlan(LocalImageLanguageBackend.GPU, LocalVisionBackend.GPU)
        else -> null
    }
    LocalImageModelKind.QUALCOMM_SM8750 -> when (preferredBackend) {
        PreferredBackendDryRunSetting.NPU,
        PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU,
        PreferredBackendDryRunSetting.DEFAULT -> LocalImageBackendPlan(LocalImageLanguageBackend.NPU, LocalVisionBackend.CPU)
        else -> null
    }
}

internal fun localImageCapacity(imageFilePaths: List<String>, history: List<LocalConversationTurn>): Int =
    imageFilePaths.size + history.filter { it.role == LocalConversationRole.USER }.sumOf { it.imageFilePaths.size }
