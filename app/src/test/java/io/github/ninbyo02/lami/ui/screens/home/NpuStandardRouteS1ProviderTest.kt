package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1ProviderTest {
    private val userPrompt = "好きな色を一つだけ答えてください"

    @Test
    fun `fixed provider returns default S1 success raw result`() {
        val raw = FixedNpuStandardRouteS1Provider().invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )

        assertEquals("success", raw.status)
        assertEquals("success", raw.result)
        assertEquals(true, raw.success)
        assertEquals("success", raw.reason)
        assertEquals("こんにちは。", raw.rawOutput)
        assertEquals("こんにちは。", raw.sanitizedOutput)
        assertEquals("natural_japanese", raw.qualityClassification)
        assertTrue(raw.runDecodeReached)
        assertEquals("QNN_HTP_V79_FastRPC_native_diag", raw.npuBackendEvidence)
        assertFalse(raw.fallbackUsed)
        assertFalse(raw.timeout)
        assertFalse(raw.freshCrash)
        assertEquals(128, raw.requestedMaxOutputTokens)
        assertEquals(128, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `failure provider returns mapper compatible failure raw result`() {
        val raw = FailureNpuStandardRouteS1Provider(reason = "test_failure").invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertEquals("failure", raw.status)
        assertEquals("failure", raw.result)
        assertEquals(false, raw.success)
        assertEquals("test_failure", raw.reason)
        assertEquals("", raw.sanitizedOutput)
        assertFalse(raw.runDecodeReached)
        assertEquals("", raw.npuBackendEvidence)
        assertEquals(128, raw.requestedMaxOutputTokens)
        assertEquals(128, raw.effectiveMaxOutputTokens)
        assertFalse(mapped.successCriteriaMet)
        assertEquals("failure", mapped.status)
        assertEquals("test_failure", mapped.reason)
    }

    @Test
    fun `invoker default provider follows build variant provider selection`() {
        val raw = NpuStandardRouteS1Invoker().invoke(userPrompt)
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            assertFalse(mapped.successCriteriaMet)
            assertEquals("dev_only_entry_unavailable", mapped.reason)
        } else {
            assertTrue(mapped.successCriteriaMet)
            assertEquals("こんにちは。", mapped.displayText)
        }
        assertTrue(mapped.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `default provider follows build variant provider selection`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider().invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            assertFalse(mapped.successCriteriaMet)
            assertEquals("failure", raw.status)
            assertEquals("dev_only_entry_unavailable", raw.reason)
        } else {
            assertTrue(mapped.successCriteriaMet)
            assertEquals("success", raw.status)
            assertEquals("こんにちは。", raw.sanitizedOutput)
            assertEquals("QNN_HTP_V79_FastRPC_native_diag", raw.npuBackendEvidence)
        }
    }

    @Test
    fun `provider selector uses fixed provider when S1 gate is disabled`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider(s1GateEnabled = false).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertTrue(mapped.successCriteriaMet)
        assertEquals("success", raw.status)
        assertEquals("こんにちは。", raw.sanitizedOutput)
    }

    @Test
    fun `provider selector uses real provider path when S1 gate is enabled`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider(s1GateEnabled = true).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertFalse(mapped.successCriteriaMet)
        assertEquals("failure", raw.status)
        assertEquals("dev_only_entry_unavailable", raw.reason)
    }

    @Test
    fun `fixed provider uses explicit max output token setting`() {
        val raw = FixedNpuStandardRouteS1Provider().invoke(
            userPrompt = userPrompt,
            maxOutputTokens = 512,
            trace = {},
        )

        assertEquals(512, raw.requestedMaxOutputTokens)
        assertEquals(512, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `provider selector for Settings mode keeps standard OFF fixed and S1 real while preserving custom compatibility`() {
        val offRaw = NpuStandardRouteS1ProviderSelector.defaultProviderForMode(NpuStandardRouteMode.OFF)
            .invoke(
                userPrompt = userPrompt,
                maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
                trace = {},
            )
        val s1Raw = NpuStandardRouteS1ProviderSelector.defaultProviderForMode(NpuStandardRouteMode.S1_ONLY)
            .invoke(
                userPrompt = userPrompt,
                maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
                trace = {},
            )

        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            assertEquals("failure", offRaw.status)
            assertEquals("dev_only_entry_unavailable", offRaw.reason)
        } else {
            assertEquals("success", offRaw.status)
            assertEquals("こんにちは。", offRaw.sanitizedOutput)
        }
        assertEquals("failure", s1Raw.status)
        assertEquals("dev_only_entry_unavailable", s1Raw.reason)
    }

    @Test
    fun `real provider class is resolvable from debug source set`() {
        val providerClass = Class.forName(NpuStandardRouteS1ProviderSelector.REAL_PROVIDER_CLASS_NAME)

        assertTrue(NpuStandardRouteS1Provider::class.java.isAssignableFrom(providerClass))
    }

    @Test
    fun `invoker accepts provider interface without ChatScreen dependency`() {
        val invoker = NpuStandardRouteS1Invoker(
            provider = FailureNpuStandardRouteS1Provider(
                reason = "provider_injected_failure",
                fallbackUsed = true,
            ),
        )
        val mapped = NpuStandardRouteS1Mapper.map(invoker.invoke(userPrompt))

        assertFalse(mapped.successCriteriaMet)
        assertEquals("provider_injected_failure", mapped.reason)
        assertTrue(mapped.fallbackUsed)
        assertTrue(mapped.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `real prompt trace uses hash and preview without full prompt`() {
        val trace = buildNpuRealPromptHandoffTrace(stage = "chat", userPrompt = userPrompt)

        assertTrue(trace.contains("NPU_REAL_PROMPT chat_prompt_hash="))
        assertTrue(trace.contains("chat_prompt_length=${userPrompt.length}"))
        assertTrue(trace.contains("chat_prompt_code_points=${userPrompt.codePointCount(0, userPrompt.length)}"))
        assertTrue(trace.contains("chat_prompt_preview="))
        assertFalse(trace.contains(userPrompt))
    }

    @Test
    fun `S1 dev trace summarizes input and outputs without full long text`() {
        val longPrompt = "こんばんは。NPU標準ルートのデバッグ表示で全文が出ないことを確認します。"
        val longRawOutput = "こんばんは。これはraw outputの長い確認文です。全文ではなくpreviewだけを表示します。"
        val longSanitizedOutput = "こんばんは。これはsanitized outputの長い確認文です。全文ではなくpreviewだけを表示します。"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = longRawOutput,
                sanitizedOutput = longSanitizedOutput,
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        val trace = buildNpuStandardRouteS1DevTraceText(
            input = longPrompt,
            result = result,
            maxOutputTokens = 512,
        )

        assertTrue(trace.contains("max_output_tokens=512"))
        assertTrue(trace.contains("input_hash="))
        assertTrue(trace.contains("input_prompt="))
        assertTrue(trace.contains("input_preview="))
        assertTrue(trace.contains("..."))
        assertTrue(trace.contains("input_length=${longPrompt.length}"))
        assertTrue(trace.contains("input_code_points=${longPrompt.codePointCount(0, longPrompt.length)}"))
        assertTrue(trace.contains("raw_output_hash="))
        assertTrue(trace.contains("raw_output_length=${longRawOutput.length}"))
        assertTrue(trace.contains("sanitized_output_hash="))
        assertTrue(trace.contains("sanitized_output_length=${longSanitizedOutput.length}"))
        assertTrue(trace.contains("status=success"))
        assertTrue(trace.contains("reason=success"))
        assertTrue(trace.contains("quality_classification=natural_japanese"))
        assertTrue(trace.contains("run_decode_reached=true"))
        assertTrue(trace.contains("timeout=false"))
        assertTrue(trace.contains("fallback=false"))
        assertTrue(trace.contains("fresh_crash=false"))
        assertFalse(trace.contains(longPrompt))
        assertFalse(trace.contains(longRawOutput))
        assertFalse(trace.contains(longSanitizedOutput))
    }

    @Test
    fun `S1 diagnostic copy keeps full input and output values`() {
        val input = "こんばんは"
        val rawOutput = "raw\noutput"
        val sanitizedOutput = "こんばんは。"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = rawOutput,
                sanitizedOutput = sanitizedOutput,
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 256,
                effectiveMaxOutputTokens = 256,
            ),
        )

        val copyText = buildNpuStandardRouteS1DiagnosticCopyText(
            input = input,
            result = result,
            maxOutputTokens = 256,
        )

        assertTrue(copyText.contains("input_prompt=こんばんは"))
        assertTrue(copyText.contains("max_output_tokens=256"))
        assertTrue(copyText.contains("raw_output=raw\\noutput"))
        assertTrue(copyText.contains("sanitized_output=こんばんは。"))
        assertTrue(copyText.contains("status=success"))
        assertTrue(copyText.contains("reason=success"))
        assertTrue(copyText.contains("quality_classification=natural_japanese"))
        assertTrue(copyText.contains("run_decode_reached=true"))
        assertTrue(copyText.contains("timeout=false"))
        assertTrue(copyText.contains("fallback=false"))
        assertTrue(copyText.contains("fresh_crash=false"))
    }

    @Test
    fun `S1 dev trace records original failure when safe greeting fallback is applied`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = NpuStandardRouteS1Contract.REASON_EMPTY_AFTER_SANITIZE,
                rawOutput = "૩です|",
                sanitizedOutput = "",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_MIXED_LANGUAGE,
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        val trace = buildNpuStandardRouteS1DevTraceText(
            input = "こんばんは",
            result = result,
            transientFallback = NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING,
        )
        val copyText = buildNpuStandardRouteS1DiagnosticCopyText(
            input = "こんばんは",
            result = result,
            transientFallback = NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING,
        )

        assertTrue(trace.contains("original_status=failure"))
        assertTrue(trace.contains("original_reason=empty_after_sanitize"))
        assertTrue(trace.contains("original_quality_classification=mixed_language"))
        assertTrue(trace.contains("fallback=safe_greeting_fallback"))
        assertTrue(copyText.contains("fallback=safe_greeting_fallback"))
    }
}
