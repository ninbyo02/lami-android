#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

patch_file="patches/qairt244_litertlm_utf8_128token_persistent_probe.patch"
rebuild_script="scripts/rebuild_qairt244_standard_debug_native_stack.sh"

grep -q 'GetMutableSamplerParams' "$patch_file" ||
  fail "native patch must configure sampler parameters"
grep -q 'sampler_config_profile=lami_stable_v1' "$patch_file" ||
  fail "native patch must expose sampler profile"
grep -q 'sampler_top_k=40' "$patch_file" || fail "native patch must expose top-k"
grep -q 'sampler_top_p=0.9' "$patch_file" || fail "native patch must expose top-p"
grep -q 'sampler_temperature=0.3' "$patch_file" || fail "native patch must expose temperature"
grep -q 'sampler_seed=42' "$patch_file" || fail "native patch must expose seed"
grep -q 'thinking_control=raw_prompt_answer_only' "$patch_file" ||
  fail "native patch must expose thinking control"
grep -q 'stable NPU conversation policy marker is missing' "$rebuild_script" ||
  fail "reproducible native build must verify sampler markers"
grep -q 'verifyQairt244CustomBuildExperimentDebugApkNpuJni' app/build.gradle.kts ||
  fail "Gradle must expose packaged JNI verification"
grep -q 'contentEquals(apkJniBytes)' app/build.gradle.kts ||
  fail "packaged JNI verification must compare exact bytes"
grep -q 'rebuild_qairt244_standard_debug_native_stack.sh'   scripts/verify_npu_conversation_policy_artifacts.sh ||
  fail "artifact verifier must run patch preflight"

echo "NPU conversation policy artifact verification checks passed"
