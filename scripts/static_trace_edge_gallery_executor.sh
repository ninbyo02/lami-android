#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
EDGE_APK_DIR="$ROOT_DIR/artifacts/external/edge_gallery_apks"
EDGE_STATIC_DIR="$ROOT_DIR/artifacts/edge_gallery_static"
OUT_DIR="$ROOT_DIR/artifacts/static_edge_gallery_executor_trace"

KEYWORD_REGEX='GPU_ARTISAN|CPU_ARTISAN|GOOGLE_TENSOR_ARTISAN|LlmGpuArtisanExecutor|Artisan model detected|RuntimeConfig|BackendConstraint|backend constraint|PreferredEngineType|preferred engine types|GpuOptions|kv cache|kv_cache|cache dir|cacheDir|maxTokens|SamplerConfig|topK|topP|temperature|generateContent|generateContentStream|nativeGenerateContent|nativeGenerateContentStream|nativeRunPrefill|nativeRunDecode|EngineConfig|ConversationConfig|LiteRtLm|CompiledModelExecutor|LlmLiteRtCompiledModelExecutor|Qualcomm|QNN|HTP|GPU delegate|tflite_gpu_kv_cache|tflite_opencl_kv_cache'

usage() {
  cat <<USAGE
Usage:
  scripts/static_trace_edge_gallery_executor.sh [--edge-apks DIR] [--edge-static DIR] [--output DIR]

Collects static Edge Gallery executor/runtime selection evidence from already
downloaded APK splits and existing static extraction artifacts. Missing inputs
are reported as path candidates instead of treated as fatal.

Default Edge APK dir:    $EDGE_APK_DIR
Default static artifact: $EDGE_STATIC_DIR
Default output dir:      $OUT_DIR
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --edge-apks)
      EDGE_APK_DIR="${2:?missing --edge-apks value}"
      shift 2
      ;;
    --edge-static)
      EDGE_STATIC_DIR="${2:?missing --edge-static value}"
      shift 2
      ;;
    --output)
      OUT_DIR="${2:?missing --output value}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

mkdir -p "$OUT_DIR"

SUMMARY="$OUT_DIR/summary.md"
STATIC_HITS="$OUT_DIR/static_artifact_keyword_hits.txt"
DEX_HITS="$OUT_DIR/apk_dex_keyword_hits.txt"
NATIVE_STRING_HITS="$OUT_DIR/native_string_keyword_hits.txt"
NATIVE_SYMBOL_HITS="$OUT_DIR/native_symbol_keyword_hits.txt"
NATIVE_NEEDED="$OUT_DIR/native_needed_libraries.txt"
EXTRACTED_NATIVE_DIR="$OUT_DIR/extracted_native"

: >"$STATIC_HITS"
: >"$DEX_HITS"
: >"$NATIVE_STRING_HITS"
: >"$NATIVE_SYMBOL_HITS"
: >"$NATIVE_NEEDED"
mkdir -p "$EXTRACTED_NATIVE_DIR"

zip_entries() {
  local apk="$1"
  if command -v zipinfo >/dev/null 2>&1; then
    zipinfo -1 "$apk" 2>/dev/null
  elif command -v jar >/dev/null 2>&1; then
    jar tf "$apk" 2>/dev/null
  else
    return 1
  fi
}

grep_keywords_from_stream() {
  local label="$1"
  local output="$2"
  grep -Eai "$KEYWORD_REGEX" 2>/dev/null | sed "s#^#$label:#" >>"$output" || true
}

grep_keywords_from_file() {
  local file="$1"
  local label="$2"
  local output="$3"
  grep -Eain "$KEYWORD_REGEX" "$file" 2>/dev/null | sed "s#^#$label:#" >>"$output" || true
}

sha_for() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf 'unavailable'
  fi
}

collect_existing_static_artifacts() {
  [ -d "$EDGE_STATIC_DIR" ] || return 0
  find "$EDGE_STATIC_DIR" -type f \
    \( -name '*.txt' -o -name '*.md' -o -name '*.tsv' -o -name '*.filtered.txt' \) 2>/dev/null |
    sort |
    while IFS= read -r file; do
      grep_keywords_from_file "$file" "$file" "$STATIC_HITS"
    done
}

