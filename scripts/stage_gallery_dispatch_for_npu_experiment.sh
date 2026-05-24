#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_ENTRY="lib/arm64-v8a/libLiteRtDispatch_Qualcomm.so"
TARGET_DIR="$ROOT_DIR/app/src/npuExperimentDebug/jniLibs/arm64-v8a"
TARGET_SO="$TARGET_DIR/libLiteRtDispatch_Qualcomm.so"
EXPECTED_SHA="92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777"
EXPECTED_BUILD_ID="643ad77b8ac2f54bd1b61e4133c77b3a"

print_header() {
  printf '\n== %s ==\n' "$1"
}

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'missing required tool: %s\n' "$1" >&2
    exit 1
  fi
}

sha_for() {
  sha256sum "$1" | awk '{print $1}'
}

build_id_for() {
  readelf -n "$1" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
}

verify_dispatch_so() {
  local so_path="$1"
  local label="$2"
  local sha
  local build_id

  print_header "$label"
  printf 'path: %s\n' "$so_path"
  file "$so_path"

  sha="$(sha_for "$so_path")"
  build_id="$(build_id_for "$so_path")"
  printf 'sha256: %s\n' "$sha"
  printf 'build id: %s\n' "${build_id:-none}"

  if [ "$sha" != "$EXPECTED_SHA" ]; then
    printf 'ERROR: sha256 mismatch. expected=%s actual=%s\n' "$EXPECTED_SHA" "$sha" >&2
    exit 1
  fi
  if [ "${build_id:-}" != "$EXPECTED_BUILD_ID" ]; then
    printf 'ERROR: build id mismatch. expected=%s actual=%s\n' "$EXPECTED_BUILD_ID" "${build_id:-none}" >&2
    exit 1
  fi

  printf '\n-- readelf -d --\n'
  readelf -d "$so_path" | grep -E 'NEEDED|SONAME' || true
  if ! readelf -d "$so_path" | grep -q 'Shared library: \[libLiteRt.so\]'; then
    printf 'ERROR: libLiteRt.so dependency not found in NEEDED entries\n' >&2
    exit 1
  fi

  printf '\n-- readelf -n build id --\n'
  readelf -n "$so_path" | grep -E 'Build ID|Android|NT_VERSION|description data' || true

  printf '\n-- nm -D LiteRtDispatchGetApi --\n'
  local dispatch_symbol
  dispatch_symbol="$(nm -D "$so_path" | grep 'LiteRtDispatchGetApi' || true)"
  printf '%s\n' "$dispatch_symbol"
  if [ -z "$dispatch_symbol" ]; then
    printf 'ERROR: LiteRtDispatchGetApi export not found\n' >&2
    exit 1
  fi

  printf '\n-- strings safety sample --\n'
  strings "$so_path" | grep -iE 'SM8750|Qualcomm|QNN|dispatch|version|mismatch' | head -80 || true
}

ensure_no_standard_pollution() {
  print_header "standard/main/release pollution check"
  local polluted
  polluted="$(
    find \
      "$ROOT_DIR/app/src/main" \
      "$ROOT_DIR/app/src/standard" \
      "$ROOT_DIR/app/src/standardDebug" \
      "$ROOT_DIR/app/src/release" \
      -type f -name 'libLiteRtDispatch_Qualcomm.so' -print 2>/dev/null || true
  )"
  if [ -n "$polluted" ]; then
    printf 'ERROR: dispatch runtime found outside npuExperimentDebug:\n%s\n' "$polluted" >&2
    exit 1
  fi
  printf 'ok: no libLiteRtDispatch_Qualcomm.so under main/standard/standardDebug/release\n'
}

print_header "Stage Gallery SM8750 LiteRT dispatch runtime"
printf 'staged for detection only; do not load; do not enable Backend.NPU\n'

if [ -z "$APK_PATH" ]; then
  printf 'usage: %s <ai-edge-gallery-sm8750.apk>\n' "$0" >&2
  exit 1
fi
if [ ! -f "$APK_PATH" ]; then
  printf 'ERROR: APK not found: %s\n' "$APK_PATH" >&2
  exit 1
fi

require_tool unzip
require_tool sha256sum
require_tool file
require_tool readelf
require_tool nm
require_tool strings
require_tool cp

ensure_no_standard_pollution

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
EXTRACTED_SO="$WORK_DIR/libLiteRtDispatch_Qualcomm.so"

print_header "Extract"
printf 'apk: %s\n' "$APK_PATH"
printf 'entry: %s\n' "$APK_ENTRY"
if ! unzip -p "$APK_PATH" "$APK_ENTRY" > "$EXTRACTED_SO"; then
  printf 'ERROR: %s not found in %s\n' "$APK_ENTRY" "$APK_PATH" >&2
  exit 1
fi

verify_dispatch_so "$EXTRACTED_SO" "Verify extracted dispatch runtime"

print_header "Copy to npuExperimentDebug only"
mkdir -p "$TARGET_DIR"
cp "$EXTRACTED_SO" "$TARGET_SO"
printf 'target: %s\n' "$TARGET_SO"

verify_dispatch_so "$TARGET_SO" "Verify staged dispatch runtime"
ensure_no_standard_pollution

print_header "Result"
printf 'staged: %s\n' "$TARGET_SO"
printf 'sha256: %s\n' "$(sha_for "$TARGET_SO")"
printf 'build id: %s\n' "$(build_id_for "$TARGET_SO")"
printf 'policy: staged for detection only; do not load; do not enable Backend.NPU\n'
