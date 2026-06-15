#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_CACHE="${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}"
EDGE_APK_DIR="$ROOT_DIR/artifacts/external/edge_gallery_apks"
EDGE_STATIC_DIR="$ROOT_DIR/artifacts/edge_gallery_static"
OUT_DIR="$ROOT_DIR/artifacts/litertlm_api_surface"
EXTRA_INPUTS=()

TARGET_CLASSES=(
  com.google.ai.edge.litertlm.Backend
  'com.google.ai.edge.litertlm.Backend$CPU'
  'com.google.ai.edge.litertlm.Backend$GPU'
  'com.google.ai.edge.litertlm.Backend$NPU'
  com.google.ai.edge.litertlm.Engine
  com.google.ai.edge.litertlm.EngineConfig
  com.google.ai.edge.litertlm.Conversation
  com.google.ai.edge.litertlm.ConversationConfig
  com.google.ai.edge.litertlm.SamplerConfig
  com.google.ai.edge.litertlm.SessionConfig
  com.google.ai.edge.litertlm.LiteRtLmJni
  com.google.ai.edge.litertlm.RuntimeConfig
  'com.google.ai.edge.litertlm.RuntimeConfig$Builder'
  com.google.ai.edge.litertlm.ExecutorConfig
  'com.google.ai.edge.litertlm.ExecutorConfig$Builder'
  com.google.ai.edge.litertlm.ExecutorSelection
  'com.google.ai.edge.litertlm.ExecutorSelection$Builder'
  com.google.ai.edge.litertlm.PreferredEngineType
  com.google.ai.edge.litertlm.BackendConstraint
  'com.google.ai.edge.litertlm.BackendConstraint$Builder'
  com.google.ai.edge.litertlm.GpuOptions
  'com.google.ai.edge.litertlm.GpuOptions$Builder'
  com.google.ai.edge.litertlm.LlmGpuArtisanExecutor
  com.google.ai.edge.litertlm.CompiledModelExecutor
)

KEYWORD_REGEX='GPU_ARTISAN|CPU_ARTISAN|GOOGLE_TENSOR_ARTISAN|LlmGpuArtisanExecutor|RuntimeConfig|BackendConstraint|PreferredEngineType|GpuOptions|ExecutorSelection|ExecutorConfig|CompiledModelExecutor|LlmLiteRtCompiledModelExecutor|nativeGenerateContent|nativeGenerateContentStream|nativeRunPrefill|nativeRunDecode|generateContent|generateContentStream|backend constraint|Artisan model detected|tflite_gpu_kv_cache|tflite_opencl_kv_cache'

usage() {
  cat <<USAGE
Usage:
  scripts/inspect_litertlm_api_surface.sh [--output DIR] [--gradle-cache DIR] [--edge-apks DIR] [--edge-static DIR] [--input FILE_OR_DIR ...]
  scripts/inspect_litertlm_api_surface.sh --self-test

Inspects local LiteRT-LM Java/native API surface candidates from Gradle cache,
APK splits, extracted directories, and existing Edge Gallery static artifacts.
Missing inputs are reported in api_surface_summary.txt and are not fatal.
USAGE
}

zip_entries() {
  local archive="$1"
  if command -v zipinfo >/dev/null 2>&1; then
    zipinfo -1 "$archive" 2>/dev/null
  elif command -v jar >/dev/null 2>&1; then
    jar tf "$archive" 2>/dev/null
  else
    return 1
  fi
}

safe_sha() {
  local file="$1"
  if [[ -f "$file" ]] && command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf 'unavailable'
  fi
}

java_class_from_entry() {
  local entry="$1"
  entry="${entry%.class}"
  entry="${entry//\//.}"
  printf '%s\n' "$entry"
}

append_unique_sorted() {
  local input="$1"
  local output="$2"
  sort -u "$input" >>"$output"
}

