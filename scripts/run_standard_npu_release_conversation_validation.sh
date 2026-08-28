#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
endpoint=
apk=
app_id=io.github.ninbyo02.lami.npuvalidation
action=io.github.ninbyo02.lami.action.STANDARD_NPU_RELEASE_PREFLIGHT
receiver=io.github.ninbyo02.lami.ui.screens.home.StandardNpuReleasePreflightReceiver

while (($#)); do
  case "$1" in
    --endpoint) endpoint=${2:?missing endpoint}; shift 2 ;;
    --apk) apk=${2:?missing apk}; shift 2 ;;
    --app-id) app_id=${2:?missing app id}; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$endpoint" && -f "$apk" ]] || {
  echo "usage: $0 --endpoint HOST:PORT --apk SIGNED_APK [--app-id ID]" >&2
  exit 2
}
timestamp=$(date +%Y%m%d_%H%M%S_%N)
out_dir="$root_dir/artifacts/standard_npu_release_conversation/$timestamp"
mkdir -p "$out_dir"
adb() { command adb -s "$endpoint" "$@"; }
adb connect "$endpoint" >"$out_dir/adb_connect.txt"
adb get-state | grep -Fxq device
adb install -r "$apk" >"$out_dir/adb_install.txt"
# Clear Android's post-install stopped state, then leave the base process dead;
# each turn is delivered to the isolated validation process.
adb shell monkey -p "$app_id" -c android.intent.category.LAUNCHER 1 \
  >"$out_dir/package_unstop.txt" 2>&1
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME \
  >"$out_dir/home_after_unstop.txt" 2>&1
adb shell am kill "$app_id" >"$out_dir/base_process_kill.txt" 2>&1 || true
sleep 1
external_dir="/sdcard/Android/data/$app_id/files"
status_name=standard_npu_release_preflight_status.txt
result_name=qairt244_persistent_custom_jni_probe_result.txt
diag_name=qairt244_persistent_custom_jni_probe_diag.txt
turn_output=

kv() {
  local key=$1 file=$2
  sed -n "s/^${key}=//p" "$file" | tail -1
}

run_turn() {
  local label=$1 prompt=$2 context=$3 expected=$4
  local dir="$out_dir/$label"
  mkdir -p "$dir"
  adb shell rm -f \
    "$external_dir/$status_name" \
    "$external_dir/$result_name" \
    "$external_dir/$diag_name" || true
  adb logcat -c
  local prompt_b64 context_b64
  prompt_b64=$(printf '%s' "$prompt" | base64 | tr -d '\n')
  context_b64=$(printf '%s' "$context" | base64 | tr -d '\n')
  adb shell am broadcast --include-stopped-packages \
    -a "$action" -n "$app_id/$receiver" \
    --es native_probe_mode standard_route_turn \
    --es user_prompt_base64 "$prompt_b64" \
    --es context_base64 "$context_b64" \
    --ei max_output_tokens 32 >"$dir/am_broadcast.txt"
  local status=
  for _ in $(seq 1 100); do
    status=$(adb shell cat "$external_dir/$status_name" 2>/dev/null | tr -d '\r' || true)
    if grep -Eq '^status=(returned|failed)$' <<<"$status"; then break; fi
    sleep 1
  done
  printf '%s\n' "$status" >"$dir/status.txt"
  for name in "$result_name" "$diag_name"; do
    adb pull "$external_dir/$name" "$dir/$name" >/dev/null 2>&1 || true
  done
  adb logcat -b all -d -t 6000 >"$dir/logcat.txt" 2>/dev/null || true
  local encoded
  encoded=$(kv output_base64 "$dir/status.txt")
  turn_output=$(printf '%s' "$encoded" | base64 -d 2>/dev/null || true)
  printf '%s' "$turn_output" >"$dir/output.txt"
  local pass=true
  grep -Fxq 'status=returned' "$dir/status.txt" || pass=false
  grep -Fxq 'route_status=success' "$dir/status.txt" || pass=false
  grep -Fxq 'decode_reached=true' "$dir/status.txt" || pass=false
  grep -Fxq 'npu_evidence=QNN_HTP_V79_FastRPC_native_diag' "$dir/status.txt" || pass=false
  grep -Fxq 'fallback=false' "$dir/status.txt" || pass=false
  grep -Fxq 'timeout=false' "$dir/status.txt" || pass=false
  [[ "$turn_output" == "$expected" ]] || pass=false
  grep -Fq 'hypothesis_result=standard_route_reuse_once_success' \
    "$dir/$result_name" 2>/dev/null || pass=false
  grep -Fq 'sampler_backend=NPU' "$dir/$result_name" 2>/dev/null || pass=false
  local code_points
  code_points=$(kv prompt_input_code_points "$dir/$result_name")
  [[ "$code_points" =~ ^[0-9]+$ && "$code_points" -le 128 ]] || pass=false
  if grep -Eqi 'SIGABRT|FATAL EXCEPTION|No usable Dispatch runtime found' "$dir/logcat.txt"; then
    pass=false
  fi
  printf 'turn=%s\nexpected=%s\nactual=%s\ncode_points=%s\nresult=%s\n' \
    "$label" "$expected" "$turn_output" "${code_points:-unavailable}" \
    "$([[ "$pass" == true ]] && echo PASS || echo FAIL)" >"$dir/assertions.txt"
  [[ "$pass" == true ]]
}

failure=0
prompt1='日本の首都を句読点なしの一語で答えてください。'
run_turn turn1 "$prompt1" '' '東京' || failure=1
turn1_output=$turn_output
context2=$'ユーザー: 日本の首都を句読点なしの一語で答えてください。\nアシスタント: '"$turn1_output"
prompt2='前の回答を踏まえ、国名を句読点なしの一語で答えてください。'
run_turn turn2 "$prompt2" "$context2" '日本' || failure=1
result=FAIL
[[ "$failure" -eq 0 ]] && result=PASS
{
  echo '# Standard NPU Release Conversation Validation'
  echo
  echo "- Result: \`$result\`"
  echo "- Endpoint: \`$endpoint\`"
  echo "- App ID: \`$app_id\`"
  echo "- Turn 1: \`$(cat "$out_dir/turn1/output.txt" 2>/dev/null)\`"
  echo "- Turn 2: \`$(cat "$out_dir/turn2/output.txt" 2>/dev/null)\`"
  echo "- APK SHA-256: \`$(sha256sum "$apk" | cut -d' ' -f1)\`"
  echo "- Isolated process: \`:npu_preflight\`"
  echo "- Artifact: \`${out_dir#"$root_dir"/}\`"
} >"$out_dir/summary.md"
cat "$out_dir/summary.md"
[[ "$failure" -eq 0 ]]
