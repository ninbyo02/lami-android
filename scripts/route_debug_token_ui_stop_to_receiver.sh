#!/usr/bin/env bash
# Routes debug foreground Stop directly to the already-running benchmark receiver; keeps file relay as fallback.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
contract="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt"
test_file="$root/app/src/test/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkUiSourceContractTest.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
for f in "$contract" "$test_file"; do test -f "$f"; cp -a "$f" "$f.bak.$timestamp"; done
CONTRACT="$contract" TEST_FILE="$test_file" python3 - <<'PY'
import os
from pathlib import Path
paths={k:Path(os.environ[k]) for k in ('CONTRACT','TEST_FILE')}
t={k:p.read_text() for k,p in paths.items()}
def once(k,old,new,tag):
 n=t[k].count(old)
 if n!=1: raise SystemExit(f'{tag} anchor count={n}')
 t[k]=t[k].replace(old,new)
once('CONTRACT','''            File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.CANCEL_RELAY_FILE_NAME)
                .writeText(timestamp, Charsets.UTF_8)
            mutableState.value.currentCase?.let { case -> writeUiMarker(timestamp, case, "ui_cancel_requested", reason) }
''','''            File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.CANCEL_RELAY_FILE_NAME)
                .writeText(timestamp, Charsets.UTF_8)
            val cancelIntent = Intent(LiteRtLmGpuBenchmarkReceiver.ACTION).apply {
                component = ComponentName(appContext, LiteRtLmGpuBenchmarkReceiver::class.java)
                setPackage(appContext.packageName)
                putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_COMMAND_CANCEL, true)
                putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMESTAMP, timestamp)
            }
            appContext.sendBroadcast(cancelIntent)
            mutableState.value.currentCase?.let { case -> writeUiMarker(timestamp, case, "ui_cancel_requested", reason) }
''','explicit cancel broadcast')
once('TEST_FILE','''            "CANCEL_RELAY_FILE_NAME",
            "startCancelRelayWatcher",
''','''            "CANCEL_RELAY_FILE_NAME",
            "EXTRA_COMMAND_CANCEL",
            "receiver_cancel_broadcast_received",
            "startCancelRelayWatcher",
''','explicit cancel contract')
for k,p in paths.items(): p.write_text(t[k])
PY
printf 'debug_token_ui_stop_receiver_broadcast=enabled\nbackups=%s,%s\n' "$contract.bak.$timestamp" "$test_file.bak.$timestamp"