collect_apk_entries() {
  [ -d "$EDGE_APK_DIR" ] || return 0
  find "$EDGE_APK_DIR" -maxdepth 1 -type f -name '*.apk' 2>/dev/null |
    sort |
    while IFS= read -r apk; do
      local apk_base
      apk_base="$(basename "$apk")"
      (zip_entries "$apk" |
        grep -E '(^classes[0-9]*\.dex$|^lib/arm64-v8a/.*\.so$)' || true) |
        while IFS= read -r entry; do
          case "$entry" in
            classes*.dex)
              unzip -p "$apk" "$entry" 2>/dev/null |
                strings -a 2>/dev/null |
                grep_keywords_from_stream "$apk_base:$entry" "$DEX_HITS"
              ;;
            lib/arm64-v8a/*.so)
              local lib_name out_name out_path
              lib_name="$(basename "$entry")"
              out_name="${apk_base}__${lib_name}"
              out_path="$EXTRACTED_NATIVE_DIR/$out_name"
              if [ ! -f "$out_path" ]; then
                unzip -p "$apk" "$entry" >"$out_path" 2>/dev/null || true
              fi
              if [ -f "$out_path" ]; then
                strings -a "$out_path" 2>/dev/null |
                  grep_keywords_from_stream "$apk_base:$entry" "$NATIVE_STRING_HITS"
                if command -v nm >/dev/null 2>&1; then
                  nm -D "$out_path" 2>/dev/null |
                    grep_keywords_from_stream "$apk_base:$entry" "$NATIVE_SYMBOL_HITS"
                fi
                if command -v readelf >/dev/null 2>&1; then
                  {
                    printf '===== %s:%s sha256=%s =====\n' "$apk_base" "$entry" "$(sha_for "$out_path")"
                    readelf -d "$out_path" 2>/dev/null | grep -E 'NEEDED|SONAME|RPATH|RUNPATH' || true
                  } >>"$NATIVE_NEEDED"
                fi
              fi
              ;;
          esac
        done
    done
}

collect_existing_static_artifacts
collect_apk_entries

count_lines() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -l <"$file" | awk '{print $1}'
  else
    printf '0'
  fi
}

cat >"$SUMMARY" <<SUMMARY
# Static Edge Gallery Executor Trace

Generated by \`scripts/static_trace_edge_gallery_executor.sh\`.

## Inputs

- Edge APK dir: \`$EDGE_APK_DIR\`
- Edge static dir: \`$EDGE_STATIC_DIR\`
- Output dir: \`$OUT_DIR\`

## Input Availability

- edge_apk_dir_present=$([ -d "$EDGE_APK_DIR" ] && printf 'true' || printf 'false')
- edge_static_dir_present=$([ -d "$EDGE_STATIC_DIR" ] && printf 'true' || printf 'false')

If either input is missing, use these expected paths:

- \`artifacts/external/edge_gallery_apks/\`
- \`artifacts/edge_gallery_static/\`

## Output Files

| File | Lines |
| --- | ---: |
| \`static_artifact_keyword_hits.txt\` | $(count_lines "$STATIC_HITS") |
| \`apk_dex_keyword_hits.txt\` | $(count_lines "$DEX_HITS") |
| \`native_string_keyword_hits.txt\` | $(count_lines "$NATIVE_STRING_HITS") |
| \`native_symbol_keyword_hits.txt\` | $(count_lines "$NATIVE_SYMBOL_HITS") |
| \`native_needed_libraries.txt\` | $(count_lines "$NATIVE_NEEDED") |

## Primary Keywords

\`\`\`text
$KEYWORD_REGEX
\`\`\`

## Reading Guidance

- \`native_string_keyword_hits.txt\` is the first place to check for
  \`GPU_ARTISAN\`, \`LlmGpuArtisanExecutor\`, backend constraint, RuntimeConfig,
  KV-cache, and JNI generate entry point strings.
- \`apk_dex_keyword_hits.txt\` helps distinguish app/Dex-visible API from
  native-only/runtime-internal strings.
- \`native_symbol_keyword_hits.txt\` is best-effort only; stripped libraries may
  not expose useful dynamic symbols.
- \`native_needed_libraries.txt\` records loaded-library dependency hints where
  \`readelf\` is available.
SUMMARY

printf 'Wrote static Edge Gallery executor trace to: %s\n' "$OUT_DIR"
