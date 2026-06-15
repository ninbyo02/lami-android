#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
EDGE_APK_DIR="$ROOT_DIR/artifacts/external/edge_gallery_apks"
EDGE_STATIC_DIR="$ROOT_DIR/artifacts/edge_gallery_static"
OUT_DIR="$ROOT_DIR/artifacts/static_edge_gallery_executor_trace"
LAMI_INPUT="$ROOT_DIR/app/build/outputs/apk/standardGpuMinimalRuntimeCandidate/debug/app-standardGpuMinimalRuntimeCandidate-debug.apk"
COMPARE_OUT_DIR="$ROOT_DIR/artifacts/apk_native_diff"
SELF_TEST=false

KEYWORD_REGEX='GPU_ARTISAN|CPU_ARTISAN|GOOGLE_TENSOR_ARTISAN|LlmGpuArtisanExecutor|Artisan model detected|RuntimeConfig|BackendConstraint|backend constraint|PreferredEngineType|preferred engine types|GpuOptions|kv cache|kv_cache|cache dir|cacheDir|maxTokens|SamplerConfig|topK|topP|temperature|generateContent|generateContentStream|nativeGenerateContent|nativeGenerateContentStream|nativeRunPrefill|nativeRunDecode|EngineConfig|ConversationConfig|LiteRtLm|CompiledModelExecutor|LlmLiteRtCompiledModelExecutor|Qualcomm|QNN|HTP|GPU delegate|tflite_gpu_kv_cache|tflite_opencl_kv_cache'
TRACE_TOKENS=(
  GPU_ARTISAN
  LlmGpuArtisanExecutor
  Artisan
  RuntimeConfig
  GetRuntimeConfig
  BackendConstraint
  PreferredEngineType
  GpuOptions
  LrtCreateGpuOptionsFromToml
  tflite_gpu_kv_cache
  kv_cache
  nativeGenerateContent
  nativeGenerateContentStream
  nativeRunPrefill
  nativeRunDecode
  CompiledModelExecutor
  LlmLiteRtCompiledModelExecutor
  LiteRtCompiledModelExecutor
  "backend constraint"
  "preferred engine"
  cache_dir
  max_tokens
  sampler
  top_k
  top_p
  temperature
  decode
  prefill
  executor
  gpu
  qnn
  qualcomm
)

usage() {
  cat <<USAGE
Usage:
  scripts/static_trace_edge_gallery_executor.sh [--edge-apks DIR] [--edge-static DIR] [--output DIR]
  scripts/static_trace_edge_gallery_executor.sh [--edge-apks DIR] [--lami-apk APK] [--compare-output DIR]
  scripts/static_trace_edge_gallery_executor.sh --self-test

Collects static Edge Gallery executor/runtime selection evidence from already
downloaded APK splits and existing static extraction artifacts. Missing inputs
are reported as path candidates instead of treated as fatal.

Default Edge APK dir:    $EDGE_APK_DIR
Default static artifact: $EDGE_STATIC_DIR
Default output dir:      $OUT_DIR
Default Lami input:      $LAMI_INPUT
Default compare output:  $COMPARE_OUT_DIR
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
    --lami-apk|--lami-dir)
      LAMI_INPUT="${2:?missing Lami input}"
      shift 2
      ;;
    --compare-output)
      COMPARE_OUT_DIR="${2:?missing --compare-output value}"
      shift 2
      ;;
    --self-test)
      SELF_TEST=true
      shift
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

