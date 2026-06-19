#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GALLERY_APK="${GALLERY_APK:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
SOURCE_DIR="${SOURCE_DIR:-}"
TARGET_DIR="$ROOT_DIR/app/src/galleryAlignedNpuProbeDebug/jniLibs/arm64-v8a"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/gallery_aligned_npu_probe_stage/$TIMESTAMP"

REQUIRED_LIBS=(
  liblitertlm_jni.so
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  libQnnHtp.so
  libQnnSystem.so
  libQnnHtpPrepare.so
  libQnnHtpV79Skel.so
  libQnnHtpV79Stub.so
  libllm_inference_engine_jni.so
)

OPTIONAL_LIBS=(
  libLiteRtCompilerPlugin_Qualcomm.so
  libGemmaModelConstraintProvider.so
)

usage() {
  cat <<'USAGE'
Usage:
  scripts/stage_gallery_aligned_npu_libs.sh [options]

Options:
  --gallery-apk PATH  Extract Gallery SM8750 libs from this APK when present.
  --source-dir DIR    Copy Gallery-aligned libs from this directory.
  --target-dir DIR    Target jniLibs/arm64-v8a dir. Defaults to galleryAlignedNpuProbeDebug.

Default source-dir fallback:
  latest artifacts/gallery_dispatch_requirements/*/gallery_stack

This stages libraries only for the debug-only galleryAlignedNpuProbe flavor.
It does not modify standard, npuExperiment, fallback policy, QAIRT/QNN settings,
or add any always-on System.loadLibrary call.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --gallery-apk)
      GALLERY_APK="${2:-}"
      shift 2
      ;;
    --source-dir)
      SOURCE_DIR="${2:-}"
      shift 2
      ;;
    --target-dir)
      TARGET_DIR="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[gallery-aligned-stage] unknown option: $1"
      usage
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR" "$TARGET_DIR"

log() {
  printf '[gallery-aligned-stage] %s\n' "$*"
}

latest_gallery_stack_dir() {
  find "$ROOT_DIR/artifacts/gallery_dispatch_requirements" -type d -path '*/gallery_stack' 2>/dev/null | sort | tail -n 1
}

if [ -z "$SOURCE_DIR" ]; then
  SOURCE_DIR="$(latest_gallery_stack_dir)"
fi

sha_for() {
  local file="$1"
  if [ -f "$file" ] && command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf -- '-'
  fi
}

build_id_for() {
  local file="$1"
  if [ -f "$file" ] && command -v readelf >/dev/null 2>&1; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  else
    printf -- '-'
  fi
}

needed_for() {
  local file="$1"
  if [ -f "$file" ] && command -v readelf >/dev/null 2>&1; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
  else
    printf -- '-'
  fi
}

extract_from_apk() {
  local lib="$1"
  local out="$2"
  if [ -f "$GALLERY_APK" ] && command -v unzip >/dev/null 2>&1; then
    unzip -p "$GALLERY_APK" "lib/arm64-v8a/$lib" >"$out" 2>/dev/null && return 0
  fi
  rm -f "$out"
  return 1
}

copy_or_extract() {
  local lib="$1"
  local temp="$OUT_DIR/$lib"
  if [ -n "$SOURCE_DIR" ] && [ -f "$SOURCE_DIR/$lib" ]; then
    cp "$SOURCE_DIR/$lib" "$temp"
    printf 'source-dir:%s\n' "$SOURCE_DIR"
    return 0
  fi
  if extract_from_apk "$lib" "$temp"; then
    printf 'gallery-apk:%s\n' "$GALLERY_APK"
    return 0
  fi
  rm -f "$temp"
  printf 'missing\n'
  return 1
}

stage_lib() {
  local lib="$1"
  local requirement="$2"
  local source
  source="$(copy_or_extract "$lib")"
  if [ "$source" = "missing" ]; then
    printf '%s\t%s\tmissing\t-\t-\t-\t-\n' "$lib" "$requirement" >>"$OUT_DIR/staged_libs.tsv"
    if [ "$requirement" = "required" ]; then
      log "ERROR: required Gallery-aligned lib missing: $lib"
      return 1
    fi
    log "optional missing: $lib"
    return 0
  fi

  cp "$OUT_DIR/$lib" "$TARGET_DIR/$lib"
  printf '%s\t%s\tstaged\t%s\t%s\t%s\t%s\n' \
    "$lib" \
    "$requirement" \
    "$(wc -c <"$TARGET_DIR/$lib" 2>/dev/null | tr -d ' ')" \
    "$(sha_for "$TARGET_DIR/$lib")" \
    "$(build_id_for "$TARGET_DIR/$lib")" \
    "$(needed_for "$TARGET_DIR/$lib")" >>"$OUT_DIR/staged_libs.tsv"
  log "staged $lib from $source build_id=$(build_id_for "$TARGET_DIR/$lib")"
  return 0
}

check_no_pollution() {
  local polluted
  polluted="$(
    find \
      "$ROOT_DIR/app/src/main" \
      "$ROOT_DIR/app/src/standard" \
      "$ROOT_DIR/app/src/standardDebug" \
      "$ROOT_DIR/app/src/npuExperimentDebug" \
      -type f \( -name 'libLiteRt.so' -o -name 'liblitertlm_jni.so' -o -name 'libQnn*.so' \) -print 2>/dev/null || true
  )"
  if [ -n "$polluted" ]; then
    log "ERROR: non-isolated source-set pollution detected:"
    printf '%s\n' "$polluted"
    return 1
  fi
  return 0
}

printf 'library\trequirement\tstatus\tsize\tsha256\tbuild_id\tneeded\n' >"$OUT_DIR/staged_libs.tsv"
for lib in "${REQUIRED_LIBS[@]}"; do
  stage_lib "$lib" "required" || exit 3
done
for lib in "${OPTIONAL_LIBS[@]}"; do
  stage_lib "$lib" "optional" || exit 3
done
check_no_pollution || exit 4

{
  printf '# Gallery-aligned NPU probe stack staging\n\n'
  printf '%s\n' "- source_dir: \`${SOURCE_DIR:-}\`"
  printf '%s\n' "- gallery_apk: \`$GALLERY_APK\`"
  printf '%s\n' "- target_dir: \`$TARGET_DIR\`"
  printf '%s\n' "- isolated_flavor: \`galleryAlignedNpuProbeDebug\`"
  printf '%s\n' "- application_id: \`io.github.ninbyo02.lami.galleryprobe\`"
  printf '%s\n' "- policy: \`debug-only local staging; no production path wiring; no fallback/QAIRT/QNN setting change\`"
  printf '\n## Staged libraries\n\n'
  printf '| Library | Requirement | Status | Build ID | SHA-256 | NEEDED |\n'
  printf '| --- | --- | --- | --- | --- | --- |\n'
  awk -F '\t' 'NR > 1 {printf "| `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n", $1, $2, $3, $6, $5, $7}' "$OUT_DIR/staged_libs.tsv"
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
log "done; staged only for galleryAlignedNpuProbeDebug"
printf '%s\n' "$OUT_DIR"
