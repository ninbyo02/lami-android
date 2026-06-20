package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuPersistentHolderApiTest {
    @Test
    fun `not exposed holder api summary does not infer persistent support`() {
        val text = formatNpuPersistentHolderApiProbeSummary()

        assertTrue(text.contains("test_name=NPU Persistent Holder API Probe"))
        assertTrue(text.contains("holder_api_available=false"))
        assertTrue(text.contains("holder_api_reason=needs_native_jni_support"))
        assertTrue(text.contains("holder_create_supported=false"))
        assertTrue(text.contains("holder_run_supported=false"))
        assertTrue(text.contains("holder_close_supported=false"))
        assertTrue(text.contains("holder_diagnostics_supported=false"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
        assertTrue(text.contains("engine_reuse_observed=unavailable"))
        assertFalse(text.contains("holder_api_available=true"))
        assertFalse(text.contains("persistent_multi_turn_possible=true"))
        assertFalse(text.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `not exposed holder api keeps session blocked and recommends native holder work`() {
        val api = NotExposedNpuPersistentHolderApi
        val result = api.createHolder(
            NpuPersistentHolderCreateRequest(
                modelPath = "/models/gemma.task",
                nativeLibraryDir = "/native",
                cacheDir = "/cache",
                maxTokens = 512,
            ),
        )
        val text = formatNpuPersistentHolderApiProbeSummary(result.diagnostics)

        assertTrue(text.contains("session_api_supported_for_npu=false"))
        assertTrue(text.contains("session_api_block_reason=session_api_logits_output_not_supported_on_npu_backend"))
        assertTrue(text.contains("standard_route_adapter_decode_success_known=true"))
        assertTrue(text.contains("standard_route_backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(text.contains("engine_lifecycle_visibility=partial"))
        assertTrue(text.contains("required_native_api=create_holder,run_holder_once,close_holder,get_holder_diagnostics"))
        assertTrue(text.contains("recommended_next_step=implement_dev_only_native_holder_api"))
    }
}
