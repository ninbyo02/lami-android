#!/usr/bin/env bash
# Adds one fixed scroll-and-retry only when an allowlisted benchmark label is off-screen.
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
old = '''PY
)"
  read -r x1 y1 x2 y2 <<<"$bounds"
'''
new = '''PY
)"
  if [[ -z "$bounds" ]]; then
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
  read -r x1 y1 x2 y2 <<<"$bounds"
'''
n = t.count(old)
if n != 1:
    raise SystemExit(f"scroll retry anchor count={n}")
p.write_text(t.replace(old, new))
PYTHON_EDIT
bash -n "$controller"
printf 'long_context_ui_scroll_runner=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
