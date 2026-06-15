#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
EDGE_INPUT=""
LAMI_INPUT=""
OUT_DIR="$ROOT_DIR/artifacts/apk_native_diff"

TARGET_LIBS=(
  libLiteRt.so
  liblitertlm_jni.so
  libLiteRtDispatch_Qualcomm.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libGemmaModelConstraintProvider.so
)

INTERNAL_SURFACE_REGEX='GPU_ARTISAN|LlmGpuArtisanExecutor|Artisan|RuntimeConfig|BackendConstraint|PreferredEngineType|GpuOptions|LrtCreateGpuOptionsFromToml|tflite_gpu_kv_cache|tflite_opencl_kv_cache|kv_cache|nativeGenerateContent|nativeGenerateContentStream|nativeRunPrefill|nativeRunDecode|CompiledModelExecutor|LlmLiteRtCompiledModelExecutor|GetRuntimeConfig|backend constraint|preferred engine'
KEYWORD_REGEX="$INTERNAL_SURFACE_REGEX|generateContent|generateContentStream"
JNI_REGEX='Java_.*LiteRtLmJni|nativeGenerateContent|nativeGenerateContentStream|nativeRunPrefill|nativeRunDecode'

usage() {
  cat <<'USAGE'
Usage:
  scripts/compare_edge_gallery_and_lami_apk.sh --edge-gallery APK --lami-apk APK [--output DIR]
  scripts/compare_edge_gallery_and_lami_apk.sh --edge-gallery-dir DIR --lami-dir DIR [--output DIR]
  scripts/compare_edge_gallery_and_lami_apk.sh --self-test

Compares arm64 native runtime stack contents between Edge Gallery and Lami APKs
or extracted directories. Output is written to artifacts/apk_native_diff/ by
default.
USAGE
}

