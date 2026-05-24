package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuRouteGateTest {
    @Test
    fun `all valid allows route`() {
        val result = DevOnlyNpuRouteGate.evaluate(validInput())

        assertTrue(result.allowed)
        assertEquals(DevOnlyNpuRouteGateReason.OK, result.reason)
    }

    @Test
    fun `not custom build is rejected`() {
        assertRejected(
            validInput(customBuildExperiment = false),
            DevOnlyNpuRouteGateReason.NOT_CUSTOM_BUILD_EXPERIMENT,
        )
    }

    @Test
    fun `editable preview disabled is rejected`() {
        assertRejected(
            validInput(allowEditablePromptPreview = false),
            DevOnlyNpuRouteGateReason.EDITABLE_PREVIEW_DISABLED,
        )
    }

    @Test
    fun `guarded run disabled is rejected`() {
        assertRejected(
            validInput(allowGuardedNpuRun = false),
            DevOnlyNpuRouteGateReason.GUARDED_RUN_DISABLED,
        )
    }

    @Test
    fun `editable execution disabled is rejected`() {
        assertRejected(
            validInput(allowEditablePromptExecution = false),
            DevOnlyNpuRouteGateReason.EDITABLE_PROMPT_EXECUTION_DISABLED,
        )
    }

    @Test
    fun `checkbox off is rejected`() {
        assertRejected(
            validInput(devCheckboxChecked = false),
            DevOnlyNpuRouteGateReason.DEV_CHECKBOX_NOT_CHECKED,
        )
    }

    @Test
    fun `validator invalid is rejected`() {
        assertRejected(
            validInput(validatorValid = false),
            DevOnlyNpuRouteGateReason.VALIDATOR_INVALID,
        )
    }

    @Test
    fun `native unsupported is rejected`() {
        assertRejected(
            validInput(nativeEditablePromptSupported = false),
            DevOnlyNpuRouteGateReason.NATIVE_PROMPT_UNSUPPORTED,
        )
    }

    @Test
    fun `running true is rejected`() {
        assertRejected(
            validInput(running = true),
            DevOnlyNpuRouteGateReason.RUN_ALREADY_IN_PROGRESS,
        )
    }

    @Test
    fun `invalid max output tokens is rejected`() {
        assertRejected(
            validInput(maxOutputTokens = 4),
            DevOnlyNpuRouteGateReason.INVALID_MAX_OUTPUT_TOKENS,
        )
    }

    @Test
    fun `bounded max output token range can be allowed for hidden experiments`() {
        val result = DevOnlyNpuRouteGate.evaluate(
            validInput(
                maxOutputTokens = 64,
                allowMaxOutputTokenRange = true,
            ),
        )

        assertTrue(result.allowed)
        assertEquals(DevOnlyNpuRouteGateReason.OK, result.reason)
    }

    @Test
    fun `bounded max output token range still rejects values above phase limit`() {
        assertRejected(
            validInput(
                maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS + 1,
                allowMaxOutputTokenRange = true,
            ),
            DevOnlyNpuRouteGateReason.INVALID_MAX_OUTPUT_TOKENS,
        )
    }

    private fun assertRejected(
        input: DevOnlyNpuRouteGateInput,
        reason: DevOnlyNpuRouteGateReason,
    ) {
        val result = DevOnlyNpuRouteGate.evaluate(input)

        assertFalse(result.allowed)
        assertEquals(reason, result.reason)
    }

    private fun validInput(
        customBuildExperiment: Boolean = true,
        allowEditablePromptPreview: Boolean = true,
        allowGuardedNpuRun: Boolean = true,
        allowEditablePromptExecution: Boolean = true,
        devCheckboxChecked: Boolean = true,
        validatorValid: Boolean = true,
        nativeEditablePromptSupported: Boolean = true,
        running: Boolean = false,
        maxOutputTokens: Int = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
        allowMaxOutputTokenRange: Boolean = false,
    ): DevOnlyNpuRouteGateInput = DevOnlyNpuRouteGateInput(
        customBuildExperiment = customBuildExperiment,
        allowEditablePromptPreview = allowEditablePromptPreview,
        allowGuardedNpuRun = allowGuardedNpuRun,
        allowEditablePromptExecution = allowEditablePromptExecution,
        devCheckboxChecked = devCheckboxChecked,
        validatorValid = validatorValid,
        nativeEditablePromptSupported = nativeEditablePromptSupported,
        running = running,
        maxOutputTokens = maxOutputTokens,
        allowMaxOutputTokenRange = allowMaxOutputTokenRange,
    )
}
