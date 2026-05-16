#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/litert_custom_build_workspace/$TIMESTAMP"

LITERT_LM_REPO="https://github.com/google-ai-edge/LiteRT-LM.git"
LITERT_REPO="https://github.com/google-ai-edge/LiteRT.git"
GALLERY_REPO="https://github.com/google-ai-edge/gallery.git"

log() {
  printf '[litert-custom-build-prep] %s\n' "$*"
}

write_cmd() {
  local out="$1"
  shift
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n'
    "$@" 2>&1
    printf '\nexit_code=%s\n' "$?"
  } >"$out"
}

append_cmd() {
  local out="$1"
  shift
  {
    printf '\n$'
    printf ' %q' "$@"
    printf '\n'
    "$@" 2>&1
    printf '\nexit_code=%s\n' "$?"
  } >>"$out"
}

tool_line() {
  local tool="$1"
  local out="$2"
  if command -v "$tool" >/dev/null 2>&1; then
    printf '%s: %s\n' "$tool" "$(command -v "$tool")" >>"$out"
    case "$tool" in
      java) java -version >>"$out" 2>&1 ;;
      python3) python3 --version >>"$out" 2>&1 ;;
      cmake) cmake --version >>"$out" 2>&1 | head -n 2 ;;
      ninja) ninja --version >>"$out" 2>&1 ;;
      adb) adb version >>"$out" 2>&1 | head -n 4 ;;
      bazel|bazelisk) "$tool" --version >>"$out" 2>&1 ;;
      *) "$tool" --version >>"$out" 2>&1 | head -n 3 ;;
    esac
  else
    printf '%s: <missing>\n' "$tool" >>"$out"
  fi
  printf '\n' >>"$out"
}

record_local_git() {
  local path="$1"
  local label="$2"
  local out="$3"
  {
    printf '## %s\n\n' "$label"
    printf 'path: `%s`\n\n' "$path"
    if [ -d "$path/.git" ]; then
      printf 'HEAD: '
      git -C "$path" rev-parse HEAD 2>/dev/null || true
      printf 'branch: '
      git -C "$path" branch --show-current 2>/dev/null || true
      printf 'describe: '
      git -C "$path" describe --tags --always --dirty 2>/dev/null || true
      printf '\n'
    else
      printf 'status: missing local git checkout\n\n'
    fi
  } >>"$out"
}

mkdir -p "$OUT_DIR/remote_refs" "$OUT_DIR/local_inspection"
cd "$ROOT_DIR" || exit 1

log "output: $OUT_DIR"

TOOLS_OUT="$OUT_DIR/tool_versions.txt"
: >"$TOOLS_OUT"
for tool in git java python3 bazel bazelisk cmake ninja adb unzip readelf nm strings sha256sum; do
  tool_line "$tool" "$TOOLS_OUT"
done

ENV_OUT="$OUT_DIR/environment.txt"
{
  printf '# Environment\n\n'
  printf 'date: %s\n' "$(date -Is)"
  printf 'cwd: %s\n' "$ROOT_DIR"
  printf 'user: %s\n' "$(id -un 2>/dev/null || true)"
  printf '\n## OS\n\n'
  uname -a
  printf '\n## Disk\n\n'
  df -h "$ROOT_DIR" /tmp /home 2>/dev/null || df -h
  printf '\n## Android / QAIRT environment variables\n\n'
  env | sort | grep -E '^(ANDROID|JAVA_HOME|NDK|QAIRT|QNN|BAZEL)' || true
  printf '\n## SDK candidates\n\n'
  for path in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "/usr/lib/android-sdk"; do
    [ -n "${path:-}" ] || continue
    if [ -d "$path" ]; then
      printf '%s\n' "$path"
      find "$path" -maxdepth 2 -type d \( -name 'android-*' -o -name 'ndk*' \) 2>/dev/null | sort | head -n 80
    fi
  done
  printf '\n## QAIRT candidates\n\n'
  for path in "${QAIRT_HOME:-}" "${QNN_SDK_ROOT:-}" "$HOME/compose/qairt" "$HOME/compose/qairt/workspace/sdk/qairt" "$ROOT_DIR/workspace/sdk/qairt"; do
    [ -n "${path:-}" ] || continue
    if [ -e "$path" ]; then
      printf '%s\n' "$path"
      find "$path" -maxdepth 4 \( -name 'qnn-net-run' -o -name 'qnn-platform-validator' -o -name 'QnnSystem.h' -o -name 'libQnnSystem.so' \) 2>/dev/null | sort | head -n 120
    fi
  done
} >"$ENV_OUT"

