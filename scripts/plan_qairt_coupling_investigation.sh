#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CUSTOM_BUILD_DIR="${1:-artifacts/litert_custom_build/20260516_235244}"
GALLERY_APK="${GALLERY_APK:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
QAIRT_HOME_DEFAULT="/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424"
QAIRT_HOME="${QAIRT_HOME:-$QAIRT_HOME_DEFAULT}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt_coupling_investigation/$TIMESTAMP"
EXTRACT_DIR="$OUT_DIR/extracted_gallery_libs"

KEYWORDS='QAIRT|QNN|Qnn|HTP|Htp|ADSP|LD_LIBRARY_PATH|LiteRtDispatch|Dispatch|dispatch|RuntimeCompatibility|capabilit|SM8750|V79|schema|model|libQnn'

BUILT_LIBS=(
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  liblitertlm_jni.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libGemmaModelConstraintProvider.so
)

GALLERY_LIBS=(
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  liblitertlm_jni.so
  libQnnSystem.so
  libQnnHtp.so
  libQnnHtpPrepare.so
  libQnnHtpV79Stub.so
  libQnnHtpV79Skel.so
)

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR" "$EXTRACT_DIR"

log() {
  printf '[qairt-coupling-plan] %s\n' "$*"
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

write_lib_row() {
  local label="$1"
  local lib="$2"
  local file="$3"
  if [ -f "$file" ]; then
    printf '%s\t%s\tpresent\t%s\t%s\t%s\t%s\t%s\n' \
      "$label" \
      "$lib" \
      "$file" \
      "$(wc -c <"$file" 2>/dev/null | tr -d ' ')" \
      "$(sha_for "$file")" \
      "$(build_id_for "$file")" \
      "$(needed_for "$file")" >>"$OUT_DIR/library_matrix.tsv"
    if command -v strings >/dev/null 2>&1; then
      strings "$file" 2>/dev/null | grep -E "$KEYWORDS" | sort -u >"$OUT_DIR/${label}_${lib}.strings.txt" || true
    fi
  else
    printf '%s\t%s\tmissing\t%s\t-\t-\t-\t-\n' "$label" "$lib" "$file" >>"$OUT_DIR/library_matrix.tsv"
  fi
}

printf 'label\tlibrary\tstatus\tpath\tsize\tsha256\tbuild_id\tneeded\n' >"$OUT_DIR/library_matrix.tsv"

{
  printf 'customBuildDir=%s\n' "$ROOT_DIR/$CUSTOM_BUILD_DIR"
  printf 'galleryApk=%s\n' "$GALLERY_APK"
  printf 'QAIRT_HOME=%s\n' "$QAIRT_HOME"
  printf 'date=%s\n' "$(date -Is)"
} >"$OUT_DIR/context.env"

if [ -d "$ROOT_DIR/$CUSTOM_BUILD_DIR/built_libs" ]; then
  for lib in "${BUILT_LIBS[@]}"; do
    write_lib_row "built" "$lib" "$ROOT_DIR/$CUSTOM_BUILD_DIR/built_libs/$lib"
  done
else
  log "WARNING: built_libs not found under $ROOT_DIR/$CUSTOM_BUILD_DIR"
fi

if [ -f "$GALLERY_APK" ]; then
  for lib in "${GALLERY_LIBS[@]}"; do
    if unzip -p "$GALLERY_APK" "lib/arm64-v8a/$lib" >"$EXTRACT_DIR/$lib" 2>/dev/null; then
      write_lib_row "gallery" "$lib" "$EXTRACT_DIR/$lib"
    else
      write_lib_row "gallery" "$lib" "$EXTRACT_DIR/$lib"
    fi
  done
else
  log "WARNING: Gallery APK not found: $GALLERY_APK"
fi

if [ -d "$QAIRT_HOME" ]; then
  {
    printf 'QAIRT_HOME=%s\n' "$QAIRT_HOME"
    find "$QAIRT_HOME" -maxdepth 3 -type f \( -name 'libQnn*.so' -o -name 'qnn-*' -o -name 'qnn_*' \) 2>/dev/null | sort
  } >"$OUT_DIR/qairt_files.txt"
  for lib in libQnnSystem.so libQnnHtp.so libQnnHtpPrepare.so libQnnHtpV79Stub.so libQnnHtpV79Skel.so; do
    qairt_lib="$(find "$QAIRT_HOME" -type f -name "$lib" 2>/dev/null | head -n 1 || true)"
    write_lib_row "qairt" "$lib" "$qairt_lib"
  done
else
  printf 'missing QAIRT_HOME: %s\n' "$QAIRT_HOME" >"$OUT_DIR/qairt_files.txt"
fi

if [ -f "$ROOT_DIR/artifacts/npu_diagnostics/20260517_005032_customnpu/crash_summary.md" ]; then
  cp "$ROOT_DIR/artifacts/npu_diagnostics/20260517_005032_customnpu/crash_summary.md" "$OUT_DIR/customnpu_crash_summary.md"
fi

{
  printf '# QNN/QAIRT coupling investigation summary\n\n'
  printf '%s\n' "- Output: \`$OUT_DIR\`"
  printf '%s\n' "- Built stack: \`$ROOT_DIR/$CUSTOM_BUILD_DIR\`"
  printf '%s\n' "- Gallery APK: \`$GALLERY_APK\`"
  printf '%s\n' "- QAIRT_HOME: \`$QAIRT_HOME\`"
  printf '\n## Scope\n\n'
  printf '%s\n' 'This script performs static collection only. It does not build, launch the app, run Engine.initialize, or copy libraries into app source sets.'
  printf '\n## Library matrix\n\n'
  printf '%s\n' 'See `library_matrix.tsv` and per-library `*.strings.txt` files.'
  printf '\n## Next manual review questions\n\n'
  printf '%s\n' '1. Do built, Gallery, and QAIRT QNN libraries report compatible QNN/HTP generations?'
  printf '%s\n' '2. Do dispatch/runtime strings mention required capabilities or SM8750/V79 assumptions?'
  printf '%s\n' '3. Is there evidence of ADSP/LD path requirements not satisfied by Android nativeLibraryDir?'
  printf '%s\n' '4. Does the model metadata require a runtime/compiler generation not represented by the built stack?'
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
log "done; no build, no app launch, no native library staging"
