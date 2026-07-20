#!/usr/bin/env bash
# Writes a marker at receiver-process entry for the explicit frontend cancel broadcast.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
receiver="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
test -f "$receiver"
cp -a "$receiver" "$receiver.bak.$timestamp"
RECEIVER="$receiver" python3 - <<'PY'
import os
from pathlib import Path
p=Path(os.environ['RECEIVER']); t=p.read_text()
old='''    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CANCEL) {
            cancelCurrentRun()
            return
        }
        val appContext = context.applicationContext
'''
new='''    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        if (intent.action == ACTION_CANCEL) {
            val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP)?.takeIf { it.isNotBlank() } ?: timestamp()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant(intent),
                closePolicy = closePolicy(intent),
                phase = phase(intent),
                stage = "receiver_cancel_broadcast_received",
                detail = "explicit_frontend_cancel_broadcast",
            )
            cancelCurrentRun()
            return
        }
'''
n=t.count(old)
if n!=1: raise SystemExit(f'receiver cancel entry anchor count={n}')
p.write_text(t.replace(old,new))
PY
printf 'debug_token_ui_cancel_broadcast_entry_diagnostic=enabled\nbackup=%s\n' "$receiver.bak.$timestamp"