log "recording remote refs"
write_cmd "$OUT_DIR/remote_refs/litert_lm_tags.txt" git ls-remote --tags "$LITERT_LM_REPO"
write_cmd "$OUT_DIR/remote_refs/litert_tags.txt" git ls-remote --tags "$LITERT_REPO"
write_cmd "$OUT_DIR/remote_refs/gallery_tags.txt" git ls-remote --tags "$GALLERY_REPO"

LOCAL_OUT="$OUT_DIR/local_sources.md"
: >"$LOCAL_OUT"
record_local_git "/tmp/litert-lm-v0.11.0" "LiteRT-LM v0.11.0 checkout candidate" "$LOCAL_OUT"
record_local_git "/tmp/litert-47615" "LiteRT commit 47615 checkout candidate" "$LOCAL_OUT"
record_local_git "/tmp/google-ai-edge-gallery-1.0.12" "Gallery 1.0.12 checkout candidate" "$LOCAL_OUT"

if [ -f "/tmp/litert-lm-v0.11.0/WORKSPACE" ]; then
  grep -nE 'LITERT_REF|LITERT_SHA256|TENSORFLOW_REF|TENSORFLOW_SHA256|http_archive\(name = "litert"' \
    "/tmp/litert-lm-v0.11.0/WORKSPACE" >"$OUT_DIR/local_inspection/litert_lm_v0.11.0_workspace_refs.txt" 2>/dev/null || true
fi

if [ -f "/tmp/litert-lm-v0.11.0/kotlin/java/com/google/ai/edge/litertlm/jni/BUILD" ]; then
  sed -n '1,220p' "/tmp/litert-lm-v0.11.0/kotlin/java/com/google/ai/edge/litertlm/jni/BUILD" \
    >"$OUT_DIR/local_inspection/litert_lm_jni_BUILD.txt" 2>/dev/null || true
fi

if [ -f "/tmp/litert-lm-v0.11.0/kotlin/java/com/google/ai/edge/litertlm/BUILD" ]; then
  sed -n '1,260p' "/tmp/litert-lm-v0.11.0/kotlin/java/com/google/ai/edge/litertlm/BUILD" \
    >"$OUT_DIR/local_inspection/litert_lm_android_BUILD.txt" 2>/dev/null || true
fi

if [ -f "/tmp/litert-lm-v0.11.0/runtime/engine/BUILD" ]; then
  sed -n '1,260p' "/tmp/litert-lm-v0.11.0/runtime/engine/BUILD" \
    >"$OUT_DIR/local_inspection/litert_lm_engine_BUILD.txt" 2>/dev/null || true
fi

if [ -f "/tmp/litert-47615/litert/vendors/qualcomm/dispatch/BUILD" ]; then
  sed -n '1,260p' "/tmp/litert-47615/litert/vendors/qualcomm/dispatch/BUILD" \
    >"$OUT_DIR/local_inspection/litert_qualcomm_dispatch_BUILD.txt" 2>/dev/null || true
fi

if [ -f "/tmp/litert-47615/litert/c/BUILD" ]; then
  grep -nE 'litert_runtime_c_api_so|libLiteRt\.so|litert_runtime_c_api_shared_lib|cc_shared_library|filegroup' \
    "/tmp/litert-47615/litert/c/BUILD" >"$OUT_DIR/local_inspection/litert_c_BUILD_targets.txt" 2>/dev/null || true
