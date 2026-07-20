#!/usr/bin/env bash
# Preserves a fixed long-context UI payload as exactly one receiver prompt even when it contains newlines.
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
paths={k:Path(os.environ[k]) for k in ('RECEIVER','CONTRACT','TEST_FILE')}
t={k:p.read_text() for k,p in paths.items()}
def once(k,old,new,tag):
 n=t[k].count(old)
 if n!=1: raise SystemExit(f'{tag} anchor count={n}')
 t[k]=t[k].replace(old,new)
once('RECEIVER','''        const val EXTRA_PROMPTS = "prompts"
''','''        const val EXTRA_PROMPTS = "prompts"
        const val EXTRA_SINGLE_PROMPT = "single_prompt"
''','receiver extra')
once('RECEIVER','''    private fun prompts(intent: Intent): List<String> {
        val raw = decodeBase64Extra(intent, EXTRA_PROMPTS_BASE64)
''','''    private fun prompts(intent: Intent): List<String> {
        intent.getStringExtra(EXTRA_SINGLE_PROMPT)
            ?.takeIf { it.isNotBlank() }
            ?.let { return listOf(it) }
        val raw = decodeBase64Extra(intent, EXTRA_PROMPTS_BASE64)
''','receiver single prompt')
once('CONTRACT','''            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_PROMPTS, promptFor(case))
''','''            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_SINGLE_PROMPT, promptFor(case))
''','contract single prompt')
once('TEST_FILE','''            "LongContext",
            "actual_input_tokens",
''','''            "LongContext",
            "EXTRA_SINGLE_PROMPT",
            "actual_input_tokens",
''','single prompt contract')
for k,p in paths.items(): p.write_text(t[k])
PY
printf 'debug_token_ui_single_long_context_prompt=enabled\nbackups=%s,%s,%s\n' "$receiver.bak.$timestamp" "$contract.bak.$timestamp" "$test_file.bak.$timestamp"
