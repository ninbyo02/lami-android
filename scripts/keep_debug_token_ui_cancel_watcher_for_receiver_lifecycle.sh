#!/usr/bin/env bash
# Keeps the debug cancel relay watcher alive for the entire receiver lifecycle, not per benchmark case.
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
paths={k:Path(os.environ[k]) for k in ('RECEIVER','TEST_FILE')}
t={k:p.read_text() for k,p in paths.items()}
def once(k,old,new,tag):
 n=t[k].count(old)
 if n!=1: raise SystemExit(f'{tag} anchor count={n}')
 t[k]=t[k].replace(old,new)
once('RECEIVER','''        cancelRequested.set(false)

        val pendingResult = goAsync()
        receiverDispatcher.execute {
''','''        cancelRequested.set(false)
        val receiverCancelMarker: (String, String) -> Unit = { stage, detail ->
            writeMarker(appContext, timestamp, backendVariant, closePolicy, phase, stage, detail)
        }
        activeCancelMarker.set(receiverCancelMarker)
        val receiverCancelWatcher = startCancelRelayWatcher(appContext, timestamp)

        val pendingResult = goAsync()
        receiverDispatcher.execute {
''','receiver lifecycle watcher start')
once('RECEIVER','''            } finally {
                val requireProcessCleanup = closeTimeoutRequiresProcessCleanup.getAndSet(false)
                running.set(false)
                pendingResult.finish()
''','''            } finally {
                receiverCancelWatcher.cancel(true)
                activeCancelMarker.compareAndSet(receiverCancelMarker, null)
                val requireProcessCleanup = closeTimeoutRequiresProcessCleanup.getAndSet(false)
                running.set(false)
                pendingResult.finish()
''','receiver lifecycle watcher stop')
once('RECEIVER','''        activeCaseFuture.set(future)
        val cancelMarker: (String, String) -> Unit = { stage, detail ->
            writeMarker(appContext, timestamp, backendVariant, closePolicy, phase, stage, detail, maxOutputTokensList)
        }
        activeCancelMarker.set(cancelMarker)
        val cancelWatcher = startCancelRelayWatcher(appContext, timestamp)
        return try {
''','''        activeCaseFuture.set(future)
        if (cancelRequested.get()) {
            cancelCurrentRun()
        }
        return try {
''','case watcher removal')
once('RECEIVER','''        } finally {
            cancelWatcher.cancel(true)
            activeCancelMarker.set(null)
            activeCaseFuture.compareAndSet(future, null)
            executor.shutdownNow()
''','''        } finally {
            activeCaseFuture.compareAndSet(future, null)
            executor.shutdownNow()
''','case watcher final removal')
once('TEST_FILE','''            "cancel_relay_received",
            "cancel_future_requested",
''','''            "receiverCancelWatcher",
            "cancel_relay_received",
            "cancel_future_requested",
''','lifecycle watcher contract')
for k,p in paths.items(): p.write_text(t[k])
PY
printf 'debug_token_ui_receiver_lifecycle_cancel_watcher=enabled\nbackups=%s,%s\n' "$receiver.bak.$timestamp" "$test_file.bak.$timestamp"
