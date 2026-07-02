#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LITERT_LM_DIR="${1:-$HOME/project/litert-custom-build/LiteRT-LM}"
if [ $# -gt 0 ] && [[ "${1:-}" != --* ]]; then
  shift
fi

LABEL=""
QAIRT_ROOT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --qairt-root)
      if [ $# -lt 2 ]; then
        printf 'ERROR: --qairt-root requires a path\n' >&2
        exit 2
      fi
      QAIRT_ROOT="$2"
      shift 2
      ;;
    --label)
      if [ $# -lt 2 ]; then
        printf 'ERROR: --label requires a value\n' >&2
        exit 2
      fi
      LABEL="$2"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/build_litert_custom_artifacts.sh [LiteRT-LM checkout] [--qairt-root <path>] [--label <label>]

Options:
  --qairt-root <path>  Exact QAIRT SDK version directory, for example
                       /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225.
                       The script creates a per-run overlay at
                       artifacts/litert_custom_build/<timestamp>_<label>/qairt_overlay/
                       and does not modify existing overlays.
  --label <label>      Suffix for the artifact directory, for example qairt244.

Safety:
  Builds only the limited target list in this script. Does not copy outputs into app source sets.
EOF
      exit 0
      ;;
    *)
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
if [ -n "$LABEL" ]; then
  OUT_DIR="${OUT_DIR:-$ROOT_DIR/artifacts/litert_custom_build/${TIMESTAMP}_${LABEL}}"
else
  OUT_DIR="${OUT_DIR:-$ROOT_DIR/artifacts/litert_custom_build/$TIMESTAMP}"
fi

BAZEL="${BAZEL:-$HOME/.local/bin/bazelisk}"
ANDROID_SDK_ROOT_DEFAULT="$HOME/Android/Sdk"
ANDROID_NDK_HOME_DEFAULT="$ANDROID_SDK_ROOT_DEFAULT/ndk/28.2.13676358"
QAIRT_OVERLAY_DEFAULT="$HOME/project/litert-custom-build/qairt_overlay/"
BAZEL_OUTPUT_BASE="${BAZEL_OUTPUT_BASE:-$HOME/project/litert-custom-build/bazel_output_base/build_$TIMESTAMP}"
BAZEL_BUILD_TIMEOUT="${BAZEL_BUILD_TIMEOUT:-7200}"

GALLERY_APK="${GALLERY_APK:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
GALLERY_STACK_APK="${GALLERY_STACK_APK:-$ROOT_DIR/app/build/outputs/apk/galleryStackExperiment/debug/app-galleryStackExperiment-debug.apk}"

export PATH="$HOME/.local/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT_OVERRIDE:-$ANDROID_HOME}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_NDK_HOME_DEFAULT}"
if [ -n "$QAIRT_ROOT" ]; then
  export LITERT_QAIRT_SDK="$OUT_DIR/qairt_overlay/"
else
  export LITERT_QAIRT_SDK="${LITERT_QAIRT_SDK:-$QAIRT_OVERLAY_DEFAULT}"
fi

TARGETS=(
  "@litert//litert/c:litert_runtime_c_api_so"
  "@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so"
  "//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni"
  "@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so"
)

LIB_NAMES=(
  "libLiteRt.so"
  "libLiteRtRuntimeCApi.so"
  "libLiteRtDispatch_Qualcomm.so"
  "liblitertlm_jni.so"
  "libLiteRtCompilerPlugin_Qualcomm.so"
  "libGemmaModelConstraintProvider.so"
)

KEYWORDS="qairt244_gpu_prefill_preinvoke_v1|LiteRtDispatchGetApi|LiteRtDispatchCheckRuntimeCompatibility|RuntimeCompatibility|capabilities|No usable Dispatch runtime found|Failed to initialize Dispatch API|dispatch_api|LiteRtRuntimeCApi|libLiteRtRuntimeCApi\.so|Qualcomm|QNN|Qnn|HTP|Htp|ADSP|LD_LIBRARY_PATH|libQnn|SM8750|sm8750|V79|schema|model"
GPU_PREFILL_PREINVOKE_MARKER="qairt244_gpu_prefill_preinvoke_v1"
GPU_PREFILL_PREINVOKE_C_SYMBOL="Qairt244GpuPrefillPreinvokeArtifactMarker"
GPU_PREFILL_PREINVOKE_JNI_SYMBOL="Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244GpuPrefillPreinvokeArtifactMarker_nativeMarker"

log() {
  printf '[litert-custom-build] %s\n' "$*"
}

