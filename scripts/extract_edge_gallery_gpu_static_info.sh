#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
INPUT_DIR="$ROOT_DIR/artifacts/external/edge_gallery_apks"
OUTPUT_DIR="$ROOT_DIR/artifacts/edge_gallery_static"
DRY_RUN=0

FOCUS_PATTERN='model|accelerator|gpu|backend|delegate|sampler|cache|litert|gemma|sm8750|qualcomm|opencl|vulkan|webgpu|qnn|npu|tpu|conversation|engineconfig|engine|maxtoken|max_tokens|topk|top_k|topp|top_p|temperature|speculative|thinking'
ARTISAN_PATTERN='GPU_ARTISAN|CPU_ARTISAN|GOOGLE_TENSOR_ARTISAN|Artisan model detected|Switching backend from GPU to GPU_ARTISAN|LlmGpuArtisanExecutor|LlmLiteRtCompiledModelExecutor|LlmLiteRtCompiledModelExecutorDynamic|backend constraint mismatch|Model requires one of|Supported backends are|No preferred engine types defined|preferred engine types|RuntimeConfig|EngineConfig|BackendType|AdapterBackend|EncoderBackend|SamplerBackend|tflite_gpu_kv_cache|tflite_opencl_kv_cache|GPU sampler unavailable|Falling back to CPU sampling'

usage() {
  printf 'usage: %s [--input <edge-gallery-apk-dir>] [--output <out-dir>] [--dry-run]\n' "$0"
  printf 'default input: %s\n' "$INPUT_DIR"
  printf 'default output: %s\n' "$OUTPUT_DIR"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --input)
      shift
      INPUT_DIR="${1:-}"
      ;;
    --output)
      shift
      OUTPUT_DIR="${1:-}"
      ;;
    --dry-run)
      DRY_RUN=1
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      INPUT_DIR="$1"
      ;;
  esac
  shift || true
done

safe_name() {
  basename "$1" | sed 's/[^A-Za-z0-9._-]/_/g'
}

entry_safe_name() {
  printf '%s' "$1" | sed 's#[/:]#_#g; s/[^A-Za-z0-9._-]/_/g'
}

zip_entries() {
  local apk="$1"
  if command -v zipinfo >/dev/null 2>&1; then
    zipinfo -1 "$apk" 2>/dev/null
  else
    unzip -Z1 "$apk" 2>/dev/null
  fi
}

sha_for() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1 && [ -f "$file" ]; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf 'unavailable'
  fi
}

size_for() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -c <"$file" 2>/dev/null | awk '{print $1}'
  else
    printf '0'
  fi
}

build_id_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  else
    printf 'unavailable'
  fi
}

filter_entry_strings() {
  local apk="$1"
  local entry="$2"
  local out="$3"
  unzip -p "$apk" "$entry" 2>/dev/null |
    strings -a 2>/dev/null |
    grep -Eai "$FOCUS_PATTERN" |
    sort -u >"$out" || true
}

filter_artisan_strings() {
  local source="$1"
  local out="$2"
  strings -a "$source" 2>/dev/null |
    grep -Ea "$ARTISAN_PATTERN" |
    sort -u >"$out" || true
}

write_artisan_keyword_presence_header() {
  local out="$1"
  printf 'source\tkeyword\tpresent\n' >"$out"
}

append_artisan_keyword_presence() {
  local label="$1"
  local strings_file="$2"
  local out="$3"
  while IFS= read -r keyword; do
    [ -z "$keyword" ] && continue
    if grep -Fq "$keyword" "$strings_file" 2>/dev/null; then
      printf '%s\t%s\tyes\n' "$label" "$keyword" >>"$out"
    else
      printf '%s\t%s\tno\n' "$label" "$keyword" >>"$out"
    fi
  done <<'EOF'
GPU_ARTISAN
CPU_ARTISAN
GOOGLE_TENSOR_ARTISAN
Artisan model detected
Switching backend from GPU to GPU_ARTISAN
LlmGpuArtisanExecutor
LlmLiteRtCompiledModelExecutor
LlmLiteRtCompiledModelExecutorDynamic
backend constraint mismatch
Model requires one of
Supported backends are
No preferred engine types defined
preferred engine types
RuntimeConfig
EngineConfig
BackendType
AdapterBackend
EncoderBackend
SamplerBackend
tflite_gpu_kv_cache
tflite_opencl_kv_cache
GPU sampler unavailable
Falling back to CPU sampling
EOF
}

