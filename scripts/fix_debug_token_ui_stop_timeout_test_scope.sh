#!/usr/bin/env bash
# Corrects the source contract to look for UI-only Stop enablement in the Activity source.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
test_file="$root/app/src/test/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkUiSourceContractTest.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
test -f "$test_file"; cp -a "$test_file" "$test_file.bak.$timestamp"
TEST_FILE="$test_file" python3 - <<'PY'
import os
from pathlib import Path
p=Path(os.environ['TEST_FILE']); text=p.read_text()
old='''    fun `frontend Stop contract cancels active native benchmark and reports terminal cancellation`() {
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        listOf(
'''
new='''    fun `frontend Stop contract cancels active native benchmark and reports terminal cancellation`() {
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        val activity = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt").readText()
        listOf(
'''
if text.count(old)!=1: raise SystemExit(f'test source anchor count={text.count(old)}')
text=text.replace(old,new)
old='''            assertTrue("missing frontend Stop cancellation contract: $required", receiver.contains(required))
'''
new='''            assertTrue("missing frontend Stop cancellation contract: $required", receiver.contains(required) || activity.contains(required))
'''
if text.count(old)!=1: raise SystemExit(f'test assertion anchor count={text.count(old)}')
p.write_text(text.replace(old,new))
PY
printf 'debug_token_ui_stop_timeout_test_scope=fixed\nbackup=%s\n' "$test_file.bak.$timestamp"