collect_material_from_apk() {
  local apk="$1"
  local label="$2"
  local out="$3"
  local tmpdir
  tmpdir="$(mktemp -d)"
  (zip_entries "$apk" |
    grep -E '(^classes[0-9]*\.dex$|^lib/arm64-v8a/.*\.so$)' || true) |
    while IFS= read -r entry; do
      case "$entry" in
        classes*.dex)
          unzip -p "$apk" "$entry" 2>/dev/null |
            strings -a 2>/dev/null |
            sed "s#^#$label:$entry:#" >>"$out" || true
          ;;
        lib/arm64-v8a/*.so)
          local lib_name lib_path
          lib_name="$(basename "$entry")"
          lib_path="$tmpdir/$lib_name"
          unzip -p "$apk" "$entry" >"$lib_path" 2>/dev/null || true
          if [ -f "$lib_path" ]; then
            strings -a "$lib_path" 2>/dev/null |
              sed "s#^#$label:$entry:#" >>"$out" || true
            if command -v nm >/dev/null 2>&1; then
              nm -D "$lib_path" 2>/dev/null |
                sed "s#^#$label:$entry:#" >>"$out" || true
            fi
          fi
          ;;
      esac
    done
  rm -rf "$tmpdir"
}

collect_material_from_input() {
  local input="$1"
  local label="$2"
  local out="$3"
  : >"$out"
  if [ -f "$input" ]; then
    collect_material_from_apk "$input" "$label:$(basename "$input")" "$out"
    return 0
  fi
  if [ -d "$input" ]; then
    local apk_count
    apk_count="$(find "$input" -maxdepth 1 -type f -name '*.apk' 2>/dev/null | wc -l | awk '{print $1}')"
    if [ "$apk_count" != "0" ]; then
      find "$input" -maxdepth 1 -type f -name '*.apk' 2>/dev/null | sort |
        while IFS= read -r apk; do
          collect_material_from_apk "$apk" "$label:$(basename "$apk")" "$out"
        done
      return 0
    fi
    find "$input" -type f \( -name '*.so' -o -name '*.dex' -o -name '*.txt' -o -name '*.md' \) 2>/dev/null |
      sort |
      while IFS= read -r file; do
        if [[ "$file" == *.so ]]; then
          strings -a "$file" 2>/dev/null |
            sed "s#^#$label:$file:#" >>"$out" || true
          if command -v nm >/dev/null 2>&1; then
            nm -D "$file" 2>/dev/null |
              sed "s#^#$label:$file:#" >>"$out" || true
          fi
        else
          strings -a "$file" 2>/dev/null |
            sed "s#^#$label:$file:#" >>"$out" || true
        fi
      done
    return 0
  fi
  return 1
}

token_count() {
  local token="$1"
  local file="$2"
  awk -v wanted="$token" '
    BEGIN { wanted = tolower(wanted); count = 0 }
    index(tolower($0), wanted) > 0 { count++ }
    END { print count }
  ' "$file" 2>/dev/null
}

token_sample() {
  local token="$1"
  local file="$2"
  grep -Fai "$token" "$file" 2>/dev/null |
    sed -n '1p' |
    tr '\t' ' ' |
    cut -c1-180 || true
}

hash_stream() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    cat >/dev/null
    printf 'unavailable'
  fi
}

comma_join_file() {
  local file="$1"
  if [ -s "$file" ]; then
    paste -sd, "$file" | cut -c1-800
  else
    printf 'none'
  fi
}

write_executor_selection_trace() {
  local edge_input="$1"
  local lami_input="$2"
  local out_dir="$3"
  mkdir -p "$out_dir"

  local trace_tsv summary edge_only lami_only common
  trace_tsv="$out_dir/executor_selection_trace.tsv"
  summary="$out_dir/executor_selection_trace_summary.txt"
  edge_only="$out_dir/edge_only_executor_tokens.txt"
  lami_only="$out_dir/lami_only_executor_tokens.txt"
  common="$out_dir/common_executor_tokens.txt"
  : >"$edge_only"
  : >"$lami_only"
  : >"$common"

  if [ ! -e "$edge_input" ] || [ ! -e "$lami_input" ]; then
    {
      printf 'EDGE_GALLERY_EXECUTOR_SELECTION_TRACE_FINGERPRINT=unavailable\n'
      printf 'LAMI_EXECUTOR_SELECTION_TRACE_FINGERPRINT=unavailable\n'
      printf 'EXECUTOR_SELECTION_TRACE_DIFF_SUMMARY=missing_input\n'
      printf 'EDGE_INPUT_PRESENT=%s\n' "$([ -e "$edge_input" ] && printf true || printf false)"
      printf 'LAMI_INPUT_PRESENT=%s\n' "$([ -e "$lami_input" ] && printf true || printf false)"
      printf 'EDGE_INPUT=%s\n' "$edge_input"
      printf 'LAMI_INPUT=%s\n' "$lami_input"
    } >"$summary"
    printf 'token\tedge_present\tlami_present\tedge_count\tlami_count\tedge_sample\tlami_sample\n' >"$trace_tsv"
    return 0
  fi

  local tmpdir edge_material lami_material
  tmpdir="$(mktemp -d)"
  edge_material="$tmpdir/edge_material.txt"
  lami_material="$tmpdir/lami_material.txt"
  collect_material_from_input "$edge_input" EDGE "$edge_material" || true
  collect_material_from_input "$lami_input" LAMI "$lami_material" || true

  printf 'token\tedge_present\tlami_present\tedge_count\tlami_count\tedge_sample\tlami_sample\n' >"$trace_tsv"
  local token edge_count lami_count edge_present lami_present edge_sample lami_sample
  for token in "${TRACE_TOKENS[@]}"; do
    edge_count="$(token_count "$token" "$edge_material")"
    lami_count="$(token_count "$token" "$lami_material")"
    edge_present=no
    lami_present=no
    [ "$edge_count" != "0" ] && edge_present=yes
    [ "$lami_count" != "0" ] && lami_present=yes
    edge_sample="$(token_sample "$token" "$edge_material")"
    lami_sample="$(token_sample "$token" "$lami_material")"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$token" "$edge_present" "$lami_present" "$edge_count" "$lami_count" "$edge_sample" "$lami_sample" >>"$trace_tsv"
    if [ "$edge_present" = yes ] && [ "$lami_present" = yes ]; then
      printf '%s\n' "$token" >>"$common"
    elif [ "$edge_present" = yes ]; then
      printf '%s\n' "$token" >>"$edge_only"
    elif [ "$lami_present" = yes ]; then
      printf '%s\n' "$token" >>"$lami_only"
    fi
  done

  local edge_fp lami_fp diff_summary
  edge_fp="$(awk -F '\t' 'NR > 1 {print $1 "=" $2}' "$trace_tsv" | hash_stream)"
  lami_fp="$(awk -F '\t' 'NR > 1 {print $1 "=" $3}' "$trace_tsv" | hash_stream)"
  diff_summary="same_executor_selection_tokens"
  if [ "$edge_fp" != "$lami_fp" ]; then
    diff_summary="different_executor_selection_tokens"
  fi
  {
    printf 'EDGE_GALLERY_EXECUTOR_SELECTION_TRACE_FINGERPRINT=%s\n' "$edge_fp"
    printf 'LAMI_EXECUTOR_SELECTION_TRACE_FINGERPRINT=%s\n' "$lami_fp"
    printf 'EXECUTOR_SELECTION_TRACE_DIFF_SUMMARY=%s\n' "$diff_summary"
    printf 'EDGE_ONLY_EXECUTOR_TOKENS=%s\n' "$(comma_join_file "$edge_only")"
    printf 'LAMI_ONLY_EXECUTOR_TOKENS=%s\n' "$(comma_join_file "$lami_only")"
    printf 'COMMON_EXECUTOR_TOKENS=%s\n' "$(comma_join_file "$common")"
    printf 'EDGE_INPUT=%s\n' "$edge_input"
    printf 'LAMI_INPUT=%s\n' "$lami_input"
  } >"$summary"
  rm -rf "$tmpdir"
}

run_self_test() {
  local tmpdir edge_dir lami_dir out_dir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT
  edge_dir="$tmpdir/edge"
  lami_dir="$tmpdir/lami"
  out_dir="$tmpdir/out"
  mkdir -p "$edge_dir" "$lami_dir"
  printf '%s\n' \
    'GPU_ARTISAN' \
    'LlmGpuArtisanExecutor::Create with the following config:' \
    'backend constraint is matched:' \
    'tflite_gpu_kv_cache' >"$edge_dir/libedge.so"
  printf '%s\n' \
    'Backend.GPU' \
    'nativeGenerateContentStream' \
    'sampler top_k top_p temperature executor' >"$lami_dir/liblami.so"
  write_executor_selection_trace "$edge_dir" "$lami_dir" "$out_dir"
  grep -Fq 'EXECUTOR_SELECTION_TRACE_DIFF_SUMMARY=different_executor_selection_tokens' "$out_dir/executor_selection_trace_summary.txt" || {
    echo "self-test failed: missing different trace summary" >&2
    cat "$out_dir/executor_selection_trace_summary.txt" >&2
    exit 1
  }
  grep -Fq 'GPU_ARTISAN' "$out_dir/edge_only_executor_tokens.txt" || {
    echo "self-test failed: missing edge-only token" >&2
    exit 1
  }
  grep -Fq 'nativeGenerateContentStream' "$out_dir/lami_only_executor_tokens.txt" || {
    echo "self-test failed: missing lami-only token" >&2
    exit 1
  }
  grep -Fq 'executor' "$out_dir/common_executor_tokens.txt" || {
    echo "self-test failed: missing common token" >&2
    exit 1
  }
  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

count_lines() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -l <"$file" | awk '{print $1}'
  else
    printf '0'
  fi
}

if [ "$SELF_TEST" = true ]; then
  run_self_test
  exit 0
fi

collect_existing_static_artifacts
collect_apk_entries
write_executor_selection_trace "$EDGE_APK_DIR" "$LAMI_INPUT" "$COMPARE_OUT_DIR"

cat >"$SUMMARY" <<SUMMARY
# Static Edge Gallery Executor Trace

Generated by \`scripts/static_trace_edge_gallery_executor.sh\`.

## Inputs

- Edge APK dir: \`$EDGE_APK_DIR\`
- Edge static dir: \`$EDGE_STATIC_DIR\`
- Output dir: \`$OUT_DIR\`
- Lami input: \`$LAMI_INPUT\`
- Compare output dir: \`$COMPARE_OUT_DIR\`

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
| \`executor_selection_trace.tsv\` | $(count_lines "$COMPARE_OUT_DIR/executor_selection_trace.tsv") |
| \`executor_selection_trace_summary.txt\` | $(count_lines "$COMPARE_OUT_DIR/executor_selection_trace_summary.txt") |
| \`edge_only_executor_tokens.txt\` | $(count_lines "$COMPARE_OUT_DIR/edge_only_executor_tokens.txt") |
| \`lami_only_executor_tokens.txt\` | $(count_lines "$COMPARE_OUT_DIR/lami_only_executor_tokens.txt") |
| \`common_executor_tokens.txt\` | $(count_lines "$COMPARE_OUT_DIR/common_executor_tokens.txt") |

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
- \`executor_selection_trace_summary.txt\` compares Edge Gallery and Lami token
  presence for executor/backend selection hints.
- \`edge_only_executor_tokens.txt\`, \`lami_only_executor_tokens.txt\`, and
  \`common_executor_tokens.txt\` summarize which high-value tokens are unique or
  common at static trace time.
SUMMARY

printf 'Wrote static Edge Gallery executor trace to: %s\n' "$OUT_DIR"
printf 'Wrote executor selection trace comparison to: %s\n' "$COMPARE_OUT_DIR"