scan_classes_jar() {
  local jar_path="$1"
  local source_label="$2"
  local tmpdir="$3"
  local class_list="$tmpdir/classes_$(safe_sha "$jar_path").txt"
  (zip_entries "$jar_path" | grep '^com/google/ai/edge/litertlm/.*\.class$' || true) >"$class_list"
  while IFS= read -r entry; do
    [[ -n "$entry" ]] || continue
    printf '%s\tclass\t%s\n' "$source_label" "$(java_class_from_entry "$entry")" >>"$OUT_DIR/class_inventory.raw"
  done <"$class_list"

  if command -v javap >/dev/null 2>&1; then
    local class_name
    for class_name in "${TARGET_CLASSES[@]}"; do
      local class_entry
      class_entry="${class_name//.//}.class"
      if grep -Fxq "$class_entry" "$class_list"; then
        {
          printf '===== %s :: %s =====\n' "$source_label" "$class_name"
          javap -classpath "$jar_path" -p -s "$class_name" 2>&1 || true
          printf '\n'
        } >>"$OUT_DIR/method_inventory.raw"
      else
        printf '%s\tmissing-target-class\t%s\n' "$source_label" "$class_name" >>"$OUT_DIR/class_inventory.raw"
      fi
    done
  else
    printf '%s\tjavap_unavailable\n' "$source_label" >>"$OUT_DIR/method_inventory.raw"
  fi
}

scan_aar() {
  local aar="$1"
  local tmpdir="$2"
  local source_label="aar:$(basename "$aar")"
  printf '%s\t%s\t%s\n' "$source_label" "$aar" "$(safe_sha "$aar")" >>"$OUT_DIR/source_inventory.raw"
  local classes_jar="$tmpdir/$(basename "$aar").classes.jar"
  if unzip -p "$aar" classes.jar >"$classes_jar" 2>/dev/null; then
    scan_classes_jar "$classes_jar" "$source_label" "$tmpdir"
  else
    printf '%s\tmissing\tclasses.jar\n' "$source_label" >>"$OUT_DIR/class_inventory.raw"
  fi
  (zip_entries "$aar" | grep -E '^jni/arm64-v8a/.*\.so$' || true) |
    while IFS= read -r entry; do
      local lib_tmp="$tmpdir/$(basename "$aar").$(basename "$entry")"
      unzip -p "$aar" "$entry" >"$lib_tmp" 2>/dev/null || true
      scan_native_file "$lib_tmp" "$source_label:$entry"
    done
}

scan_jar() {
  local jar_path="$1"
  local tmpdir="$2"
  local source_label="jar:$(basename "$jar_path")"
  printf '%s\t%s\t%s\n' "$source_label" "$jar_path" "$(safe_sha "$jar_path")" >>"$OUT_DIR/source_inventory.raw"
  scan_classes_jar "$jar_path" "$source_label" "$tmpdir"
}

scan_apk() {
  local apk="$1"
  local tmpdir="$2"
  local source_label="apk:$(basename "$apk")"
  printf '%s\t%s\t%s\n' "$source_label" "$apk" "$(safe_sha "$apk")" >>"$OUT_DIR/source_inventory.raw"
  (zip_entries "$apk" | grep '^classes[0-9]*\.dex$' || true) |
    while IFS= read -r entry; do
      unzip -p "$apk" "$entry" 2>/dev/null |
        strings -a 2>/dev/null |
        grep -Eai 'com.google.ai.edge.litertlm|GPU_ARTISAN|RuntimeConfig|BackendConstraint|PreferredEngineType|LlmGpuArtisanExecutor' |
        sed "s#^#$source_label:$entry:#" >>"$OUT_DIR/class_inventory.raw" || true
    done
  (zip_entries "$apk" | grep -E '^lib/arm64-v8a/.*\.so$' || true) |
    while IFS= read -r entry; do
      local lib_tmp="$tmpdir/$(basename "$apk").$(basename "$entry")"
      unzip -p "$apk" "$entry" >"$lib_tmp" 2>/dev/null || true
      scan_native_file "$lib_tmp" "$source_label:$entry"
    done
}

