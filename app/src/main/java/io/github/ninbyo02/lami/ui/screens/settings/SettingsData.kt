package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode

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
    val developerAccessEnabled: Boolean = false,
    val devEnableNpuChatScreenRoute: Boolean = false,
    val devEnableQairt244Sm8750NpuRoute: Boolean = false,
    val hiddenQairt244PromptTemplateMode: HiddenQairt244PromptTemplateMode = HiddenQairt244PromptTemplateMode.RAW,
)
