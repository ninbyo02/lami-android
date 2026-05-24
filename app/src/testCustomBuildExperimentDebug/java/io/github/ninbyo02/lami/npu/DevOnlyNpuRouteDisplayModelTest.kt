package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevOnlyNpuRouteDisplayModelTest {
    @Test
    fun `success result maps to success display`() {
        val model = DevOnlyNpuRouteDisplayModelMapper.from(
            result(
                success = true,
                output = "ok",
                reasonCode = "ok",
                elapsedMs = 42L,
                decodeElapsedMs = 7L,
                backendEvidence = "QNN_HTP_V79_FastRPC",
                artifactPath = "artifacts/dev_npu/success",
            ),
        )

        assertEquals(DevOnlyNpuRouteDisplayModel.Status.SUCCESS, model.status)
        assertEquals("DEV NPU route success", model.title)
        assertEquals("output=ok", model.message)
        assertEquals("ok", model.output)
        assertEquals("ok", model.reasonCode)
        assertEquals("elapsed_ms=42 decode_elapsed_ms=7", model.elapsedText)
        assertEquals("QNN_HTP_V79_FastRPC", model.backendEvidenceText)
        assertEquals("artifacts/dev_npu/success", model.artifactText)
    }

    @Test
    fun `adapter not connected maps to blocked display`() {
        val model = DevOnlyNpuRouteDisplayModelMapper.from(
            result(reasonCode = BlockedDevOnlyNpuRouteAdapter.REASON_ADAPTER_NOT_CONNECTED),
        )

        assertEquals(DevOnlyNpuRouteDisplayModel.Status.BLOCKED, model.status)
        assertEquals("DEV NPU route blocked", model.title)
        assertEquals("NPU route adapter is not connected", model.message)
        assertNull(model.output)
    }

    @Test
    fun `gate blocked reason maps to blocked display`() {
        val model = DevOnlyNpuRouteDisplayModelMapper.from(
            result(reasonCode = "gate_blocked:VALIDATOR_INVALID"),
        )

        assertEquals(DevOnlyNpuRouteDisplayModel.Status.BLOCKED, model.status)
        assertEquals("gate_blocked:VALIDATOR_INVALID", model.reasonCode)
        assertEquals("blocked reason=gate_blocked:VALIDATOR_INVALID", model.message)
    }

    @Test
    fun `timeout flag maps to timeout display`() {
        val model = DevOnlyNpuRouteDisplayModelMapper.from(
            result(
                reasonCode = "timeout",
                timeout = true,
                elapsedMs = 30_000L,
            ),
        )

        assertEquals(DevOnlyNpuRouteDisplayModel.Status.TIMEOUT, model.status)
        assertEquals("DEV NPU route timeout", model.title)
        assertEquals("timeout reason=timeout", model.message)
        assertEquals("elapsed_ms=30000 decode_elapsed_ms=unknown", model.elapsedText)
    }

    @Test
    fun `fresh crash flag maps to crash display`() {
        val model = DevOnlyNpuRouteDisplayModelMapper.from(
            result(
                reasonCode = "fresh_crash",
                freshCrash = true,
            ),
        )

        assertEquals(DevOnlyNpuRouteDisplayModel.Status.CRASH, model.status)
        assertEquals("DEV NPU route crash evidence", model.title)
        assertEquals("fresh crash evidence reason=fresh_crash", model.message)
    }

    @Test
    fun `unknown failure maps to error display`() {
        val model = DevOnlyNpuRouteDisplayModelMapper.from(
            result(reasonCode = "unexpected_failure"),
        )

        assertEquals(DevOnlyNpuRouteDisplayModel.Status.ERROR, model.status)
        assertEquals("DEV NPU route error", model.title)
        assertEquals("error reason=unexpected_failure", model.message)
    }

    @Test
    fun `null output elapsed evidence and artifact use explicit placeholders`() {
        val model = DevOnlyNpuRouteDisplayModelMapper.from(
            result(
                output = null,
                elapsedMs = null,
                decodeElapsedMs = null,
                backendEvidence = null,
                artifactPath = null,
            ),
        )

        assertNull(model.output)
        assertEquals("elapsed_ms=unknown decode_elapsed_ms=unknown", model.elapsedText)
        assertEquals("backendEvidence=none", model.backendEvidenceText)
        assertEquals("artifactPath=none", model.artifactText)
    }

    private fun result(
        success: Boolean = false,
        output: String? = null,
        reasonCode: String = "adapter_not_connected",
        elapsedMs: Long? = null,
        decodeElapsedMs: Long? = null,
        prompt: String = "Hello",
        maxOutputTokens: Int = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
        backendEvidence: String? = null,
        artifactPath: String? = null,
        freshCrash: Boolean = false,
        timeout: Boolean = false,
    ): DevOnlyNpuRouteResult =
        DevOnlyNpuRouteResult(
            success = success,
            output = output,
            reasonCode = reasonCode,
            elapsedMs = elapsedMs,
            decodeElapsedMs = decodeElapsedMs,
            prompt = prompt,
            maxOutputTokens = maxOutputTokens,
            backendEvidence = backendEvidence,
            artifactPath = artifactPath,
            freshCrash = freshCrash,
            timeout = timeout,
        )
}
