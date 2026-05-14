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
    trace: LocalInferenceTrace? = null,
): LocalInferenceDevDiagnosticsUiModel {
    return LocalInferenceDevDiagnosticsUiModel(
        heldEngineReuseSummary = resolveDevSummaryEngineReuse(devHeldStateText, trace),
        heldEngineStateSummary = resolveDevSummaryHeldState(devHeldStateText, trace),
        closeStatusSummary = resolveDevSummaryCloseStatus(devCloseLifecycleText),
        failureSummary = resolveDevSummaryFailure(devDebugText),
    )
}

internal fun withProbeStateLabel(value: String?, state: String): String =
    "${value ?: "—"}（$state）"

private fun resolveDevSummaryEngineReuse(devHeldStateText: String?, trace: LocalInferenceTrace?): String {
    val heldExists = devHeldStateText.devLineValue("heldExists")?.toBooleanStrictOrNull()
    val useCount = devHeldStateText.devLineValue("useCount")?.toIntOrNull()
    val heldHash = devHeldStateText.devLineValue("heldHash")
    val acquireAction = trace?.holderLastAcquireAction
    return when {
        acquireAction == "reused" -> "再利用あり"
        acquireAction == "created" -> "新規作成"
        acquireAction?.startsWith("failed:") == true -> "取得失敗"
        heldExists == true && useCount != null && useCount >= 1 -> "再利用あり"
        heldExists == true && !heldHash.isNullOrBlank() && heldHash != "null" -> "再利用あり"
        trace?.heldEngineCreatedDuringRun == true -> "新規作成"
        trace?.heldEngineWasPresentAtRunStart == true -> "再利用あり"
        heldExists == false -> "再利用なし"
        heldExists == true && useCount == 0 -> "再利用なし"
        heldExists == true -> "再利用あり"
        else -> "不明"
    }
}

private fun resolveDevSummaryHeldState(devHeldStateText: String?, trace: LocalInferenceTrace?): String {
    val heldExists = devHeldStateText.devLineValue("heldExists")?.toBooleanStrictOrNull()
    val foregroundSuffix = when (trace?.holderAppInForeground) {
        true -> " / foreground"
        false -> " / background"
        null -> ""
    }
    return when (heldExists) {
        true -> "存在$foregroundSuffix"
        false -> "未保持$foregroundSuffix"
        null -> when {
            trace?.heldEngineHash != null -> "存在$foregroundSuffix"
            trace?.heldEngineWasPresentAtRunStart == true -> "存在$foregroundSuffix"
            else -> "不明$foregroundSuffix"
        }
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