write_artisan_analysis_readme() {
  local out="$1"
  cat >"$out" <<'EOF'
# Edge Gallery backend / artisan static analysis

This directory is static-only. It does not use logcat and does not run Edge
Gallery or LAMI inference.

Files:

- `keyword_presence.tsv`: exact keyword presence by APK entry.
- `all_backend_artisan_hits.txt`: merged unique strings for backend/artisan/executor clues.
- `dex/*.txt`: filtered strings from `classes.dex`, `classes2.dex`, ...
- `native/*.txt`: filtered strings from `libLiteRt.so` and `liblitertlm_jni.so`.
- `jadx_hits.txt`: optional source hits when `jadx` is installed.

Interpretation:

- `GPU_ARTISAN` / `LlmGpuArtisanExecutor` hits show that the runtime stack has
  an artisan executor path, but they do not prove that Edge Gallery selected it
  for the observed model.
- `Artisan model detected. Switching backend from GPU to GPU_ARTISAN.` is a
  strong clue that model metadata/backend constraints can rewrite a simple GPU
  request.
- Do not copy Edge Gallery native libraries into LAMI from these artifacts.
EOF
}

write_app_data_instructions() {
  local out="$1"
  cat >"$out" <<'EOF'
# Edge Gallery app data static check

No logcat is required for this step.

1. Find the installed package name:

   adb shell pm list packages | grep -i 'gallery\|edge\|google'

2. If the app is debuggable, list static app data locations:

   adb shell run-as <edge_gallery_package> ls -la
   adb shell run-as <edge_gallery_package> find shared_prefs files databases -maxdepth 4 -print

3. If `run-as` fails with a package/debuggable error, record:

   run_as_available=false
   run_as_failure=<exact shell message>

4. Do not use logcat for this comparison. Do not copy Edge Gallery native
   runtime files into LAMI. The app data check is only for model path,
   model size, accelerator config, cache, and preference inspection.
EOF
}

if [ -z "$INPUT_DIR" ] || [ -z "$OUTPUT_DIR" ]; then
  usage
  exit 1
fi

if [ ! -d "$INPUT_DIR" ]; then
  printf 'missing Edge Gallery APK directory: %s\n' "$INPUT_DIR"
  exit 1
fi

APK_LIST="$(find "$INPUT_DIR" -maxdepth 1 -type f -name '*.apk' | sort)"

printf 'edge_gallery_apk_input=%s\n' "$INPUT_DIR"
printf 'edge_gallery_static_output=%s\n' "$OUTPUT_DIR"

if [ -z "$APK_LIST" ]; then
  printf 'no APK files found\n'
  exit 1
fi

printf 'apk_count=%s\n' "$(printf '%s\n' "$APK_LIST" | grep -c '\.apk$')"

if [ "$DRY_RUN" = "1" ]; then
  printf 'dry_run=true\n'
  printf '%s\n' "$APK_LIST"
  printf 'planned_outputs=summary.txt,apk_inventory.tsv,native_lib_inventory.tsv,apk_entries/,native_libs/,strings/,backend_artisan_analysis/,app_data_static_check_instructions.md\n'
  exit 0
fi

mkdir -p "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/apk_entries"
mkdir -p "$OUTPUT_DIR/native_libs"
mkdir -p "$OUTPUT_DIR/strings"
mkdir -p "$OUTPUT_DIR/backend_artisan_analysis/dex"
mkdir -p "$OUTPUT_DIR/backend_artisan_analysis/native"

