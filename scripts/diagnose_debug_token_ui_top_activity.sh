#!/usr/bin/env bash
# Include current top-resumed Activity and debug app PID in fixed live-state readback.
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
old = '''  echo "debug_token_ui_live_state=begin"
  adb exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_state.txt 2>/dev/null || true
'''
new = '''  echo "debug_token_ui_live_state=begin"
  echo "debug_token_ui_pid=$(adb shell pidof "$package" 2>/dev/null | tr -d '\\r\\n' || true)"
  echo "debug_token_ui_top_resumed=$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' || true)"
  adb exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_state.txt 2>/dev/null || true
'''
n=t.count(old)
if n != 1:
    raise SystemExit(f'live state diagnostic anchor count={n}')
p.write_text(t.replace(old,new))
PY
bash -n "$controller"
printf 'debug_token_ui_top_activity_diagnostic=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
