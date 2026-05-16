#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GALLERY_APK="${1:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
GALLERY_STACK_APK="${2:-app/build/outputs/apk/galleryStackExperiment/debug/app-galleryStackExperiment-debug.apk}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="artifacts/gallery_dispatch_requirements/$TIMESTAMP"

LIBS=(
  "liblitertlm_jni.so"
  "libLiteRt.so"
  "libLiteRtDispatch_Qualcomm.so"
  "libQnnSystem.so"
  "libQnnHtp.so"
  "libQnnHtpPrepare.so"
  "libQnnHtpV79Stub.so"
  "libQnnHtpV79Skel.so"
  "libLiteRtRuntimeCApi.so"
  "libllm_inference_engine_jni.so"
)

KEYWORDS="LiteRtRuntimeCApi|libLiteRtRuntimeCApi\.so|LiteRtDispatchGetApi|LiteRtDispatchCheckRuntimeCompatibility|DispatchCheckRuntimeCompatibility|RuntimeCompatibility|capabilities|insufficient|No usable Dispatch runtime found|Failed to initialize Dispatch API|dispatch_delegate|dispatch_api|dispatch|Qualcomm|QNN|Qnn|HTP|Htp|ADSP|LD_LIBRARY_PATH|dlopen|cannot locate|library not found|symbol not found|version mismatch|schema|model|sm8750|SM8750|V79|skel|stub|libQnn"

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR/gallery" "$OUT_DIR/gallery_stack"

log() {
  printf '[gallery-dispatch-req] %s\n' "$*"
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

soname_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Library soname: \[\(.*\)\].*/\1/p' | head -n 1
  fi
}

needed_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
  fi
}

extract_libs() {
  local apk="$1"
  local dest="$2"
  local label="$3"
  if [ ! -f "$apk" ]; then
    log "$label APK missing: $apk"
    printf 'missing: %s\n' "$apk" >"$dest/MISSING_APK.txt"
    return 0
  fi
  for lib in "${LIBS[@]}"; do
    unzip -p "$apk" "lib/arm64-v8a/$lib" >"$dest/$lib" 2>/dev/null || rm -f "$dest/$lib"
  done
}

write_symbols() {
  local file="$1"
  local out_prefix="$2"
  if command -v nm >/dev/null 2>&1; then
    nm -D --defined-only "$file" >"${out_prefix}_defined_symbols.txt" 2>/dev/null || true
    nm -D -u "$file" >"${out_prefix}_undefined_symbols.txt" 2>/dev/null || true
  fi
  if command -v readelf >/dev/null 2>&1; then
    readelf -Ws "$file" >"${out_prefix}_readelf_symbols.txt" 2>/dev/null || true
  fi
}

analyze_dir() {
  local dir="$1"
  local label="$2"
  local summary="$OUT_DIR/${label}_summary.tsv"
  printf 'library\tpresent\tsize\tsha256\tbuild_id\tsoname\tneeded\n' >"$summary"
  for lib in "${LIBS[@]}"; do
    local file="$dir/$lib"
    if [ ! -f "$file" ]; then
      printf '%s\tfalse\t-\t-\t-\t-\t-\n' "$lib" >>"$summary"
      printf '<missing>\n' >"$OUT_DIR/${label}_${lib}_strings.txt"
      continue
    fi
    local size sha build soname needed safe_name
    size="$(wc -c <"$file" | tr -d ' ')"
    sha="$(sha_for "$file")"
    build="$(build_id_for "$file")"
    soname="$(soname_for "$file")"
    needed="$(needed_for "$file")"
    printf '%s\ttrue\t%s\t%s\t%s\t%s\t%s\n' "$lib" "$size" "${sha:-}" "${build:-}" "${soname:-}" "${needed:-}" >>"$summary"
    safe_name="${label}_${lib%.so}"
    file "$file" >"$OUT_DIR/${safe_name}_file.txt" 2>/dev/null || true
    readelf -d "$file" >"$OUT_DIR/${safe_name}_dynamic.txt" 2>/dev/null || true
    readelf -n "$file" >"$OUT_DIR/${safe_name}_notes.txt" 2>/dev/null || true
    write_symbols "$file" "$OUT_DIR/$safe_name"
    strings "$file" 2>/dev/null | grep -Ei "$KEYWORDS" | sort -u >"$OUT_DIR/${safe_name}_strings.txt" || true
  done
}

write_keyword_summary() {
  local out="$OUT_DIR/requirements_summary.md"
  {
    printf '# Gallery dispatch runtime requirements static analysis\n\n'
    printf '%s\n' "- Gallery APK: \`$GALLERY_APK\`"
    printf '%s\n\n' "- galleryStackExperiment APK: \`$GALLERY_STACK_APK\`"
    printf '## Library matrix\n\n'
    printf '### Gallery SM8750 APK\n\n```text\n'
    cat "$OUT_DIR/gallery_summary.tsv"
    printf '```\n\n'
    printf '### galleryStackExperimentDebug APK\n\n```text\n'
    cat "$OUT_DIR/gallery_stack_summary.tsv"
    printf '```\n\n'
    printf '## Key evidence\n\n'
    for keyword in \
      "libLiteRtRuntimeCApi.so" \
      "LiteRtRuntimeCApi" \
      "LiteRtDispatchGetApi" \
      "LiteRtDispatchCheckRuntimeCompatibility" \
      "No usable Dispatch runtime found" \
      "Failed to initialize Dispatch API" \
      "insufficient" \
      "capabilities" \
      "ADSP" \
      "LD_LIBRARY_PATH" \
      "dlopen" \
      "libQnn"; do
      count="$(grep -R -i -F "$keyword" "$OUT_DIR"/*_strings.txt 2>/dev/null | wc -l | tr -d ' ')"
      printf '%s\n' "- \`$keyword\`: $count string hits"
    done
    printf '\n## Focused strings\n\n'
    for file in "$OUT_DIR"/*_strings.txt; do
      [ -f "$file" ] || continue
      if grep -Eiq 'LiteRtRuntimeCApi|LiteRtDispatch|No usable|Failed to initialize|insufficient|capabilities|ADSP|LD_LIBRARY_PATH|dlopen|libQnn|QNN|HTP' "$file"; then
        printf '### %s\n\n```text\n' "$(basename "$file")"
        grep -Ei 'LiteRtRuntimeCApi|LiteRtDispatch|No usable|Failed to initialize|insufficient|capabilities|ADSP|LD_LIBRARY_PATH|dlopen|libQnn|QNN|HTP' "$file" | head -n 120
        printf '```\n\n'
      fi
    done
  } >"$out"
}

log "output: $OUT_DIR"
extract_libs "$GALLERY_APK" "$OUT_DIR/gallery" "Gallery"
extract_libs "$GALLERY_STACK_APK" "$OUT_DIR/gallery_stack" "galleryStackExperiment"
analyze_dir "$OUT_DIR/gallery" "gallery"
analyze_dir "$OUT_DIR/gallery_stack" "gallery_stack"
write_keyword_summary
log "wrote $OUT_DIR/requirements_summary.md"
printf '%s\n' "$OUT_DIR"
