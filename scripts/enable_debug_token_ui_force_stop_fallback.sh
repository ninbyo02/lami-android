#!/usr/bin/env bash
# Adds the fail-safe cleanup only for a debug-token run that remained running after its frontend Stop tap.
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
anchor = '''stop_debug_token_ui_benchmark() {
'''
function = '''force_stop_debug_token_ui_benchmark() {
  local serial="192.168.52.52:43045" package="io.github.ninbyo02.lami.gpunoconstraint" pid
  [[ "$(adb -s "$serial" get-state 2>/dev/null || true)" == "device" ]] || { echo "debug_token_ui_force_stop=device_gate_blocked"; return 65; }
  [[ "$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\\r')" == "NX733J" ]] || { echo "debug_token_ui_force_stop=model_gate_blocked"; return 65; }
  adb -s "$serial" shell am force-stop "$package"
  sleep 1
  pid="$(adb -s "$serial" shell pidof "$package" 2>/dev/null | tr -d '\\r\\n' || true)"
  [[ -z "$pid" ]] || { echo "debug_token_ui_force_stop=pid_still_running pid=$pid"; return 65; }
  echo "debug_token_ui_force_stop=completed"
}

'''
n=t.count(anchor)
if n != 1:
    raise SystemExit(f'force-stop function anchor count={n}')
t=t.replace(anchor,function+anchor)
old = '''  debug-token-ui-stop)
    stop_debug_token_ui_benchmark ;;
'''
new = '''  debug-token-ui-force-stop)
    force_stop_debug_token_ui_benchmark ;;
  debug-token-ui-stop)
    stop_debug_token_ui_benchmark ;;
'''
n=t.count(old)
if n != 1:
    raise SystemExit(f'force-stop dispatch anchor count={n}')
p.write_text(t.replace(old,new))
PY
bash -n "$controller"
printf 'debug_token_ui_force_stop_fallback=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
