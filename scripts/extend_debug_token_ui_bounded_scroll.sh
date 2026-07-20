#!/usr/bin/env bash
# Extends the fixed UI benchmark runner's off-screen lookup to a bounded six swipes.
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
old = '''  if [[ -z "$bounds" ]]; then
    adb -s "$serial" shell input swipe 540 2100 540 350 350
    sleep 1
    adb -s "$serial" shell uiautomator dump "$remote_xml" >/dev/null
    adb -s "$serial" pull "$remote_xml" "$local_xml" >/dev/null
    adb -s "$serial" shell rm -f "$remote_xml" >/dev/null 2>&1 || true
    bounds="$(python3 - "$local_xml" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('text') == sys.argv[2]:
        m=re.fullmatch(r'\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]', node.attrib.get('bounds',''))
        if m:
            print(' '.join(m.groups())); break
PY
)"
  fi
'''
new = '''  for _ in $(seq 1 6); do
    [[ -n "$bounds" ]] && break
    adb -s "$serial" shell input swipe 540 2100 540 350 350
    sleep 1
    adb -s "$serial" shell uiautomator dump "$remote_xml" >/dev/null
    adb -s "$serial" pull "$remote_xml" "$local_xml" >/dev/null
    adb -s "$serial" shell rm -f "$remote_xml" >/dev/null 2>&1 || true
    bounds="$(python3 - "$local_xml" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.attrib.get('text') == sys.argv[2]:
        m=re.fullmatch(r'\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]', node.attrib.get('bounds',''))
        if m:
            print(' '.join(m.groups())); break
PY
)"
  done
'''
n = t.count(old)
if n != 1:
    raise SystemExit(f"bounded scroll retry anchor count={n}")
p.write_text(t.replace(old, new))
PYTHON_EDIT
bash -n "$controller"
printf 'debug_token_ui_bounded_scroll=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