SUMMARY="$OUTPUT_DIR/summary.txt"
APK_INVENTORY="$OUTPUT_DIR/apk_inventory.tsv"
NATIVE_INVENTORY="$OUTPUT_DIR/native_lib_inventory.tsv"
ARTISAN_DIR="$OUTPUT_DIR/backend_artisan_analysis"
ARTISAN_KEYWORD_PRESENCE="$ARTISAN_DIR/keyword_presence.tsv"
ARTISAN_ALL_HITS="$ARTISAN_DIR/all_backend_artisan_hits.txt"
ALL_CLASSES="$OUTPUT_DIR/strings/all_classes_dex.filtered.txt"
ALL_LITERT="$OUTPUT_DIR/strings/all_libLiteRt_so.filtered.txt"
ALL_LITERTLM="$OUTPUT_DIR/strings/all_liblitertlm_jni_so.filtered.txt"
ALL_FOCUS="$OUTPUT_DIR/strings/all_edge_gallery_gpu_focus.filtered.txt"

: >"$ALL_CLASSES"
: >"$ALL_LITERT"
: >"$ALL_LITERTLM"
: >"$ALL_FOCUS"
: >"$ARTISAN_ALL_HITS"
write_artisan_keyword_presence_header "$ARTISAN_KEYWORD_PRESENCE"
write_artisan_analysis_readme "$ARTISAN_DIR/README.md"

{
  printf 'generated_at_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || printf 'unavailable')"
  printf 'input_dir=%s\n' "$INPUT_DIR"
  printf 'output_dir=%s\n' "$OUTPUT_DIR"
  printf 'focus_pattern=%s\n' "$FOCUS_PATTERN"
} >"$SUMMARY"

printf 'apk_file\tsize_bytes\tsha256\n' >"$APK_INVENTORY"
printf 'apk_file\tentry\tpresent\textracted_file\tsize_bytes\tsha256\tbuild_id\n' >"$NATIVE_INVENTORY"

while IFS= read -r apk; do
  [ -z "$apk" ] && continue
  apk_base="$(safe_name "$apk")"
  apk_entries_file="$OUTPUT_DIR/apk_entries/${apk_base}.entries.txt"
  zip_entries "$apk" >"$apk_entries_file"

  printf '%s\t%s\t%s\n' "$apk" "$(size_for "$apk")" "$(sha_for "$apk")" >>"$APK_INVENTORY"

  native_dir="$OUTPUT_DIR/native_libs/${apk_base}"
  mkdir -p "$native_dir"

  while IFS= read -r entry; do
    [ -z "$entry" ] && continue
    entry_out="$OUTPUT_DIR/strings/${apk_base}__$(entry_safe_name "$entry").filtered.txt"
    filter_entry_strings "$apk" "$entry" "$entry_out"
    cat "$entry_out" >>"$ALL_CLASSES"
    cat "$entry_out" >>"$ALL_FOCUS"
    artisan_out="$ARTISAN_DIR/dex/${apk_base}__$(entry_safe_name "$entry").backend_artisan.txt"
    unzip -p "$apk" "$entry" 2>/dev/null |
      strings -a 2>/dev/null |
      grep -Ea "$ARTISAN_PATTERN" |
      sort -u >"$artisan_out" || true
    cat "$artisan_out" >>"$ARTISAN_ALL_HITS"
    append_artisan_keyword_presence "$apk_base:$entry" "$artisan_out" "$ARTISAN_KEYWORD_PRESENCE"
  done <<EOF
