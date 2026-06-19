package io.github.ninbyo02.lami.ui.screens.home

import java.util.Locale

internal const val LITERT_LM_MODEL_KIND_GENERIC = "generic-litertlm"
internal const val LITERT_LM_BASELINE_CPU_STABLE = "cpu_stable_baseline"
internal const val LITERT_LM_BASELINE_GPU_EXPERIMENTAL = "gpu_experimental"
internal const val LITERT_LM_BASELINE_NPU_EXPERIMENTAL = "npu_experimental"
internal const val LITERT_LM_BASELINE_UNKNOWN = "unknown"

internal fun classifyLiteRtLmModelKindForBaseline(selectedModel: String): String {
    val lower = selectedModel.substringAfterLast('/').lowercase(Locale.ROOT)
    return when {
        "qualcomm" in lower && "sm8750" in lower -> "qualcomm-sm8750-litertlm"
        listOf("qualcomm", "qcs", "qnn", "htp").any { it in lower } -> "qualcomm-litertlm"
        "litertlm" in lower -> LITERT_LM_MODEL_KIND_GENERIC
        else -> "unknown"
    }
}

internal fun resolveLiteRtLmBaselineRole(
    modelKind: String,
    preferredBackend: String,
): String {
    val backend = preferredBackend.trim().uppercase(Locale.ROOT)
    return when {
        backend.contains("NPU") -> LITERT_LM_BASELINE_NPU_EXPERIMENTAL
        modelKind == LITERT_LM_MODEL_KIND_GENERIC && backend == "CPU" -> LITERT_LM_BASELINE_CPU_STABLE
        modelKind == LITERT_LM_MODEL_KIND_GENERIC && backend == "GPU" -> LITERT_LM_BASELINE_GPU_EXPERIMENTAL
        else -> LITERT_LM_BASELINE_UNKNOWN
    }
}

internal fun isGenericLiteRtLmCpuStableBaseline(
    modelKind: String,
    baselineRole: String,
): Boolean =
    modelKind == LITERT_LM_MODEL_KIND_GENERIC &&
        baselineRole == LITERT_LM_BASELINE_CPU_STABLE

internal fun formatLiteRtLmBaselineRoleForUi(baselineRole: String): String =
    when (baselineRole) {
        LITERT_LM_BASELINE_CPU_STABLE -> "CPU stable baseline"
        LITERT_LM_BASELINE_GPU_EXPERIMENTAL -> "GPU experimental"
        LITERT_LM_BASELINE_NPU_EXPERIMENTAL -> "NPU experimental"
        else -> "unknown"
    }
