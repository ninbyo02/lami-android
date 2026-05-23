package io.github.ninbyo02.lami.npu

import android.content.Context
import io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticPromptValidator
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import kotlinx.coroutines.runBlocking

object DevOnlyNpuChatScreenBlockedBranch {
    @JvmStatic
    fun run(prompt: String): String {
        val validation = NpuDiagnosticPromptValidator.validate(prompt)
        val normalizedPrompt = validation.normalizedPrompt.ifBlank { prompt }
        val result = runBlocking {
            DevOnlyNpuRoutePlanner(
                adapter = BlockedDevOnlyNpuRouteAdapter(),
            ).runIfAllowed(
                gateInput = DevOnlyNpuRouteGateInput(
                    customBuildExperiment = true,
                    allowEditablePromptPreview = true,
                    allowGuardedNpuRun = true,
                    allowEditablePromptExecution = true,
                    devCheckboxChecked = true,
                    validatorValid = validation.isValid,
                    nativeEditablePromptSupported = true,
                    running = false,
                    maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                ),
                prompt = normalizedPrompt,
                maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                timeoutMs = DevOnlyNpuRouteAdapter.DEFAULT_TIMEOUT_MS,
            )
        }
        val displayModel = DevOnlyNpuRouteDisplayModelMapper.from(result)
        val transientState = DevOnlyNpuTransientPresenter.present(displayModel)
        return listOf(
            "DEV NPU blocked",
            "status=${transientState.status}",
            "reason=${transientState.reasonCode}",
            "db=${transientState.shouldPersistToDb}",
            "tts=${transientState.shouldSpeakTts}",
            "markdown=${transientState.shouldRenderMarkdown}",
            "stream=${transientState.shouldStream}",
        ).joinToString(" ")
    }

    @JvmStatic
    fun run(context: Context, prompt: String): String {
        val appContext = context.applicationContext
        val validation = NpuDiagnosticPromptValidator.validate(prompt)
        val normalizedPrompt = validation.normalizedPrompt.ifBlank { prompt }
        val result = runBlocking {
            try {
                DevOnlyNpuRoutePlanner(
                    adapter = Qairt244DevOnlyNpuRouteAdapter(appContext),
                ).runIfAllowed(
                    gateInput = DevOnlyNpuRouteGateInput(
                        customBuildExperiment = true,
                        allowEditablePromptPreview = true,
                        allowGuardedNpuRun = true,
                        allowEditablePromptExecution = true,
                        devCheckboxChecked = true,
                        validatorValid = validation.isValid,
                        nativeEditablePromptSupported = true,
                        running = false,
                        maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                    ),
                    prompt = normalizedPrompt,
                    maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
                    timeoutMs = DevOnlyNpuRouteAdapter.DEFAULT_TIMEOUT_MS,
                )
            } finally {
                SettingsPreferences(appContext).saveDevEnableNpuChatScreenRoute(false)
            }
        }
        val displayModel = DevOnlyNpuRouteDisplayModelMapper.from(result)
        val transientState = DevOnlyNpuTransientPresenter.present(displayModel)
        return listOf(
            "DEV NPU route",
            "status=${transientState.status}",
            "reason=${transientState.reasonCode}",
            "output=${transientState.outputPreview ?: "-"}",
            "db=${transientState.shouldPersistToDb}",
            "tts=${transientState.shouldSpeakTts}",
            "markdown=${transientState.shouldRenderMarkdown}",
            "stream=${transientState.shouldStream}",
        ).joinToString(" ")
    }
}
