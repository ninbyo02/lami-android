#!/usr/bin/env bash
# Reset the debug-only benchmark Activity process before every fixed UI case to reset scroll state.
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
old = '  [[ "$case_name" == "gpu32" ]] && adb -s "$serial" shell am force-stop "$package"\n'
new = '  # Fixed debug-only surface: reset Activity/Compose scroll state before every allowlisted case.\n  adb -s "$serial" shell am force-stop "$package"\n'
n = t.count(old)
if n != 1:
    raise SystemExit(f"debug Activity reset anchor count={n}")
p.write_text(t.replace(old, new))
PY
bash -n "$controller"
printf 'debug_token_ui_fresh_activity=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