sha_for_file() {
  local file="$1"
  if [[ -f "$file" ]] && command -v sha256sum >/dev/null 2>&1; then
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
  local source_file="$3"
  (zip_entries "$apk" | grep '^lib/arm64-v8a/.*\.so$' || true) |
    while IFS= read -r entry; do
      [[ -n "$entry" ]] || continue
      local lib
      lib="$(basename "$entry")"
      mkdir -p "$out_dir"
      unzip -p "$apk" "$entry" >"$out_dir/$lib" 2>/dev/null || true
      printf '%s\t%s\t%s\n' "$lib" "$(basename "$apk")" "$entry" >>"$source_file"
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
    extract_apk_libs "$input" "$out_dir" "$source_file"
    return
  fi
  if [[ -d "$input" ]]; then
    local apk_count
    apk_count="$(find "$input" -maxdepth 1 -type f -name '*.apk' 2>/dev/null | wc -l | awk '{print $1}')"
    if [[ "$apk_count" != "0" ]]; then
      find "$input" -maxdepth 1 -type f -name '*.apk' 2>/dev/null | sort |
        while IFS= read -r apk; do
          extract_apk_libs "$apk" "$out_dir" "$source_file"
        done
    else
      copy_dir_libs "$input" "$out_dir" "$source_file"
    fi
  fi
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

presence_flags() {
  local file="$1"
  local strings_content
  strings_content="$(strings_for_lib "$file")"
  local flag output=""
  for flag in GPU_ARTISAN LlmGpuArtisanExecutor Artisan RuntimeConfig BackendConstraint PreferredEngineType GpuOptions LrtCreateGpuOptionsFromToml tflite_gpu_kv_cache tflite_opencl_kv_cache kv_cache CompiledModelExecutor LlmLiteRtCompiledModelExecutor GetRuntimeConfig nativeGenerateContent nativeGenerateContentStream nativeRunPrefill nativeRunDecode; do
    if printf '%s\n' "$strings_content" | grep -Fq "$flag"; then
      output="${output}${flag}=yes;"
    else
      output="${output}${flag}=no;"
    fi
  done
  printf '%s\n' "$output"
}

target_lib_list() {
  local edge_dir="$1"
  local lami_dir="$2"
  {
    printf '%s\n' "${TARGET_LIBS[@]}"
    find "$edge_dir" "$lami_dir" -maxdepth 1 -type f -name 'libQnn*.so' 2>/dev/null | xargs -r -n1 basename
  } | sort -u
}

write_inventory() {
  local edge_dir="$1"
  local lami_dir="$2"
  local out="$3"
  printf 'library\tedge_present\tedge_size\tedge_sha256\tlami_present\tlami_size\tlami_sha256\tsame_sha256\tedge_keyword_flags\tlami_keyword_flags\n' >"$out"
  target_lib_list "$edge_dir" "$lami_dir" |
    while IFS= read -r lib; do
      local edge_path lami_path edge_present lami_present edge_sha lami_sha
      edge_path="$edge_dir/$lib"
      lami_path="$lami_dir/$lib"
      edge_present=no
      lami_present=no
      [[ -f "$edge_path" ]] && edge_present=yes
      [[ -f "$lami_path" ]] && lami_present=yes
      edge_sha="$(sha_for_file "$edge_path")"
      lami_sha="$(sha_for_file "$lami_path")"
      local same=no
      [[ "$edge_sha" != "unavailable" && "$edge_sha" == "$lami_sha" ]] && same=yes
      printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$lib" \
        "$edge_present" \
        "$(size_for_file "$edge_path")" \
        "$edge_sha" \
        "$lami_present" \
        "$(size_for_file "$lami_path")" \
        "$lami_sha" \
        "$same" \
        "$(presence_flags "$edge_path")" \
        "$(presence_flags "$lami_path")" >>"$out"
    done
}

jni_material() {
  local lib_dir="$1"
  local path="$lib_dir/liblitertlm_jni.so"
  {
    symbols_for_lib "$path"
    strings_for_lib "$path"
  } | grep -Eai "$JNI_REGEX" | sort -u || true
}

write_jni_diff() {
  local edge_dir="$1"
  local lami_dir="$2"
  local out="$3"
  local tmpdir edge_symbols lami_symbols all_symbols
  tmpdir="$(mktemp -d)"
  edge_symbols="$tmpdir/edge.txt"
  lami_symbols="$tmpdir/lami.txt"
  all_symbols="$tmpdir/all.txt"
  jni_material "$edge_dir" >"$edge_symbols"
  jni_material "$lami_dir" >"$lami_symbols"
  cat "$edge_symbols" "$lami_symbols" | sort -u >"$all_symbols"
  printf 'symbol\tedge_present\tlami_present\n' >"$out"
  while IFS= read -r symbol; do
    [[ -n "$symbol" ]] || continue
    local edge_present=no lami_present=no
    grep -Fxq "$symbol" "$edge_symbols" && edge_present=yes
    grep -Fxq "$symbol" "$lami_symbols" && lami_present=yes
    printf '%s\t%s\t%s\n' "$symbol" "$edge_present" "$lami_present" >>"$out"
  done <"$all_symbols"
  rm -rf "$tmpdir"
}

executor_material() {
  local lib_dir="$1"
  find "$lib_dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null | sort |
    while IFS= read -r path; do
      {
        symbols_for_lib "$path"
        strings_for_lib "$path"
      } | grep -Eai "$KEYWORD_REGEX" | sed "s#^#$(basename "$path"):#" || true
    done | sort -u
}

internal_surface_material() {
  local lib_dir="$1"
  find "$lib_dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null | sort |
    while IFS= read -r path; do
      {
        symbols_for_lib "$path"
        strings_for_lib "$path"
      } | grep -Eai "$INTERNAL_SURFACE_REGEX" | sed "s#^#$(basename "$path"):#" || true
    done | sort -u
}

fingerprint_from_material() {
  sha_for_stream
}

write_fingerprints() {
  local edge_input="$1"
  local lami_input="$2"
  local edge_dir="$3"
  local lami_dir="$4"
  local out="$5"
  {
    "$ROOT_DIR/scripts/generate_native_stack_fingerprint.sh" --input "$edge_input" --label EDGE
    "$ROOT_DIR/scripts/generate_native_stack_fingerprint.sh" --input "$lami_input" --label LAMI
    printf 'EDGE_EXECUTOR_KEYWORD_FINGERPRINT=%s\n' "$(executor_material "$edge_dir" | fingerprint_from_material)"
    printf 'LAMI_EXECUTOR_KEYWORD_FINGERPRINT=%s\n' "$(executor_material "$lami_dir" | fingerprint_from_material)"
    printf 'EDGE_GALLERY_INTERNAL_SURFACE_FINGERPRINT=%s\n' "$(internal_surface_material "$edge_dir" | fingerprint_from_material)"
    printf 'LAMI_INTERNAL_SURFACE_FINGERPRINT=%s\n' "$(internal_surface_material "$lami_dir" | fingerprint_from_material)"
  } >"$out"
}

read_key_from_file() {
  local file="$1"
  local key="$2"
  awk -F= -v wanted="$key" '$1 == wanted {print $2; exit}' "$file"
}

write_summary() {
  local edge_input="$1"
  local lami_input="$2"
  local fingerprint_file="$3"
  local inventory_file="$4"
  local out="$5"
  local edge_runtime lami_runtime edge_jni lami_jni edge_exec lami_exec edge_internal lami_internal edge_qualcomm lami_qualcomm
  edge_runtime="$(read_key_from_file "$fingerprint_file" EDGE_RUNTIME_STACK_FINGERPRINT)"
  lami_runtime="$(read_key_from_file "$fingerprint_file" LAMI_RUNTIME_STACK_FINGERPRINT)"
  edge_jni="$(read_key_from_file "$fingerprint_file" EDGE_JNI_SURFACE_FINGERPRINT)"
  lami_jni="$(read_key_from_file "$fingerprint_file" LAMI_JNI_SURFACE_FINGERPRINT)"
  edge_exec="$(read_key_from_file "$fingerprint_file" EDGE_EXECUTOR_SYMBOL_FINGERPRINT)"
  lami_exec="$(read_key_from_file "$fingerprint_file" LAMI_EXECUTOR_SYMBOL_FINGERPRINT)"
  edge_internal="$(read_key_from_file "$fingerprint_file" EDGE_GALLERY_INTERNAL_SURFACE_FINGERPRINT)"
  lami_internal="$(read_key_from_file "$fingerprint_file" LAMI_INTERNAL_SURFACE_FINGERPRINT)"
  edge_qualcomm="$(read_key_from_file "$fingerprint_file" EDGE_QUALCOMM_STACK_FINGERPRINT)"
  lami_qualcomm="$(read_key_from_file "$fingerprint_file" LAMI_QUALCOMM_STACK_FINGERPRINT)"
  {
    printf 'edge_input=%s\n' "$edge_input"
    printf 'lami_input=%s\n' "$lami_input"
    printf 'runtime_stack_same=%s\n' "$([[ "$edge_runtime" == "$lami_runtime" ]] && printf yes || printf no)"
    printf 'jni_surface_same=%s\n' "$([[ "$edge_jni" == "$lami_jni" ]] && printf yes || printf no)"
    printf 'executor_symbol_same=%s\n' "$([[ "$edge_exec" == "$lami_exec" ]] && printf yes || printf no)"
    printf 'internal_surface_same=%s\n' "$([[ "$edge_internal" == "$lami_internal" ]] && printf yes || printf no)"
    printf 'INTERNAL_SURFACE_DIFF_SUMMARY=%s\n' "$([[ "$edge_internal" == "$lami_internal" ]] && printf same_internal_surface || printf different_internal_surface)"
    printf 'qualcomm_stack_same=%s\n' "$([[ "$edge_qualcomm" == "$lami_qualcomm" ]] && printf yes || printf no)"
    printf 'missing_high_priority_lami_libs='
    awk -F '\t' 'NR > 1 && $2 == "yes" && $5 == "no" && $1 !~ /^libQnn/ {printf "%s,", $1}' "$inventory_file"
    printf '\n'
    printf 'sha_mismatch_high_priority_libs='
    awk -F '\t' 'NR > 1 && $2 == "yes" && $5 == "yes" && $8 == "no" && $1 !~ /^libQnn/ {printf "%s,", $1}' "$inventory_file"
    printf '\n'
  } >"$out"
}

run_compare() {
  local edge_input="$1"
  local lami_input="$2"
  local out_dir="$3"
  local tmpdir edge_dir lami_dir edge_sources lami_sources
  tmpdir="$(mktemp -d)"
  COMPARE_TMPDIR="$tmpdir"
  trap 'rm -rf "${COMPARE_TMPDIR:-}"' RETURN
  edge_dir="$tmpdir/edge"
  lami_dir="$tmpdir/lami"
  edge_sources="$tmpdir/edge_sources.tsv"
  lami_sources="$tmpdir/lami_sources.tsv"
  prepare_lib_dir "$edge_input" "$edge_dir" "$edge_sources"
  prepare_lib_dir "$lami_input" "$lami_dir" "$lami_sources"

  mkdir -p "$out_dir"
  write_inventory "$edge_dir" "$lami_dir" "$out_dir/native_lib_inventory.tsv"
  write_jni_diff "$edge_dir" "$lami_dir" "$out_dir/jni_symbol_diff.tsv"
  write_fingerprints "$edge_input" "$lami_input" "$edge_dir" "$lami_dir" "$out_dir/native_stack_fingerprint.txt"
  write_summary "$edge_input" "$lami_input" "$out_dir/native_stack_fingerprint.txt" "$out_dir/native_lib_inventory.tsv" "$out_dir/runtime_stack_summary.txt"
  printf 'Wrote APK native diff artifacts to: %s\n' "$out_dir"
}

run_self_test() {
  local tmpdir out_dir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT
  mkdir -p "$tmpdir/edge/lib/arm64-v8a" "$tmpdir/lami/lib/arm64-v8a"
  printf 'GPU_ARTISAN\nLlmGpuArtisanExecutor\nJava_com_google_ai_edge_litertlm_LiteRtLmJni_nativeGenerateContent\n' \
    >"$tmpdir/edge/lib/arm64-v8a/liblitertlm_jni.so"
  printf 'Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeGenerateContent\n' \
    >"$tmpdir/lami/lib/arm64-v8a/liblitertlm_jni.so"
  printf 'LiteRtRegisterGpuAccelerator\n' >"$tmpdir/edge/lib/arm64-v8a/libLiteRt.so"
  printf 'LiteRtRegisterGpuAccelerator\nstandard\n' >"$tmpdir/lami/lib/arm64-v8a/libLiteRt.so"
  out_dir="$tmpdir/out"
  run_compare "$tmpdir/edge" "$tmpdir/lami" "$out_dir" >/tmp/lami_apk_native_diff_self_test.out
  [[ -s "$out_dir/native_lib_inventory.tsv" ]] || {
    echo "self-test failed: missing native_lib_inventory.tsv" >&2
    exit 1
  }
  [[ -s "$out_dir/jni_symbol_diff.tsv" ]] || {
    echo "self-test failed: missing jni_symbol_diff.tsv" >&2
    exit 1
  }
  grep -Fq 'runtime_stack_same=no' "$out_dir/runtime_stack_summary.txt" || {
    echo "self-test failed: expected runtime stack difference" >&2
    cat "$out_dir/runtime_stack_summary.txt" >&2
    exit 1
  }
  grep -Fq 'INTERNAL_SURFACE_DIFF_SUMMARY=different_internal_surface' "$out_dir/runtime_stack_summary.txt" || {
    echo "self-test failed: expected internal surface difference" >&2
    cat "$out_dir/runtime_stack_summary.txt" >&2
    exit 1
  }
  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --edge-gallery|--edge-gallery-dir)
      EDGE_INPUT="${2:?missing Edge Gallery input}"
      shift 2
      ;;
    --lami-apk|--lami-dir)
      LAMI_INPUT="${2:?missing Lami input}"
      shift 2
      ;;
    --output)
      OUT_DIR="${2:?missing --output value}"
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

if [[ -z "$EDGE_INPUT" || -z "$LAMI_INPUT" ]]; then
  usage >&2
  exit 2
fi

if [[ ! -e "$EDGE_INPUT" ]]; then
  echo "Edge Gallery input not found: $EDGE_INPUT" >&2
  exit 1
fi

if [[ ! -e "$LAMI_INPUT" ]]; then
  echo "Lami input not found: $LAMI_INPUT" >&2
  exit 1
fi

run_compare "$EDGE_INPUT" "$LAMI_INPUT" "$OUT_DIR"
