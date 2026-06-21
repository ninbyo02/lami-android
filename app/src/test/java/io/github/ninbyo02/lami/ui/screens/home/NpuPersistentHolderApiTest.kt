package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuPersistentHolderApiTest {
    @Test
    fun `create close dev ui labels are stable`() {
        assertTrue(NPU_PERSISTENT_HOLDER_CREATE_CLOSE_UI_TITLE.contains("NPU Persistent Holder Create/Close Probe"))
        assertTrue(NPU_PERSISTENT_HOLDER_CREATE_CLOSE_RUN_LABEL.contains("Run Holder Create/Close Probe"))
        assertTrue(
            NPU_PERSISTENT_HOLDER_CREATE_CLOSE_COPY_SUMMARY_LABEL.contains(
                "Copy Holder Create/Close Summary",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_CREATE_CLOSE_COPY_FULL_DUMP_LABEL.contains(
                "Copy Holder Create/Close Full Dump",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_CREATE_CLOSE_NO_RESULT.contains(
                "no holder create/close probe result available",
            ),
        )
        assertTrue(NPU_PERSISTENT_HOLDER_RUN_ONCE_UI_TITLE.contains("NPU Persistent Holder Run Once Probe"))
        assertTrue(NPU_PERSISTENT_HOLDER_RUN_ONCE_RUN_LABEL.contains("Run Holder Run Once Probe"))
        assertTrue(
            NPU_PERSISTENT_HOLDER_RUN_ONCE_COPY_SUMMARY_LABEL.contains(
                "Copy Holder Run Once Summary",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_RUN_ONCE_COPY_FULL_DUMP_LABEL.contains(
                "Copy Holder Run Once Full Dump",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_RUN_ONCE_NO_RESULT.contains(
                "no holder run once probe result available",
            ),
        )
        assertTrue(NPU_PERSISTENT_HOLDER_TWO_TURN_UI_TITLE.contains("NPU Persistent Holder Two-Turn Probe"))
        assertTrue(NPU_PERSISTENT_HOLDER_TWO_TURN_RUN_LABEL.contains("Run Holder Two-Turn Probe"))
        assertTrue(
            NPU_PERSISTENT_HOLDER_TWO_TURN_COPY_SUMMARY_LABEL.contains(
                "Copy Holder Two-Turn Summary",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_TWO_TURN_COPY_FULL_DUMP_LABEL.contains(
                "Copy Holder Two-Turn Full Dump",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_TWO_TURN_NO_RESULT.contains(
                "no holder two-turn probe result available",
            ),
        )
        assertTrue(NPU_PERSISTENT_HOLDER_FIVE_TURN_UI_TITLE.contains("NPU Persistent Holder Five-Turn Probe"))
        assertTrue(NPU_PERSISTENT_HOLDER_FIVE_TURN_RUN_LABEL.contains("Run Holder Five-Turn Probe"))
        assertTrue(
            NPU_PERSISTENT_HOLDER_FIVE_TURN_COPY_SUMMARY_LABEL.contains(
                "Copy Holder Five-Turn Summary",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_FIVE_TURN_COPY_FULL_DUMP_LABEL.contains(
                "Copy Holder Five-Turn Full Dump",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_FIVE_TURN_NO_RESULT.contains(
                "no holder five-turn probe result available",
            ),
        )
        assertTrue(NPU_PERSISTENT_HOLDER_TEN_TURN_UI_TITLE.contains("NPU Persistent Holder Ten-Turn Probe"))
        assertTrue(NPU_PERSISTENT_HOLDER_TEN_TURN_RUN_LABEL.contains("Run Holder Ten-Turn Probe"))
        assertTrue(
            NPU_PERSISTENT_HOLDER_TEN_TURN_COPY_SUMMARY_LABEL.contains(
                "Copy Holder Ten-Turn Summary",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_TEN_TURN_COPY_FULL_DUMP_LABEL.contains(
                "Copy Holder Ten-Turn Full Dump",
            ),
        )
        assertTrue(
            NPU_PERSISTENT_HOLDER_TEN_TURN_NO_RESULT.contains(
                "no holder ten-turn probe result available",
            ),
        )
        assertTrue(
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_UI_TITLE.contains(
                "NPU True Engine Holder Create/Close Probe",
            ),
        )
        assertTrue(
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_RUN_LABEL.contains(
                "Run True Engine Holder Create/Close Probe",
            ),
        )
        assertTrue(
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_COPY_SUMMARY_LABEL.contains(
                "Copy True Engine Holder Summary",
            ),
        )
        assertTrue(
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_COPY_FULL_DUMP_LABEL.contains(
                "Copy True Engine Holder Full Dump",
            ),
        )
        assertTrue(
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_NO_RESULT.contains(
                "no true engine holder create/close probe result available",
            ),
        )
    }

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
        assertTrue(text.contains("native_holder_stub_available=false"))
        assertTrue(text.contains("native_holder_create_close_available=false"))
        assertTrue(text.contains("native_holder_stub_version=unavailable"))
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
