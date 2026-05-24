#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LITERT_LM_DIR="${1:-$HOME/project/litert-custom-build/LiteRT-LM}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/artifacts/litert_custom_build_query/$TIMESTAMP}"
BAZEL_QUERY_TIMEOUT="${BAZEL_QUERY_TIMEOUT:-420}"

BAZEL="${BAZEL:-$HOME/.local/bin/bazelisk}"
ANDROID_SDK_ROOT_DEFAULT="$HOME/Android/Sdk"
ANDROID_NDK_HOME_DEFAULT="$ANDROID_SDK_ROOT_DEFAULT/ndk/28.2.13676358"
QAIRT_OVERLAY_DEFAULT="$HOME/project/litert-custom-build/qairt_overlay/"
BAZEL_OUTPUT_BASE="${BAZEL_OUTPUT_BASE:-$HOME/project/litert-custom-build/bazel_output_base/$TIMESTAMP}"

export PATH="$HOME/.local/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT_OVERRIDE:-$ANDROID_HOME}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_NDK_HOME_DEFAULT}"
export LITERT_QAIRT_SDK="${LITERT_QAIRT_SDK:-$QAIRT_OVERLAY_DEFAULT}"

QUERY_TARGETS=(
  "//kotlin/java/com/google/ai/edge/litertlm/jni:*"
  "@litert//litert/vendors/qualcomm/dispatch:*"
  "@litert//litert/c:*"
  "@litert//litert/vendors/qualcomm/compiler:*"
)

CQUERY_TARGETS=(
  "@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so"
  "@litert//litert/c:litert_runtime_c_api_so"
  "//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni"
)

log() {
  printf '[litert-query] %s\n' "$*"
}

run_capture() {
  local name="$1"
  shift
  local out="$OUT_DIR/$name"
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n\n'
    timeout "$BAZEL_QUERY_TIMEOUT" "$@"
    local code=$?
    printf '\nexit_code=%s\n' "$code"
    return "$code"
  } >"$out" 2>&1
}

record_result() {
  local name="$1"
  local code="$2"
  printf '%s\t%s\n' "$name" "$code" >>"$OUT_DIR/results.tsv"
}

mkdir -p "$OUT_DIR"
mkdir -p "$BAZEL_OUTPUT_BASE"
: >"$OUT_DIR/results.tsv"

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

BAZEL_STARTUP_OPTS=("--output_base=$BAZEL_OUTPUT_BASE")
BAZEL_COMMON_OPTS=(
  "--repo_env=ANDROID_HOME=$ANDROID_HOME"
  "--repo_env=ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
  "--repo_env=ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
  "--repo_env=LITERT_QAIRT_SDK=$LITERT_QAIRT_SDK"
  "--repo_env=HERMETIC_PYTHON_VERSION=3.12"
)

{
  printf 'LiteRT-LM checkout: %s\n' "$LITERT_LM_DIR"
  printf 'HEAD: '
  git rev-parse HEAD 2>/dev/null || true
  printf 'describe: '
  git describe --tags --always --dirty 2>/dev/null || true
  printf 'bazelversion: '
  cat .bazelversion 2>/dev/null || true
  printf '\n'
  printf 'ANDROID_HOME=%s\n' "$ANDROID_HOME"
  printf 'ANDROID_SDK_ROOT=%s\n' "$ANDROID_SDK_ROOT"
  printf 'ANDROID_NDK_HOME=%s\n' "$ANDROID_NDK_HOME"
  printf 'LITERT_QAIRT_SDK=%s\n' "$LITERT_QAIRT_SDK"
  printf 'BAZEL_OUTPUT_BASE=%s\n' "$BAZEL_OUTPUT_BASE"
  printf 'BAZEL_QUERY_TIMEOUT=%s\n' "$BAZEL_QUERY_TIMEOUT"
  printf '\nWORKSPACE refs:\n'
  grep -nE 'LITERT_REF|LITERT_SHA256|qairt|android_ndk_repository|android_sdk_repository' WORKSPACE 2>/dev/null || true
} >"$OUT_DIR/source_env.txt"

run_capture "bazel_version.txt" "$BAZEL" "${BAZEL_STARTUP_OPTS[@]}" version
record_result "bazel_version" "$?"

{
  printf '# .bazelrc Android/config summary\n\n'
  grep -nE 'build:android|android_arm64|crosstool|fat_apk_cpu|platforms=|ANDROID|NDK|qairt|short_logs' .bazelrc 2>/dev/null || true
} >"$OUT_DIR/bazelrc_summary.txt"

