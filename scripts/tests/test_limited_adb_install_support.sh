#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

help_text="$(./update.sh --help)"
[[ "$help_text" == *"--host HOST"* ]] || fail "update.sh help should document --host HOST"
[[ "$help_text" == *"Allowed hosts"* ]] || fail "update.sh help should document allowed hosts"

grep -q '^DEFAULT_PHONE_HOST=' update.sh || fail "update.sh should expose DEFAULT_PHONE_HOST"
grep -q 'validate_phone_host' update.sh || fail "update.sh should validate ADB host values"
grep -q 'validate_adb_port' update.sh || fail "update.sh should validate ADB port values"
grep -q 'adb connect "${phone_host}:${port}"' update.sh || fail "update.sh should use the selected phone host for adb connect"

test -f scripts/lami_build_remote_control_limited_adb.sh || fail "limited ADB remote_control template missing"
test -f scripts/lami_build_qairt244_forced_commands.sh || fail "qairt244 forced-command extension missing"
grep -q 'install-future' scripts/lami_build_remote_control_limited_adb.sh || fail "template should expose install-future"
grep -q 'adb-devices' scripts/lami_build_remote_control_limited_adb.sh || fail "template should expose adb-devices"
grep -q 'validate_host' scripts/lami_build_remote_control_limited_adb.sh || fail "template should validate host allowlist"
grep -q 'qairt244-artifacts' scripts/lami_build_remote_control_limited_adb.sh || fail "template should expose qairt244 artifact listing"
grep -q 'stage-qairt244-custom-jni' scripts/lami_build_remote_control_limited_adb.sh || fail "template should expose qairt244 custom JNI staging"
grep -q 'build-qairt244-custom-jni' scripts/lami_build_remote_control_limited_adb.sh || fail "template should expose qairt244 custom JNI build+stage"
grep -q 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt' scripts/lami_build_qairt244_forced_commands.sh || fail "qairt244 extension should verify the nativeRunEditablePrompt JNI symbol"
grep -q 'JNI marker' scripts/lami_build_qairt244_forced_commands.sh || fail "qairt244 extension should verify the GPU prefill marker in litertlm.cc"
grep -q 'selected_ref_gpu_prefill_preinvoke_marker_litertlm_source_present' scripts/check_qairt244_native_patch.sh || fail "selected-ref check should prove the litertlm.cc marker hunk is present"
grep -q 'skipped-missing-required-marker' scripts/build_litert_custom_artifacts.sh || fail "artifact copy should skip non-marker JNI candidates for diagnostic builds"

echo "limited ADB install support checks passed"
