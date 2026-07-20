#!/usr/bin/env bash
# Avoid a redundant second DebugTokenBenchmarkActivity launch that can trigger onStop cancellation.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
controller="$root/scripts/lami_build_remote_control_full.sh"
timestamp="$(date +%Y%m%d-%H%M%S)"
test -f "$controller"
cp -a "$controller" "$controller.bak.$timestamp"
ROOT="$root" python3 - <<'PY'
import os
from pathlib import Path
p = Path(os.environ['ROOT']) / 'scripts/lami_build_remote_control_full.sh'
t = p.read_text()
old = '''  adb -s "$serial" shell am start -W -n "$debug_component" >"$out_root/open_debug_activity.txt" 2>&1 || {
    echo "debug_activity_start=failed"; sed -n '1,80p' "$out_root/open_debug_activity.txt"; return 65;
  }
  for _ in $(seq 1 20); do
'''
new = '''  printf 'debug_activity_start=reused_foreground_launch\\n' >"$out_root/open_debug_activity.txt"
  for _ in $(seq 1 20); do
'''
n = t.count(old)
if n != 1:
    raise SystemExit(f'redundant Activity launch anchor count={n}')
p.write_text(t.replace(old, new))
PY
bash -n "$controller"
printf 'debug_token_ui_single_activity_launch=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
