#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_TARGET_DIR="$ROOT_DIR/app/src/customBuildExperimentDebug/jniLibs/arm64-v8a"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_libcdsprpc_staging_experiment/$TIMESTAMP"
PULLED_DIR="$OUT_DIR/vendor_libs"
DEVICE_LIB_PATH="${1:-/vendor/lib64/libcdsprpc.so}"
LIB_NAME="libcdsprpc.so"

cd "$ROOT_DIR" || exit 1

log() {
  printf '[libcdsprpc-stage] %s\n' "$*"
}

sha_for() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1 && [ -f "$file" ]; then
    sha256sum "$file" | awk '{print $1}'
  fi
}

build_id_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  fi
}

needed_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
  fi
}

if ! command -v adb >/dev/null 2>&1; then
  log "ERROR: adb not found."
  exit 2
fi

DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -lt 1 ]; then
  log "ERROR: no adb device connected."
  exit 3
fi

mkdir -p "$PULLED_DIR" "$APP_TARGET_DIR"

{
  printf 'candidate\tstatus\n'
  for candidate in \
    /vendor/lib64/libcdsprpc.so \
    /system_ext/lib64/libcdsprpc.so \
    /odm/lib64/libcdsprpc.so; do
    status="$(adb shell ls -l "$candidate" 2>/dev/null | tr -d '\r' || true)"
    if [ -n "$status" ]; then
      printf '%s\t%s\n' "$candidate" "$status"
    else
      printf '%s\tmissing-or-inaccessible\n' "$candidate"
    fi
  done
} >"$OUT_DIR/device_lib_candidates.tsv"

log "pulling $DEVICE_LIB_PATH"
if ! adb pull "$DEVICE_LIB_PATH" "$PULLED_DIR/$LIB_NAME" >/dev/null; then
  log "ERROR: failed to pull $DEVICE_LIB_PATH"
  exit 4
fi

if [ -e "$APP_TARGET_DIR/$LIB_NAME" ]; then
  chmod u+w "$APP_TARGET_DIR/$LIB_NAME" 2>/dev/null || true
fi
cp -f "$PULLED_DIR/$LIB_NAME" "$APP_TARGET_DIR/$LIB_NAME"

{
  printf 'library\tsource\tstaged_to\tsize\tsha256\tbuild_id\tneeded\n'
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$LIB_NAME" \
    "$DEVICE_LIB_PATH" \
    "$APP_TARGET_DIR/$LIB_NAME" \
    "$(wc -c <"$APP_TARGET_DIR/$LIB_NAME" 2>/dev/null | tr -d ' ')" \
    "$(sha_for "$APP_TARGET_DIR/$LIB_NAME")" \
    "$(build_id_for "$APP_TARGET_DIR/$LIB_NAME")" \
    "$(needed_for "$APP_TARGET_DIR/$LIB_NAME")"
} >"$OUT_DIR/staged_libcdsprpc.tsv"

if command -v file >/dev/null 2>&1; then
  file "$PULLED_DIR/$LIB_NAME" >"$OUT_DIR/file.txt" 2>&1 || true
fi
if command -v readelf >/dev/null 2>&1; then
  readelf -h "$PULLED_DIR/$LIB_NAME" >"$OUT_DIR/readelf_header.txt" 2>&1 || true
  readelf -n "$PULLED_DIR/$LIB_NAME" >"$OUT_DIR/readelf_notes.txt" 2>&1 || true
  readelf -d "$PULLED_DIR/$LIB_NAME" >"$OUT_DIR/readelf_dynamic.txt" 2>&1 || true
fi
git check-ignore -v "$APP_TARGET_DIR/$LIB_NAME" >"$OUT_DIR/git_check_ignore.txt" 2>&1 || true

{
  printf '# libcdsprpc customBuildExperiment staging\n\n'
  printf '%s\n' "- Device source: \`$DEVICE_LIB_PATH\`"
  printf '%s\n' "- Pulled artifact: \`$PULLED_DIR/$LIB_NAME\`"
  printf '%s\n' "- Staged target: \`$APP_TARGET_DIR/$LIB_NAME\`"
  printf '%s\n' "- Git tracking: intentionally excluded by \`app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/.gitignore\`."
  printf '%s\n' "- Git ignore proof: \`$OUT_DIR/git_check_ignore.txt\`."
  printf '%s\n' "- Scope: customBuildExperimentDebug only; do not redistribute this vendor library."
  printf '\n## Identity\n\n'
  printf '| Library | Build ID | SHA-256 | NEEDED |\n'
  printf '| --- | --- | --- | --- |\n'
  awk -F '\t' 'NR == 2 {printf "| `%s` | `%s` | `%s` | `%s` |\n", $1, $6, $5, $7}' "$OUT_DIR/staged_libcdsprpc.tsv"
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
log "staged $LIB_NAME for customBuildExperimentDebug only"
