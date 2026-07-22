package io.github.ninbyo02.lami.gpu

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTokenBenchmarkUiSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `debug foreground UI benchmark is fixed fail closed and release invisible`() {
        val activity = File(
            root,
            "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt",
        )
        assertTrue("debug-only foreground benchmark Activity must exist", activity.isFile)
        val source = activity.readText()
        listOf(
            "GPU 16",
            "GPU 32",
            "GPU 128",
            "GPU 512",
            "CPU 32",
            "total-context",
            "generic_fallback",
            "send-message",
            "normal",
            "Stop",
            "already_running",
            "output_tokens",
            "artifact",
            "elapsed",
        ).forEach { required -> assertTrue("missing fixed UI contract: $required", required in source) }
        listOf("OutlinedTextField", "EXTRA_PROMPTS_BASE64", "EXTRA_MODEL_PATH", "Runtime.getRuntime", "ProcessBuilder")
            .forEach { forbidden -> assertFalse("arbitrary input/shell forbidden: $forbidden", forbidden in source) }

        val manifest = File(root, "app/src/debug/AndroidManifest.xml").readText()
        assertTrue("DebugTokenBenchmarkActivity" in manifest)
        assertTrue(Regex("DebugTokenBenchmarkActivity[\\s\\S]{0,300}android:exported=\"true\"").containsMatchIn(manifest))
        assertFalse(File(root, "app/src/main/AndroidManifest.xml").readText().contains("DebugTokenBenchmarkActivity"))
    }

    @Test
    fun `coordinator has fixed cases serialization cancellation and monotonic gates`() {
        val contractFile = File(
            root,
            "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt",
        )
        assertTrue("debug-only coordinator contract must exist", contractFile.isFile)
        val source = contractFile.readText()
        listOf(
            "GPU_32",
            "GPU_128",
            "GPU_512",
            "CPU_32",
            "AtomicBoolean",
            "already_running",
            "cancel",
            "gpu32Passed",
            "gpu128Passed",
            "timeout",
            "fallback",
            "freshCrash",
            "outputTokens",
            "effectiveTokens",
            "finishEvidence",
            "getValidLocalGenericModelPathOrNull",
            "GENERIC_MODEL_PATH_RELAY_FILE_NAME",
        ).forEach { required -> assertTrue("missing coordinator contract: $required", required in source) }
    }

    @Test
    fun `fixed UI runner verifies Android 16 resumed component with the debug activity`() {
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()
        val fixedUiRunner = controller.substringAfter("run_debug_token_ui_case() {").substringBefore("read_debug_token_ui_artifact() {")
        assertTrue("must use Android 16 top-resumed signal", "topResumedActivity" in controller)
        assertTrue("runner must define the canonical fully-qualified MainActivity component", "main_component" in fixedUiRunner)
        assertFalse("suffix APK uses a fully-qualified component, never package-relative MainActivity", ("$" + "package/.MainActivity") in fixedUiRunner)
        assertTrue(
            "must verify the debug benchmark Activity after launch",
            Regex("debug_component[\\s\\S]{0,1800}topResumedActivity[\\s\\S]{0,1000}DebugTokenBenchmarkActivity")
                .containsMatchIn(controller),
        )
        assertFalse("legacy-only mResumedActivity probe is insufficient", "grep -m1 'mResumedActivity'" in controller)
    }

    @Test
    fun `fixed UI runner launches the GPU no-constraint debug package directly from adb shell`() {
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()
        assertTrue(
            "foreground GPU benchmark must target its isolated no-constraint appId",
            "package=\"io.github.ninbyo02.lami.gpunoconstraint\"" in controller,
        )
        assertTrue(
            "exported debug Activity must be launched by adb shell, not run-as",
            Regex("adb -s .* shell am start -W -n").containsMatchIn(controller),
        )
        assertFalse("run-as am start fails Android 16 caller-UID validation", Regex("run-as .* am start").containsMatchIn(controller))
    }
    @Test
    fun `long context benchmark contract requires fixed 32k boundary evidence`() {
        val contract = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt").readText()
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        listOf(
            "GPU_LONG_CONTEXT_2048",
            "GPU_LONG_CONTEXT_8192",
            "GPU_LONG_CONTEXT_16384",
            "GPU_LONG_CONTEXT_24576",
            "GPU_LONG_CONTEXT_32768",
            "GPU_LONG_CONTEXT_32769",
            "24576, 28800, 32768, 32769",
            "LongContext",
            "EXTRA_SINGLE_PROMPT",
            "actual_input_tokens",
            "context_boundary",
        ).forEach { required ->
            assertTrue("missing fixed long-context contract: $required", contract.contains(required) || receiver.contains(required))
        }
        assertFalse("long-context benchmark must not expose arbitrary prompt input", contract.contains("OutlinedTextField"))
    }

    @Test
    fun `fixed 22400 long context profile is wired end to end`() {
        val contract = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt").readText()
        val activity = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt").readText()
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()

        assertTrue("missing fixed 22.4k enum", "GPU_LONG_CONTEXT_22400" in contract)
        assertTrue("missing fixed 22.4k token value", "22400" in contract)
        assertTrue("missing fixed 22.4k foreground button", "GPU long context 22400" in activity)
        assertTrue("receiver must allow exactly 22400", Regex("16384, 22400, 24576").containsMatchIn(receiver))
        assertTrue("host runner must allow fixed 22.4k case", "gpu-long-22400" in controller)
        assertFalse("22.4k profile must not expose arbitrary prompt input", "OutlinedTextField" in contract)
    }

    @Test
    fun `fixed 28800 long context profile is wired end to end`() {
        val contract = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt").readText()
        val activity = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt").readText()
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()

        assertTrue("missing fixed 28.8k enum", "GPU_LONG_CONTEXT_28800" in contract)
        assertTrue("missing fixed 28.8k token value", "28800" in contract)
        assertTrue("missing fixed 28.8k foreground button", "GPU long context 28800" in activity)
        assertTrue("receiver must allow exactly 28800", Regex("24576, 28800, 32768").containsMatchIn(receiver))
        assertTrue("host runner must allow fixed 28.8k case", "gpu-long-28800" in controller)
        assertFalse("28.8k profile must not expose arbitrary prompt input", "OutlinedTextField" in contract)
    }

    @Test
    fun `wireless adb benchmark helpers select one NX733J without stale port literals`() {
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()
        val helperStart = "single_nx733j_serial() {"
        val helperEnd = "force_stop_debug_token_ui_benchmark() {"
        assertTrue("shared one-device helper must exist", helperStart in controller)
        assertTrue("force-stop helper boundary must exist", helperEnd in controller)
        val helper = controller
            .substringAfter(helperStart)
            .substringBefore(helperEnd)
        assertTrue("one-device helper must enumerate connected adb devices", "adb devices" in helper)
        assertTrue(
            "one-device helper must require exactly one connected device",
            "[[ \"${'$'}connected_count\" == \"1\" ]]" in helper,
        )
        assertTrue("one-device helper must fail closed", "return 65" in helper)
        assertTrue("one-device helper must verify NX733J", "NX733J" in helper)
        listOf(
            "force_stop_debug_token_ui_benchmark()",
            "stop_debug_token_ui_benchmark()",
            "read_debug_token_ui_live_state()",
        ).forEach { functionName ->
            val functionStart = "$functionName {"
            assertTrue("$functionName must exist", functionStart in controller)
            val body = controller.substringAfter(functionStart).substringBefore("\n}")
            assertTrue("$functionName must use the shared device gate", "single_nx733j_serial" in body)
            assertFalse("$functionName must not pin a rotating wireless adb port", Regex("192\\.168\\.52\\.52:[0-9]+").containsMatchIn(body))
        }
    }

    @Test
    fun `frontend Stop contract cancels active native benchmark and reports terminal cancellation`() {
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        val activity = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt").readText()
        listOf(
            "activeCaseFuture",
            "activeConversation",
            "activeEngine",
            "cancelled_by_debug_foreground_ui",
            "cancelRequested.set(false)",
            "CANCEL_RELAY_FILE_NAME",
            "EXTRA_COMMAND_CANCEL",
            "receiver_cancel_broadcast_received",
            "startCancelRelayWatcher",
            "receiverCancelWatcher",
            "cancel_relay_received",
            "streamingThrowable is CancellationException",
            "cancel_process_cleanup_scheduled",
            "CANCEL_PROCESS_CLEANUP_DELAY_MS",
            "CLOSE_TIMEOUT_MS",
            "close_timeout",
            "engine_close_timeout",
            "killProcess",
            "host_observation_timeout",
            "enabled = state.running || state.stage == \"host_observation_timeout\"",
        ).forEach { required ->
            assertTrue("missing frontend Stop cancellation contract: $required", receiver.contains(required) || activity.contains(required))
        }
    }

}
