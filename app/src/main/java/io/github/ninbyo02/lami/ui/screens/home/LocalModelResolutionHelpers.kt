package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.io.File

internal const val LOCAL_LITERT_BACKEND_KEY = "text=GPU/vision=GPU/audio=CPU"

internal fun buildLocalLiteRtBackendKey(
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting,
): String {
    val textBackend = when (preferredBackendDryRunSetting) {
        PreferredBackendDryRunSetting.CPU,
        PreferredBackendDryRunSetting.DEFAULT -> "CPU"
        PreferredBackendDryRunSetting.GPU -> "GPU"
        PreferredBackendDryRunSetting.NPU,
        PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU -> "GPU"
    }
    return "$LOCAL_LITERT_BACKEND_KEY/requested=${preferredBackendDryRunSetting.name}/text=$textBackend"
}

internal fun buildLiteRtCacheDirPath(context: Context): String = context.cacheDir.absolutePath

internal fun resolveLocalModelDisplayName(
    localModelDisplayName: String?,
    modelPath: String,
): String {
    val normalizedDisplayName = localModelDisplayName?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedDisplayName != null) return normalizedDisplayName
    return File(modelPath).name.removeSuffix(".litertlm")
}
