#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="${1:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
TARGET_DIR="$ROOT_DIR/app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/gallery_native_stack_stage/$TIMESTAMP"

REQUIRED_LIBS=(
  liblitertlm_jni.so
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  libQnnSystem.so
  libQnnHtp.so
  libQnnHtpV79Stub.so
  libQnnHtpV79Skel.so
)

OPTIONAL_LIBS=(
  libQnnHtpPrepare.so
  libQnnGpu.so
  libQnnDsp.so
  libQnnHtpV75Stub.so
  libQnnHtpV75Skel.so
  libQnnHtpV73Stub.so
  libQnnHtpV73Skel.so
  libQnnHtpV69Stub.so
  libQnnHtpV69Skel.so
  libQnnHtpV68Stub.so
  libQnnHtpV68Skel.so
  libQnnDspV66Stub.so
  libQnnDspV66Skel.so
)

cd "$ROOT_DIR" || exit 1

log() {
  printf '[gallery-stack-stage] %s\n' "$*"
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

stage_lib() {
  local lib="$1"
  local required="$2"
  local extracted="$OUT_DIR/extracted/lib/arm64-v8a/$lib"
  if [ ! -f "$extracted" ]; then
    printf '%s\t%s\tmissing\t-\t-\t-\t-\n' "$lib" "$required" >>"$OUT_DIR/staged_libs.tsv"
    if [ "$required" = "required" ]; then
      log "ERROR: required library missing from APK: $lib"
      return 1
    fi
    log "optional missing: $lib"
    return 0
  fi

  cp "$extracted" "$TARGET_DIR/$lib"
  printf '%s\t%s\tstaged\t%s\t%s\t%s\t%s\n' \
    "$lib" \
    "$required" \
    "$(wc -c <"$TARGET_DIR/$lib" 2>/dev/null | tr -d ' ')" \
    "$(sha_for "$TARGET_DIR/$lib")" \
    "$(build_id_for "$TARGET_DIR/$lib")" \
    "$(needed_for "$TARGET_DIR/$lib")" >>"$OUT_DIR/staged_libs.tsv"
  log "staged $lib build_id=$(build_id_for "$TARGET_DIR/$lib") sha256=$(sha_for "$TARGET_DIR/$lib")"
  return 0
}

if [ ! -f "$APK_PATH" ]; then
  log "ERROR: APK not found: $APK_PATH"
  exit 2
fi

mkdir -p "$OUT_DIR/extracted" "$TARGET_DIR"
log "extracting Gallery SM8750 arm64 libs from $APK_PATH"
unzip -q -o "$APK_PATH" 'lib/arm64-v8a/*' -d "$OUT_DIR/extracted" 2>/dev/null || true

printf 'library\trequirement\tstatus\tsize\tsha256\tbuild_id\tneeded\n' >"$OUT_DIR/staged_libs.tsv"
for lib in "${REQUIRED_LIBS[@]}"; do
  stage_lib "$lib" "required" || exit 3
done
for lib in "${OPTIONAL_LIBS[@]}"; do
  stage_lib "$lib" "optional" || exit 3
done

POLLUTION=0
STANDARD_POLLUTED="$(find "$ROOT_DIR/app/src/standardDebug" "$ROOT_DIR/app/src/main/jniLibs" -type f \( -name 'libLiteRt*.so' -o -name 'liblitertlm_jni.so' -o -name 'libQnn*.so' \) 2>/dev/null || true)"
NPU_FORBIDDEN="$(find "$ROOT_DIR/app/src/npuExperimentDebug/jniLibs/arm64-v8a" -type f \( -name 'libLiteRt.so' -o -name 'liblitertlm_jni.so' -o -name 'libQnn*.so' \) 2>/dev/null || true)"
if [ -n "$STANDARD_POLLUTED" ]; then
  POLLUTION=1
  log "ERROR: standard/main source-set pollution detected:"
  printf '%s\n' "$STANDARD_POLLUTED"
fi
if [ -n "$NPU_FORBIDDEN" ]; then
  POLLUTION=1
  log "ERROR: npuExperiment source-set contains forbidden Gallery stack libs:"
  printf '%s\n' "$NPU_FORBIDDEN"
fi

{
  printf '# Gallery stack staging summary\n\n'
  printf '%s\n' "- APK: \`$APK_PATH\`"
  printf '%s\n' "- APK SHA-256: \`$(sha_for "$APK_PATH")\`"
  printf '%s\n' "- Target dir: \`$TARGET_DIR\`"
  printf '%s\n' "- Artifact dir: \`$OUT_DIR\`"
  printf '%s\n' "- Action: staged only into galleryStackExperimentDebug."
  printf '\n## Staged libraries\n\n'
  printf '| Library | Status | Build ID | SHA-256 | NEEDED |\n'
  printf '| --- | --- | --- | --- | --- |\n'
  awk -F '\t' 'NR > 1 {printf "| `%s` | `%s` | `%s` | `%s` | `%s` |\n", $1, $3, $6, $5, $7}' "$OUT_DIR/staged_libs.tsv"
  printf '\n## Leakage check\n\n'
  printf '%s\n' "- standard/main source-set pollution: \`$(if [ -n "$STANDARD_POLLUTED" ]; then printf yes; else printf no; fi)\`"
  printf '%s\n' "- npuExperiment forbidden Gallery stack libs: \`$(if [ -n "$NPU_FORBIDDEN" ]; then printf yes; else printf no; fi)\`"
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
if [ "$POLLUTION" -ne 0 ]; then
  exit 4
fi
log "done; staged for galleryStackExperimentDebug only"
