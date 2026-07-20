#!/usr/bin/env bash
# Uses the receiver's already-registered ACTION with a fixed cancel command extra.
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
once('RECEIVER','''        if (intent.action == ACTION_CANCEL) {
''','''        if (intent.getBooleanExtra(EXTRA_COMMAND_CANCEL, false)) {
''','receiver cancel command')
once('RECEIVER','''        const val ACTION_CANCEL = "io.github.ninbyo02.lami.action.CANCEL_LITERT_LM_GPU_BENCHMARK"
''','''        const val EXTRA_COMMAND_CANCEL = "command_cancel"
''','receiver cancel extra constant')
once('CONTRACT','''            Intent(LiteRtLmGpuBenchmarkReceiver.ACTION_CANCEL).apply {
                component = ComponentName(appContext, LiteRtLmGpuBenchmarkReceiver::class.java)
                setPackage(appContext.packageName)
''','''            Intent(LiteRtLmGpuBenchmarkReceiver.ACTION).apply {
                component = ComponentName(appContext, LiteRtLmGpuBenchmarkReceiver::class.java)
                setPackage(appContext.packageName)
                putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_COMMAND_CANCEL, true)
''','coordinator registered action cancel extra')
once('TEST_FILE','''            "ACTION_CANCEL",
''','''            "EXTRA_COMMAND_CANCEL",
''','cancel extra source contract')
for k,p in files.items(): p.write_text(texts[k])
PY
printf 'debug_token_ui_stop_registered_action=enabled\nbackups=%s,%s,%s\n' "$receiver.bak.$timestamp" "$contract.bak.$timestamp" "$test_file.bak.$timestamp"
