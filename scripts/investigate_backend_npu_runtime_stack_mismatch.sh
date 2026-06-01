#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/backend_npu_runtime_stack_mismatch/$TIMESTAMP"

NPU_APK="${NPU_APK:-$ROOT_DIR/app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk}"
NPU_LIB_DIR="${NPU_LIB_DIR:-$ROOT_DIR/app/src/npuExperimentDebug/jniLibs/arm64-v8a}"
GALLERY_APK="${GALLERY_APK:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
GALLERY_LIB_DIR="${GALLERY_LIB_DIR:-}"
MODEL_PATH="${MODEL_PATH:-/home/sato/Downloads/gemma-4-E2B-it_qualcomm_sm8750.litertlm}"

KEYWORDS='QNN|Qnn|QAIRT|qairt|Qualcomm|SM8750|sm8750|V79|v79|HTP|Htp|FastRPC|fastrpc|ADSP|LD_LIBRARY_PATH|DSP_LIBRARY|LiteRT|LiteRt|litert|dispatch|Dispatch|compiler|Compiler|plugin|Plugin|qnn_partition|runtime|Runtime|version|Version|schema|Schema|context|Context|backend|Backend|skel|Skel|stub|Stub|No usable Dispatch runtime|Failed to initialize Dispatch API|symbol|mismatch|capabilit'

LIBS=(
  liblitertlm_jni.so
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libQnnHtp.so
  libQnnSystem.so
  libQnnHtpPrepare.so
  libQnnHtpV79Skel.so
  libQnnHtpV79Stub.so
  libGemmaModelConstraintProvider.so
  libllm_inference_engine_jni.so
)

usage() {
  cat <<'USAGE'
Usage:
  scripts/investigate_backend_npu_runtime_stack_mismatch.sh [options]

Options:
  --npu-apk PATH       npuExperiment APK to inspect.
  --npu-lib-dir DIR    fallback npuExperiment lib directory.
  --gallery-apk PATH   Google AI Edge Gallery SM8750 APK to inspect.
  --gallery-lib-dir DIR fallback Gallery stack lib directory.
  --model-path PATH    optional local .litertlm model for static strings scan.
  --out-dir DIR        output directory.

This is static analysis only. It does not install, launch, run Engine.initialize,
modify QAIRT/QNN settings, or replace native libraries.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --npu-apk)
      NPU_APK="${2:-}"
      shift 2
      ;;
    --npu-lib-dir)
      NPU_LIB_DIR="${2:-}"
      shift 2
      ;;
    --gallery-apk)
      GALLERY_APK="${2:-}"
      shift 2
      ;;
    --gallery-lib-dir)
      GALLERY_LIB_DIR="${2:-}"
      shift 2
      ;;
    --model-path)
      MODEL_PATH="${2:-}"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[backend-npu-runtime-stack] unknown option: $1"
      usage
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"/{npu,gallery,metadata,strings,dynamic,symbols,diff,model}

log() {
  printf '[backend-npu-runtime-stack] %s\n' "$*"
}

latest_gallery_stack_dir() {
  if [ -d "$ROOT_DIR/artifacts/gallery_dispatch_requirements" ]; then
    find "$ROOT_DIR/artifacts/gallery_dispatch_requirements" -type d -path '*/gallery_stack' 2>/dev/null | sort | tail -n 1
  fi
}

if [ -z "$GALLERY_LIB_DIR" ]; then
  GALLERY_LIB_DIR="$(latest_gallery_stack_dir)"
  if [ -z "$GALLERY_LIB_DIR" ]; then
    GALLERY_LIB_DIR="$ROOT_DIR/app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a"
  fi
fi

sha_for() {
  local file="$1"
  if [ -f "$file" ] && command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf -- '-'
  fi
}

size_for() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -c <"$file" 2>/dev/null | tr -d ' '
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

extract_apk_lib() {
  local apk="$1"
  local lib="$2"
  local out="$3"
  if [ -f "$apk" ] && command -v unzip >/dev/null 2>&1; then
    unzip -p "$apk" "lib/arm64-v8a/$lib" >"$out" 2>/dev/null && return 0
  fi
  rm -f "$out"
  return 1
}

copy_or_extract_lib() {
  local label="$1"
  local lib="$2"
  local apk="$3"
  local dir="$4"
  local dest="$OUT_DIR/$label/$lib"

  if extract_apk_lib "$apk" "$lib" "$dest"; then
    printf '%s\n' "apk:$apk"
    return 0
  fi
  if [ -n "$dir" ] && [ -f "$dir/$lib" ]; then
    cp "$dir/$lib" "$dest"
    printf '%s\n' "dir:$dir"
    return 0
  fi
  rm -f "$dest"
  printf '%s\n' "missing"
  return 1
}

