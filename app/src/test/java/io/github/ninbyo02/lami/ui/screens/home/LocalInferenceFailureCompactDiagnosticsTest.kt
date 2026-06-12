package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException

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

    @Test
    fun `InvocationTargetException target and root cause are expanded`() {
        val root = IllegalArgumentException("backend enum mismatch")
        val target = IllegalStateException("engine create failed", root)
        val wrapper = InvocationTargetException(target)
        val text = buildFailureText(
            setting = PreferredBackendDryRunSetting.CPU,
            throwable = wrapper,
            exceptionClass = wrapper.javaClass.name,
            exceptionMessage = wrapper.message ?: "none",
        )

        assertTrue(text.contains("failure_exception_class=java.lang.reflect.InvocationTargetException"))
        assertTrue(text.contains("failure_exception_message=none"))
        assertTrue(text.contains("failure_cause_class=java.lang.IllegalStateException"))
        assertTrue(text.contains("failure_cause_message=engine create failed"))
        assertTrue(text.contains("failure_root_cause_class=java.lang.IllegalArgumentException"))
        assertTrue(text.contains("failure_root_cause_message=backend enum mismatch"))
        assertTrue(text.contains("reflection_target_exception_class=java.lang.IllegalStateException"))
        assertTrue(text.contains("reflection_target_exception_message=engine create failed"))
        assertTrue(text.contains("reflection_target_exception_root_cause_class=java.lang.IllegalArgumentException"))
        assertTrue(text.contains("reflection_target_exception_root_cause_message=backend enum mismatch"))
        assertTrue(text.contains("exception_chain=java.lang.reflect.InvocationTargetException:none -> java.lang.IllegalStateException:engine create failed -> java.lang.IllegalArgumentException:backend enum mismatch"))
    }

    @Test
    fun `null exception messages are rendered as none in chain`() {
        val root = IllegalStateException()
        val wrapper = InvocationTargetException(root)
        val text = buildFailureText(
            setting = PreferredBackendDryRunSetting.GPU,
            throwable = wrapper,
            exceptionClass = wrapper.javaClass.name,
            exceptionMessage = wrapper.message ?: "none",
        )

        assertTrue(text.contains("failure_exception_message=none"))
        assertTrue(text.contains("failure_cause_message=none"))
        assertTrue(text.contains("failure_root_cause_message=none"))
        assertTrue(text.contains("reflection_target_exception_message=none"))
        assertTrue(text.contains("exception_chain=java.lang.reflect.InvocationTargetException:none -> java.lang.IllegalStateException:none"))
    }

    @Test
    fun `local compact reflection keys do not change NPU S1 compact header`() {
        val localText = buildFailureText(
            setting = PreferredBackendDryRunSetting.DEFAULT,
            throwable = InvocationTargetException(IllegalStateException("local failure")),
            exceptionClass = InvocationTargetException::class.java.name,
            exceptionMessage = "none",
        )
        val npuText = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = NpuStandardRouteS1Result(
                status = "success",
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                inputPrompt = "こんにちは",
            ),
        )

        assertTrue(localText.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(localText.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(npuText.contains("[DEV診断: NPU S1 compact]"))
        assertFalse(npuText.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(npuText.contains("reflection_target_exception_class="))
    }

    private fun buildFailureText(
        setting: PreferredBackendDryRunSetting,
        throwable: Throwable? = null,
        exceptionClass: String? = null,
        exceptionMessage: String? = null,
    ): String =
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
                exceptionClass = exceptionClass,
                exceptionMessage = exceptionMessage,
                throwable = throwable,
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
