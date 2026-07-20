#!/usr/bin/env bash
# Compose Button text nodes are not necessarily clickable; retain exact Stop text matching and tap its bounds.
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
old = "    if node.attrib.get('text') == 'Stop' and node.attrib.get('clickable') == 'true':\n"
new = "    if node.attrib.get('text') == 'Stop':\n"
n = t.count(old)
if n != 1:
    raise SystemExit(f'frontend Stop semantic anchor count={n}')
p.write_text(t.replace(old, new))
PY
bash -n "$controller"
printf 'debug_token_ui_frontend_stop_text_bounds=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
