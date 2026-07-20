#!/usr/bin/env bash
# Routes frontend cancellation through a fixed explicit broadcast to the receiver process.
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
once('RECEIVER','''    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
''','''    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CANCEL) {
            cancelCurrentRun()
            return
        }
        val appContext = context.applicationContext
''','receiver cancel action')
once('RECEIVER','''        const val ACTION = "io.github.ninbyo02.lami.action.RUN_LITERT_LM_GPU_BENCHMARK"
''','''        const val ACTION = "io.github.ninbyo02.lami.action.RUN_LITERT_LM_GPU_BENCHMARK"
        const val ACTION_CANCEL = "io.github.ninbyo02.lami.action.CANCEL_LITERT_LM_GPU_BENCHMARK"
''','receiver cancel constant')
once('CONTRACT','''        LiteRtLmGpuBenchmarkReceiver.cancelCurrentRun()
        mutableState.value.timestamp?.let { timestamp ->
''','''        appContext.sendBroadcast(
            Intent(LiteRtLmGpuBenchmarkReceiver.ACTION_CANCEL).apply {
                component = ComponentName(appContext, LiteRtLmGpuBenchmarkReceiver::class.java)
                setPackage(appContext.packageName)
            },
        )
        mutableState.value.timestamp?.let { timestamp ->
''','coordinator explicit cancel broadcast')
once('TEST_FILE','''            "cancelRequested.set(false)",
''','''            "cancelRequested.set(false)",
            "ACTION_CANCEL",
''','cancel action source contract')
for k,p in files.items(): p.write_text(texts[k])
PY
printf 'debug_token_ui_stop_receiver_broadcast=enabled\nbackups=%s,%s,%s\n' "$receiver.bak.$timestamp" "$contract.bak.$timestamp" "$test_file.bak.$timestamp"
