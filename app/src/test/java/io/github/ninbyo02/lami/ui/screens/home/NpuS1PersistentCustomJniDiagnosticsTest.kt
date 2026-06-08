package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertTrue
import org.junit.Test

class NpuS1PersistentCustomJniDiagnosticsTest {
    @Test
    fun `summary includes holder key and model update fields`() {
        val state = NpuS1PersistentCustomJniProbeState(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED,
            engineCreateCount = "0",
            decodeAttemptCount = "7",
            decodeSuccessCount = "6",
            holderKey = NpuS1PersistentCustomJniHolderKey(
                modelPath = "/data/user/0/io.github.ninbyo02.lami/files/local_models/model.litertlm",
                modelFileLastModified = "1700000000000",
                modelFileSize = "123456",
                backend = "NPU",
                cacheDir = "/data/user/0/io.github.ninbyo02.lami/cache",
                maxTokenBudget = "32",
                engineConfigVersion = "persistent_custom_jni_holder_poc_v1",
            ),
            holderInvalidated = "true",
            nativeHolderEntrypointAvailable = "false",
            persistentCustomJniHypothesisResult = "native_holder_entrypoint_not_available",
        )

        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(state)

        assertTrue(text.contains("[DEV診断: NPU S1 persistent custom JNI summary]"))
        assertTrue(text.contains("decode_attempt_count=7"))
        assertTrue(text.contains("decode_success_count=6"))
        assertTrue(text.contains("holder_key_model_path=/data/user/0/io.github.ninbyo02.lami/files/local_models/model.litertlm"))
        assertTrue(text.contains("holder_key_model_file_last_modified=1700000000000"))
        assertTrue(text.contains("holder_key_model_file_size=123456"))
        assertTrue(text.contains("holder_key_backend=NPU"))
        assertTrue(text.contains("holder_key_cache_dir=/data/user/0/io.github.ninbyo02.lami/cache"))
        assertTrue(text.contains("holder_key_max_token_budget=32"))
        assertTrue(text.contains("holder_key_engine_config_version=persistent_custom_jni_holder_poc_v1"))
        assertTrue(text.contains("native_holder_entrypoint_available=false"))
        assertTrue(text.contains("persistent_custom_jni_hypothesis_result=native_holder_entrypoint_not_available"))
    }

    @Test
    fun `details include requested decode and failure fields`() {
        val state = NpuS1PersistentCustomJniProbeState(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED,
            records = listOf(
                NpuS1PersistentCustomJniRunRecord(
                    runIndex = 7,
                    status = "failure",
                    reason = "adapter_failure:LiteRtLmJniException",
                    sessionCreated = "true",
                    sessionClosed = "true",
                    prefillStarted = "true",
                    prefillFinished = "true",
                    decodeStarted = "true",
                    decodeFinished = "false",
                    prefillMs = 42,
                    cleanupMs = 3,
                    failureStage = "decode",
                    failureExceptionClass = "LiteRtLmJniException",
                    failureExceptionMessage = "engine-create-failed:INTERNAL",
                    nativeDiagTail = "before EngineFactory::CreateDefault",
                ),
            ),
        )

        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(state)

        assertTrue(text.contains("[DEV診断: NPU S1 persistent custom JNI details]"))
        assertTrue(text.contains("run_index=7"))
        assertTrue(text.contains("session_created=true"))
        assertTrue(text.contains("session_closed=true"))
        assertTrue(text.contains("prefill_started=true"))
        assertTrue(text.contains("prefill_finished=true"))
        assertTrue(text.contains("decode_started=true"))
        assertTrue(text.contains("decode_finished=false"))
        assertTrue(text.contains("prefill_ms=42"))
        assertTrue(text.contains("cleanup_ms=3"))
        assertTrue(text.contains("failure_stage=decode"))
        assertTrue(text.contains("failure_exception_class=LiteRtLmJniException"))
        assertTrue(text.contains("native_diag_tail=before EngineFactory::CreateDefault"))
    }

    @Test
    fun `unavailable values are not coerced to zero or false`() {
        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(
            NpuS1PersistentCustomJniProbeState(),
        )

        assertTrue(text.contains("engine_create_count=unavailable"))
        assertTrue(text.contains("engine_close_reached=unavailable"))
        assertTrue(text.contains("holder_generation=unavailable"))
        assertTrue(text.contains("records=empty"))
    }

    @Test
    fun `append helper adds custom JNI diagnostics to existing copy`() {
        val text = appendNpuS1PersistentCustomJniDiagnosticsForDev(
            text = "base",
            state = NpuS1PersistentCustomJniProbeState(engineCreateCount = "1"),
        )

        assertTrue(text.startsWith("base"))
        assertTrue(text.contains("[DEV診断: NPU S1 persistent custom JNI summary]"))
        assertTrue(text.contains("engine_create_count=1"))
    }
}
