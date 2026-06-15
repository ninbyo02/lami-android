#!/usr/bin/env bash
set -euo pipefail

INPUT=""
OUTPUT_FILE=""
LABEL="INPUT"

TARGET_LIBS=(
  libLiteRt.so
  liblitertlm_jni.so
  libLiteRtDispatch_Qualcomm.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libGemmaModelConstraintProvider.so
)

KEYWORD_REGEX='GPU_ARTISAN|LlmGpuArtisanExecutor|RuntimeConfig|BackendConstraint|PreferredEngineType|CompiledModelExecutor|LlmLiteRtCompiledModelExecutor|generateContent|generateContentStream|nativeGenerateContent|nativeGenerateContentStream|nativeRunPrefill|nativeRunDecode'
JNI_REGEX='Java_.*LiteRtLmJni|nativeGenerateContent|nativeGenerateContentStream|nativeRunPrefill|nativeRunDecode'

usage() {
  cat <<'USAGE'
Usage:
  scripts/generate_native_stack_fingerprint.sh --apk FILE [--label NAME] [--output FILE]
  scripts/generate_native_stack_fingerprint.sh --dir DIR [--label NAME] [--output FILE]
  scripts/generate_native_stack_fingerprint.sh --input FILE_OR_DIR [--label NAME] [--output FILE]
  scripts/generate_native_stack_fingerprint.sh --self-test

Prints native runtime fingerprints for an APK, APK-split directory, or extracted
directory.
USAGE
}

sha_for_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1 && [[ -f "$file" ]]; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf 'unavailable'
  fi
}

sha_for_stream() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    cat >/dev/null
    printf 'unavailable'
  fi
}

size_for_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    wc -c <"$file" 2>/dev/null | awk '{print $1}'
  else
    printf '0'
  fi
}

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

extract_apk_libs() {
  local apk="$1"
  local out_dir="$2"
  local source_label="$3"
  local source_file="$4"
  (zip_entries "$apk" | grep '^lib/arm64-v8a/.*\.so$' || true) |
    while IFS= read -r entry; do
      [[ -n "$entry" ]] || continue
      local lib
      lib="$(basename "$entry")"
      mkdir -p "$out_dir"
      unzip -p "$apk" "$entry" >"$out_dir/$lib" 2>/dev/null || true
      printf '%s\t%s\t%s\n' "$lib" "$source_label" "$entry" >>"$source_file"
    done
}

copy_dir_libs() {
  local dir="$1"
  local out_dir="$2"
  local source_file="$3"
  find "$dir" -type f -name '*.so' 2>/dev/null |
    while IFS= read -r so; do
      local lib
      lib="$(basename "$so")"
      mkdir -p "$out_dir"
      cp "$so" "$out_dir/$lib"
      printf '%s\t%s\t%s\n' "$lib" "$dir" "$so" >>"$source_file"
    done
}

prepare_lib_dir() {
  local input="$1"
  local out_dir="$2"
  local source_file="$3"
  : >"$source_file"
  mkdir -p "$out_dir"
  if [[ -f "$input" ]]; then
    extract_apk_libs "$input" "$out_dir" "$(basename "$input")" "$source_file"
    return
  fi
  if [[ -d "$input" ]]; then
    local apk_count
    apk_count="$(find "$input" -maxdepth 1 -type f -name '*.apk' 2>/dev/null | wc -l | awk '{print $1}')"
    if [[ "$apk_count" != "0" ]]; then
      find "$input" -maxdepth 1 -type f -name '*.apk' 2>/dev/null | sort |
        while IFS= read -r apk; do
          extract_apk_libs "$apk" "$out_dir" "$(basename "$apk")" "$source_file"
        done
    else
      copy_dir_libs "$input" "$out_dir" "$source_file"
    fi
  fi
}

lib_path() {
  local lib_dir="$1"
  local lib="$2"
  printf '%s/%s\n' "$lib_dir" "$lib"
}

strings_for_lib() {
  local file="$1"
  if [[ -f "$file" ]] && command -v strings >/dev/null 2>&1; then
    strings -a "$file" 2>/dev/null || true
  fi
}

symbols_for_lib() {
  local file="$1"
  if [[ -f "$file" ]] && command -v nm >/dev/null 2>&1; then
    nm -D "$file" 2>/dev/null || true
  fi
}

material_for_runtime() {
  local lib_dir="$1"
  local lib path
  for lib in "${TARGET_LIBS[@]}"; do
    path="$(lib_path "$lib_dir" "$lib")"
    if [[ -f "$path" ]]; then
      printf '%s\t%s\t%s\n' "$lib" "$(size_for_file "$path")" "$(sha_for_file "$path")"
    else
      printf '%s\tmissing\tmissing\n' "$lib"
    fi
  done
  find "$lib_dir" -maxdepth 1 -type f -name 'libQnn*.so' 2>/dev/null | sort |
    while IFS= read -r path; do
      printf '%s\t%s\t%s\n' "$(basename "$path")" "$(size_for_file "$path")" "$(sha_for_file "$path")"
    done
}

