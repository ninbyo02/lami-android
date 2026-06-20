package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuPersistentHolderNativeStubTest {
    @Test
    fun `native holder stub summary reports declared but not implemented`() {
        val diagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            nativeRunCalled = true,
            nativeCloseCalled = true,
            nativeDiagnosticsCalled = true,
        )
        val text = formatNpuPersistentHolderNativeStubProbeSummary(diagnostics)

        assertTrue(text.contains("test_name=NPU Persistent Holder Native Stub Probe"))
        assertTrue(text.contains("holder_api_available=false"))
        assertTrue(text.contains("native_holder_stub_available=true"))
        assertTrue(text.contains("native_holder_stub_version=dev_only_standard_route_adapter_holder_stub_v1"))
        assertTrue(text.contains("native_create_declared=true"))
        assertTrue(text.contains("native_run_declared=true"))
        assertTrue(text.contains("native_close_declared=true"))
        assertTrue(text.contains("native_diagnostics_declared=true"))
        assertTrue(text.contains("native_create_called=true"))
        assertTrue(text.contains("native_run_called=true"))
        assertTrue(text.contains("native_close_called=true"))
        assertTrue(text.contains("native_diagnostics_called=true"))
        assertTrue(text.contains("engine_create_called=false"))
        assertTrue(text.contains("model_assets_create_called=false"))
        assertTrue(text.contains("npu_decode_called=false"))
        assertTrue(text.contains("qnn_called=false"))
        assertTrue(text.contains("status=not_implemented"))
        assertTrue(text.contains("reason=dev_only_native_holder_stub_no_engine_create"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
        assertTrue(text.contains("recommended_next_step=implement_native_create_close_without_decode"))
        assertFalse(text.contains("persistent_multi_turn_possible=true"))
        assertFalse(text.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `native holder stub parser keeps engine and qnn calls false`() {
        val result = parseNpuPersistentHolderNativeStubResult(
            nativeSummary = """
                holder_api_available=false
                native_holder_stub_available=true
                native_holder_stub_version=dev_only_standard_route_adapter_holder_stub_v1
                native_create_declared=true
                native_run_declared=true
                native_close_declared=true
                native_diagnostics_declared=true
                native_create_called=true
                native_run_called=false
                native_close_called=false
                native_diagnostics_called=false
                engine_create_called=false
                model_assets_create_called=false
                npu_decode_called=false
                qnn_called=false
                holder_id=unavailable
                status=not_implemented
                reason=dev_only_native_holder_stub_no_engine_create
                persistent_multi_turn_possible=false
                recommended_next_step=implement_native_create_close_without_decode
            """.trimIndent(),
        )

        assertEquals("not_implemented", result.status)
        assertEquals("dev_only_native_holder_stub_no_engine_create", result.reason)
        assertEquals("unavailable", result.holderId)
        assertTrue(result.diagnostics.nativeHolderStubAvailable)
        assertTrue(result.diagnostics.nativeCreateDeclared)
        assertTrue(result.diagnostics.nativeRunDeclared)
        assertTrue(result.diagnostics.nativeCloseDeclared)
        assertTrue(result.diagnostics.nativeDiagnosticsDeclared)
        assertTrue(result.diagnostics.nativeCreateCalled)
        assertFalse(result.diagnostics.nativeRunCalled)
        assertFalse(result.diagnostics.nativeCloseCalled)
        assertFalse(result.diagnostics.nativeDiagnosticsCalled)
        assertFalse(result.diagnostics.engineCreateCalled)
        assertFalse(result.diagnostics.modelAssetsCreateCalled)
        assertFalse(result.diagnostics.npuDecodeCalled)
        assertFalse(result.diagnostics.qnnCalled)
        assertFalse(result.diagnostics.persistentMultiTurnPossible)
    }

    @Test
    fun `native holder stub diagnostics merge called flags without enabling persistence`() {
        val diagnostics = mergeNpuPersistentHolderNativeStubDiagnostics(
            listOf(
                npuPersistentHolderNativeStubDiagnostics(nativeCreateCalled = true),
                npuPersistentHolderNativeStubDiagnostics(nativeRunCalled = true),
                npuPersistentHolderNativeStubDiagnostics(nativeCloseCalled = true),
                npuPersistentHolderNativeStubDiagnostics(nativeDiagnosticsCalled = true),
            ),
        )

        assertTrue(diagnostics.nativeCreateCalled)
        assertTrue(diagnostics.nativeRunCalled)
        assertTrue(diagnostics.nativeCloseCalled)
        assertTrue(diagnostics.nativeDiagnosticsCalled)
        assertFalse(diagnostics.engineCreateCalled)
        assertFalse(diagnostics.npuDecodeCalled)
        assertFalse(diagnostics.qnnCalled)
        assertEquals("not_implemented", diagnostics.status)
        assertFalse(diagnostics.persistentMultiTurnPossible)
    }
}
