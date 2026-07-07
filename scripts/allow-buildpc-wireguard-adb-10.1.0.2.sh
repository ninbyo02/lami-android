#!/usr/bin/env bash
set -euo pipefail

# Build PCで実行: forced-command controllerのADB許可先に exact 10.1.0.2 だけ追加する。
# 目的: WireGuard経由のAndroid Wireless ADB 10.1.0.2:<port> を install/start/connect で使えるようにする。

CANDIDATES=(
  "$HOME/lami-build-control/remote_control.sh"
  "$HOME/lami-build-control/remote_control_full.sh"
  "$HOME/lami-build-control/remote_control_limited_adb.sh"
)

TARGET=""
for f in "${CANDIDATES[@]}"; do
  if [[ -f "$f" ]] && grep -q '192\.168\.52\.52\|10\.5\.5\.3' "$f"; then
    TARGET="$f"
    break
  fi
done

if [[ -z "$TARGET" ]]; then
  echo "ERROR: controller file not found. Checked:" >&2
  printf '  %s\n' "${CANDIDATES[@]}" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$TARGET.bak-allow-10.1.0.2-$STAMP"
cp -a "$TARGET" "$BACKUP"
echo "backup=$BACKUP"

python3 - "$TARGET" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
if "10.1.0.2" in s:
    print("10.1.0.2 already present")
    raise SystemExit(0)
replacements = [
    ('10.5.5.3|192.168.52.52', '10.5.5.3|192.168.52.52|10.1.0.2'),
    ('192.168.52.52|10.5.5.3', '192.168.52.52|10.5.5.3|10.1.0.2'),
    ('10.5.5.3 192.168.52.52', '10.5.5.3 192.168.52.52 10.1.0.2'),
    ('192.168.52.52 10.5.5.3', '192.168.52.52 10.5.5.3 10.1.0.2'),
    ('"10.5.5.3" "192.168.52.52"', '"10.5.5.3" "192.168.52.52" "10.1.0.2"'),
    ('"192.168.52.52" "10.5.5.3"', '"192.168.52.52" "10.5.5.3" "10.1.0.2"'),
]
for old, new in replacements:
    if old in s:
        s = s.replace(old, new)
        p.write_text(s)
        print(f"updated {p} using pattern: {old}")
        raise SystemExit(0)
print("ERROR: known allowlist pattern not found; inspect manually", file=sys.stderr)
raise SystemExit(1)
PY

bash -n "$TARGET"

echo
echo "== help check =="
bash "$TARGET" help | grep -E 'adb-(pair|connect)|install-future|10\.1\.0\.2' || true

echo
echo "DONE: exact 10.1.0.2 allowlist update applied."