material_for_jni() {
  local lib_dir="$1"
  local path
  path="$(lib_path "$lib_dir" liblitertlm_jni.so)"
  {
    symbols_for_lib "$path"
    strings_for_lib "$path"
  } | grep -Eai "$JNI_REGEX" | sort -u || true
}

material_for_executor() {
  local lib_dir="$1"
  find "$lib_dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null | sort |
    while IFS= read -r path; do
      {
        symbols_for_lib "$path"
        strings_for_lib "$path"
      } | grep -Eai "$KEYWORD_REGEX" | sed "s#^#$(basename "$path"):#" || true
    done | sort -u
}

material_for_qualcomm() {
  local lib_dir="$1"
  find "$lib_dir" -maxdepth 1 -type f \
    \( -name 'libQnn*.so' -o -name 'libLiteRtDispatch_Qualcomm.so' -o -name 'libLiteRtCompilerPlugin_Qualcomm.so' -o -name 'libGemmaModelConstraintProvider.so' \) 2>/dev/null |
    sort |
    while IFS= read -r path; do
      printf '%s\t%s\t%s\n' "$(basename "$path")" "$(size_for_file "$path")" "$(sha_for_file "$path")"
    done
}

fingerprint_from_material() {
  sha_for_stream
}

generate_fingerprints() {
  local input="$1"
  local label="$2"
  local tmpdir lib_dir source_file runtime_fp jni_fp executor_fp qualcomm_fp
  tmpdir="$(mktemp -d)"
  FINGERPRINT_TMPDIR="$tmpdir"
  trap 'rm -rf "${FINGERPRINT_TMPDIR:-}"' RETURN
  lib_dir="$tmpdir/libs"
  source_file="$tmpdir/sources.tsv"
  prepare_lib_dir "$input" "$lib_dir" "$source_file"

  runtime_fp="$(material_for_runtime "$lib_dir" | fingerprint_from_material)"
  jni_fp="$(material_for_jni "$lib_dir" | fingerprint_from_material)"
  executor_fp="$(material_for_executor "$lib_dir" | fingerprint_from_material)"
  qualcomm_fp="$(material_for_qualcomm "$lib_dir" | fingerprint_from_material)"

  printf '%s_RUNTIME_STACK_FINGERPRINT=%s\n' "$label" "$runtime_fp"
  printf '%s_JNI_SURFACE_FINGERPRINT=%s\n' "$label" "$jni_fp"
  printf '%s_EXECUTOR_SYMBOL_FINGERPRINT=%s\n' "$label" "$executor_fp"
  printf '%s_QUALCOMM_STACK_FINGERPRINT=%s\n' "$label" "$qualcomm_fp"
  printf '%s_NATIVE_LIB_COUNT=%s\n' "$label" "$(find "$lib_dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null | wc -l | awk '{print $1}')"
}

run_self_test() {
  local tmpdir out
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT
  mkdir -p "$tmpdir/input/lib/arm64-v8a"
  printf 'GPU_ARTISAN\nLlmGpuArtisanExecutor\nJava_com_google_ai_edge_litertlm_LiteRtLmJni_nativeGenerateContent\n' \
    >"$tmpdir/input/lib/arm64-v8a/liblitertlm_jni.so"
  printf 'LiteRtRegisterGpuAccelerator\n' >"$tmpdir/input/lib/arm64-v8a/libLiteRt.so"
  out="$(scripts/generate_native_stack_fingerprint.sh --dir "$tmpdir/input" --label TEST)"
  printf '%s\n' "$out" | grep -Fq 'TEST_RUNTIME_STACK_FINGERPRINT=' || {
    echo "self-test failed: missing runtime fingerprint" >&2
    exit 1
  }
  printf '%s\n' "$out" | grep -Fq 'TEST_JNI_SURFACE_FINGERPRINT=' || {
    echo "self-test failed: missing JNI fingerprint" >&2
    exit 1
  }
  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk|--dir|--input)
      INPUT="${2:?missing input value}"
      shift 2
      ;;
    --label)
      LABEL="${2:?missing --label value}"
      shift 2
      ;;
    --output)
      OUTPUT_FILE="${2:?missing --output value}"
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

if [[ -z "$INPUT" ]]; then
  usage >&2
  exit 2
fi

if [[ ! -e "$INPUT" ]]; then
  echo "Input not found: $INPUT" >&2
  exit 1
fi

if [[ -n "$OUTPUT_FILE" ]]; then
  mkdir -p "$(dirname "$OUTPUT_FILE")"
  generate_fingerprints "$INPUT" "$LABEL" >"$OUTPUT_FILE"
else
  generate_fingerprints "$INPUT" "$LABEL"
fi
