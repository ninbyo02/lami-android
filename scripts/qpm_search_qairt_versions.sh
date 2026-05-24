#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/artifacts/qpm_search/$TIMESTAMP}"
QPM_CLI="${QPM_CLI:-}"

log() {
  printf '[qpm-search] %s\n' "$*"
}

usage() {
  cat <<'EOF'
Usage:
  bash scripts/qpm_search_qairt_versions.sh

Static/safe QPM catalog probe. This script detects qpm-cli and runs read-only
search/list style commands when available. It does not install QAIRT, does not
build LiteRT, does not install an app, and does not write into app jniLibs.

Environment:
  QPM_CLI=/path/to/qpm-cli  Override qpm-cli path.
  OUT_DIR=...              Override artifact output directory.
EOF
}

mask_secrets() {
  sed -E \
    -e 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/<email>/g' \
    -e 's/(Password|password|Token|token|Bearer)[^[:space:]]*/\1=<redacted>/g'
}

run_probe() {
  local name="$1"
  shift
  local out="$OUT_DIR/${name}.txt"
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n\n'
    timeout 60 "$@" 2>&1 || true
  } | mask_secrets >"$out"
}

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  usage
  exit 0
fi

mkdir -p "$OUT_DIR"

if [ -z "$QPM_CLI" ]; then
  QPM_CLI="$(command -v qpm-cli 2>/dev/null || true)"
fi

{
  printf 'timestamp=%s\n' "$TIMESTAMP"
  printf 'qpm_cli=%s\n' "${QPM_CLI:-<missing>}"
  printf 'safety=no install, no LiteRT build, no app install, no Engine.initialize\n'
} >"$OUT_DIR/context.env"

if [ -z "$QPM_CLI" ] || [ ! -x "$QPM_CLI" ]; then
  cat >"$OUT_DIR/summary.md" <<'EOF'
# QPM QAIRT Catalog Search Summary

`qpm-cli` was not found. No catalog search was run.

Next step:

1. Log in to the official Qualcomm Package Manager portal.
2. Download Qualcomm Package Manager 3 for Linux.
3. Install it with the official `.deb` installer.
4. Re-run:

   ```bash
   bash scripts/qpm_search_qairt_versions.sh
   ```

Safety: no install, no LiteRT build, no app install, no Engine.initialize.
EOF
  log "qpm-cli missing; wrote $OUT_DIR/summary.md"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

run_probe qpm_cli_help "$QPM_CLI" --help
run_probe qpm_cli_version "$QPM_CLI" --version
run_probe search_qairt "$QPM_CLI" search qairt
run_probe search_qualcomm_ai_runtime "$QPM_CLI" search "Qualcomm AI Runtime"
run_probe search_244 "$QPM_CLI" search 2.44
run_probe search_242 "$QPM_CLI" search 2.42
run_probe search_246 "$QPM_CLI" search 2.46
run_probe search_260225 "$QPM_CLI" search 260225
run_probe list "$QPM_CLI" list

{
  printf '# QPM QAIRT Catalog Search Summary\n\n'
  printf -- '- qpm-cli: `%s`\n' "$QPM_CLI"
  printf -- '- Output: `%s`\n' "$OUT_DIR"
  printf -- '- Install attempted: `no`\n'
  printf -- '- LiteRT build: `not run`\n'
  printf -- '- App install: `not run`\n'
  printf -- '- Engine.initialize: `not run`\n\n'
  printf 'Review these files for QAIRT 2.44 availability:\n\n'
  printf -- '- `search_qairt.txt`\n'
  printf -- '- `search_qualcomm_ai_runtime.txt`\n'
  printf -- '- `search_244.txt`\n'
  printf -- '- `search_260225.txt`\n'
  printf -- '- `list.txt`\n'
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
printf '%s\n' "$OUT_DIR"
