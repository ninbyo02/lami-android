#!/usr/bin/env bash
# Adds only fixed long-context UI runner cases to the Build PC controller.
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
def once(old, new, name):
    n = t.count(old)
    if n != 1:
        raise SystemExit(f"{name} anchor count={n}")
    return t.replace(old, new)
t = once(
    "gpu16|gpu32|gpu128|gpu512|gpu1024|gpu2048|gpu4096|gpu8192|gpu16384|gpu32768|gpu65536|gpu131072|gpu262144|gpu524288|gpu1048576|cpu32)",
    "gpu16|gpu32|gpu128|gpu512|gpu1024|gpu2048|gpu4096|gpu8192|gpu16384|gpu32768|gpu65536|gpu131072|gpu262144|gpu524288|gpu1048576|gpu-long-2048|gpu-long-8192|gpu-long-16384|gpu-long-24576|gpu-long-32768|gpu-long-32769|cpu32)",
    "runner allowlist",
)
t = once(
    'gpu1048576) label="GPU 1048576" ;; cpu32',
    'gpu1048576) label="GPU 1048576" ;; gpu-long-2048) label="GPU long context 2048" ;; gpu-long-8192) label="GPU long context 8192" ;; gpu-long-16384) label="GPU long context 16384" ;; gpu-long-24576) label="GPU long context 24576" ;; gpu-long-32768) label="GPU long context 32768" ;; gpu-long-32769) label="GPU long context 32769 boundary" ;; cpu32',
    "runner labels",
)
p.write_text(t)
PY
bash -n "$controller"
printf 'long_context_ui_runner=enabled\nbackup=%s\n' "$controller.bak.$timestamp"
