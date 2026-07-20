#!/usr/bin/env bash
# Adds a fixed read-only command for the foreground debug-token benchmark's current state.
set -euo pipefail
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
root="$(cd "$root" && pwd -P)"
controller="$root/scripts/lami_build_remote_control_full.sh"
timestamp="$(date +%Y%m%d-%H%M%S)"
test -f "$controller"
cp -a "$controller" "$controller.bak.$timestamp"
ROOT="$root" python3 - <<'PYTHON_EDIT'
import os
from pathlib import Path
p = Path(os.environ["ROOT"]) / "scripts/lami_build_remote_control_full.sh"
t = p.read_text()
anchor = '''read_debug_token_ui_artifact() {
'''
function = '''read_debug_token_ui_live_state() {
  local package="io.github.ninbyo02.lami.gpunoconstraint"
  echo "debug_token_ui_live_state=begin"
  adb exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_state.txt 2>/dev/null || true
  echo "debug_token_ui_marker_history_tail=begin"
  adb exec-out run-as "$package" cat files/litert_lm_gpu_benchmark_marker_history.txt 2>/dev/null | tail -120 || true
  echo "debug_token_ui_live_state=end"
}

'''
n = t.count(anchor)
if n != 1:
    raise SystemExit(f"live state function anchor count={n}")
t = t.replace(anchor, function + anchor)
old_case = '''  debug-token-ui-artifact\\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail
    read_debug_token_ui_artifact "${parts[1]}" ;;
'''
new_case = '''  debug-token-ui-live-state)
    read_debug_token_ui_live_state ;;
  debug-token-ui-artifact\\ *)
    parts=($CMD); [[ "${#parts[@]}" -eq 2 ]] || fail
    read_debug_token_ui_artifact "${parts[1]}" ;;
'''
n = t.count(old_case)
if n != 1:
    raise SystemExit(f"live state dispatch anchor count={n}")
p.write_text(t.replace(old_case, new_case))
PYTHON_EDIT
bash -n "$controller"
printf 'debug_token_ui_live_state=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
