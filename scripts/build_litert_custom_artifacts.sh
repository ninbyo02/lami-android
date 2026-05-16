#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LITERT_LM_DIR="${1:-$HOME/project/litert-custom-build/LiteRT-LM}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/artifacts/litert_custom_build/$TIMESTAMP}"

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
export LITERT_QAIRT_SDK="${LITERT_QAIRT_SDK:-$QAIRT_OVERLAY_DEFAULT}"

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
)

KEYWORDS="LiteRtDispatchGetApi|LiteRtDispatchCheckRuntimeCompatibility|RuntimeCompatibility|capabilities|No usable Dispatch runtime found|Failed to initialize Dispatch API|dispatch_api|LiteRtRuntimeCApi|libLiteRtRuntimeCApi\.so|Qualcomm|QNN|Qnn|HTP|Htp|ADSP|LD_LIBRARY_PATH|libQnn|SM8750|sm8750|V79|schema|model"

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
  "$BAZEL" "--output_base=$BAZEL_OUTPUT_BASE" info bazel-bin 2>/dev/null | tail -n 1
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

mkdir -p "$OUT_DIR/build_logs" "$OUT_DIR/built_libs" "$OUT_DIR/metadata" "$OUT_DIR/symbols" "$OUT_DIR/strings" "$OUT_DIR/reference_libs/gallery" "$OUT_DIR/reference_libs/gallery_stack" "$OUT_DIR/reference_libs/maven_0.11.0" "$BAZEL_OUTPUT_BASE"
: >"$OUT_DIR/build_results.tsv"

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
  printf 'Expected LiteRT strip path: %sqairt/2.44.0.260225\n' "$LITERT_QAIRT_SDK"
  find "$LITERT_QAIRT_SDK" -maxdepth 4 \( -name envsetup.sh -o -name qnn-net-run -o -name qnn-platform-validator -o -name libQnnSystem.so -o -name libQnnHtp.so -o -name libQnnHtpPrepare.so \) 2>/dev/null | sort
} >"$OUT_DIR/qairt_env.txt"

for target in "${TARGETS[@]}"; do
  log "building target: $target"
  run_build "$target"
  code="$?"
  record_target_result "$target" "$code"
  log "target exit $code: $target"
done

BAZEL_BIN="$(find_bazel_bin)"
if [ -d "$LITERT_LM_DIR/bazel-bin" ]; then
  BAZEL_BIN="$LITERT_LM_DIR/bazel-bin"
fi
printf '%s\n' "$BAZEL_BIN" >"$OUT_DIR/bazel_bin.txt"

if [ -d "$BAZEL_BIN" ]; then
  for lib in "${LIB_NAMES[@]}" '*qnn*.so' '*Qnn*.so' '*dispatch*.so' '*Dispatch*.so' '*litert*.so' '*LiteRt*.so'; do
    find -L "$BAZEL_BIN" -type f -name "$lib" 2>/dev/null
  done | sort -u >"$OUT_DIR/built_lib_candidates.txt"

  while IFS= read -r candidate; do
    [ -f "$candidate" ] || continue
    cp -f "$candidate" "$OUT_DIR/built_libs/$(basename "$candidate")"
  done <"$OUT_DIR/built_lib_candidates.txt"
fi

for file in "$OUT_DIR"/built_libs/*.so; do
  [ -f "$file" ] || continue
  extract_metadata "$file" "built" "$OUT_DIR/metadata" "$OUT_DIR/symbols" "$OUT_DIR/strings"
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
  printf -- '- Build executed: limited explicit targets only\n'
  printf -- '- App integration: `no`\n'
  printf -- '- Engine.initialize rerun: `no`\n\n'
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
