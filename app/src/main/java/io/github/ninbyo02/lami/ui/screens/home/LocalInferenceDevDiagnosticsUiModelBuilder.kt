package io.github.ninbyo02.lami.ui.screens.home

internal data class LocalInferenceDevDiagnosticsUiModel(
    val heldEngineReuseSummary: String,
    val heldEngineStateSummary: String,
    val closeStatusSummary: String,
    val failureSummary: String,
)

internal fun buildLocalInferenceDevDiagnosticsUiModel(
    devHeldStateText: String?,
    devCloseLifecycleText: String?,
    devDebugText: String?,
): LocalInferenceDevDiagnosticsUiModel {
    return LocalInferenceDevDiagnosticsUiModel(
        heldEngineReuseSummary = resolveDevSummaryEngineReuse(devHeldStateText),
        heldEngineStateSummary = resolveDevSummaryHeldState(devHeldStateText),
        closeStatusSummary = resolveDevSummaryCloseStatus(devCloseLifecycleText),
        failureSummary = resolveDevSummaryFailure(devDebugText),
    )
}

internal fun withProbeStateLabel(value: String?, state: String): String =
    "${value ?: "—"}（$state）"

private fun resolveDevSummaryEngineReuse(devHeldStateText: String?): String {
    val heldExists = devHeldStateText.devLineValue("heldExists")?.toBooleanStrictOrNull()
    val useCount = devHeldStateText.devLineValue("useCount")?.toIntOrNull()
    val heldHash = devHeldStateText.devLineValue("heldHash")
    return when {
        heldExists == true && useCount != null && useCount >= 1 -> "再利用あり"
        heldExists == true && !heldHash.isNullOrBlank() && heldHash != "null" -> "再利用あり"
        heldExists == false -> "再利用なし"
        heldExists == true && useCount == 0 -> "再利用なし"
        heldExists == true -> "再利用あり"
        else -> "不明"
    }
}

private fun resolveDevSummaryHeldState(devHeldStateText: String?): String {
    val heldExists = devHeldStateText.devLineValue("heldExists")?.toBooleanStrictOrNull()
    return when (heldExists) {
        true -> "存在"
        false -> "未保持"
        null -> "不明"
    }
}

private fun resolveDevSummaryCloseStatus(devCloseLifecycleText: String?): String {
    if (devCloseLifecycleText.isNullOrBlank()) return "—"
    val conversation = devCloseLifecycleText.devLineValue("conversation")?.substringAfter("status=")?.substringBefore(" ")
    val engine = devCloseLifecycleText.devLineValue("engine")?.substringAfter("status=")?.substringBefore(" ")
    return listOfNotNull(
        conversation?.let { "conversation:$it" },
        engine?.let { "engine:$it" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "—"
}

private fun resolveDevSummaryFailure(devDebugText: String?): String {
    if (devDebugText.isNullOrBlank()) return "—"
    val stage = devDebugText.devLineValue("stage")
    val errorClass = devDebugText.devLineValue("class")
    val message = devDebugText.devLineValue("message")
    val primary = listOfNotNull(stage, errorClass)
        .filter { it.isNotBlank() }
    if (primary.isNotEmpty()) return primary.take(2).joinToString(" / ")
    return message
        ?.takeIf { it.isNotBlank() }
        ?.take(40)
        ?.let { if (it.length < (message.length)) "$it…" else it }
        ?: "—"
}

private fun String?.devLineValue(key: String): String? {
    if (this.isNullOrBlank()) return null
    return lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}
