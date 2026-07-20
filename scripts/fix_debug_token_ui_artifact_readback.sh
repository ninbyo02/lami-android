#!/usr/bin/env bash
# Make debug-token artifact readback tolerate no initial grep match under set -e.
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
p = Path(os.environ["ROOT"]) / "scripts/lami_build_remote_control_full.sh"
t = p.read_text()
old = '''    dir="$(grep -rl "^timestamp=$timestamp$" artifacts/debug_token_ui/*/host_gate.txt 2>/dev/null | head -1 | xargs -r dirname)"
'''
new = '''    dir="$( (grep -rl "^timestamp=$timestamp$" artifacts/debug_token_ui/*/host_gate.txt 2>/dev/null || true) | head -1 | xargs -r dirname)"
'''
n = t.count(old)
if n != 1:
    raise SystemExit(f"artifact readback anchor count={n}")
p.write_text(t.replace(old, new))
PY
bash -n "$controller"
printf 'debug_token_artifact_readback=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
