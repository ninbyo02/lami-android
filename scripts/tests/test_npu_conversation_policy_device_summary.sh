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
  "exec-out run-as io.github.ninbyo02.lami.customnpu cat files/dev_only_npu_one_turn_conversation_result.txt")
    cat <<'RESULT'
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
sanitized_output=東京
RESULT
    ;;
  "exec-out run-as io.github.ninbyo02.lami.customnpu cat files/qairt244_short_multitoken_smoke_result.txt")
    printf '%s\n' sampler_top_k=40 sampler_top_p=0.9 \
      sampler_temperature=0.3 sampler_seed=42 prompt_input_code_points=96 ;;
  "exec-out run-as io.github.ninbyo02.lami.customnpu cat files/qairt244_native_diag.txt")
    printf 'QNN HTP FastRPC\n' ;;
  *) ;;
esac
ADB
chmod +x "$TMP_DIR/bin/adb"

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
grep -q 'top-k=40, top-p=0.9, temperature=0.3, seed=42' "$summary"
grep -q 'DB/TTS/Markdown/streaming/selected-path=false' "$summary"

printf 'npu_conversation_policy_device_summary_test=ok\n'