fi

if [ -f "/tmp/litert-47615/litert/vendors/qualcomm/compiler/BUILD" ]; then
  sed -n '1,240p' "/tmp/litert-47615/litert/vendors/qualcomm/compiler/BUILD" \
    >"$OUT_DIR/local_inspection/litert_qualcomm_compiler_BUILD.txt" 2>/dev/null || true
fi

if [ -f "/tmp/litert-47615/litert/integration_test/litert_device_common.bzl" ]; then
  grep -nE 'Qualcomm|V79|ADSP_LIBRARY_PATH|LD_LIBRARY_PATH|LiteRtDispatch|CompilerPlugin|QnnHtp|QnnSystem' \
    "/tmp/litert-47615/litert/integration_test/litert_device_common.bzl" \
    >"$OUT_DIR/local_inspection/litert_qualcomm_integration_spec.txt" 2>/dev/null || true
fi

if [ -f "/tmp/google-ai-edge-gallery-1.0.12/Android/src/gradle/libs.versions.toml" ]; then
  grep -nE 'litertlm|tasks-genai|qnn|litert' "/tmp/google-ai-edge-gallery-1.0.12/Android/src/gradle/libs.versions.toml" \
    >"$OUT_DIR/local_inspection/gallery_1.0.12_versions.txt" 2>/dev/null || true
fi

SUMMARY="$OUT_DIR/summary.md"
{
  printf '# LiteRT Custom Build Workspace Preparation\n\n'
  printf -- '- Timestamp: `%s`\n' "$TIMESTAMP"
  printf -- '- Repository: `%s`\n' "$ROOT_DIR"
  printf -- '- Build executed: `no`\n'
  printf -- '- Native artifacts generated: `no`\n'
  printf -- '- App files modified by script: `no`\n\n'
  printf '## Recommended source candidates\n\n'
  printf -- '- LiteRT-LM: `v0.11.0` / `c87189528a758db32ead241f4fc9c64836398ee7`\n'
  printf -- '- LiteRT: `47615eb6eaec25e8dfcd1aba922c560a57cba0a2` pinned by LiteRT-LM `v0.11.0`\n'
  printf -- '- Gallery: `1.0.12` / `302f7e463b19f45f51825f4ec2fd30309366cb06`\n\n'
  printf '## First query targets when tools are ready\n\n'
  printf '```bash\n'
  printf "bazel query '@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so'\n"
  printf "bazel query '@litert//litert/c:litert_runtime_c_api_so'\n"
  printf "bazel query '@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so'\n"
  printf "bazel query '//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni'\n"
  printf '```\n\n'
  printf '## Files\n\n'
  printf -- '- `tool_versions.txt`\n'
  printf -- '- `environment.txt`\n'
  printf -- '- `remote_refs/`\n'
  printf -- '- `local_sources.md`\n'
  printf -- '- `local_inspection/`\n\n'
  printf '## Safety\n\n'
  printf 'This preparation script intentionally does not clone, query, build, stage, install, or run `Engine.initialize`.\n'
} >"$SUMMARY"

cat >"$OUT_DIR/next_commands_dry_run.txt" <<'EOF'
# Commands for a later phase only. Do not run until explicitly approved.

# Source checkout only:
# git clone https://github.com/google-ai-edge/LiteRT-LM.git
# git -C LiteRT-LM checkout v0.11.0
# git clone https://github.com/google-ai-edge/LiteRT.git
# git -C LiteRT checkout 47615eb6eaec25e8dfcd1aba922c560a57cba0a2

# Query only after Bazel/Bazelisk and Android NDK are configured:
# bazel query '@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so'
# bazel query '@litert//litert/c:litert_runtime_c_api_so'
# bazel query '@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so'
# bazel query '//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni'
EOF

log "wrote $SUMMARY"
printf '%s\n' "$OUT_DIR"
