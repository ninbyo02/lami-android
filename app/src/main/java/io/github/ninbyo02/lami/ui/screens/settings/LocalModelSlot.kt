package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.navigation.Routes
import kotlinx.coroutines.flow.Flow

internal sealed class LocalModelSlot(
    val title: String,
    val description: String,
) {
    abstract fun displayNameFlow(settingsPreferences: SettingsPreferences): Flow<String?>
    abstract fun filePathFlow(settingsPreferences: SettingsPreferences): Flow<String?>
    abstract suspend fun saveModelInfo(
        settingsPreferences: SettingsPreferences,
        displayName: String,
        filePath: String,
    )

    abstract suspend fun clearModelInfo(settingsPreferences: SettingsPreferences)

    data object NpuPreview : LocalModelSlot(
        title = "Snapdragon NPU向けのLiteRTモデル",
        description = "高速・省電力なローカル推論に使用します",
    ) {
        override fun displayNameFlow(settingsPreferences: SettingsPreferences): Flow<String?> {
            return settingsPreferences.localBaseModelDisplayNameFlow
        }

        override fun filePathFlow(settingsPreferences: SettingsPreferences): Flow<String?> {
            return settingsPreferences.localBaseModelFilePathFlow
        }

        override suspend fun saveModelInfo(
            settingsPreferences: SettingsPreferences,
            displayName: String,
            filePath: String,
        ) {
            settingsPreferences.saveLocalBaseModelInfo(displayName = displayName, filePath = filePath)
        }

        override suspend fun clearModelInfo(settingsPreferences: SettingsPreferences) {
            settingsPreferences.clearLocalBaseModelInfo()
        }
    }

    data object GenericFallback : LocalModelSlot(
        title = "GPU／CPU向けのLiteRTモデル",
        description = "NPUを使用できない場合や長い処理に使用します",
    ) {
        override fun displayNameFlow(settingsPreferences: SettingsPreferences): Flow<String?> {
            return settingsPreferences.localGenericModelDisplayNameFlow
        }

        override fun filePathFlow(settingsPreferences: SettingsPreferences): Flow<String?> {
            return settingsPreferences.localGenericModelFilePathFlow
        }

        override suspend fun saveModelInfo(
            settingsPreferences: SettingsPreferences,
            displayName: String,
            filePath: String,
        ) {
            settingsPreferences.saveLocalGenericModelInfo(displayName = displayName, filePath = filePath)
        }

        override suspend fun clearModelInfo(settingsPreferences: SettingsPreferences) {
            settingsPreferences.clearLocalGenericModelInfo()
        }
    }
}

internal fun localModelSlotActionLabel(hasModel: Boolean): String = if (hasModel) "変更" else "選択"
internal fun localModelSlotStatusLabel(displayName: String?): String =
    displayName?.takeIf { it.isNotBlank() } ?: "LiteRTモデルが選択されていません"
internal fun localModelCompatibilityWarning(slot: LocalModelSlot, displayName: String?): String? {
    val normalized = displayName?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    val looksNpuSpecific = listOf("qualcomm", "qairt", "qnn", "sm8750", "snapdragon", "npu").any(normalized::contains)
    val looksGeneric = listOf("generic", "gpu", "cpu").any(normalized::contains)
    return when {
        slot == LocalModelSlot.GenericFallback && looksNpuSpecific ->
            "このファイル名はQualcomm／NPU向けモデルの可能性があります。汎用モデルとして選択を続行します。"
        slot == LocalModelSlot.NpuPreview && looksGeneric && !looksNpuSpecific ->
            "このファイル名は汎用GPU／CPU向けモデルの可能性があります。NPUモデルとして選択を続行します。"
        else -> null
    }
}

internal enum class SettingsLocalModelFocus(val routeValue: String) {
    SECTION("section"), NPU("npu"), GENERIC("generic"),
}

internal fun settingsRouteForLocalModelFocus(focus: SettingsLocalModelFocus?): String =
    focus?.let { "${Routes.SETTINGS}?localModelFocus=${it.routeValue}" } ?: Routes.SETTINGS

internal fun decodeSettingsLocalModelFocus(route: String?): SettingsLocalModelFocus? {
    val value = route?.substringAfter("localModelFocus=", "")?.substringBefore('&')?.takeIf { it.isNotBlank() }
    return SettingsLocalModelFocus.entries.firstOrNull { it.routeValue == value }
}

internal fun resolveLocalModelHighlightSlots(
    focus: SettingsLocalModelFocus?,
    isNpuConfigured: Boolean,
    isGenericConfigured: Boolean,
): Set<LocalModelSlot> {
    if (focus == null) return emptySet()
    return buildSet {
        if (!isNpuConfigured) add(LocalModelSlot.NpuPreview)
        if (!isGenericConfigured) add(LocalModelSlot.GenericFallback)
    }
}

internal object LocalModelHighlightVisualContract {
    const val changesContainerColor = false
    const val restingBorderWidthDp = 1
    const val highlightBorderWidthDp = 2
    const val startsHighlighted = false
    const val pulseCount = 3
    const val offPhaseMillis = 400
    const val onPhaseMillis = 400
    const val totalDurationMillis = pulseCount * (offPhaseMillis + onPhaseMillis)
    const val endsHighlighted = false
}

internal object LocalModelCardLayoutContract {
    const val minHeightDp = 120
    const val alwaysReservesClearActionSlot = true
}

internal object SettingsLocalModelScrollTarget {
    const val itemKey = "local-model-section"
    const val itemIndex = 3
    // LazyList の負の offset は対象 item を viewport 上端より下へ置く。
    // 48dp top bar と合わせ、見出しを画面上端から約 100dp にする。
    const val scrollOffsetDp = -52
}

internal class OneShotSettingsLocalModelFocus(private val focus: SettingsLocalModelFocus?) {
    private var consumed = false
    fun consume(): SettingsLocalModelFocus? {
        if (consumed) return null
        consumed = true
        return focus
    }
}
