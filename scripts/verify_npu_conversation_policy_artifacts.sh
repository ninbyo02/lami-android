#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_PREFLIGHT=true

usage() {
  cat <<'USAGE'
Usage: scripts/verify_npu_conversation_policy_artifacts.sh [--skip-preflight]

Verifies the pinned LiteRT-LM patches apply, assembles the custom NPU APK,
checks stable sampler markers in the staged JNI, and proves the APK contains
the exact staged JNI bytes.
USAGE
}

while (($#)); do
  case "$1" in
    --skip-preflight) RUN_PREFLIGHT=false; shift ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

cd "$ROOT_DIR"
if [[ "$RUN_PREFLIGHT" == true ]]; then
  scripts/rebuild_qairt244_standard_debug_native_stack.sh \
    --preflight-only \
    --label qairt244_npu_conversation_policy_verification
fi
ANDROID_HOME_VALUE="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ANDROID_HOME="$ANDROID_HOME_VALUE" \
ANDROID_SDK_ROOT="$ANDROID_HOME_VALUE" \
  ./gradlew verifyQairt244CustomBuildExperimentDebugApkNpuJni

printf 'npu_conversation_policy_artifact_verification=ok\n'
