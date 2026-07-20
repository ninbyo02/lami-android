#!/usr/bin/env bash
# Keeps debug Stop available when the Activity observer times out while receiver work remains active.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
activity="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt"
contract="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt"
test_file="$root/app/src/test/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkUiSourceContractTest.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
for f in "$activity" "$contract" "$test_file"; do test -f "$f"; cp -a "$f" "$f.bak.$timestamp"; done
ACTIVITY="$activity" CONTRACT="$contract" TEST_FILE="$test_file" python3 - <<'PY'
import os
from pathlib import Path
files={k:Path(os.environ[k]) for k in ('ACTIVITY','CONTRACT','TEST_FILE')}
texts={k:p.read_text() for k,p in files.items()}
def once(k,old,new,tag):
 n=texts[k].count(old)
 if n!=1: raise SystemExit(f'{tag} anchor count={n}')
 texts[k]=texts[k].replace(old,new)
once('ACTIVITY','''            enabled = state.running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Stop") }
''','''            enabled = state.running || state.stage == "host_observation_timeout",
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Stop") }
''','stop enable')
once('CONTRACT','''    fun cancel(reason: String = "ui_stop_or_screen_left") {
        if (!running.get()) return
        cancelled.set(true)
''','''    fun cancel(reason: String = "ui_stop_or_screen_left") {
        cancelled.set(true)
''','cancel guard')
once('TEST_FILE','''            "killProcess",
''','''            "killProcess",
            "host_observation_timeout",
            "enabled = state.running || state.stage == \\\"host_observation_timeout\\\"",
''','late stop source contract')
for k,p in files.items(): p.write_text(texts[k])
PY
printf 'debug_token_ui_stop_after_observer_timeout=enabled\nbackups=%s,%s,%s\n' "$activity.bak.$timestamp" "$contract.bak.$timestamp" "$test_file.bak.$timestamp"
