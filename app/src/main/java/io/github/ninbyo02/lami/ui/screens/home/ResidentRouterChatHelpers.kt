package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.InferenceBackendSelection
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting

internal fun PreferredBackendDryRunSetting.toResidentRouterInferenceBackendSelection(): InferenceBackendSelection =
    when (this) {
        PreferredBackendDryRunSetting.NPU,
        PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU -> InferenceBackendSelection.NPU
        PreferredBackendDryRunSetting.GPU -> InferenceBackendSelection.GPU
        PreferredBackendDryRunSetting.CPU -> InferenceBackendSelection.CPU
        PreferredBackendDryRunSetting.DEFAULT -> InferenceBackendSelection.AUTOMATIC
    }

internal fun estimateLocalPromptTokensForResidentRouter(prompt: String): Int =
    (prompt.codePointCount(0, prompt.length) / 2).coerceAtLeast(1)

internal fun isResidentRouterRealRoutingEnabledForDebug(
    propertyReader: (String) -> String? = ::readResidentRouterDebugProperty,
): Boolean {
    if (!BuildConfig.DEBUG) return false
    val value = propertyReader("debug.lami.resident_router_real_route_enabled")
        ?: propertyReader("lami.resident_router_real_route_enabled")
        ?: return false
    return value.equals("true", ignoreCase = true) || value == "1" || value.equals("yes", ignoreCase = true)
}

internal fun readResidentRouterDebugProperty(key: String): String? {
    val jvmProperty = runCatching {
        System.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()
    if (jvmProperty != null) return jvmProperty
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        (method.invoke(null, key, "") as? String)?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
