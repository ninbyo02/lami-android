#!/usr/bin/env bash
# Keep the debug-only foreground benchmark Activity awake during long fixed-context runs.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
activity="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"
test -f "$activity"
cp -a "$activity" "$activity.bak.$timestamp"
ACTIVITY="$activity" python3 - <<'PY'
import os
from pathlib import Path
p = Path(os.environ['ACTIVITY'])
t = p.read_text()
def once(old, new, label):
    global t
    n = t.count(old)
    if n != 1:
        raise SystemExit(f'{label} anchor count={n}')
    t = t.replace(old, new)
once(
    'import android.os.Bundle\n',
    'import android.os.Bundle\nimport android.view.WindowManager\n',
    'WindowManager import',
)
once(
    '        super.onCreate(savedInstanceState)\n        coordinator = DebugTokenBenchmarkCoordinator(applicationContext, lifecycleScope)\n',
    '        super.onCreate(savedInstanceState)\n        // Debug-only foreground benchmark: prevent display sleep from cancelling a long fixed prefill.\n        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)\n        coordinator = DebugTokenBenchmarkCoordinator(applicationContext, lifecycleScope)\n',
    'onCreate keep-screen-on',
)
once(
    '''    override fun onStop() {
        coordinator.cancel("screen_left")
        super.onStop()
    }
''',
    '''    override fun onStop() {
        coordinator.cancel("screen_left")
        super.onStop()
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
''',
    'onDestroy keep-screen-on clear',
)
p.write_text(t)
PY
printf 'debug_token_benchmark_keep_screen_on=enabled\nbackup=%s\n' "$activity.bak.$timestamp"
