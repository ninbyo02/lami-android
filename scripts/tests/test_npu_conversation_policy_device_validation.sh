#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNNER="$ROOT_DIR/scripts/run_npu_conversation_policy_device_validation.sh"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

[[ -x "$RUNNER" ]] || fail "device validation runner must be executable"
bash -n "$RUNNER" || fail "device validation runner syntax"

help_output="$("$RUNNER" --help)"
grep -q -- '--endpoint <IPv4:port>' <<<"$help_output" ||
  fail "help must require explicit endpoint"
grep -q -- '--skip-install' <<<"$help_output" ||
  fail "help must document install override"

if "$RUNNER" --endpoint invalid --skip-install --skip-artifact-verification \
  >/dev/null 2>&1; then
  fail "invalid endpoint must fail before ADB use"
fi

grep -q 'adb connect "$ENDPOINT"' "$RUNNER" ||
  fail "runner must connect only to explicit endpoint"
grep -q 'install -r "$APK"' "$RUNNER" ||
  fail "runner must replace-install the selected APK"
grep -q 'shell monkey -p "$APP_ID"' "$RUNNER" ||
  fail "runner must cold-start the installed app before receiver validation"
grep -q 'shell pidof "$APP_ID"' "$RUNNER" ||
  fail "runner must confirm the app process is ready"
grep -q 'qairt244_persistent_custom_jni_probe_result.txt' "$RUNNER" ||
  fail "runner must collect the persistent native result"
grep -q 'qairt244_persistent_custom_jni_probe_diag.txt' "$RUNNER" ||
  fail "runner must collect the persistent native diagnostics"
grep -q 'turn1' "$RUNNER" || fail "runner must include turn 1"
grep -q 'turn2' "$RUNNER" || fail "runner must include turn 2"
grep -q -- '--es context_base64' "$RUNNER" ||
  fail "turn 2 must pass shell-safe base64 conversation context"
grep -q "base64 | tr -d" "$RUNNER" ||
  fail "turn 2 context must not expose embedded newlines to adb shell"

for expected in \
  'sampler_top_k=40' \
  'sampler_top_p=0.9' \
  'sampler_temperature=0.3' \
  'sampler_seed=42' \
  'prompt_input_code_points' \
  'backend_npu_persisted true' \
  'fallback_used false' \
  'selected_path_npu_saved false' \
  'app_template_mode raw' \
  'prompt_transport base64'; do
  grep -q "$expected" "$RUNNER" ||
    fail "missing policy assertion: $expected"
done

grep -q 'for marker in QNN HTP FastRPC' "$RUNNER" ||
  fail "runner must require every NPU runtime marker"
grep -q 'QNN_HTP_V79_FastRPC_native_diag' "$RUNNER" ||
  fail "runner must require the exact NPU evidence profile"
grep -q 'prompt_tail_variant raw_dialog_tail_variant_a' "$RUNNER" ||
  fail "runner must exercise the production raw prompt variant"
grep -q 'turn1_output=' "$RUNNER" ||
  fail "turn 2 must use the actual turn 1 output"
grep -q 'ユーザー:|アシスタント:' "$RUNNER" ||
  fail "runner must reject role-label continuation"

printf 'npu_conversation_policy_device_validation_test=ok\n'