scan_native_file() {
  local file="$1"
  local source_label="$2"
  [[ -f "$file" ]] || return 0
  if command -v strings >/dev/null 2>&1; then
    strings -a "$file" 2>/dev/null |
      grep -Eai "$KEYWORD_REGEX" |
      sed "s#^#$source_label:string:#" >>"$OUT_DIR/gpu_executor_candidate_symbols.raw" || true
  fi
  if command -v nm >/dev/null 2>&1; then
    nm -D "$file" 2>/dev/null |
      grep -Eai "$KEYWORD_REGEX" |
      sed "s#^#$source_label:symbol:#" >>"$OUT_DIR/gpu_executor_candidate_symbols.raw" || true
  fi
}

scan_directory_or_file() {
  local input="$1"
  local tmpdir="$2"
  if [[ -f "$input" ]]; then
    case "$input" in
      *.aar) scan_aar "$input" "$tmpdir" ;;
      *.jar) scan_jar "$input" "$tmpdir" ;;
      *.apk) scan_apk "$input" "$tmpdir" ;;
      *.so) scan_native_file "$input" "file:$input" ;;
      *) printf 'unknown_file_type\t%s\n' "$input" >>"$OUT_DIR/source_inventory.raw" ;;
    esac
    return
  fi
  if [[ -d "$input" ]]; then
    find "$input" -maxdepth 5 -type f \( -name '*.aar' -o -name '*.jar' -o -name '*.apk' -o -name '*.so' \) 2>/dev/null |
      sort |
      while IFS= read -r file; do
        scan_directory_or_file "$file" "$tmpdir"
      done
    return
  fi
  printf 'missing_input\t%s\n' "$input" >>"$OUT_DIR/source_inventory.raw"
}

scan_default_sources() {
  local tmpdir="$1"
  if [[ -d "$GRADLE_CACHE" ]]; then
    find "$GRADLE_CACHE/com.google.ai.edge.litertlm" -type f \( -name '*.aar' -o -name '*.jar' \) 2>/dev/null |
      sort |
      while IFS= read -r file; do
        scan_directory_or_file "$file" "$tmpdir"
      done
  else
    printf 'missing_gradle_cache\t%s\n' "$GRADLE_CACHE" >>"$OUT_DIR/source_inventory.raw"
  fi
  if [[ -d "$EDGE_APK_DIR" ]]; then
    scan_directory_or_file "$EDGE_APK_DIR" "$tmpdir"
  else
    printf 'missing_edge_apk_dir\t%s\n' "$EDGE_APK_DIR" >>"$OUT_DIR/source_inventory.raw"
  fi
  if [[ -d "$EDGE_STATIC_DIR" ]]; then
    find "$EDGE_STATIC_DIR" -type f \( -name '*.txt' -o -name '*.md' -o -name '*.tsv' -o -name '*.filtered.txt' \) 2>/dev/null |
      sort |
      while IFS= read -r file; do
        grep -Eain "$KEYWORD_REGEX" "$file" 2>/dev/null |
          sed "s#^#static:$file:#" >>"$OUT_DIR/gpu_executor_candidate_symbols.raw" || true
      done
  else
    printf 'missing_edge_static_dir\t%s\n' "$EDGE_STATIC_DIR" >>"$OUT_DIR/source_inventory.raw"
  fi
}

write_reflection_candidates() {
  {
    grep -Eai 'RuntimeConfig|BackendConstraint|PreferredEngineType|GpuOptions|ExecutorSelection|ExecutorConfig|Artisan|GPU_ARTISAN|set[A-Za-z]*(Backend|Runtime|Executor|Engine|Constraint|Gpu|Cache|Sampler)|get[A-Za-z]*(Backend|Runtime|Executor|Engine|Constraint|Gpu|Cache|Sampler)' "$OUT_DIR/method_inventory.raw" || true
    grep -Eai 'RuntimeConfig|BackendConstraint|PreferredEngineType|GpuOptions|ExecutorSelection|ExecutorConfig|LlmGpuArtisanExecutor|GPU_ARTISAN' "$OUT_DIR/class_inventory.raw" || true
  } | sort -u >"$OUT_DIR/reflection_candidate_methods.txt"
}

