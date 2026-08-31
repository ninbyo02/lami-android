package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteMode
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import io.github.ninbyo02.lami.viewmodels.RemoteProvider


enum class ScreenOrientationMode(
    val storageValue: String,
    val displayName: String,
    val description: String,
) {
    PORTRAIT(
        storageValue = "portrait",
        displayName = "縦画面",
        description = "会話・発話中に画面回転で中断しにくくします。",
    ),
    LANDSCAPE(
        storageValue = "landscape",
        displayName = "横画面",
        description = "横向きで固定します。タブレットや横置き利用向けです。",
    ),
    AUTO(
        storageValue = "auto",
        displayName = "AUTO",
        description = "端末の自動回転設定に従います。発話中に回転すると中断する場合があります。",
    );

    companion object {
        fun fromStorage(raw: String?): ScreenOrientationMode =
            entries.firstOrNull { it.storageValue == raw || it.name == raw } ?: PORTRAIT
    }
}

enum class LemonadeAutoUnloadMode(
    val storageValue: String,
    val displayName: String,
    val description: String,
    val delayMs: Long?,
) {
    OFF(
        storageValue = "off",
        displayName = "OFF",
        description = "自動では解放しません。",
        delayMs = null,
    ),
    AFTER_RESPONSE(
        storageValue = "after_response",
        displayName = "応答完了後すぐ",
        description = "連続会話より省電力を優先します。",
        delayMs = 0L,
    ),
    AFTER_5_MIN(
        storageValue = "after_5_min",
        displayName = "5分後",
        description = "短い連続会話と省電力のバランス重視。",
        delayMs = 5 * 60 * 1000L,
    ),
    AFTER_15_MIN(
        storageValue = "after_15_min",
        displayName = "15分後",
        description = "Ollamaのkeep_alive風に、未使用時だけ解放します。",
        delayMs = 15 * 60 * 1000L,
    ),
    AFTER_30_MIN(
        storageValue = "after_30_min",
        displayName = "30分後",
        description = "快適性を優先しつつ、放置時は解放します。",
        delayMs = 30 * 60 * 1000L,
    );

    companion object {
        fun fromStorage(raw: String?): LemonadeAutoUnloadMode =
            entries.firstOrNull { it.storageValue == raw || it.name == raw } ?: OFF
    }
}


data class PendingLemonadeAutoUnload(
    val baseUrl: String,
    val targetModel: String,
    val mode: LemonadeAutoUnloadMode,
    val deadlineEpochMs: Long,
) {
    fun isValid(): Boolean =
        baseUrl.isNotBlank() && targetModel.isNotBlank() && mode.delayMs != null && deadlineEpochMs > 0L
}

enum class HiddenQairt244PromptTemplateMode(
    val storageValue: String,
    val displayName: String,
    val description: String,
) {
    RAW(
        storageValue = "raw",
        displayName = "raw",
        description = "入力をそのままモデルへ渡します。",
    ),
    SIMPLE_JA_CHAT(
        storageValue = "simple_ja_chat",
        displayName = "simple_ja_chat",
        description = "日本語アシスタント形式の簡易テンプレートを付与します。",
    ),
    GEMMA_IT_LIKE(
        storageValue = "gemma_it_like",
        displayName = "gemma_it_like",
        description = "Gemma instruction tuning 風の turn marker を付与します。",
    );

    companion object {
        fun fromStorage(raw: String?): HiddenQairt244PromptTemplateMode =
            entries.firstOrNull { it.storageValue == raw } ?: RAW
    }
}

data class SettingsData(
    val url: String = "",
    val name: String = "",
    val logo: Int = 0,
    val useDynamicColor: Boolean = false,
    val characterAnimationEnabled: Boolean = true,
    val inferenceStatsDisplayMode: InferenceStatsDisplayMode = InferenceStatsDisplayMode.SIMPLE,
    val preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    val markdownStreamingMode: MarkdownStreamingMode = MarkdownStreamingMode.DEFAULT,
    val remoteProvider: RemoteProvider = RemoteProvider.OLLAMA,
    val lemonadeAutoUnloadMode: LemonadeAutoUnloadMode = LemonadeAutoUnloadMode.OFF,
    val screenOrientationMode: ScreenOrientationMode = ScreenOrientationMode.PORTRAIT,
    val developerAccessEnabled: Boolean = false,
    val devEnableNpuChatScreenRoute: Boolean = false,
    val devEnableQairt244Sm8750NpuRoute: Boolean = false,
    val npuStandardRouteMode: NpuStandardRouteMode = NpuStandardRouteMode.OFF,
    val npuStandardRouteSelectionSource: NpuStandardRouteSelectionSource =
        NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
    val npuStandardRouteMaxOutputTokens: Int = 128,
    val hiddenQairt244PromptTemplateMode: HiddenQairt244PromptTemplateMode = HiddenQairt244PromptTemplateMode.RAW,
) {
    val inferenceBackendSelection: InferenceBackendSelection
        get() = InferenceBackendSelection.userFacingFromSettings(
            preferredBackend = preferredBackendDryRunSetting,
            npuStandardRouteMode = npuStandardRouteMode,
        )
}
