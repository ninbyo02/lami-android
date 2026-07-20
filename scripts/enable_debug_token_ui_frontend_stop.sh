#!/usr/bin/env bash
# Adds a fixed frontend Stop action for the debug-token benchmark Activity.
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
anchor = '''read_debug_token_ui_live_state() {
'''
function = r'''stop_debug_token_ui_benchmark() {
  local serial="192.168.52.52:43045" package="io.github.ninbyo02.lami.gpunoconstraint"
  local component="$package/io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkActivity"
  local remote_xml="/data/local/tmp/lami_debug_token_stop.xml" local_xml bounds x1 y1 x2 y2 tap_x tap_y
  [[ "$(adb -s "$serial" get-state 2>/dev/null || true)" == "device" ]] || { echo "debug_token_ui_stop=device_gate_blocked"; return 65; }
  [[ "$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')" == "NX733J" ]] || { echo "debug_token_ui_stop=model_gate_blocked"; return 65; }
  adb -s "$serial" shell am start -W -n "$component" >/dev/null || { echo "debug_token_ui_stop=activity_start_failed"; return 65; }
  adb -s "$serial" shell uiautomator dump "$remote_xml" >/dev/null
  local_xml="$(mktemp)"
  trap 'rm -f "$local_xml"' RETURN
  adb -s "$serial" pull "$remote_xml" "$local_xml" >/dev/null
  adb -s "$serial" shell rm -f "$remote_xml" >/dev/null 2>&1 || true
  bounds="$(python3 - "$local_xml" <<'PY_STOP'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('text') == 'Stop' and node.attrib.get('clickable') == 'true':
        m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if m:
            print(','.join(m.groups()))
            break
PY_STOP
)"
  [[ "$bounds" =~ ^[0-9]+,[0-9]+,[0-9]+,[0-9]+$ ]] || { echo "debug_token_ui_stop=button_not_found"; return 65; }
  IFS=, read -r x1 y1 x2 y2 <<< "$bounds"
  tap_x=$(( (x1 + x2) / 2 )); tap_y=$(( (y1 + y2) / 2 ))
  adb -s "$serial" shell input tap "$tap_x" "$tap_y"
  echo "debug_token_ui_stop=ui_tapped x=$tap_x y=$tap_y"
}

'''
n=t.count(anchor)
if n != 1:
    raise SystemExit(f'stop function anchor count={n}')
t=t.replace(anchor,function+anchor)
old = '''  debug-token-ui-live-state)
    read_debug_token_ui_live_state ;;
'''
new = '''  debug-token-ui-stop)
    stop_debug_token_ui_benchmark ;;
  debug-token-ui-live-state)
    read_debug_token_ui_live_state ;;
'''
n=t.count(old)
if n != 1:
    raise SystemExit(f'stop dispatch anchor count={n}')
p.write_text(t.replace(old,new))
PY
bash -n "$controller"
printf 'debug_token_ui_frontend_stop=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