write_lib_side_artifacts() {
  local label="$1"
  local lib="$2"
  local file="$3"
  local safe="${label}_${lib%.so}"
  if [ ! -f "$file" ]; then
    printf '<missing>\n' >"$OUT_DIR/strings/${safe}.filtered.txt"
    printf '<missing>\n' >"$OUT_DIR/dynamic/${safe}.readelf-d.txt"
    printf '<missing>\n' >"$OUT_DIR/symbols/${safe}.exports.txt"
    printf '<missing>\n' >"$OUT_DIR/symbols/${safe}.undefined.txt"
    return
  fi
  file "$file" >"$OUT_DIR/metadata/${safe}.file.txt" 2>/dev/null || true
  readelf -d "$file" >"$OUT_DIR/dynamic/${safe}.readelf-d.txt" 2>/dev/null || true
  strings "$file" 2>/dev/null | grep -E "$KEYWORDS" | sort -u >"$OUT_DIR/strings/${safe}.filtered.txt" || true
  nm -D --defined-only "$file" >"$OUT_DIR/symbols/${safe}.exports.txt" 2>/dev/null || true
  nm -D -u "$file" >"$OUT_DIR/symbols/${safe}.undefined.txt" 2>/dev/null || true
}

record_lib() {
  local label="$1"
  local lib="$2"
  local apk="$3"
  local dir="$4"
  local source file_path exists size sha build needed string_hits
  source="$(copy_or_extract_lib "$label" "$lib" "$apk" "$dir")"
  file_path="$OUT_DIR/$label/$lib"
  if [ -f "$file_path" ]; then
    exists=true
    write_lib_side_artifacts "$label" "$lib" "$file_path"
    size="$(size_for "$file_path")"
    sha="$(sha_for "$file_path")"
    build="$(build_id_for "$file_path")"
    needed="$(needed_for "$file_path")"
    string_hits="$(wc -l <"$OUT_DIR/strings/${label}_${lib%.so}.filtered.txt" 2>/dev/null | tr -d ' ')"
  else
    exists=false
    write_lib_side_artifacts "$label" "$lib" "$file_path"
    size="-"
    sha="-"
    build="-"
    needed="-"
    string_hits=0
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$label" "$lib" "$exists" "$source" "$size" "$sha" "$build" "$needed" "$string_hits" >>"$OUT_DIR/lib_inventory.tsv"
}

write_symbol_diff() {
  local lib="$1"
  local npu_exports="$OUT_DIR/symbols/npu_${lib%.so}.exports.txt"
  local gallery_exports="$OUT_DIR/symbols/gallery_${lib%.so}.exports.txt"
  local npu_names="$OUT_DIR/diff/${lib%.so}.npu.exports.names.txt"
  local gallery_names="$OUT_DIR/diff/${lib%.so}.gallery.exports.names.txt"

  if [ -f "$npu_exports" ] && [ -f "$gallery_exports" ]; then
    awk '{print $NF}' "$npu_exports" | grep -v '^<missing>$' | sort -u >"$npu_names" || true
    awk '{print $NF}' "$gallery_exports" | grep -v '^<missing>$' | sort -u >"$gallery_names" || true
    comm -23 "$npu_names" "$gallery_names" >"$OUT_DIR/diff/${lib%.so}.exports.only_npu.txt" || true
    comm -13 "$npu_names" "$gallery_names" >"$OUT_DIR/diff/${lib%.so}.exports.only_gallery.txt" || true
  fi
}

write_comparison() {
  printf 'library\tnpu_exists\tgallery_exists\tsize_match\tsha256_match\tbuild_id_match\tnpu_build_id\tgallery_build_id\n' >"$OUT_DIR/gallery_comparison.tsv"
  for lib in "${LIBS[@]}"; do
    local npu_file="$OUT_DIR/npu/$lib"
    local gallery_file="$OUT_DIR/gallery/$lib"
    local npu_exists=false gallery_exists=false npu_size gallery_size npu_sha gallery_sha npu_build gallery_build
    [ -f "$npu_file" ] && npu_exists=true
    [ -f "$gallery_file" ] && gallery_exists=true
    npu_size="$(size_for "$npu_file")"
    gallery_size="$(size_for "$gallery_file")"
    npu_sha="$(sha_for "$npu_file")"
    gallery_sha="$(sha_for "$gallery_file")"
    npu_build="$(build_id_for "$npu_file")"
    gallery_build="$(build_id_for "$gallery_file")"
    local build_id_match
    if [ -n "$npu_build" ] && [ "$npu_build" != "-" ] && [ "$npu_build" = "$gallery_build" ]; then
      build_id_match=true
    else
      build_id_match=false
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$lib" "$npu_exists" "$gallery_exists" \
      "$([ "$npu_size" = "$gallery_size" ] && printf true || printf false)" \
      "$([ "$npu_sha" = "$gallery_sha" ] && [ "$npu_sha" != "-" ] && printf true || printf false)" \
      "$build_id_match" \
      "$npu_build" "$gallery_build" >>"$OUT_DIR/gallery_comparison.tsv"
    write_symbol_diff "$lib"
  done
}

