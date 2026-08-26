#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNNER="$ROOT_DIR/scripts/run_npu_conversation_policy_device_validation.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$TMP_DIR/bin"
touch "$TMP_DIR/fake.apk"

cat >"$TMP_DIR/bin/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${FAKE_ADB_LOG:?}"
if [[ "${1:-}" == connect ]]; then
  printf 'connected to %s\n' "$2"
  exit 0
fi
if [[ "${1:-}" == devices ]]; then
  printf 'List of devices attached\n192.0.2.1:5555 device product:test model:Test_Device\n'
  exit 0
fi
shift 2
case "$*" in
  "shell dumpsys package io.github.ninbyo02.lami.customnpu")
    printf 'versionCode=1 versionName=test\nDevOnlyNpuOneTurnConversationReceiver\n' ;;
  "shell pm path io.github.ninbyo02.lami.customnpu")
    printf 'package:/data/app/test/base.apk\n' ;;
  "shell getprop ro.product.manufacturer") printf 'TestMaker\n' ;;
  "shell getprop ro.product.model") printf 'TestDevice\n' ;;
  "shell getprop ro.soc.model") printf 'SM8750\n' ;;
  "shell monkey -p io.github.ninbyo02.lami.customnpu -c android.intent.category.LAUNCHER 1") ;;
  "shell pidof io.github.ninbyo02.lami.customnpu") printf '1234\n' ;;
  "exec-out run-as io.github.ninbyo02.lami.customnpu cat files/dev_only_npu_one_turn_conversation_result.txt")
    [[ "${FAKE_ADB_RESULT_MODE:-success}" == success ]] || exit 0
    count=0
    [[ ! -f "${FAKE_ADB_STATE_FILE:?}" ]] || read -r count <"$FAKE_ADB_STATE_FILE"
    count=$((count + 1))
    printf '%s\n' "$count" >"$FAKE_ADB_STATE_FILE"
    output=東京
    ((count < 2)) || output=日本
    cat <<RESULT
status=success
run_decode_reached=true
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
backend_npu_persisted=true
db=false
tts=false
markdown=false
streaming=false
selected_path_npu_saved=false
app_template_mode=raw
prompt_transport=base64
sanitized_output=$output
RESULT
    ;;
  "exec-out run-as io.github.ninbyo02.lami.customnpu cat files/qairt244_persistent_custom_jni_probe_result.txt")
    printf '%s\n' sampler_top_k=40 sampler_top_p=0.9 \
      sampler_temperature=0.3 sampler_seed=42 prompt_input_code_points=96 ;;
  "exec-out run-as io.github.ninbyo02.lami.customnpu cat files/qairt244_persistent_custom_jni_probe_diag.txt")
    printf 'QNN HTP FastRPC\n' ;;
  *) ;;
esac
ADB
chmod +x "$TMP_DIR/bin/adb"
export FAKE_ADB_LOG="$TMP_DIR/adb.log"
export FAKE_ADB_STATE_FILE="$TMP_DIR/adb-state"
export FAKE_ADB_RESULT_MODE=success

cd "$ROOT_DIR"
before="$(find artifacts/npu_conversation_policy_device_validation -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort || true)"
PATH="$TMP_DIR/bin:$PATH" "$RUNNER" --endpoint 192.0.2.1:5555 \
  --apk "$TMP_DIR/fake.apk" --skip-install --skip-artifact-verification \
  >"$TMP_DIR/run.log"
after="$(find artifacts/npu_conversation_policy_device_validation -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort || true)"
artifact="$(comm -13 <(printf '%s\n' "$before") <(printf '%s\n' "$after") | tail -1)"
[[ -n "$artifact" ]] || { printf 'FAIL: artifact not created\n' >&2; exit 1; }
trap 'rm -rf "$TMP_DIR" "$artifact"' EXIT
summary="$artifact/summary.md"

grep -q 'Result: \*\*PASS\*\*' "$summary"
grep -q 'TestMaker TestDevice' "$summary"
grep -q 'SM8750' "$summary"
grep -q '| turn1 | success | true | false |' "$summary"
grep -q '| turn2 | success | true | false |' "$summary"
grep -q 'QNN_HTP_V79_FastRPC_native_diag' "$summary"
grep -q '| 96 | 東京 |' "$summary"
grep -q '| 96 | 日本 |' "$summary"
grep -q 'top-k=40, top-p=0.9, temperature=0.3, seed=42' "$summary"
grep -q 'DB/TTS/Markdown/streaming/selected-path=false' "$summary"
grep -q 'prompt_tail_variant raw_dialog_tail_variant_a' "$FAKE_ADB_LOG"
expected_context_base64="$(printf '%s' $'ユーザー: 日本の首都を句読点なしの一語で答えてください。\nアシスタント: 東京' | base64 | tr -d '\n')"
grep -q "context_base64 $expected_context_base64" "$FAKE_ADB_LOG"

before_failure="$after"
: >"$FAKE_ADB_LOG"
: >"$FAKE_ADB_STATE_FILE"
export FAKE_ADB_RESULT_MODE=missing
if PATH="$TMP_DIR/bin:$PATH" "$RUNNER" --endpoint 192.0.2.1:5555 \
  --apk "$TMP_DIR/fake.apk" --timeout 1 --skip-install \
  --skip-artifact-verification >"$TMP_DIR/failure.log" 2>&1; then
  printf 'FAIL: missing result must fail validation\n' >&2
  exit 1
fi
after_failure="$(find artifacts/npu_conversation_policy_device_validation -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort || true)"
failure_artifact="$(comm -13 <(printf '%s\n' "$before_failure") <(printf '%s\n' "$after_failure") | tail -1)"
[[ -n "$failure_artifact" ]] || { printf 'FAIL: failure artifact not created\n' >&2; exit 1; }
trap 'rm -rf "$TMP_DIR" "$artifact" "$failure_artifact"' EXIT
grep -q 'Result: \*\*FAIL\*\*' "$failure_artifact/summary.md"
grep -q '| turn1 |  |  |  | unavailable | unavailable | unavailable |' "$failure_artifact/summary.md"

printf 'npu_conversation_policy_device_summary_test=ok\n'
