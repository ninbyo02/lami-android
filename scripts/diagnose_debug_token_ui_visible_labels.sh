#!/usr/bin/env bash
# Emit bounded, non-sensitive visible UI labels only when a fixed benchmark label is absent.
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
old = '''  [[ -n "${x1:-}" && -n "${y1:-}" && -n "${x2:-}" && -n "${y2:-}" ]] || { echo "fixed_ui_verb=blocked label=$label"; return 65; }
'''
new = '''  [[ -n "${x1:-}" && -n "${y1:-}" && -n "${x2:-}" && -n "${y2:-}" ]] || {
    echo "fixed_ui_verb=blocked label=$label"
    echo "visible_ui_labels_begin"
    python3 - "$local_xml" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
    labels = []
    for node in root.iter('node'):
        text = node.attrib.get('text', '')
        desc = node.attrib.get('content-desc', '')
        if text: labels.append('text=' + repr(text))
        if desc: labels.append('content_desc=' + repr(desc))
    print('\\n'.join(labels[:120]))
except Exception as e:
    print('ui_dump_parse_error=' + type(e).__name__)
PY
    echo "visible_ui_labels_end"
    return 65
  }
'''
n = t.count(old)
if n != 1:
    raise SystemExit(f"visible label diagnostic anchor count={n}")
p.write_text(t.replace(old, new))
PYTHON_EDIT
bash -n "$controller"
printf 'debug_token_visible_label_diagnostic=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
