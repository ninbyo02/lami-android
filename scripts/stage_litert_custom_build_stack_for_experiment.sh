#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACT_DIR="${1:-artifacts/litert_custom_build/20260516_235244}"
SOURCE_DIR="$ROOT_DIR/$ARTIFACT_DIR/built_libs"
QNN_RUNTIME_SOURCE_DIR="$ROOT_DIR/$ARTIFACT_DIR/qnn_runtime_libs"
TARGET_DIR="$ROOT_DIR/app/src/customBuildExperimentDebug/jniLibs/arm64-v8a"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/litert_custom_build_stage/$TIMESTAMP"
GEMMA_PROVIDER_FALLBACKS=(
  "$HOME/project/litert-custom-build/LiteRT-LM/prebuilt/android_arm64/libGemmaModelConstraintProvider.so"
  "/home/lami-build/project/litert-custom-build/LiteRT-LM/prebuilt/android_arm64/libGemmaModelConstraintProvider.so"
  "/home/sato/project/litert-custom-build/LiteRT-LM/prebuilt/android_arm64/libGemmaModelConstraintProvider.so"
)

REQUIRED_LIBS=(
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  liblitertlm_jni.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libGemmaModelConstraintProvider.so
)

QNN_RUNTIME_LIBS=(
  libQnnSystem.so
  libQnnHtp.so
  libQnnHtpPrepare.so
  libQnnHtpV79Stub.so
  libQnnHtpV79Skel.so
  libQnnDsp.so
  libQnnGpu.so
)

cd "$ROOT_DIR" || exit 1

