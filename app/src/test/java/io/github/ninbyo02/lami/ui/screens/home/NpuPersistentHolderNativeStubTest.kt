package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuPersistentHolderNativeStubTest {
    @Test
    fun `native holder create close summary reports lifecycle without decode`() {
        val diagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            nativeCloseCalled = true,
            nativeDiagnosticsCalled = true,
            holderCreateRequested = true,
            holderCreateSucceeded = true,
            holderId = "native-holder-1",
            holderOpen = false,
            holderCloseRequested = true,
            holderCloseSucceeded = true,
            holderDoubleCloseSafe = true,
            status = "closed",
            reason = "holder_closed_without_decode",
        )
        val text = formatNpuPersistentHolderNativeStubProbeSummary(diagnostics)

        assertTrue(text.contains("test_name=NPU Persistent Holder Create Close Probe"))
        assertTrue(text.contains("holder_api_available=true"))
        assertTrue(text.contains("native_holder_stub_available=true"))
        assertTrue(text.contains("native_holder_create_close_available=true"))
        assertTrue(text.contains("native_holder_stub_version=dev_only_standard_route_adapter_holder_create_close_v1"))
        assertTrue(text.contains("native_create_declared=true"))
        assertTrue(text.contains("native_run_declared=true"))
        assertTrue(text.contains("native_close_declared=true"))
        assertTrue(text.contains("native_diagnostics_declared=true"))
        assertTrue(text.contains("native_create_called=true"))
        assertTrue(text.contains("native_run_called=false"))
        assertTrue(text.contains("native_close_called=true"))
        assertTrue(text.contains("native_diagnostics_called=true"))
        assertTrue(text.contains("holder_create_requested=true"))
        assertTrue(text.contains("holder_create_called=true"))
        assertTrue(text.contains("holder_create_succeeded=true"))
        assertTrue(text.contains("holder_id=native-holder-1"))
        assertTrue(text.contains("holder_open=false"))
        assertTrue(text.contains("holder_close_requested=true"))
        assertTrue(text.contains("holder_close_called=true"))
        assertTrue(text.contains("holder_close_succeeded=true"))
        assertTrue(text.contains("holder_double_close_safe=true"))
        assertTrue(text.contains("holder_fatal_latch=false"))
        assertTrue(text.contains("engine_factory_create_called=false"))
        assertTrue(text.contains("engine_create_called=false"))
        assertTrue(text.contains("model_assets_create_called=false"))
        assertTrue(text.contains("engine_settings_create_called=false"))
        assertTrue(text.contains("npu_decode_called=false"))
        assertTrue(text.contains("generate_called=false"))
        assertTrue(text.contains("qnn_decode_called=false"))
        assertTrue(text.contains("qnn_called=false"))
        assertTrue(text.contains("run_once_supported=false"))
        assertTrue(text.contains("status=closed"))
        assertTrue(text.contains("reason=holder_closed_without_decode"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
        assertTrue(text.contains("restart_app_recommended=false"))
        assertTrue(text.contains("recommended_next_step=review_create_close_device_result_then_implement_run_once_without_multi_turn"))
        assertFalse(text.contains("persistent_multi_turn_possible=true"))
        assertFalse(text.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `native holder create parser keeps engine and qnn calls false`() {
        val result = parseNpuPersistentHolderNativeStubResult(
            nativeSummary = """
                test_name=NPU Persistent Holder Create Close Probe
                holder_api_available=true
                native_holder_stub_available=true
                native_holder_create_close_available=true
                native_holder_stub_version=dev_only_standard_route_adapter_holder_create_close_v1
                native_create_declared=true
                native_run_declared=true
                native_close_declared=true
                native_diagnostics_declared=true
                holder_create_requested=true
                holder_create_called=true
                holder_create_succeeded=true
                holder_id=native-holder-1
                holder_open=true
                holder_close_requested=false
                holder_close_called=false
                holder_close_succeeded=false
                holder_double_close_safe=true
                holder_fatal_latch=false
                holder_fatal_reason=none
                native_create_called=true
                native_run_called=false
                native_close_called=false
                native_diagnostics_called=false
                engine_factory_create_called=false
                engine_create_called=false
                model_assets_create_called=false
                engine_settings_create_called=false
                npu_decode_called=false
                generate_called=false
                qnn_decode_called=false
                qnn_called=false
                run_once_supported=false
                status=created
                reason=app_jni_holder_lifecycle_created_without_engine_create
                persistent_multi_turn_possible=false
                restart_app_recommended=false
                recommended_next_step=review_create_close_device_result_then_implement_run_once_without_multi_turn
            """.trimIndent(),
        )

        assertEquals("created", result.status)
        assertEquals("app_jni_holder_lifecycle_created_without_engine_create", result.reason)
        assertEquals("native-holder-1", result.holderId)
        assertTrue(result.diagnostics.nativeHolderStubAvailable)
        assertTrue(result.diagnostics.nativeHolderCreateCloseAvailable)
        assertTrue(result.diagnostics.nativeCreateDeclared)
        assertTrue(result.diagnostics.nativeRunDeclared)
        assertTrue(result.diagnostics.nativeCloseDeclared)
        assertTrue(result.diagnostics.nativeDiagnosticsDeclared)
        assertTrue(result.diagnostics.nativeCreateCalled)
        assertTrue(result.diagnostics.holderCreateRequested)
        assertTrue(result.diagnostics.holderCreateCalled)
        assertTrue(result.diagnostics.holderCreateSucceeded)
        assertTrue(result.diagnostics.holderOpen)
        assertTrue(result.diagnostics.holderDoubleCloseSafe)
        assertFalse(result.diagnostics.nativeRunCalled)
        assertFalse(result.diagnostics.nativeCloseCalled)
        assertFalse(result.diagnostics.nativeDiagnosticsCalled)
        assertFalse(result.diagnostics.engineFactoryCreateCalled)
        assertFalse(result.diagnostics.engineCreateCalled)
        assertFalse(result.diagnostics.modelAssetsCreateCalled)
        assertFalse(result.diagnostics.engineSettingsCreateCalled)
        assertFalse(result.diagnostics.npuDecodeCalled)
        assertFalse(result.diagnostics.generateCalled)
        assertFalse(result.diagnostics.qnnDecodeCalled)
        assertFalse(result.diagnostics.qnnCalled)
        assertFalse(result.diagnostics.runOnceSupported)
        assertFalse(result.diagnostics.persistentMultiTurnPossible)
    }

    @Test
    fun `native holder run once remains not implemented`() {
        val result = parseNpuPersistentHolderNativeStubResult(
            nativeSummary = """
                native_holder_create_close_available=true
                native_run_called=true
                holder_id=native-holder-1
                npu_decode_called=false
                generate_called=false
                qnn_decode_called=false
                run_once_supported=false
                status=not_implemented
                reason=run_once_not_implemented_create_close_only_probe
                persistent_multi_turn_possible=false
            """.trimIndent(),
        )

        assertEquals("not_implemented", result.status)
        assertEquals("run_once_not_implemented_create_close_only_probe", result.reason)
        assertTrue(result.diagnostics.nativeRunCalled)
        assertFalse(result.diagnostics.runOnceSupported)
        assertFalse(result.diagnostics.npuDecodeCalled)
        assertFalse(result.diagnostics.generateCalled)
        assertFalse(result.diagnostics.qnnDecodeCalled)
        assertFalse(result.diagnostics.persistentMultiTurnPossible)
    }

    @Test
    fun `native holder fatal latch recommends app restart`() {
        val result = parseNpuPersistentHolderNativeStubResult(
            nativeSummary = """
                holder_fatal_latch=true
                holder_fatal_reason=invalid_model_path
                restart_app_recommended=true
                status=failed
                reason=invalid_model_path
                npu_decode_called=false
                generate_called=false
                qnn_decode_called=false
                persistent_multi_turn_possible=false
            """.trimIndent(),
        )

        assertEquals("failed", result.status)
        assertTrue(result.diagnostics.holderFatalLatch)
        assertEquals("invalid_model_path", result.diagnostics.holderFatalReason)
        assertTrue(result.diagnostics.restartAppRecommended)
        assertFalse(result.diagnostics.persistentMultiTurnPossible)
    }

    @Test
    fun `native holder create close diagnostics merge called flags without enabling persistence`() {
        val diagnostics = mergeNpuPersistentHolderNativeStubDiagnostics(
            listOf(
                npuPersistentHolderNativeStubDiagnostics(
                    nativeCreateCalled = true,
                    holderCreateSucceeded = true,
                    holderId = "native-holder-1",
                    holderOpen = true,
                ),
                npuPersistentHolderNativeStubDiagnostics(nativeRunCalled = true),
                npuPersistentHolderNativeStubDiagnostics(
                    nativeCloseCalled = true,
                    holderCloseSucceeded = true,
                    holderOpen = false,
                ),
                npuPersistentHolderNativeStubDiagnostics(nativeDiagnosticsCalled = true),
            ),
        )

        assertTrue(diagnostics.nativeCreateCalled)
        assertTrue(diagnostics.nativeRunCalled)
        assertTrue(diagnostics.nativeCloseCalled)
        assertTrue(diagnostics.nativeDiagnosticsCalled)
        assertTrue(diagnostics.holderCreateSucceeded)
        assertTrue(diagnostics.holderCloseSucceeded)
        assertTrue(diagnostics.holderDoubleCloseSafe)
        assertFalse(diagnostics.holderOpen)
        assertFalse(diagnostics.engineFactoryCreateCalled)
        assertFalse(diagnostics.engineCreateCalled)
        assertFalse(diagnostics.npuDecodeCalled)
        assertFalse(diagnostics.generateCalled)
        assertFalse(diagnostics.qnnDecodeCalled)
        assertFalse(diagnostics.qnnCalled)
        assertEquals("not_implemented", diagnostics.status)
        assertFalse(diagnostics.persistentMultiTurnPossible)
    }
}