$(grep -E '^classes([0-9]+)?\.dex$' "$apk_entries_file" || true)
EOF

  while IFS= read -r entry; do
    [ -z "$entry" ] && continue
    lib_name="$(basename "$entry")"
    extracted="$native_dir/$lib_name"
    if unzip -p "$apk" "$entry" >"$extracted" 2>/dev/null; then
      printf '%s\t%s\tyes\t%s\t%s\t%s\t%s\n' \
        "$apk" \
        "$entry" \
        "$extracted" \
        "$(size_for "$extracted")" \
        "$(sha_for "$extracted")" \
        "$(build_id_for "$extracted")" >>"$NATIVE_INVENTORY"

      case "$lib_name" in
        libLiteRt.so|liblitertlm_jni.so)
          lib_out="$OUTPUT_DIR/strings/${apk_base}__${lib_name}.filtered.txt"
          strings -a "$extracted" 2>/dev/null |
            grep -Eai "$FOCUS_PATTERN" |
            sort -u >"$lib_out" || true
          artisan_lib_out="$ARTISAN_DIR/native/${apk_base}__${lib_name}.backend_artisan.txt"
          filter_artisan_strings "$extracted" "$artisan_lib_out"
          cat "$artisan_lib_out" >>"$ARTISAN_ALL_HITS"
          append_artisan_keyword_presence "$apk_base:$entry" "$artisan_lib_out" "$ARTISAN_KEYWORD_PRESENCE"
          if [ "$lib_name" = "libLiteRt.so" ]; then
            cat "$lib_out" >>"$ALL_LITERT"
          else
            cat "$lib_out" >>"$ALL_LITERTLM"
          fi
          cat "$lib_out" >>"$ALL_FOCUS"
          ;;
      esac
    else
      printf '%s\t%s\tno\tnone\t0\tunavailable\tunavailable\n' "$apk" "$entry" >>"$NATIVE_INVENTORY"
    fi
  done <<EOF
$(grep -E '^lib/arm64-v8a/.*\.so$' "$apk_entries_file" || true)
EOF
done <<EOF
$APK_LIST
EOF

sort -u "$ALL_CLASSES" -o "$ALL_CLASSES"
sort -u "$ALL_LITERT" -o "$ALL_LITERT"
sort -u "$ALL_LITERTLM" -o "$ALL_LITERTLM"
sort -u "$ALL_FOCUS" -o "$ALL_FOCUS"
sort -u "$ARTISAN_ALL_HITS" -o "$ARTISAN_ALL_HITS"

if command -v jadx >/dev/null 2>&1; then
  JADX_DIR="$ARTISAN_DIR/jadx_sources"
  mkdir -p "$JADX_DIR"
  while IFS= read -r apk; do
    [ -z "$apk" ] && continue
    jadx -q -d "$JADX_DIR/$(safe_name "$apk")" "$apk" >/dev/null 2>&1 || true
  done <<EOF
$APK_LIST
EOF
  if command -v rg >/dev/null 2>&1; then
    rg -n "$ARTISAN_PATTERN" "$JADX_DIR" >"$ARTISAN_DIR/jadx_hits.txt" 2>/dev/null || true
  else
    grep -RInE "$ARTISAN_PATTERN" "$JADX_DIR" >"$ARTISAN_DIR/jadx_hits.txt" 2>/dev/null || true
  fi
else
  printf 'jadx_unavailable=true\n' >"$ARTISAN_DIR/jadx_hits.txt"
fi

write_app_data_instructions "$OUTPUT_DIR/app_data_static_check_instructions.md"

{
  printf 'apk_inventory=%s\n' "$APK_INVENTORY"
  printf 'native_lib_inventory=%s\n' "$NATIVE_INVENTORY"
  printf 'classes_dex_filtered=%s\n' "$ALL_CLASSES"
  printf 'libLiteRt_filtered=%s\n' "$ALL_LITERT"
  printf 'liblitertlm_jni_filtered=%s\n' "$ALL_LITERTLM"
  printf 'all_focus_filtered=%s\n' "$ALL_FOCUS"
  printf 'backend_artisan_analysis=%s\n' "$ARTISAN_DIR"
  printf 'backend_artisan_keyword_presence=%s\n' "$ARTISAN_KEYWORD_PRESENCE"
  printf 'backend_artisan_all_hits=%s\n' "$ARTISAN_ALL_HITS"
  printf 'app_data_instructions=%s\n' "$OUTPUT_DIR/app_data_static_check_instructions.md"
} >>"$SUMMARY"

printf 'wrote %s\n' "$OUTPUT_DIR"