scan_model() {
  {
    printf 'model_path=%s\n' "${MODEL_PATH:-}"
    if [ -n "${MODEL_PATH:-}" ] && [ -f "$MODEL_PATH" ]; then
      printf 'model_exists=true\n'
      printf 'model_size=%s\n' "$(size_for "$MODEL_PATH")"
      printf 'model_sha256=%s\n' "$(sha_for "$MODEL_PATH")"
      file "$MODEL_PATH" 2>/dev/null || true
      printf 'qnn_partition_hits='
      grep -a -o -E 'qnn_partition_[0-9]+' "$MODEL_PATH" 2>/dev/null | sort -u | paste -sd ',' -
    else
      printf 'model_exists=false\n'
    fi
  } >"$OUT_DIR/model/model_inventory.txt"

  if [ -n "${MODEL_PATH:-}" ] && [ -f "$MODEL_PATH" ]; then
    strings "$MODEL_PATH" 2>/dev/null | grep -E "$KEYWORDS" | sort -u >"$OUT_DIR/model/model_strings.filtered.txt" || true
  else
    printf '<missing>\n' >"$OUT_DIR/model/model_strings.filtered.txt"
  fi
}

write_summary() {
  local total_libs differing_builds missing_npu missing_gallery model_qnn_hits
  total_libs="${#LIBS[@]}"
  differing_builds="$(awk -F '\t' 'NR > 1 && $6 == "false" {count++} END {print count + 0}' "$OUT_DIR/gallery_comparison.tsv")"
  missing_npu="$(awk -F '\t' 'NR > 1 && $2 == "false" {count++} END {print count + 0}' "$OUT_DIR/gallery_comparison.tsv")"
  missing_gallery="$(awk -F '\t' 'NR > 1 && $3 == "false" {count++} END {print count + 0}' "$OUT_DIR/gallery_comparison.tsv")"
  model_qnn_hits="$(sed -n 's/^qnn_partition_hits=//p' "$OUT_DIR/model/model_inventory.txt")"

  {
    printf '# Backend.NPU runtime stack mismatch investigation\n\n'
    printf '%s\n' "- generated_at: \`$TIMESTAMP\`"
    printf '%s\n' "- npu_apk: \`$NPU_APK\`"
    printf '%s\n' "- npu_lib_dir_fallback: \`$NPU_LIB_DIR\`"
    printf '%s\n' "- gallery_apk: \`$GALLERY_APK\`"
    printf '%s\n' "- gallery_lib_dir_fallback: \`$GALLERY_LIB_DIR\`"
    printf '%s\n' "- model_path: \`${MODEL_PATH:-}\`"
    printf '%s\n\n' "- scope: static comparison only; no install, no app launch, no Engine.initialize, no library replacement"
    printf '## Result\n\n'
    printf '%s\n' "- compared_libraries: \`$total_libs\`"
    printf '%s\n' "- build_id_mismatch_or_missing_count: \`$differing_builds\`"
    printf '%s\n' "- npu_missing_count: \`$missing_npu\`"
    printf '%s\n' "- gallery_missing_count: \`$missing_gallery\`"
    printf '%s\n\n' "- suspected_root_cause: \`runtime_stack_mismatch_candidate\`"
    printf '## Gallery comparison\n\n```text\n'
    cat "$OUT_DIR/gallery_comparison.tsv"
    printf '```\n\n'
    printf '## NPU/Gallery inventory\n\n```text\n'
    cat "$OUT_DIR/lib_inventory.tsv"
    printf '```\n\n'
    printf '## Model hints\n\n```text\n'
    cat "$OUT_DIR/model/model_inventory.txt"
    printf '```\n\n'
    case "$model_qnn_hits" in
      *qnn_partition_0*) printf '%s\n' "- qnn_partition_0_present: \`true\`" ;;
      *) printf '%s\n' "- qnn_partition_0_present: \`unknown-or-not-found-by-static-scan\`" ;;
    esac
    printf '\n## Key files\n\n'
    printf '%s\n' "- \`$OUT_DIR/lib_inventory.tsv\`"
    printf '%s\n' "- \`$OUT_DIR/gallery_comparison.tsv\`"
    printf '%s\n' "- \`$OUT_DIR/model/model_strings.filtered.txt\`"
    printf '%s\n' "- \`$OUT_DIR/strings\`"
    printf '%s\n' "- \`$OUT_DIR/dynamic\`"
    printf '%s\n' "- \`$OUT_DIR/symbols\`"
    printf '%s\n' "- \`$OUT_DIR/diff\`"
  } >"$OUT_DIR/summary.md"
}

log "output: $OUT_DIR"
printf 'label\tlibrary\texists\tsource\tsize\tsha256\tbuild_id\tneeded\tfiltered_string_hits\n' >"$OUT_DIR/lib_inventory.tsv"

for lib in "${LIBS[@]}"; do
  record_lib "npu" "$lib" "$NPU_APK" "$NPU_LIB_DIR"
  record_lib "gallery" "$lib" "$GALLERY_APK" "$GALLERY_LIB_DIR"
done

write_comparison
scan_model
write_summary

log "wrote $OUT_DIR/summary.md"
printf '%s\n' "$OUT_DIR"