write_summary() {
  local class_count method_blocks symbol_count reflection_count
  class_count="$(grep -c $'\tclass\t' "$OUT_DIR/class_inventory.txt" 2>/dev/null || true)"
  method_blocks="$(grep -c '^=====' "$OUT_DIR/method_inventory.txt" 2>/dev/null || true)"
  symbol_count="$(wc -l <"$OUT_DIR/gpu_executor_candidate_symbols.txt" 2>/dev/null | awk '{print $1}')"
  reflection_count="$(wc -l <"$OUT_DIR/reflection_candidate_methods.txt" 2>/dev/null | awk '{print $1}')"
  public_class_presence() {
    local class_name="$1"
    if grep -Fq $'\tclass\t'"$class_name" "$OUT_DIR/class_inventory.txt" 2>/dev/null; then
      printf 'present'
    else
      printf 'absent'
    fi
  }
  native_evidence_presence() {
    local pattern="$1"
    if grep -Eaiq "$pattern" "$OUT_DIR/gpu_executor_candidate_symbols.txt" 2>/dev/null; then
      printf 'present'
    else
      printf 'absent'
    fi
  }
  {
    printf 'output_dir=%s\n' "$OUT_DIR"
    printf 'gradle_cache=%s\n' "$GRADLE_CACHE"
    printf 'edge_apk_dir=%s\n' "$EDGE_APK_DIR"
    printf 'edge_static_dir=%s\n' "$EDGE_STATIC_DIR"
    printf 'source_count=%s\n' "$(wc -l <"$OUT_DIR/source_inventory.txt" 2>/dev/null | awk '{print $1}')"
    printf 'litertlm_class_count=%s\n' "$class_count"
    printf 'javap_method_blocks=%s\n' "$method_blocks"
    printf 'gpu_executor_candidate_symbol_hits=%s\n' "$symbol_count"
    printf 'reflection_candidate_lines=%s\n' "$reflection_count"
    printf 'runtime_config_public_class=%s\n' "$(public_class_presence 'com.google.ai.edge.litertlm.RuntimeConfig')"
    printf 'backend_constraint_public_class=%s\n' "$(public_class_presence 'com.google.ai.edge.litertlm.BackendConstraint')"
    printf 'preferred_engine_type_public_class=%s\n' "$(public_class_presence 'com.google.ai.edge.litertlm.PreferredEngineType')"
    printf 'gpu_options_public_class=%s\n' "$(public_class_presence 'com.google.ai.edge.litertlm.GpuOptions')"
    printf 'llm_gpu_artisan_executor_public_class=%s\n' "$(public_class_presence 'com.google.ai.edge.litertlm.LlmGpuArtisanExecutor')"
    printf 'runtime_config_native_evidence=%s\n' "$(native_evidence_presence 'RuntimeConfig|GetRuntimeConfig')"
    printf 'backend_constraint_native_evidence=%s\n' "$(native_evidence_presence 'BackendConstraint|backend constraint|Supported backends')"
    printf 'preferred_engine_type_native_evidence=%s\n' "$(native_evidence_presence 'PreferredEngineType')"
    printf 'gpu_options_native_evidence=%s\n' "$(native_evidence_presence 'GpuOptions|LrtCreateGpuOptionsFromToml')"
    printf 'gpu_artisan_native_evidence=%s\n' "$(native_evidence_presence 'GPU_ARTISAN|LlmGpuArtisanExecutor')"
    printf 'gpu_kv_cache_native_evidence=%s\n' "$(native_evidence_presence 'tflite_gpu_kv_cache|tflite_opencl_kv_cache')"
  } >"$OUT_DIR/api_surface_summary.txt"
}

