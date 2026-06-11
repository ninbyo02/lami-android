package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceFailureCompactDiagnosticsTest {
    @Test
    fun `CPU failure builds local compact diagnostics`() {
        val text = buildFailureText(PreferredBackendDryRunSetting.CPU)

        assertTrue(text.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(text.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(text.contains("selected_backend=CPU"))
        assertTrue(text.contains("requested_backend=CPU"))
        assertTrue(text.contains("effective_backend=CPU"))
        assertTrue(text.contains("route_family=local_cpu"))
        assertTrue(text.contains("backend_evidence=cpu_route"))
        assertFalse(text.contains("selected_backend=CPU\nrequested_backend=NPU"))
    }

    @Test
    fun `GPU failure builds local compact diagnostics`() {
        val text = buildFailureText(PreferredBackendDryRunSetting.GPU)

        assertTrue(text.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(text.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(text.contains("selected_backend=GPU"))
        assertTrue(text.contains("requested_backend=GPU"))
        assertTrue(text.contains("effective_backend=GPU"))
        assertTrue(text.contains("route_family=local_gpu"))
        assertTrue(text.contains("backend_evidence=gpu_route"))
        assertFalse(text.contains("selected_backend=GPU\nrequested_backend=NPU"))
    }

    @Test
    fun `Automatic failure builds local compact diagnostics`() {
        val text = buildFailureText(PreferredBackendDryRunSetting.DEFAULT)

        assertTrue(text.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(text.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(text.contains("selected_backend=Automatic"))
        assertTrue(text.contains("requested_backend=Automatic"))
        assertTrue(text.contains("effective_backend=Automatic"))
        assertTrue(text.contains("route_family=local_default"))
        assertTrue(text.contains("backend_evidence=local_default"))
    }

    private fun buildFailureText(setting: PreferredBackendDryRunSetting): String =
        buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = setting,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    localModelDisplayName = "gemma-local",
                    mediaPipeProbeModelPath = "/tmp/gemma-local.litertlm",
                    requestedPreferredBackend = setting.name,
                    appliedPreferredBackend = when (setting) {
                        PreferredBackendDryRunSetting.DEFAULT -> null
                        else -> setting.name
                    },
                    preferredBackendApplyResult = if (setting == PreferredBackendDryRunSetting.DEFAULT) {
                        "skipped-default"
                    } else {
                        "applied"
                    },
                    localFailureDiagnosticsText = """
                        failure stage=engine-create
                        exception class=IllegalStateException
                        exception message=create failed
                    """.trimIndent(),
                ),
                reason = "local_inference_failure",
                routeContext = buildLocalRouteDiagnosticContext(
                    selectedModelName = "gemma-local",
                    selectedModelFile = "/tmp/gemma-local.litertlm",
                    preferredBackend = setting.name,
                    npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                    effectiveNpuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                    shouldEnterNpuS1 = false,
                    localRouteEntered = true,
                ),
                ttsRequested = true,
                markdownRequested = true,
                streamingRequested = true,
                processPid = "1234",
            ),
        )
}
