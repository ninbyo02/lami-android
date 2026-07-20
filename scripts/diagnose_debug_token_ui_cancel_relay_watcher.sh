#!/usr/bin/env bash
# Adds one-shot receiver-side diagnostics for the debug cancel relay watcher.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
receiver="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
test -f "$receiver"; cp -a "$receiver" "$receiver.bak.$timestamp"
RECEIVER="$receiver" python3 - <<'PY'
import os
from pathlib import Path
p=Path(os.environ['RECEIVER']); t=p.read_text()
old='''        private fun startCancelRelayWatcher(appContext: Context, timestamp: String): Future<*> =
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
'''
new='''        private fun startCancelRelayWatcher(appContext: Context, timestamp: String): Future<*> {
            activeCancelMarker.get()?.invoke("cancel_relay_watcher_started", "timestamp=$timestamp")
            return cancelRelayDispatcher.submit {
                val relay = File(appContext.filesDir, CANCEL_RELAY_FILE_NAME)
                var observedRelay = ""
                while (!Thread.currentThread().isInterrupted && running.get()) {
                    val requested = runCatching { relay.readText(Charsets.UTF_8).trim() }.getOrDefault("")
                    if (requested.isNotBlank() && requested != observedRelay) {
                        observedRelay = requested
                        activeCancelMarker.get()?.invoke(
                            "cancel_relay_observed",
                            "timestamp_matched=${requested == timestamp}",
                        )
                    }
                    if (requested == timestamp) {
                        activeCancelMarker.get()?.invoke("cancel_relay_received", "timestamp_matched=true")
                        cancelCurrentRun()
                        return@submit
                    }
                    Thread.sleep(50L)
                }
            }
        }
'''
n=t.count(old)
if n!=1: raise SystemExit(f'cancel relay watcher anchor count={n}')
p.write_text(t.replace(old,new))
PY
printf 'debug_token_ui_cancel_relay_watcher_diagnostic=enabled\nbackup=%s\n' "$receiver.bak.$timestamp"