safe_name() {
  printf '%s' "$1" | sed 's#[/@:]#_#g'
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

string_marker_present() {
  local file="$1"
  local marker="$2"
  [ -f "$file" ] || return 1
  strings "$file" 2>/dev/null | grep -Fq "$marker"
}

readelf_marker_present() {
  local file="$1"
  local marker="$2"
  [ -f "$file" ] || return 1
  readelf -p .rodata "$file" 2>/dev/null | grep -Fq "$marker"
}

exported_symbol_present() {
  local file="$1"
  local symbol="$2"
  [ -f "$file" ] || return 1
  readelf -Ws "$file" 2>/dev/null | awk -v symbol="$symbol" '
    $0 ~ /GLOBAL/ && $0 ~ /DEFAULT/ && index($0, symbol) {
      found = 1
    }
    END { exit found ? 0 : 1 }
  '
}

source_marker_present() {
  local file="$1"
  local marker="$2"
  [ -f "$file" ] || return 1
  grep -aFq "$marker" "$file"
}

bool_from_command() {
  "$@" && printf true || printf false
}

print_gpu_prefill_preinvoke_stage_diagnostic() {
  local stage="$1"
  local kind="$2"
  local file="$3"
  local sha="missing"
  local marker_present=false
  local strings_present=n/a
  local rodata_present=n/a
  local c_symbol_exported=n/a
  local jni_symbol_exported=n/a

  if [ -f "$file" ]; then
    sha="$(sha_for "$file")"
    if [ "$kind" = "source" ]; then
      marker_present="$(bool_from_command source_marker_present "$file" "$GPU_PREFILL_PREINVOKE_MARKER")"
    else
      strings_present="$(bool_from_command string_marker_present "$file" "$GPU_PREFILL_PREINVOKE_MARKER")"
      rodata_present="$(bool_from_command readelf_marker_present "$file" "$GPU_PREFILL_PREINVOKE_MARKER")"
      c_symbol_exported="$(bool_from_command exported_symbol_present "$file" "$GPU_PREFILL_PREINVOKE_C_SYMBOL")"
      jni_symbol_exported="$(bool_from_command exported_symbol_present "$file" "$GPU_PREFILL_PREINVOKE_JNI_SYMBOL")"
      if [ "$strings_present" = true ] && [ "$rodata_present" = true ]; then
        marker_present=true
      fi
    fi
  fi

  local line
  line="$(printf 'qairt244_marker_stage stage=%s kind=%s path=%s sha256=%s marker=%s marker_present=%s strings_present=%s rodata_present=%s c_symbol_exported=%s jni_symbol_exported=%s' \
    "$stage" "$kind" "$file" "$sha" "$GPU_PREFILL_PREINVOKE_MARKER" \
    "$marker_present" "$strings_present" "$rodata_present" \
    "$c_symbol_exported" "$jni_symbol_exported")"
  printf '%s\n' "$line"
  printf '%s\n' "$line" >>"$OUT_DIR/marker_stage_diagnostics.log"
}

write_liblitertlm_jni_candidate_list() {
  local out="$1"
  : >"$out"
  if [ -d "${BAZEL_BIN:-}" ]; then
    find -L "$BAZEL_BIN" -type f -name "liblitertlm_jni.so" 2>/dev/null >>"$out" || true
  fi
  if [ -e "$LITERT_LM_DIR/bazel-bin" ]; then
    find -L "$LITERT_LM_DIR/bazel-bin" -type f -name "liblitertlm_jni.so" 2>/dev/null >>"$out" || true
  fi

  local bazel_bin_realpath=""
  bazel_bin_realpath="$(readlink -f "${BAZEL_BIN:-}" 2>/dev/null || true)"
  if [[ "$bazel_bin_realpath" == */bazel-out/* ]]; then
    local execroot="${bazel_bin_realpath%%/bazel-out/*}"
    if [ -d "$execroot/bazel-out" ]; then
      find -L "$execroot/bazel-out" -type f -name "liblitertlm_jni.so" 2>/dev/null >>"$out" || true
    fi
  fi

  sort -u "$out" -o "$out"
}

gpu_prefill_preinvoke_marker_required() {
  [ "${QAIRT244_REQUIRE_GPU_PREFILL_PREINVOKE_MARKER:-false}" = true ] ||
    [[ "${LABEL:-}" == *gpu_prefill_preinvoke_diag* ]]
}

gpu_prefill_preinvoke_marker_complete() {
  local file="$1"
  string_marker_present "$file" "$GPU_PREFILL_PREINVOKE_MARKER" &&
    readelf_marker_present "$file" "$GPU_PREFILL_PREINVOKE_MARKER" &&
    exported_symbol_present "$file" "$GPU_PREFILL_PREINVOKE_C_SYMBOL" &&
    exported_symbol_present "$file" "$GPU_PREFILL_PREINVOKE_JNI_SYMBOL"
}

record_copy_source() {
  local lib="$1"
  local label="$2"
  local source="$3"
  local source_sha="$4"
  local dest_sha="$5"
  local marker_strings_present="$6"
  local marker_readelf_present="$7"
  local action="$8"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$lib" "$label" "$source" "$source_sha" "$dest_sha" \
    "$marker_strings_present" "$marker_readelf_present" "$action" \
    >>"$OUT_DIR/copied_built_lib_sources.tsv"
}

require_gpu_prefill_preinvoke_marker_file() {
  local file="$1"
  local label="$2"
  if ! gpu_prefill_preinvoke_marker_required; then
    return 0
  fi
  if ! [ -f "$file" ]; then
    log "ERROR: required $label output is missing: $file"
    printf 'required %s output is missing: %s\n' "$label" "$file" >"$OUT_DIR/ERROR.txt"
    exit 65
  fi
  if ! gpu_prefill_preinvoke_marker_complete "$file"; then
    log "ERROR: required marker $GPU_PREFILL_PREINVOKE_MARKER missing from $label output: $file"
    {
      printf 'required_marker=%s\n' "$GPU_PREFILL_PREINVOKE_MARKER"
      printf 'label=%s\n' "$label"
      printf 'file=%s\n' "$file"
      printf 'strings_present=%s\n' "$(string_marker_present "$file" "$GPU_PREFILL_PREINVOKE_MARKER" && printf true || printf false)"
      printf 'readelf_present=%s\n' "$(readelf_marker_present "$file" "$GPU_PREFILL_PREINVOKE_MARKER" && printf true || printf false)"
      printf 'c_symbol=%s\n' "$GPU_PREFILL_PREINVOKE_C_SYMBOL"
      printf 'c_symbol_exported=%s\n' "$(exported_symbol_present "$file" "$GPU_PREFILL_PREINVOKE_C_SYMBOL" && printf true || printf false)"
      printf 'jni_symbol=%s\n' "$GPU_PREFILL_PREINVOKE_JNI_SYMBOL"
      printf 'jni_symbol_exported=%s\n' "$(exported_symbol_present "$file" "$GPU_PREFILL_PREINVOKE_JNI_SYMBOL" && printf true || printf false)"
    } >"$OUT_DIR/ERROR.txt"
    exit 65
  fi
}

markdown_cell() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/|/\\|/g; s/`/\\`/g'
}

copy_built_lib() {
  local source="$1"
  local label="$2"
  [ -f "$source" ] || return 0
  local dest="$OUT_DIR/built_libs/$(basename "$source")"
  local lib
  lib="$(basename "$source")"
  local source_strings_present=false
  local source_readelf_present=false
  local dest_strings_present=false
  local dest_readelf_present=false
  string_marker_present "$source" "$GPU_PREFILL_PREINVOKE_MARKER" && source_strings_present=true
  readelf_marker_present "$source" "$GPU_PREFILL_PREINVOKE_MARKER" && source_readelf_present=true
  if [ -f "$dest" ]; then
    string_marker_present "$dest" "$GPU_PREFILL_PREINVOKE_MARKER" && dest_strings_present=true
    readelf_marker_present "$dest" "$GPU_PREFILL_PREINVOKE_MARKER" && dest_readelf_present=true
  fi
  if gpu_prefill_preinvoke_marker_required &&
     [ "$lib" = "liblitertlm_jni.so" ] &&
     { [ "$source_strings_present" != true ] || [ "$source_readelf_present" != true ]; }; then
    if [ "$label" = "explicit-target" ]; then
      log "ERROR: explicit liblitertlm_jni.so lacks $GPU_PREFILL_PREINVOKE_MARKER: $source"
      record_copy_source "$lib" "$label" "$source" "$(sha_for "$source")" "-" \
        "$source_strings_present" "$source_readelf_present" "rejected-missing-required-marker"
      exit 65
    fi
    if [ "$dest_strings_present" = true ] && [ "$dest_readelf_present" = true ]; then
      record_copy_source "$lib" "$label" "$source" "$(sha_for "$source")" "$(sha_for "$dest")" \
        "$source_strings_present" "$source_readelf_present" "blocked-overwrite-marker-destination"
    else
      record_copy_source "$lib" "$label" "$source" "$(sha_for "$source")" "-" \
        "$source_strings_present" "$source_readelf_present" "skipped-missing-required-marker"
    fi
    return 0
  fi
  cp -f "$source" "$dest"
  record_copy_source "$lib" "$label" "$source" "$(sha_for "$source")" "$(sha_for "$dest")" \
    "$source_strings_present" "$source_readelf_present" "copied"
}

extract_metadata() {
  local file="$1"
  local label="$2"
  local meta_dir="$3"
  local symbols_dir="$4"
  local strings_dir="$5"
  local base
  base="$(basename "$file")"
  mkdir -p "$meta_dir" "$symbols_dir" "$strings_dir"
  {
    printf 'path=%s\n' "$file"
    printf 'label=%s\n' "$label"
    if [ -f "$file" ]; then
      printf 'present=true\n'
      printf 'size=%s\n' "$(wc -c <"$file" | tr -d ' ')"
      printf 'sha256=%s\n' "$(sha_for "$file")"
      printf 'build_id=%s\n' "$(build_id_for "$file")"
      printf 'soname=%s\n' "$(soname_for "$file")"
      printf 'needed=%s\n' "$(needed_for "$file")"
      file "$file" 2>/dev/null || true
    else
      printf 'present=false\n'
    fi
  } >"$meta_dir/$base.txt"

  if [ -f "$file" ]; then
    nm -D --defined-only "$file" >"$symbols_dir/$base.exports.txt" 2>/dev/null || true
    nm -D -u "$file" >"$symbols_dir/$base.undefined.txt" 2>/dev/null || true
    strings "$file" 2>/dev/null | grep -Ei "$KEYWORDS" | sort -u >"$strings_dir/$base.filtered.txt" || true
  else
    printf '<missing>\n' >"$symbols_dir/$base.exports.txt"
    printf '<missing>\n' >"$symbols_dir/$base.undefined.txt"
    printf '<missing>\n' >"$strings_dir/$base.filtered.txt"
  fi
}

find_bazel_bin() {
  "$BAZEL" "--output_base=$BAZEL_OUTPUT_BASE" info bazel-bin \
    "--repo_env=ANDROID_HOME=$ANDROID_HOME" \
    "--repo_env=ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT" \
    "--repo_env=ANDROID_NDK_HOME=$ANDROID_NDK_HOME" \
    "--repo_env=LITERT_QAIRT_SDK=$LITERT_QAIRT_SDK" \
    "--repo_env=HERMETIC_PYTHON_VERSION=3.12" \
    --config=android_arm64 2>/dev/null | tail -n 1
}

record_target_result() {
  local target="$1"
  local code="$2"
  printf '%s\t%s\n' "$target" "$code" >>"$OUT_DIR/build_results.tsv"
}

run_build() {
  local target="$1"
  local name
  name="$(safe_name "$target")"
  local log_file="$OUT_DIR/build_logs/${name}.log"
  {
    printf '$ %q --output_base=%q build --repo_env=ANDROID_HOME=%q --repo_env=ANDROID_SDK_ROOT=%q --repo_env=ANDROID_NDK_HOME=%q --repo_env=LITERT_QAIRT_SDK=%q --repo_env=HERMETIC_PYTHON_VERSION=3.12 %q --config=android_arm64\n\n' \
      "$BAZEL" "$BAZEL_OUTPUT_BASE" "$ANDROID_HOME" "$ANDROID_SDK_ROOT" "$ANDROID_NDK_HOME" "$LITERT_QAIRT_SDK" "$target"
    timeout "$BAZEL_BUILD_TIMEOUT" "$BAZEL" "--output_base=$BAZEL_OUTPUT_BASE" build \
      "--repo_env=ANDROID_HOME=$ANDROID_HOME" \
      "--repo_env=ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT" \
      "--repo_env=ANDROID_NDK_HOME=$ANDROID_NDK_HOME" \
      "--repo_env=LITERT_QAIRT_SDK=$LITERT_QAIRT_SDK" \
      "--repo_env=HERMETIC_PYTHON_VERSION=3.12" \
      "$target" --config=android_arm64
    local code=$?
    printf '\nexit_code=%s\n' "$code"
    return "$code"
  } >"$log_file" 2>&1
}

extract_apk_refs() {
  local apk="$1"
  local dest="$2"
  local label="$3"
  mkdir -p "$dest"
  if [ ! -f "$apk" ]; then
    printf 'missing apk: %s\n' "$apk" >"$dest/MISSING_APK.txt"
    return 0
  fi
  for lib in "${LIB_NAMES[@]}" libQnnSystem.so libQnnHtp.so libQnnHtpPrepare.so libQnnHtpV79Stub.so libQnnHtpV79Skel.so libllm_inference_engine_jni.so; do
    unzip -p "$apk" "lib/arm64-v8a/$lib" >"$dest/$lib" 2>/dev/null || rm -f "$dest/$lib"
  done
  printf '%s\n' "$label" >"$dest/LABEL.txt"
}

extract_aar_refs() {
  local dest="$1"
  mkdir -p "$dest"
  local aar
  aar="$(find "$HOME/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litertlm/litertlm-android/0.11.0" -name "litertlm-android-0.11.0.aar" -type f 2>/dev/null | head -n 1)"
  if [ -z "$aar" ] || [ ! -f "$aar" ]; then
    printf 'missing litertlm-android 0.11.0 aar\n' >"$dest/MISSING_AAR.txt"
    return 0
  fi
  for lib in "${LIB_NAMES[@]}"; do
    unzip -p "$aar" "jni/arm64-v8a/$lib" >"$dest/$lib" 2>/dev/null || rm -f "$dest/$lib"
  done
  printf '%s\n' "$aar" >"$dest/AAR_PATH.txt"
}

write_matrix_for_dir() {
  local dir="$1"
  local label="$2"
  local out="$3"
  for lib in "${LIB_NAMES[@]}" libQnnSystem.so libQnnHtp.so libQnnHtpPrepare.so libQnnHtpV79Stub.so libQnnHtpV79Skel.so libllm_inference_engine_jni.so; do
    local file="$dir/$lib"
    if [ -f "$file" ]; then
      printf '%s\t%s\ttrue\t%s\t%s\t%s\t%s\t%s\n' \
        "$label" "$lib" "$(wc -c <"$file" | tr -d ' ')" "$(sha_for "$file")" "$(build_id_for "$file")" "$(soname_for "$file")" "$(needed_for "$file")" >>"$out"
    else
      printf '%s\t%s\tfalse\t-\t-\t-\t-\t-\n' "$label" "$lib" >>"$out"
    fi
  done
}

mkdir -p "$OUT_DIR/build_logs" "$OUT_DIR/built_libs" "$OUT_DIR/metadata" "$OUT_DIR/symbols" "$OUT_DIR/strings" "$OUT_DIR/readelf" "$OUT_DIR/reference_libs/gallery" "$OUT_DIR/reference_libs/gallery_stack" "$OUT_DIR/reference_libs/maven_0.11.0" "$BAZEL_OUTPUT_BASE"
: >"$OUT_DIR/build_results.tsv"
: >"$OUT_DIR/copied_built_lib_sources.tsv"
: >"$OUT_DIR/marker_stage_diagnostics.log"

if [ -n "$QAIRT_ROOT" ]; then
  {
    printf 'requested_qairt_root=%s\n' "$QAIRT_ROOT"
    printf 'expected_version=2.44.0.260225\n'
    printf 'artifact_overlay=%s\n' "$LITERT_QAIRT_SDK"
  } >"$OUT_DIR/qairt_root_check.txt"

  if [ ! -d "$QAIRT_ROOT" ]; then
    log "missing --qairt-root directory: $QAIRT_ROOT"
    printf 'status=missing\n' >>"$OUT_DIR/qairt_root_check.txt"
    printf 'missing --qairt-root directory: %s\n' "$QAIRT_ROOT" >"$OUT_DIR/ERROR.txt"
    printf '%s\n' "$OUT_DIR"
    exit 2
  fi

  if [ "$(basename "$QAIRT_ROOT")" != "2.44.0.260225" ]; then
    log "WARNING: --qairt-root basename is not 2.44.0.260225: $QAIRT_ROOT"
    printf 'basename_match=false\n' >>"$OUT_DIR/qairt_root_check.txt"
  else
    printf 'basename_match=true\n' >>"$OUT_DIR/qairt_root_check.txt"
  fi

  mkdir -p "$LITERT_QAIRT_SDK/qairt"
  ln -sfn "$QAIRT_ROOT" "$LITERT_QAIRT_SDK/qairt/2.44.0.260225"
  {
    printf 'status=present\n'
    printf 'symlink=%s\n' "$LITERT_QAIRT_SDK/qairt/2.44.0.260225"
    printf 'symlink_target='
    readlink "$LITERT_QAIRT_SDK/qairt/2.44.0.260225" 2>/dev/null || true
    printf '\nversion_files:\n'
    find "$QAIRT_ROOT" -maxdepth 3 -type f \( -name version.txt -o -name manifest.xml -o -name envsetup.sh \) 2>/dev/null | sort
    printf '\nrequired_files:\n'
    for path in \
      "$QAIRT_ROOT/bin/envsetup.sh" \
      "$QAIRT_ROOT/bin/x86_64-linux-clang/qnn-net-run" \
      "$QAIRT_ROOT/bin/x86_64-linux-clang/qnn-platform-validator" \
      "$QAIRT_ROOT/lib/aarch64-android/libQnnSystem.so" \
      "$QAIRT_ROOT/lib/aarch64-android/libQnnHtp.so" \
      "$QAIRT_ROOT/lib/aarch64-android/libQnnHtpPrepare.so" \
      "$QAIRT_ROOT/lib/aarch64-android/libQnnHtpV79Stub.so" \
      "$QAIRT_ROOT/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so"; do
      if [ -e "$path" ]; then
        printf 'present\t%s\n' "$path"
      else
        printf 'missing\t%s\n' "$path"
      fi
    done
  } >>"$OUT_DIR/qairt_root_check.txt"
fi

if [ ! -d "$LITERT_LM_DIR" ]; then
  log "missing LiteRT-LM checkout: $LITERT_LM_DIR"
  printf 'missing LiteRT-LM checkout: %s\n' "$LITERT_LM_DIR" >"$OUT_DIR/ERROR.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

if [ ! -x "$BAZEL" ]; then
  if command -v bazelisk >/dev/null 2>&1; then
    BAZEL="$(command -v bazelisk)"
  elif command -v bazel >/dev/null 2>&1; then
    BAZEL="$(command -v bazel)"
  fi
fi

if [ ! -x "$BAZEL" ]; then
  log "missing bazel/bazelisk"
  printf 'missing bazel/bazelisk\n' >"$OUT_DIR/ERROR.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

cd "$LITERT_LM_DIR" || exit 0

log "output: $OUT_DIR"
log "checkout: $LITERT_LM_DIR"
log "bazel: $BAZEL"

print_gpu_prefill_preinvoke_stage_diagnostic \
  "patched-source-after-apply-executor" \
  "source" \
  "$LITERT_LM_DIR/runtime/executor/llm_litert_compiled_model_executor.cc"
print_gpu_prefill_preinvoke_stage_diagnostic \
  "patched-source-after-apply-jni" \
  "source" \
  "$LITERT_LM_DIR/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"

{
  printf '# LiteRT Custom Build Environment\n\n'
  printf 'date=%s\n' "$(date -Is)"
  printf 'checkout=%s\n' "$LITERT_LM_DIR"
  printf 'head='
  git rev-parse HEAD 2>/dev/null || true
  printf 'describe='
  git describe --tags --always --dirty 2>/dev/null || true
  printf 'bazelversion='
  cat .bazelversion 2>/dev/null || true
  printf '\nANDROID_HOME=%s\n' "$ANDROID_HOME"
  printf 'ANDROID_SDK_ROOT=%s\n' "$ANDROID_SDK_ROOT"
  printf 'ANDROID_NDK_HOME=%s\n' "$ANDROID_NDK_HOME"
  printf 'LITERT_QAIRT_SDK=%s\n' "$LITERT_QAIRT_SDK"
  printf 'QAIRT_ROOT=%s\n' "${QAIRT_ROOT:-<default-overlay>}"
  printf 'LABEL=%s\n' "${LABEL:-<none>}"
  printf 'BAZEL_OUTPUT_BASE=%s\n' "$BAZEL_OUTPUT_BASE"
  printf 'BAZEL_BUILD_TIMEOUT=%s\n' "$BAZEL_BUILD_TIMEOUT"
  printf '\nWORKSPACE refs:\n'
  grep -nE 'LITERT_REF|LITERT_SHA256|qairt|android_ndk_repository|android_sdk_repository' WORKSPACE 2>/dev/null || true
} >"$OUT_DIR/environment.txt"

"$BAZEL" "--output_base=$BAZEL_OUTPUT_BASE" version >"$OUT_DIR/bazel_version.txt" 2>&1 || true

{
  printf '# Android NDK\n\n'
  printf 'ANDROID_NDK_HOME=%s\n\n' "$ANDROID_NDK_HOME"
  cat "$ANDROID_NDK_HOME/source.properties" 2>/dev/null || true
  printf '\nclang:\n'
  "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" --version 2>/dev/null || true
} >"$OUT_DIR/android_ndk_env.txt"

{
  printf '# QAIRT/QNN\n\n'
  printf 'LITERT_QAIRT_SDK=%s\n' "$LITERT_QAIRT_SDK"
  printf 'QAIRT_ROOT=%s\n' "${QAIRT_ROOT:-<default-overlay>}"
  printf 'Expected LiteRT strip path: %sqairt/2.44.0.260225\n' "$LITERT_QAIRT_SDK"
  find "$LITERT_QAIRT_SDK" -maxdepth 4 \( -name envsetup.sh -o -name qnn-net-run -o -name qnn-platform-validator -o -name libQnnSystem.so -o -name libQnnHtp.so -o -name libQnnHtpPrepare.so \) 2>/dev/null | sort
} >"$OUT_DIR/qairt_env.txt"

for target in "${TARGETS[@]}"; do
  log "building target: $target"
  run_build "$target"
  code="$?"
  record_target_result "$target" "$code"
  log "target exit $code: $target"
  if [ "$code" -ne 0 ]; then
    name="$(safe_name "$target")"
    log "failed target log tail: $OUT_DIR/build_logs/${name}.log"
    tail -220 "$OUT_DIR/build_logs/${name}.log" || true
  fi
done

BAZEL_BIN="$(find_bazel_bin)"
printf '%s\n' "$BAZEL_BIN" >"$OUT_DIR/bazel_bin.txt"
{
  printf 'configured_bazel_bin=%s\n' "$BAZEL_BIN"
  printf 'workspace_bazel_bin=%s\n' "$LITERT_LM_DIR/bazel-bin"
  if [ -e "$LITERT_LM_DIR/bazel-bin" ]; then
    printf 'workspace_bazel_bin_realpath='
    readlink -f "$LITERT_LM_DIR/bazel-bin" 2>/dev/null || true
    printf '\n'
  else
    printf 'workspace_bazel_bin_realpath=<missing>\n'
  fi
} >"$OUT_DIR/bazel_bin_resolution.txt"

if [ ! -d "$BAZEL_BIN" ]; then
  log "ERROR: configured bazel-bin directory is missing: $BAZEL_BIN"
  {
    printf 'configured_bazel_bin_missing=%s\n' "$BAZEL_BIN"
    cat "$OUT_DIR/bazel_bin_resolution.txt"
  } >"$OUT_DIR/ERROR.txt"
  if gpu_prefill_preinvoke_marker_required; then
    exit 65
  fi
fi

if [ -d "$BAZEL_BIN" ]; then
  LITERTLM_JNI_EXPLICIT_OUTPUT="$BAZEL_BIN/kotlin/java/com/google/ai/edge/litertlm/jni/liblitertlm_jni.so"
  print_gpu_prefill_preinvoke_stage_diagnostic \
    "post-bazel-explicit-liblitertlm_jni" \
    "elf" \
    "$LITERTLM_JNI_EXPLICIT_OUTPUT"

  write_liblitertlm_jni_candidate_list "$OUT_DIR/bazel_liblitertlm_jni_candidates.txt"
  while IFS= read -r candidate; do
    [ -n "$candidate" ] || continue
    print_gpu_prefill_preinvoke_stage_diagnostic \
      "post-bazel-candidate-liblitertlm_jni" \
      "elf" \
      "$candidate"
  done <"$OUT_DIR/bazel_liblitertlm_jni_candidates.txt"

  for lib in "${LIB_NAMES[@]}" '*qnn*.so' '*Qnn*.so' '*dispatch*.so' '*Dispatch*.so' '*litert*.so' '*LiteRt*.so'; do
    find -L "$BAZEL_BIN" -type f -name "$lib" 2>/dev/null
  done | sort -u >"$OUT_DIR/built_lib_candidates.txt"

  while IFS= read -r candidate; do
    copy_built_lib "$candidate" "bazel-bin-scan"
  done <"$OUT_DIR/built_lib_candidates.txt"

  # Prefer explicit target outputs over runfiles/solib duplicates when basename
  # collisions occur in bazel-bin.
  require_gpu_prefill_preinvoke_marker_file "$LITERTLM_JNI_EXPLICIT_OUTPUT" "explicit liblitertlm_jni.so"
  copy_built_lib "$BAZEL_BIN/external/litert/litert/c/libLiteRt.so" "explicit-target"
  copy_built_lib "$BAZEL_BIN/external/litert/litert/vendors/qualcomm/dispatch/libLiteRtDispatch_Qualcomm.so" "explicit-target"
  copy_built_lib "$LITERTLM_JNI_EXPLICIT_OUTPUT" "explicit-target"
  print_gpu_prefill_preinvoke_stage_diagnostic \
    "artifact-after-copy-liblitertlm_jni" \
    "elf" \
    "$OUT_DIR/built_libs/liblitertlm_jni.so"
  require_gpu_prefill_preinvoke_marker_file "$OUT_DIR/built_libs/liblitertlm_jni.so" "artifact built_libs/liblitertlm_jni.so after copy"
  copy_built_lib "$BAZEL_BIN/external/litert/litert/vendors/qualcomm/compiler/libLiteRtCompilerPlugin_Qualcomm.so" "explicit-target"
fi

GEMMA_PROVIDER_PREBUILT="$LITERT_LM_DIR/prebuilt/android_arm64/libGemmaModelConstraintProvider.so"
if [ -f "$GEMMA_PROVIDER_PREBUILT" ]; then
  copy_built_lib "$GEMMA_PROVIDER_PREBUILT" "prebuilt"
  printf '%s\n' "$GEMMA_PROVIDER_PREBUILT" >>"$OUT_DIR/built_lib_candidates.txt"
fi

if [ -n "${QAIRT_ROOT:-}" ] && [ -d "$QAIRT_ROOT" ]; then
  mkdir -p "$OUT_DIR/qnn_runtime_libs"
  for qnn_path in \
    "$QAIRT_ROOT/lib/aarch64-android/libQnnSystem.so" \
    "$QAIRT_ROOT/lib/aarch64-android/libQnnHtp.so" \
    "$QAIRT_ROOT/lib/aarch64-android/libQnnHtpPrepare.so" \
    "$QAIRT_ROOT/lib/aarch64-android/libQnnHtpV79Stub.so" \
    "$QAIRT_ROOT/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so" \
    "$QAIRT_ROOT/lib/aarch64-android/libQnnDsp.so" \
    "$QAIRT_ROOT/lib/aarch64-android/libQnnGpu.so"; do
    if [ -f "$qnn_path" ]; then
      cp -f "$qnn_path" "$OUT_DIR/qnn_runtime_libs/$(basename "$qnn_path")"
    fi
  done
fi

for file in "$OUT_DIR"/built_libs/*.so; do
  [ -f "$file" ] || continue
  extract_metadata "$file" "built" "$OUT_DIR/metadata" "$OUT_DIR/symbols" "$OUT_DIR/strings"
  readelf -p .rodata "$file" >"$OUT_DIR/readelf/$(basename "$file").rodata.txt" 2>/dev/null || true
done

extract_apk_refs "$GALLERY_APK" "$OUT_DIR/reference_libs/gallery" "Gallery SM8750 APK"
extract_apk_refs "$GALLERY_STACK_APK" "$OUT_DIR/reference_libs/gallery_stack" "galleryStackExperimentDebug APK"
extract_aar_refs "$OUT_DIR/reference_libs/maven_0.11.0"

for dir in "$OUT_DIR/reference_libs/gallery" "$OUT_DIR/reference_libs/gallery_stack" "$OUT_DIR/reference_libs/maven_0.11.0"; do
  for file in "$dir"/*.so; do
    [ -f "$file" ] || continue
    label="$(basename "$dir")"
    extract_metadata "$file" "$label" "$OUT_DIR/reference_metadata/$label" "$OUT_DIR/reference_symbols/$label" "$OUT_DIR/reference_strings/$label"
  done
done

MATRIX="$OUT_DIR/static_compare_matrix.tsv"
printf 'source\tlibrary\tpresent\tsize\tsha256\tbuild_id\tsoname\tneeded\n' >"$MATRIX"
write_matrix_for_dir "$OUT_DIR/built_libs" "built" "$MATRIX"
write_matrix_for_dir "$OUT_DIR/reference_libs/gallery" "gallery-sm8750" "$MATRIX"
write_matrix_for_dir "$OUT_DIR/reference_libs/gallery_stack" "galleryStackExperimentDebug" "$MATRIX"
write_matrix_for_dir "$OUT_DIR/reference_libs/maven_0.11.0" "maven-litertlm-0.11.0" "$MATRIX"

{
  printf '# LiteRT Custom Build Static Summary\n\n'
  printf -- '- Output: `%s`\n' "$OUT_DIR"
  printf -- '- Label: `%s`\n' "${LABEL:-<none>}"
  printf -- '- Build executed: limited explicit targets only\n'
  printf -- '- App integration: `no`\n'
  printf -- '- Engine.initialize rerun: `no`\n\n'
  printf '## Diagnostic markers\n\n'
  printf '| Marker | Library | Strings Present | Readelf Present | Strings Evidence | Readelf Evidence |\n'
  printf '| --- | --- | --- | --- | --- | --- |\n'
  for file in "$OUT_DIR"/built_libs/*.so; do
    [ -f "$file" ] || continue
    lib="$(basename "$file")"
    marker_strings_present=false
    marker_readelf_present=false
    marker_strings_evidence_file="$OUT_DIR/strings/$lib.filtered.txt"
    marker_readelf_evidence_file="$OUT_DIR/readelf/$lib.rodata.txt"
    if string_marker_present "$file" "$GPU_PREFILL_PREINVOKE_MARKER"; then
      marker_strings_present=true
    fi
    if readelf_marker_present "$file" "$GPU_PREFILL_PREINVOKE_MARKER"; then
      marker_readelf_present=true
    fi
    printf '| `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n' \
      "$GPU_PREFILL_PREINVOKE_MARKER" "$lib" "$marker_strings_present" "$marker_readelf_present" "$marker_strings_evidence_file" "$marker_readelf_evidence_file"
  done
  printf '\n'
  printf '## Copied built library sources\n\n'
  printf '| Library | Copy Label | Source | Source SHA-256 | Destination SHA-256 | Marker Strings | Marker Readelf | Action |\n'
  printf '| --- | --- | --- | --- | --- | --- | --- | --- |\n'
  if [ -s "$OUT_DIR/copied_built_lib_sources.tsv" ]; then
    while IFS="$(printf '\t')" read -r copied_lib copied_label copied_source copied_source_sha copied_dest_sha copied_marker_strings copied_marker_readelf copied_action; do
      printf '| `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n' \
        "$(markdown_cell "$copied_lib")" \
        "$(markdown_cell "$copied_label")" \
        "$(markdown_cell "$copied_source")" \
        "$(markdown_cell "$copied_source_sha")" \
        "$(markdown_cell "$copied_dest_sha")" \
        "$(markdown_cell "$copied_marker_strings")" \
        "$(markdown_cell "$copied_marker_readelf")" \
        "$(markdown_cell "$copied_action")"
    done <"$OUT_DIR/copied_built_lib_sources.tsv"
  else
    printf '| `<none>` |  |  |  |  |  |  |  |\n'
  fi
  printf '\n'
  printf '## Target results\n\n```text\n'
  cat "$OUT_DIR/build_results.tsv"
  printf '```\n\n'
  printf '## Built libraries\n\n```text\n'
  find "$OUT_DIR/built_libs" -maxdepth 1 -type f -name '*.so' -printf '%f\n' | sort
  printf '```\n\n'
  printf '## Built library metadata\n\n'
  for file in "$OUT_DIR"/built_libs/*.so; do
    [ -f "$file" ] || continue
    lib="$(basename "$file")"
    printf '### `%s`\n\n' "$lib"
    printf '```text\n'
    cat "$OUT_DIR/metadata/$lib.txt"
    printf '```\n\n'
    if grep -q 'LiteRtDispatchGetApi' "$OUT_DIR/symbols/$lib.exports.txt" 2>/dev/null; then
      printf -- '- `LiteRtDispatchGetApi` export: yes\n\n'
    else
      printf -- '- `LiteRtDispatchGetApi` export: no\n\n'
    fi
  done
  printf '## Static compare matrix\n\n```text\n'
  cat "$MATRIX"
  printf '```\n'
} >"$OUT_DIR/static_summary.md"

log "wrote $OUT_DIR/static_summary.md"
printf '%s\n' "$OUT_DIR"

exit 0
