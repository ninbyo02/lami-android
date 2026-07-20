#!/usr/bin/env bash
# Routes debug frontend Stop through a timestamped app-private relay file watched by the receiver process.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
receiver="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt"
contract="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt"
test_file="$root/app/src/test/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkUiSourceContractTest.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
for f in "$receiver" "$contract" "$test_file"; do test -f "$f"; cp -a "$f" "$f.bak.$timestamp"; done
RECEIVER="$receiver" CONTRACT="$contract" TEST_FILE="$test_file" python3 - <<'PY'
import os
from pathlib import Path
files={k:Path(os.environ[k]) for k in ('RECEIVER','CONTRACT','TEST_FILE')}
texts={k:p.read_text() for k,p in files.items()}
def once(k,old,new,tag):
 n=texts[k].count(old)
 if n!=1: raise SystemExit(f'{tag} anchor count={n}')
 texts[k]=texts[k].replace(old,new)
once('CONTRACT','''        appContext.sendBroadcast(
            Intent(LiteRtLmGpuBenchmarkReceiver.ACTION).apply {
                component = ComponentName(appContext, LiteRtLmGpuBenchmarkReceiver::class.java)
                setPackage(appContext.packageName)
                putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_COMMAND_CANCEL, true)
            },
        )
        mutableState.value.timestamp?.let { timestamp ->
''','''        mutableState.value.timestamp?.let { timestamp ->
            File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.CANCEL_RELAY_FILE_NAME)
                .writeText(timestamp, Charsets.UTF_8)
''','coordinator relay write')
once('RECEIVER','''        activeCaseFuture.set(future)
        val cancelMarker: (String, String) -> Unit = { stage, detail ->
            writeMarker(appContext, timestamp, backendVariant, closePolicy, phase, stage, detail, maxOutputTokensList)
        }
        activeCancelMarker.set(cancelMarker)
        return try {
''','''        activeCaseFuture.set(future)
        val cancelMarker: (String, String) -> Unit = { stage, detail ->
            writeMarker(appContext, timestamp, backendVariant, closePolicy, phase, stage, detail, maxOutputTokensList)
        }
        activeCancelMarker.set(cancelMarker)
        val cancelWatcher = startCancelRelayWatcher(appContext, timestamp)
        return try {
''','start relay watcher')
once('RECEIVER','''            activeCaseFuture.compareAndSet(future, null)
            activeCancelMarker.compareAndSet(cancelMarker, null)
            executor.shutdownNow()
''','''            cancelWatcher.cancel(true)
            activeCaseFuture.compareAndSet(future, null)
            activeCancelMarker.compareAndSet(cancelMarker, null)
            executor.shutdownNow()
''','stop relay watcher')
once('RECEIVER','''        const val STATE_FILE_NAME = "litert_lm_gpu_benchmark_state.txt"
''','''        const val STATE_FILE_NAME = "litert_lm_gpu_benchmark_state.txt"
        const val CANCEL_RELAY_FILE_NAME = "litert_lm_gpu_benchmark_cancel.txt"
''','cancel relay constant')
once('RECEIVER','''        private val cancelCloseDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCancelClose")
        }

        /** Cooperative cancellation used only by the debug foreground UI. */
''','''        private val cancelCloseDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCancelClose")
        }
        private val cancelRelayDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCancelRelay")
        }

        private fun startCancelRelayWatcher(appContext: Context, timestamp: String): Future<*> =
            cancelRelayDispatcher.submit {
                val relay = File(appContext.filesDir, CANCEL_RELAY_FILE_NAME)
                while (!Thread.currentThread().isInterrupted && running.get()) {
                    if (runCatching { relay.readText(Charsets.UTF_8).trim() }.getOrDefault("") == timestamp) {
                        activeCancelMarker.get()?.invoke("cancel_relay_received", "timestamp_matched=true")
                        cancelCurrentRun()
                        return@submit
                    }
                    Thread.sleep(50L)
                }
            }

        /** Cooperative cancellation used only by the debug foreground UI. */
''','relay watcher')
once('TEST_FILE','''            "EXTRA_COMMAND_CANCEL",
''','''            "CANCEL_RELAY_FILE_NAME",
            "startCancelRelayWatcher",
            "cancel_relay_received",
''','relay source contract')
for k,p in files.items(): p.write_text(texts[k])
PY
printf 'debug_token_ui_stop_cancel_relay=enabled\nbackups=%s,%s,%s\n' "$receiver.bak.$timestamp" "$contract.bak.$timestamp" "$test_file.bak.$timestamp"