log() {
  printf '[custom-build-stage] %s\n' "$*"
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

source_for_lib() {
  local lib="$1"
  local built="$SOURCE_DIR/$lib"
  if [ -f "$built" ]; then
    printf '%s\n' "$built"
    return 0
  fi
  if [ "$lib" = "libGemmaModelConstraintProvider.so" ]; then
    local fallback
    for fallback in "${GEMMA_PROVIDER_FALLBACKS[@]}"; do
      if [ -f "$fallback" ]; then
        printf '%s\n' "$fallback"
        return 0
      fi
    done
  fi
  return 1
}

if [ ! -d "$SOURCE_DIR" ]; then
  log "ERROR: built_libs directory not found: $SOURCE_DIR"
  exit 2
fi

mkdir -p "$OUT_DIR" "$TARGET_DIR"
touch "$TARGET_DIR/.gitkeep"
printf 'library\tstatus\tsource\tsize\tsha256\tbuild_id\tneeded\n' >"$OUT_DIR/staged_libs.tsv"
printf 'library\tstatus\tsource\tsize\tsha256\tbuild_id\tneeded\n' >"$OUT_DIR/staged_qnn_runtime_libs.tsv"

for lib in "${REQUIRED_LIBS[@]}"; do
  source_path="$(source_for_lib "$lib" || true)"
  if [ -z "$source_path" ]; then
    printf '%s\tmissing\t-\t-\t-\t-\t-\n' "$lib" >>"$OUT_DIR/staged_libs.tsv"
    log "ERROR: required custom build library not found: $lib"
    exit 3
  fi
  if [ -e "$TARGET_DIR/$lib" ]; then
    chmod u+w "$TARGET_DIR/$lib" 2>/dev/null || true
  fi
  cp -f "$source_path" "$TARGET_DIR/$lib"
  printf '%s\tstaged\t%s\t%s\t%s\t%s\t%s\n' \
    "$lib" \
    "$source_path" \
    "$(wc -c <"$TARGET_DIR/$lib" 2>/dev/null | tr -d ' ')" \
    "$(sha_for "$TARGET_DIR/$lib")" \
    "$(build_id_for "$TARGET_DIR/$lib")" \
    "$(needed_for "$TARGET_DIR/$lib")" >>"$OUT_DIR/staged_libs.tsv"
  log "staged $lib build_id=$(build_id_for "$TARGET_DIR/$lib") sha256=$(sha_for "$TARGET_DIR/$lib")"
done

QNN_RUNTIME_STAGED=0
if [ -d "$QNN_RUNTIME_SOURCE_DIR" ]; then
  for lib in "${QNN_RUNTIME_LIBS[@]}"; do
    source_path="$QNN_RUNTIME_SOURCE_DIR/$lib"
    if [ ! -f "$source_path" ]; then
      printf '%s\tmissing\t-\t-\t-\t-\t-\n' "$lib" >>"$OUT_DIR/staged_qnn_runtime_libs.tsv"
      log "QNN runtime library not provided by artifact: $lib"
      continue
    fi
    if [ -e "$TARGET_DIR/$lib" ]; then
      chmod u+w "$TARGET_DIR/$lib" 2>/dev/null || true
    fi
    cp -f "$source_path" "$TARGET_DIR/$lib"
    QNN_RUNTIME_STAGED=1
    printf '%s\tstaged\t%s\t%s\t%s\t%s\t%s\n' \
      "$lib" \
      "$source_path" \
      "$(wc -c <"$TARGET_DIR/$lib" 2>/dev/null | tr -d ' ')" \
      "$(sha_for "$TARGET_DIR/$lib")" \
      "$(build_id_for "$TARGET_DIR/$lib")" \
      "$(needed_for "$TARGET_DIR/$lib")" >>"$OUT_DIR/staged_qnn_runtime_libs.tsv"
    log "staged QNN runtime $lib build_id=$(build_id_for "$TARGET_DIR/$lib") sha256=$(sha_for "$TARGET_DIR/$lib")"
  done
else
  for lib in "${QNN_RUNTIME_LIBS[@]}"; do
    printf '%s\tnot-requested\t-\t-\t-\t-\t-\n' "$lib" >>"$OUT_DIR/staged_qnn_runtime_libs.tsv"
  done
  log "QNN runtime artifact directory not present; leaving existing dependency-provided QNN libs unchanged."
fi

POLLUTION=0
STANDARD_POLLUTED="$(find "$ROOT_DIR/app/src/main/jniLibs" "$ROOT_DIR/app/src/standardDebug" -type f \( -name 'libLiteRt*.so' -o -name 'liblitertlm_jni.so' -o -name 'libGemmaModelConstraintProvider.so' \) 2>/dev/null || true)"
NPU_FORBIDDEN="$(find "$ROOT_DIR/app/src/npuExperimentDebug/jniLibs/arm64-v8a" -type f \( -name 'libLiteRt.so' -o -name 'liblitertlm_jni.so' -o -name 'libLiteRtCompilerPlugin_Qualcomm.so' -o -name 'libGemmaModelConstraintProvider.so' \) 2>/dev/null || true)"
GALLERY_FORBIDDEN="$(find "$ROOT_DIR/app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a" -type f \( -name 'libLiteRtCompilerPlugin_Qualcomm.so' -o -name 'libGemmaModelConstraintProvider.so' \) 2>/dev/null || true)"
if [ -n "$STANDARD_POLLUTED" ]; then
  POLLUTION=1
  log "ERROR: standard/main source-set pollution detected:"
  printf '%s\n' "$STANDARD_POLLUTED"
fi
if [ -n "$NPU_FORBIDDEN" ]; then
  POLLUTION=1
  log "ERROR: npuExperiment source-set contains forbidden custom stack libs:"
  printf '%s\n' "$NPU_FORBIDDEN"
fi
if [ -n "$GALLERY_FORBIDDEN" ]; then
  POLLUTION=1
  log "ERROR: galleryStackExperiment source-set contains forbidden custom-only libs:"
  printf '%s\n' "$GALLERY_FORBIDDEN"
fi

{
  printf '# Custom LiteRT-LM native stack staging summary\n\n'
  printf '%s\n' "- Artifact dir: \`$ROOT_DIR/$ARTIFACT_DIR\`"
  printf '%s\n' "- Target dir: \`$TARGET_DIR\`"
  printf '%s\n' "- Action: staged only into customBuildExperimentDebug."
  printf '%s\n' "- QNN SDK libs copied: \`$(if [ "$QNN_RUNTIME_STAGED" -eq 1 ]; then printf yes; else printf no; fi)\`"
  printf '%s\n' "- Gallery libs copied: \`no\`"
  printf '\n## Staged libraries\n\n'
  printf '| Library | Status | Build ID | SHA-256 | NEEDED |\n'
  printf '| --- | --- | --- | --- | --- |\n'
  awk -F '\t' 'NR > 1 {printf "| `%s` | `%s` | `%s` | `%s` | `%s` |\n", $1, $2, $6, $5, $7}' "$OUT_DIR/staged_libs.tsv"
  printf '\n## Staged QNN runtime libraries\n\n'
  printf '| Library | Status | Build ID | SHA-256 | NEEDED |\n'
  printf '| --- | --- | --- | --- | --- |\n'
  awk -F '\t' 'NR > 1 {printf "| `%s` | `%s` | `%s` | `%s` | `%s` |\n", $1, $2, $6, $5, $7}' "$OUT_DIR/staged_qnn_runtime_libs.tsv"
  printf '\n## Leakage check\n\n'
  printf '%s\n' "- standard/main source-set pollution: \`$(if [ -n "$STANDARD_POLLUTED" ]; then printf yes; else printf no; fi)\`"
  printf '%s\n' "- npuExperiment forbidden custom stack libs: \`$(if [ -n "$NPU_FORBIDDEN" ]; then printf yes; else printf no; fi)\`"
  printf '%s\n' "- galleryStackExperiment forbidden custom-only libs: \`$(if [ -n "$GALLERY_FORBIDDEN" ]; then printf yes; else printf no; fi)\`"
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
if [ "$POLLUTION" -ne 0 ]; then
  exit 4
fi
log "done; staged for customBuildExperimentDebug only"
