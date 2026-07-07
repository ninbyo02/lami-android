#!/usr/bin/env bash
set -euo pipefail

# Build PCで実行: forced-command controller内の 10.1.0.2 許可漏れを追加修正する v2。
# help表示だけでなく、実行時validator/regex/host caseも exact 10.1.0.2 を許可する。

CANDIDATES=(
  "$HOME/lami-build-control/remote_control.sh"
  "$HOME/lami-build-control/remote_control_full.sh"
  "$HOME/lami-build-control/remote_control_limited_adb.sh"
)

TARGET=""
for f in "${CANDIDATES[@]}"; do
  if [[ -f "$f" ]] && grep -q 'adb-connect' "$f"; then
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
BACKUP="$TARGET.bak-allow-10.1.0.2-v2-$STAMP"
cp -a "$TARGET" "$BACKUP"
echo "target=$TARGET"
echo "backup=$BACKUP"

python3 - "$TARGET" <<'PY'
from pathlib import Path
import re
import sys
p = Path(sys.argv[1])
s = p.read_text()
orig = s

# Add 10.1.0.2 after any known host allowlist pattern, including already-updated help-only patterns.
patterns = [
    (r'10\.5\.5\.3\|192\.168\.52\.52(?!\|10\.1\.0\.2)', r'10.5.5.3|192.168.52.52|10.1.0.2'),
    (r'192\.168\.52\.52\|10\.5\.5\.3(?!\|10\.1\.0\.2)', r'192.168.52.52|10.5.5.3|10.1.0.2'),
    (r'10\.5\.5\.3 192\.168\.52\.52(?! 10\.1\.0\.2)', r'10.5.5.3 192.168.52.52 10.1.0.2'),
    (r'192\.168\.52\.52 10\.5\.5\.3(?! 10\.1\.0\.2)', r'192.168.52.52 10.5.5.3 10.1.0.2'),
    (r'"10\.5\.5\.3" "192\.168\.52\.52"(?! "10\.1\.0\.2")', r'"10.5.5.3" "192.168.52.52" "10.1.0.2"'),
    (r'"192\.168\.52\.52" "10\.5\.5\.3"(?! "10\.1\.0\.2")', r'"192.168.52.52" "10.5.5.3" "10.1.0.2"'),
]
for pat, repl in patterns:
    s = re.sub(pat, repl, s)

# Common shell case validator style:
#   case "$host" in
#     10.5.5.3|192.168.52.52) ;;
# Make sure any line containing the two existing IPs also contains 10.1.0.2.
lines = []
for line in s.splitlines(True):
    if '10.5.5.3' in line and '192.168.52.52' in line and '10.1.0.2' not in line:
        if ')' in line:
            line = line.replace(')', '|10.1.0.2)', 1)
        elif '"' in line:
            # Last resort for quoted space lists handled above; leave unchanged if ambiguous.
            pass
    lines.append(line)
s = ''.join(lines)

if s == orig:
    print('WARN: no additional replacement made; dumping matching lines:')
else:
    p.write_text(s)
    print('updated controller')

for i, line in enumerate(s.splitlines(), 1):
    if 'adb-connect' in line or 'install-future' in line or '10.1.0.2' in line or ('10.5.5.3' in line and '192.168.52.52' in line):
        print(f'{i}: {line}')
PY

bash -n "$TARGET"

echo
echo "== help check =="
bash "$TARGET" help | grep -E 'adb-(pair|connect)|install-future|10\.1\.0\.2' || true

echo
echo "== direct validation smoke if script supports forced op =="
# Do not require success; device may be unreachable. We only want to ensure it no longer prints 'not allowed'.
set +e
OUT="$(bash "$TARGET" adb-connect 10.1.0.2 36327 2>&1)"
RC=$?
set -e
printf '%s\n' "$OUT"
if printf '%s\n' "$OUT" | grep -q 'not allowed'; then
  echo "ERROR: still blocked by allowlist" >&2
  exit 2
fi
echo "validation passed: no allowlist block (rc=$RC may be normal ADB connectivity result)"

echo
echo "DONE: exact 10.1.0.2 allowlist v2 update applied."
