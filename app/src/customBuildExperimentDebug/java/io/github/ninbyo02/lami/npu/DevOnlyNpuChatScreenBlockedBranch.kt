package io.github.ninbyo02.lami.npu

import io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticPromptValidator
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
}
