#!/usr/bin/env bash
# Bounds debug-only LiteRT close so a stuck GPU engine cannot retain the process indefinitely.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
receiver="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt"
test_file="$root/app/src/test/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkUiSourceContractTest.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
for f in "$receiver" "$test_file"; do test -f "$f"; cp -a "$f" "$f.bak.$timestamp"; done
RECEIVER="$receiver" TEST_FILE="$test_file" python3 - <<'PY'
import os
from pathlib import Path
files={k:Path(os.environ[k]) for k in ('RECEIVER','TEST_FILE')}
texts={k:p.read_text() for k,p in files.items()}
def once(k,old,new,tag):
 n=texts[k].count(old)
 if n!=1: raise SystemExit(f'{tag} anchor count={n}')
 texts[k]=texts[k].replace(old,new)
once('RECEIVER','''        val executor = Executors.newSingleThreadExecutor { runnable ->
''','''        closeTimedOut.set(false)
        val executor = Executors.newSingleThreadExecutor { runnable ->
''','reset close timeout')
once('RECEIVER','''            future.get(timeoutMs, TimeUnit.MILLISECONDS)
''','''            future.get(timeoutMs, TimeUnit.MILLISECONDS).let { row ->
                if (closeTimedOut.get()) {
                    row.copy(status = "failure", reason = "engine_close_timeout", timeout = true)
                } else {
                    row
                }
            }
''','terminal close timeout row')
once('RECEIVER','''            } finally {
                running.set(false)
                pendingResult.finish()
            }
''','''            } finally {
                val requireProcessCleanup = closeTimeoutRequiresProcessCleanup.getAndSet(false)
                running.set(false)
                pendingResult.finish()
                if (requireProcessCleanup) {
                    processCleanupDispatcher.execute {
                        Thread.sleep(PROCESS_CLEANUP_DELAY_MS)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }
''','post-report process cleanup')
once('RECEIVER','''        try {
            block()
            writeMarker(
''','''        try {
            val closeFuture = closeTimeoutDispatcher.submit(block)
            try {
                closeFuture.get(CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                closeFuture.cancel(true)
                closeTimedOut.set(true)
                closeTimeoutRequiresProcessCleanup.set(true)
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    stage = "close_timeout",
                    detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} target=$target timeout_ms=$CLOSE_TIMEOUT_MS",
                    maxOutputTokensList = maxOutputTokensList,
                )
                return
            }
            writeMarker(
''','bounded close')
once('RECEIVER','''        private const val DEFAULT_TIMEOUT_MS = 60_000L
''','''        private const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val CLOSE_TIMEOUT_MS = 10_000L
        private const val PROCESS_CLEANUP_DELAY_MS = 500L
''','close timeout constants')
once('RECEIVER','''        private val cancelCloseDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCancelClose")
        }
''','''        private val cancelCloseDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCancelClose")
        }
        private val closeTimeoutDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCloseTimeout")
        }
        private val processCleanupDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkProcessCleanup")
        }
        private val closeTimedOut = AtomicBoolean(false)
        private val closeTimeoutRequiresProcessCleanup = AtomicBoolean(false)
''','close timeout dispatchers')
once('TEST_FILE','''            "cancel_relay_received",
''','''            "cancel_relay_received",
            "CLOSE_TIMEOUT_MS",
            "close_timeout",
            "engine_close_timeout",
            "killProcess",
''','close timeout source contract')
for k,p in files.items(): p.write_text(texts[k])
PY
printf 'debug_token_ui_engine_close_timeout=enabled\nbackups=%s,%s\n' "$receiver.bak.$timestamp" "$test_file.bak.$timestamp"