{
  printf '# Android NDK environment\n\n'
  printf 'ANDROID_HOME=%s\n' "$ANDROID_HOME"
  printf 'ANDROID_SDK_ROOT=%s\n' "$ANDROID_SDK_ROOT"
  printf 'ANDROID_NDK_HOME=%s\n' "$ANDROID_NDK_HOME"
  if [ -f "$ANDROID_NDK_HOME/source.properties" ]; then
    printf '\nsource.properties:\n'
    cat "$ANDROID_NDK_HOME/source.properties"
  else
    printf '\nsource.properties: <missing>\n'
  fi
  if [ -x "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]; then
    printf '\nclang:\n'
    "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" --version
  else
    printf '\nclang: <missing>\n'
  fi
} >"$OUT_DIR/android_ndk_env.txt" 2>&1

{
  printf '# QAIRT/QNN environment\n\n'
  printf 'LITERT_QAIRT_SDK=%s\n' "$LITERT_QAIRT_SDK"
  printf 'Expected LiteRT strip path: %s/qairt/2.44.0.260225\n' "$LITERT_QAIRT_SDK"
  find "$LITERT_QAIRT_SDK" -maxdepth 4 \( -name envsetup.sh -o -name qnn-net-run -o -name qnn-platform-validator -o -name libQnnSystem.so -o -name libQnnHtp.so -o -name libQnnHtpPrepare.so \) 2>/dev/null | sort
} >"$OUT_DIR/qairt_env.txt"

run_capture "query_litertlm_jni.txt" "$BAZEL" "${BAZEL_STARTUP_OPTS[@]}" query "${BAZEL_COMMON_OPTS[@]}" "//kotlin/java/com/google/ai/edge/litertlm/jni:*"
record_result "query_litertlm_jni" "$?"

run_capture "query_qualcomm_dispatch.txt" "$BAZEL" "${BAZEL_STARTUP_OPTS[@]}" query "${BAZEL_COMMON_OPTS[@]}" "@litert//litert/vendors/qualcomm/dispatch:*"
record_result "query_qualcomm_dispatch" "$?"

run_capture "query_litert_c.txt" "$BAZEL" "${BAZEL_STARTUP_OPTS[@]}" query "${BAZEL_COMMON_OPTS[@]}" "@litert//litert/c:*"
record_result "query_litert_c" "$?"

run_capture "query_qualcomm_compiler.txt" "$BAZEL" "${BAZEL_STARTUP_OPTS[@]}" query "${BAZEL_COMMON_OPTS[@]}" "@litert//litert/vendors/qualcomm/compiler:*"
record_result "query_qualcomm_compiler" "$?"

run_capture "cquery_dispatch_android_arm64.txt" "$BAZEL" "${BAZEL_STARTUP_OPTS[@]}" cquery "${BAZEL_COMMON_OPTS[@]}" "@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so" --config=android_arm64
record_result "cquery_dispatch_android_arm64" "$?"

run_capture "cquery_litert_runtime_android_arm64.txt" "$BAZEL" "${BAZEL_STARTUP_OPTS[@]}" cquery "${BAZEL_COMMON_OPTS[@]}" "@litert//litert/c:litert_runtime_c_api_so" --config=android_arm64
record_result "cquery_litert_runtime_android_arm64" "$?"

run_capture "cquery_litertlm_jni_android_arm64.txt" "$BAZEL" "${BAZEL_STARTUP_OPTS[@]}" cquery "${BAZEL_COMMON_OPTS[@]}" "//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni" --config=android_arm64
record_result "cquery_litertlm_jni_android_arm64" "$?"

{
  printf '# LiteRT Custom Build Query Summary\n\n'
  printf -- '- LiteRT-LM checkout: `%s`\n' "$LITERT_LM_DIR"
  printf -- '- Output: `%s`\n' "$OUT_DIR"
  printf -- '- Build executed: `no`\n'
  printf -- '- Native artifacts generated: `no`\n\n'
  printf '## Results\n\n'
  printf '```text\n'
  cat "$OUT_DIR/results.tsv"
  printf '```\n\n'
  printf '## Files\n\n'
  for file in "$OUT_DIR"/*.txt "$OUT_DIR"/*.tsv; do
    [ -f "$file" ] || continue
    printf -- '- `%s`\n' "$(basename "$file")"
  done
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
printf '%s\n' "$OUT_DIR"

exit 0
