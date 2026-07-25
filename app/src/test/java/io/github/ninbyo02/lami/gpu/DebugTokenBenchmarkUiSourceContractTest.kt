package io.github.ninbyo02.lami.gpu

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTokenBenchmarkUiSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    private fun extractBetween(source: String, start: String, end: String): String {
        val startIndex = source.indexOf(start)
        assertTrue("missing start anchor: $start", startIndex >= 0)
        val contentStart = startIndex + start.length
        val endIndex = source.indexOf(end, contentStart)
        assertTrue("missing end anchor after $start: $end", endIndex >= contentStart)
        return source.substring(contentStart, endIndex)
    }

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
    fun `state and current marker publication use fsynced same-directory atomic replacement`() {
        val receiver = File(
            root,
            "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt",
        ).readText()
        val atomicWriter = extractBetween(
            receiver,
            "internal fun writeUtf8Atomically(",
            "\nprivate fun writeMarker(",
        )
        val stateWriter = extractBetween(receiver, "private fun writeState(", "\nprivate fun writeMarker(")
        val markerWriter = extractBetween(receiver, "private fun writeMarker(", "\nprivate fun sanitizeOutput(")
        val reportWriter = extractBetween(receiver, "internal fun writeReports(", "\ninternal fun buildGpuBenchmarkMarkdown(")

        assertTrue("atomic writer must create its temp beside the target", "target.parentFile" in atomicWriter)
        assertTrue("atomic writer must fsync complete UTF-8 bytes before publication", ".fd.sync()" in atomicWriter)
        assertTrue("publication must require an atomic rename", "StandardCopyOption.ATOMIC_MOVE" in atomicWriter)
        assertTrue("publication must replace the complete previous file", "StandardCopyOption.REPLACE_EXISTING" in atomicWriter)
        assertTrue("atomic rename durability must fsync the parent directory", "FileChannel.open(parent.toPath(), StandardOpenOption.READ)" in atomicWriter)
        assertTrue("parent directory sync must force metadata after rename", ".force(true)" in atomicWriter)
        assertTrue("state publication must use the atomic writer", "writeUtf8Atomically(stateFile" in stateWriter)
        assertTrue("current marker publication must use the atomic writer", "writeUtf8Atomically(" in markerWriter)
        assertTrue("report publication must prepare complete markdown before touching either final path", "val markdown = buildGpuBenchmarkMarkdown" in reportWriter)
        assertTrue("report publication must prepare complete CSV before touching either final path", "val csv = buildGpuBenchmarkCsv" in reportWriter)
        assertEquals(2, Regex("writeUtf8Atomically\\(").findAll(reportWriter).count())
        assertTrue(
            "both complete report payloads must exist before the first atomic publication",
            reportWriter.indexOf("val csv = buildGpuBenchmarkCsv") in 0 until reportWriter.indexOf("writeUtf8Atomically("),
        )
        assertFalse("state publication must not truncate the observer-visible path", "stateFile.writeText" in stateWriter)
        assertFalse("current marker publication must not truncate the observer-visible path", "MARKER_FILE_NAME).writeText" in markerWriter)
        assertFalse("terminal reports must not truncate observer-visible paths", ".writeText(" in reportWriter)
    }

    @Test
    fun `pre-dispatch timeout publishes coherent artifacts atomically`() {
        val contract = File(
            root,
            "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt",
        ).readText()
        val timeoutPublisher = extractBetween(
            contract,
            "private fun publishPreDispatchTimeout(",
            "\n    companion object {",
        )
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()

        assertEquals(3, Regex("writeUtf8Atomically\\(").findAll(timeoutPublisher).count())
        assertFalse("pre-dispatch artifacts must never truncate observer-visible files", ".writeText(" in timeoutPublisher)
        assertTrue("timeout CSV must bind its row to the run timestamp", "timestamp,status,reason" in timeoutPublisher)
        assertTrue("timeout is exclusive from ordinary failures", "failure_count=0\\ntimeout_count=1" in timeoutPublisher)
        assertTrue("host acceptance must validate downloaded artifact contents", "debug_token_validate_terminal_artifacts" in controller)
    }

    @Test
    fun `fixed UI runner verifies Android 16 resumed component with the debug activity`() {
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()
        val fixedUiRunner = extractBetween(
            source = controller,
            start = "run_debug_token_ui_case() (",
            end = "read_debug_token_ui_artifact() {",
        )
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
            "24576, 28800, 30400, 32768, 32769",
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
        assertTrue("receiver must allow exactly 28800", Regex("24576, 28800, 30400").containsMatchIn(receiver))
        assertTrue("host runner must allow fixed 28.8k case", "gpu-long-28800" in controller)
        assertFalse("28.8k profile must not expose arbitrary prompt input", "OutlinedTextField" in contract)
    }

    @Test
    fun `fixed 30400 long context profile is wired end to end`() {
        val contract = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt").readText()
        val activity = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt").readText()
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()
        val receiverAllowlist = receiver.lineSequence().single {
            it.trimStart().startsWith("private val GPU_TOKEN_PROBE_MAX_OUTPUT_TOKENS_ALLOWLIST = setOf(")
        }
        val runtimeAllowlist = controller.lineSequence().single {
            it.trimStart().startsWith("case \"${'$'}case_name\" in gpu16|")
        }
        val labelMappingLine = controller.lineSequence().zipWithNext().single { (caseLine, mappingLine) ->
            caseLine.trim() == "case \"${'$'}case_name\" in" &&
                mappingLine.trimStart().startsWith("gpu16) label=\"GPU 16\" ;;")
        }.second
        val helpLine = controller.lineSequence().single {
            it.trimStart().startsWith("debug-token-ui-run <192.168.52.52> <port> <")
        }

        assertTrue(
            "missing exact fixed 30.4k enum contract",
            "GPU_LONG_CONTEXT_30400(\"GPU long context 30400\", \"gpu\", 30400, true)" in contract,
        )
        assertTrue(
            "30.4k foreground button must be disabled while running and start the exact case",
            Regex(
                """FixedCaseButton\(\s*label = "GPU long context 30400",\s*enabled = !state\.running,\s*onClick = \{ coordinator\.start\(DebugTokenBenchmarkCase\.GPU_LONG_CONTEXT_30400\) \},\s*\)""",
            ).containsMatchIn(activity),
        )
        assertTrue(
            "receiver fixed-token allowlist must include exactly positioned 30.4k case",
            "28800, 30400, 32768" in receiverAllowlist,
        )
        assertTrue(
            "host runner runtime allowlist must include fixed 30.4k case",
            "gpu-long-28800|gpu-long-30400|gpu-long-32768" in runtimeAllowlist &&
                runtimeAllowlist.endsWith(") ;; *) fail ;; esac"),
        )
        assertTrue(
            "host runner must map fixed 30.4k case to its exact UI label",
            "gpu-long-30400) label=\"GPU long context 30400\"" in labelMappingLine,
        )
        assertTrue(
            "host runner help must advertise fixed 30.4k case",
            "gpu-long-28800|gpu-long-30400|gpu-long-32768" in helpLine &&
                helpLine.endsWith("> # fixed foreground UI"),
        )
        assertFalse("30.4k profile must not expose arbitrary prompt input", "OutlinedTextField" in contract)
    }

    @Test
    fun `wireless adb benchmark helpers require the passed NX733J endpoint without stale port literals`() {
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()
        val helperStart = "single_nx733j_serial() {"
        val helperEnd = "force_stop_debug_token_ui_benchmark() ("
        assertTrue("shared one-device helper must exist", helperStart in controller)
        assertTrue("force-stop helper boundary must exist", helperEnd in controller)
        val helper = extractBetween(controller, helperStart, helperEnd)
        assertTrue("one-device helper must invoke the direct stateful gate", "debug_token_single_nx733j_device_gate" in helper)
        assertTrue("one-device helper must consume the validated side-channel serial", "NX733J_SERIAL=\"${'$'}DEBUG_TOKEN_NX733J_SERIAL\"" in helper)
        assertFalse("one-device helper must not be consumed through command substitution", "${'$'}(single_nx733j_serial" in controller)
        listOf(
            "force_stop_debug_token_ui_benchmark()",
            "stop_debug_token_ui_benchmark()",
            "read_debug_token_ui_live_state()",
        ).forEach { functionName ->
            val functionStart = "$functionName ("
            assertTrue("$functionName must exist", functionStart in controller)
            val body = extractBetween(controller, functionStart, "\n)")
            assertTrue("$functionName must use the passed endpoint gate", "nx733j_serial_for_endpoint" in body)
            assertFalse("$functionName must not pin a rotating wireless adb port", Regex("192\\.168\\.52\\.52:[0-9]+").containsMatchIn(body))
        }
    }

    @Test
    fun `30400 boundary matrix and typed callback comparison are wired end to end`() {
        val contract = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt").readText()
        val activity = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt").readText()
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()
        val fixedCases = listOf(
            "gpu-long-30400-r80" to "GPU long 30400 ratio 80%",
            "gpu-long-30400-r825" to "GPU long 30400 ratio 82.5%",
            "gpu-long-30400-r85" to "GPU long 30400 ratio 85%",
            "gpu-long-30400-payload-22400" to "GPU 30400 payload-equivalent 22400",
            "gpu-long-30400-payload-28800" to "GPU 30400 payload-equivalent 28800",
            "gpu-long-32768-payload-30400-r85" to "GPU 32768 same bytes as 30400 ratio 85%",
            "gpu-long-30400-flow-compare" to "GPU 30400 Flow comparison",
            "gpu-long-30400-typed-callback-compare" to "GPU 30400 typed callback comparison",
        )
        fixedCases.forEach { (wire, label) ->
            assertTrue("missing case wire name $wire", wire in controller)
            assertTrue("missing exact UI label $label", label in activity && label in controller)
        }
        listOf(
            "payloadBasisTokens",
            "payloadRatioPermille",
            "sendApiMode",
            "EXTRA_SEND_API_MODE",
            "typed_contents_callback",
            "flow_string",
        ).forEach { required -> assertTrue("missing comparison contract $required", required in contract || required in receiver) }
        assertFalse("fixed matrix must not expose arbitrary input", "OutlinedTextField" in activity)
    }

    @Test
    fun `measurement lifecycle and bounded observer evidence are explicit`() {
        val activity = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt").readText()
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()
        listOf("onStart", "onResume", "onPause", "onStop", "screen_left").forEach {
            assertTrue("missing Activity lifecycle evidence $it", it in activity)
        }
        listOf(
            "lastPrefillTokenCount",
            "measuredPrefillTokens",
            "emitCount",
            "nonemptyEmitCount",
            "firstNonemptyMs",
            "rawLength",
            "sanitizedLength",
            "finishReasonAvailable",
            "stopReasonAvailable",
            "callbackOnMessageCount",
            "callbackOnDoneCount",
            "callbackOnErrorCount",
            "chunkTypeLengthSummary",
        ).forEach { assertTrue("missing receiver measurement $it", it in receiver) }
        listOf(
            "debug-token-ui-observe",
            "observer_max_seconds",
            "600",
            "topResumedActivity",
            "dumpsys power",
            "dumpsys thermalservice",
            "dumpsys meminfo",
            "marker_running_no_rerun",
            "timestamp_matched_terminal",
        ).forEach { assertTrue("missing bounded host observer evidence $it", it in controller) }
        assertTrue("observer must classify an active timestamp-matched running state", "debug_token_observer_state_class" in controller)
        assertTrue("observer must fail closed unless classified running", "state_class\" == \"running" in controller)
        assertTrue("observer must use one millisecond monotonic deadline", "observer_deadline_ms=" in controller && "debug_token_monotonic_ms" in controller && "observer_max_seconds * 1000" in controller)
        assertFalse("observer must not mix an undefined wall-clock deadline", "observer_deadline_epoch" in controller)
        assertTrue("observer must capture the initial PID", "initial_pid=" in controller)
        assertTrue("observer must fail closed on main/benchmark PID replacement or foreground loss", "debug_token_observer_dual_running_process_gate" in controller)
        assertTrue("observer must record terminal holder cleanup", "conversation_close_finished=" in controller && "engine_close_finished=" in controller)
        assertTrue("terminal observer must wait for holder cleanup or benchmark PID disappearance", "debug_token_observer_dual_terminal_cleanup_gate" in controller)
        assertTrue("normal UI runner must bound every ADB call to one monotonic deadline", "run_debug_token_ui_case_bounded_adb" in controller)
        assertTrue("normal UI runner must fail closed on foreground loss while running", "debug_token_observer_dual_running_process_gate \"${'$'}pid_before\" \"${'$'}pid_after\" \"${'$'}benchmark_pid_before\" \"${'$'}benchmark_pid_after\" \"${'$'}resumed\" \"${'$'}debug_component\"" in controller)
        assertTrue("receiver and foreground UI must share the 600 second timeout ceiling", ".coerceIn(1_000L, 600_000L)" in receiver)
        assertTrue("timed-out native workers must keep the run gate closed until process cleanup", "case_timeout_process_cleanup_required" in receiver && "if (!requireProcessCleanup) running.set(false)" in receiver)
        assertTrue("failure rows must persist partial Flow output", "flowPartialRawOutput = failedObservation?.flowPartialRawOutput.orEmpty()" in receiver)
        assertTrue("failure rows must persist partial Flow emit counts", "flowPartialEmitCount = failedObservation?.flowPartialEmitCount ?: 0" in receiver)
        assertTrue("failure rows must persist partial Flow timing", "flowPartialFirstNonemptyMs = failedObservation?.flowPartialFirstNonemptyMs" in receiver)
        assertTrue("prefill evidence key must use the specified durable name", "measured_prefill_tokens" in receiver)
        assertTrue("callback evidence must include runtime chunk type", "chunkType" in receiver)
        listOf(
            "stop_debug_token_ui_benchmark",
            "force_stop_debug_token_ui_benchmark",
            "read_debug_token_ui_live_state",
            "observe_debug_token_ui_benchmark",
        ).forEach { functionName ->
            val functionStart = "\n$functionName() "
            val startIndex = controller.indexOf(functionStart)
            assertTrue("$functionName must exist as a top-level function", startIndex >= 0)
            val body = controller.substring(startIndex, minOf(controller.length, startIndex + 5_000))
            assertTrue("$functionName must accept the current host and port", "local host=\"${'$'}1\" port=\"${'$'}2\"" in body)
            assertTrue("$functionName must use the passed endpoint", "nx733j_serial_for_endpoint \"${'$'}host\" \"${'$'}port\"" in body)
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
            "rethrowCancellationOrInterrupt(streamingThrowable)",
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

    @Test
    fun `foreground run owns one exact deadline and resource close failures are terminal`() {
        val contract = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt").readText()
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        val controller = File(root, "scripts/lami_build_remote_control_full.sh").readText()

        assertTrue("foreground coordinator must define one exact run deadline", "CASE_DEADLINE_MS = 600_000L" in contract)
        assertTrue("dispatch must receive the absolute elapsed-realtime deadline", "dispatch(case, timestamp, deadlineElapsedRealtime)" in contract)
        assertTrue("receiver timeout must be the remaining shared budget", "remainingRunBudgetMs(deadlineElapsedRealtime)" in contract)
        assertTrue("dispatch must forward the same monotonic deadline to the receiver", "EXTRA_RUN_DEADLINE_ELAPSED_REALTIME" in contract)
        assertTrue("pre-launch expiry must publish a timestamp-matched timeout report", "publishPreDispatchTimeout" in contract)
        assertTrue("terminal observer must use the same absolute deadline", "awaitTerminal(case, timestamp, started, deadlineElapsedRealtime)" in contract)
        assertTrue("terminal observer must read state before classifying deadline expiry", "while (true)" in contract && "remainingBeforePollMs" in contract)
        assertFalse("deadline must not include an unowned grace period", "CASE_TIMEOUT_MS + 15_000L" in contract)
        assertTrue("receiver must consume the absolute monotonic deadline", "deadlineElapsedRealtime(intent)" in receiver)
        assertTrue("receiver must reserve bounded time for report and terminal publication", "TERMINAL_PUBLISH_RESERVE_MS" in receiver)
        assertTrue("case wait must use the current remaining deadline budget", "future.get(caseBudgetMs" in receiver)
        assertTrue("close cancellation must remain cancellation", "if (closeCause is CancellationException) throw closeCause" in receiver)
        assertTrue("close interruption must terminalize before restoring the interrupt flag", "closeCause is InterruptedException" in receiver && "Thread.currentThread().interrupt()" in receiver)
        assertFalse("close interruption must not bypass terminal publication", "rethrowCancellationOrInterrupt(closeCause)" in receiver)
        assertTrue("host timeout must use monotonic milliseconds", "debug_token_monotonic_ms" in controller)
        assertTrue("lifecycle failures must be classified explicitly", "failure_class=harness_lifecycle_failure" in controller)
        assertTrue("successful runs must require terminal artifacts", "terminal_artifact_missing_or_empty" in controller)
        assertFalse("host deadline must not use second-granularity SECONDS", "observer_deadline_seconds=" in controller)
        assertFalse("case wait must not restart the full relative timeout", "future.get(timeoutMs" in receiver)

        assertTrue("close failures must be tracked independently from close timeouts", "closeFailureReason" in receiver)
        assertTrue("close failure must require benchmark-process cleanup", "resource_close_exception_process_cleanup_required" in receiver)
        assertTrue("a close failure must override an otherwise successful row", "reason = closeFailure" in receiver)
        assertTrue("interrupted close must terminalize instead of bypassing reports", "closeCause is InterruptedException" in receiver)
        assertTrue("coroutine cancellation must remain distinct from close interruption", "closeCause is CancellationException" in receiver)
        assertTrue("host lifecycle defects must use the required classification", "failure_class=harness_lifecycle_failure" in controller)
        assertTrue("named terminal reports must exist and be nonempty", "terminal_artifact_missing_or_empty" in controller)
        assertTrue("foreground coordinator must require non-empty Markdown as well as CSV", "markdownFile?.isFile == true && markdownFile.length() > 0L" in contract)
        assertTrue("foreground coordinator must bind both artifact names to the exact run timestamp", "litert_lm_gpu_benchmark_${'$'}timestamp.csv" in contract && "litert_lm_gpu_benchmark_${'$'}timestamp.md" in contract)
        assertTrue("foreground coordinator must bind the CSV row to the exact run timestamp", "row[\"timestamp\"] == timestamp" in contract)
        assertTrue("foreground coordinator must bind Markdown content to the exact run timestamp", "- timestamp: `${'$'}timestamp`" in contract)
        assertTrue("foreground coordinator must consume output-token provenance from the CSV", "row[\"output_token_source\"]" in contract)
        assertTrue("foreground coordinator must require exact benchmark output-token provenance", "LiteRT benchmarkInfo.lastDecodeTokenCount" in contract)
        assertTrue("foreground runner terminal matching must use an exact timestamp field", "grep -Fxq \"timestamp=${'$'}timestamp\"" in controller)
        assertTrue("foreground runner must recheck the benchmark PID after artifact collection", "benchmark_pid_final" in controller && "benchmark_process_stable" in controller)
        assertTrue("foreground runner must not accept main-process disappearance after success", "\"${'$'}process_stable\" == true" in controller && "\"${'$'}benchmark_process_stable\" == true" in controller)
        assertTrue("foreground success must use the strict two-process close gate", "debug_token_observer_success_process_gate" in controller)
        assertTrue("host deadline must use a monotonic millisecond clock", "debug_token_monotonic_ms" in controller)
        assertFalse("host deadline must not use coarse Bash SECONDS", "observer_deadline_seconds=\"${'$'}((SECONDS + observer_max_seconds))\"" in controller)
    }

}
