#!/usr/bin/env bash
# Adds a RED source contract for frontend Stop to cancel the active native debug benchmark.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
test_file="$root/app/src/test/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkUiSourceContractTest.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
test -f "$test_file"
cp -a "$test_file" "$test_file.bak.$timestamp"
TEST_FILE="$test_file" python3 - <<'PY'
import os
from pathlib import Path
p = Path(os.environ['TEST_FILE'])
t = p.read_text()
anchor = '\n}\n'
if not t.endswith(anchor):
    raise SystemExit('test class closing anchor missing')
test = '''
    @Test
    fun `frontend Stop contract cancels active native benchmark and reports terminal cancellation`() {
        val receiver = File(root, "app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt").readText()
        listOf(
            "activeCaseFuture",
            "activeConversation",
            "activeEngine",
            "cancelled_by_debug_foreground_ui",
            "cancelRequested.set(false)",
        ).forEach { required ->
            assertTrue("missing frontend Stop cancellation contract: $required", receiver.contains(required))
        }
    }
'''
p.write_text(t[:-len(anchor)] + test + anchor)
PY
printf 'debug_token_ui_stop_contract_red=enabled\nbackup=%s\n' "$test_file.bak.$timestamp"