run_inspection() {
  local tmpdir
  mkdir -p "$OUT_DIR"
  : >"$OUT_DIR/source_inventory.raw"
  : >"$OUT_DIR/class_inventory.raw"
  : >"$OUT_DIR/method_inventory.raw"
  : >"$OUT_DIR/gpu_executor_candidate_symbols.raw"
  tmpdir="$(mktemp -d)"
  API_SURFACE_TMPDIR="$tmpdir"
  trap 'rm -rf "${API_SURFACE_TMPDIR:-}"' RETURN
  scan_default_sources "$tmpdir"
  local input
  for input in "${EXTRA_INPUTS[@]}"; do
    scan_directory_or_file "$input" "$tmpdir"
  done
  sort -u "$OUT_DIR/source_inventory.raw" >"$OUT_DIR/source_inventory.txt"
  sort -u "$OUT_DIR/class_inventory.raw" >"$OUT_DIR/class_inventory.txt"
  cat "$OUT_DIR/method_inventory.raw" >"$OUT_DIR/method_inventory.txt"
  sort -u "$OUT_DIR/gpu_executor_candidate_symbols.raw" >"$OUT_DIR/gpu_executor_candidate_symbols.txt"
  write_reflection_candidates
  write_summary
  rm -f "$OUT_DIR"/*.raw
  printf 'Wrote LiteRT-LM API surface inventory to: %s\n' "$OUT_DIR"
}

run_self_test() {
  local tmpdir fixture_root aar_dir classes_root classes_jar aar_file out_dir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT
  fixture_root="$tmpdir/fixture"
  aar_dir="$tmpdir/aar"
  classes_root="$tmpdir/classes"
  out_dir="$tmpdir/out"
  mkdir -p "$classes_root/com/google/ai/edge/litertlm" "$aar_dir/jni/arm64-v8a" "$fixture_root/com.google.ai.edge.litertlm/litertlm-android/0.test"
  printf 'fake class bytes\n' >"$classes_root/com/google/ai/edge/litertlm/EngineConfig.class"
  printf 'fake class bytes\n' >"$classes_root/com/google/ai/edge/litertlm/Backend.class"
  (cd "$classes_root" && jar cf "$aar_dir/classes.jar" com >/dev/null 2>&1)
  printf 'GPU_ARTISAN\nLlmGpuArtisanExecutor\nnativeGenerateContentStream\nRuntimeConfig\n' >"$aar_dir/jni/arm64-v8a/liblitertlm_jni.so"
  aar_file="$fixture_root/com.google.ai.edge.litertlm/litertlm-android/0.test/litertlm-android-0.test.aar"
  (cd "$aar_dir" && jar cf "$aar_file" classes.jar jni >/dev/null 2>&1)
  GRADLE_CACHE="$fixture_root"
  EDGE_APK_DIR="$tmpdir/missing_apks"
  EDGE_STATIC_DIR="$tmpdir/missing_static"
  OUT_DIR="$out_dir"
  EXTRA_INPUTS=()
  run_inspection >/tmp/lami_litertlm_api_surface_self_test.out
  grep -Fq 'com.google.ai.edge.litertlm.EngineConfig' "$OUT_DIR/class_inventory.txt" || {
    echo "self-test failed: missing EngineConfig class" >&2
    exit 1
  }
  grep -Fq 'GPU_ARTISAN' "$OUT_DIR/gpu_executor_candidate_symbols.txt" || {
    echo "self-test failed: missing GPU_ARTISAN symbol hit" >&2
    exit 1
  }
  grep -Fq 'api_surface_summary' /tmp/lami_litertlm_api_surface_self_test.out || true
  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)
      OUT_DIR="${2:?missing --output value}"
      shift 2
      ;;
    --gradle-cache)
      GRADLE_CACHE="${2:?missing --gradle-cache value}"
      shift 2
      ;;
    --edge-apks)
      EDGE_APK_DIR="${2:?missing --edge-apks value}"
      shift 2
      ;;
    --edge-static)
      EDGE_STATIC_DIR="${2:?missing --edge-static value}"
      shift 2
      ;;
    --input)
      EXTRA_INPUTS+=("${2:?missing --input value}")
      shift 2
      ;;
    --self-test)
      run_self_test
      exit 0
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

run_inspection
