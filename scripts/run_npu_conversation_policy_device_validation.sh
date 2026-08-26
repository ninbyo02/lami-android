#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT_DIR/app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTION="io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION"
RECEIVER="$APP_ID/io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationReceiver"
RESULT_FILE="dev_only_npu_one_turn_conversation_result.txt"
NATIVE_RESULT_FILE="qairt244_short_multitoken_smoke_result.txt"
NATIVE_DIAG_FILE="qairt244_native_diag.txt"
ENDPOINT=""
TIMEOUT_SECONDS=90
INSTALL=true
VERIFY_ARTIFACT=true

usage() {
  cat <<'USAGE'
Usage: scripts/run_npu_conversation_policy_device_validation.sh \
  --endpoint <IPv4:port> [--apk <path>] [--timeout <seconds>] \
  [--skip-install] [--skip-artifact-verification]

Connects only to the explicit ADB endpoint, installs the latest custom NPU APK,
runs two isolated DEV-only NPU conversation turns, and saves machine-readable
policy, sampler, input-length, fallback, and QNN/HTP/FastRPC evidence.
USAGE
}

while (($#)); do
  case "$1" in
    --endpoint) ENDPOINT="${2:-}"; shift 2 ;;
    --apk) APK="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --skip-install) INSTALL=false; shift ;;
    --skip-artifact-verification) VERIFY_ARTIFACT=false; shift ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

validate_endpoint() {
  [[ "$ENDPOINT" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}:[0-9]{1,5}$ ]] ||
    fail "--endpoint must be an explicit IPv4:port"
  local host="${ENDPOINT%:*}" port="${ENDPOINT##*:}" octet
  IFS=. read -r -a octets <<<"$host"
  for octet in "${octets[@]}"; do
    ((10#$octet <= 255)) || fail "invalid IPv4 address: $host"
  done
  ((10#$port >= 1 && 10#$port <= 65535)) || fail "invalid port: $port"
}
validate_timeout() {
  [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] ||
    fail "--timeout must be a positive integer"
  ((TIMEOUT_SECONDS >= 1 && TIMEOUT_SECONDS <= 600)) ||
    fail "--timeout must be between 1 and 600 seconds"
}

kv_value() {
  local key="$1" file="$2"
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$file"
}

require_value() {
  local file="$1" key="$2" expected="$3" actual
  actual="$(kv_value "$key" "$file")"
  if [[ "$actual" != "$expected" ]]; then
    printf 'FAIL file=%s key=%s expected=%s actual=%s\n' \
      "$file" "$key" "$expected" "${actual:-missing}" >&2
    return 1
  fi
  printf 'PASS key=%s value=%s\n' "$key" "$actual"
}

pull_app_file() {
  local remote="$1" target="$2"
  adb -s "$ENDPOINT" exec-out run-as "$APP_ID" cat "files/$remote" \
    >"$target" 2>"$target.err" || true
}
wait_for_result() {
  local target="$1" waited=0 status
  while ((waited < TIMEOUT_SECONDS)); do
    pull_app_file "$RESULT_FILE" "$target"
    status="$(kv_value status "$target")"
    [[ "$status" == success || "$status" == failure ]] && return 0
    sleep 1
    ((waited += 1))
  done
  return 124
}

assert_npu_evidence() {
  local result="$1" native_result="$2" native_diag="$3" logcat="$4" evidence
  evidence="$(kv_value npu_backend_evidence "$result")"
  [[ -n "$evidence" && "$evidence" != "-" ]] ||
    { printf 'FAIL npu_backend_evidence missing\n' >&2; return 1; }
  grep -Eqi 'QNN|HTP|FastRPC' "$result" "$native_result" "$native_diag" "$logcat" ||
    { printf 'FAIL missing QNN/HTP/FastRPC evidence\n' >&2; return 1; }
  printf 'PASS npu_runtime_evidence=%s\n' "$evidence"
}

assert_sampler() {
  local native_result="$1" native_diag="$2"
  local merged="$native_result.merged"
  cat "$native_result" "$native_diag" >"$merged"
  grep -q 'sampler_top_k=40' "$merged" || return 1
  grep -q 'sampler_top_p=0.9' "$merged" || return 1
  grep -q 'sampler_temperature=0.3' "$merged" || return 1
  grep -q 'sampler_seed=42' "$merged" || return 1
  printf 'PASS sampler=top_k_40_top_p_0.9_temperature_0.3_seed_42\n'
}

assert_input_bound() {
  local native_result="$1" native_diag="$2" value
  value="$(cat "$native_result" "$native_diag" |
    sed -n 's/.*prompt_input_code_points=\([0-9][0-9]*\).*/\1/p' |
    tail -1)"
  [[ "$value" =~ ^[0-9]+$ ]] ||
    { printf 'FAIL prompt_input_code_points missing\n' >&2; return 1; }
  ((value <= 128)) ||
    { printf 'FAIL prompt_input_code_points=%s exceeds 128\n' "$value" >&2; return 1; }
  printf 'PASS prompt_input_code_points=%s limit=128\n' "$value"
}

assert_output_policy() {
  local result="$1" output
  output="$(kv_value sanitized_output "$result")"
  [[ -n "$output" ]] ||
    { printf 'FAIL sanitized_output is empty\n' >&2; return 1; }
  if printf '%s\n' "$output" | grep -Eq 'ユーザー:|アシスタント:'; then
    printf 'FAIL output contains a role label: %s\n' "$output" >&2
    return 1
  fi
  printf 'PASS final_answer_only=true output=%s\n' "$output"
}

assert_common_policy() {
  local result="$1" check=0
  require_value "$result" status success || check=1
  require_value "$result" run_decode_reached true || check=1
  require_value "$result" fallback_used false || check=1
  require_value "$result" timeout false || check=1
  require_value "$result" fresh_crash false || check=1
  require_value "$result" backend_npu_persisted true || check=1
  require_value "$result" db false || check=1
  require_value "$result" tts false || check=1
  require_value "$result" markdown false || check=1
  require_value "$result" streaming false || check=1
  require_value "$result" selected_path_npu_saved false || check=1
  require_value "$result" app_template_mode raw || check=1
  require_value "$result" prompt_transport base64 || check=1
  assert_output_policy "$result" || check=1
  ((check == 0))
}

run_turn() {
  local label="$1" prompt="$2" context="$3"
  local run_dir="$OUT_DIR/$label"
  mkdir -p "$run_dir"
  adb -s "$ENDPOINT" logcat -c >/dev/null 2>&1 || true
  adb -s "$ENDPOINT" shell run-as "$APP_ID" rm -f \
    "files/$RESULT_FILE" "files/$NATIVE_RESULT_FILE" "files/$NATIVE_DIAG_FILE" \
    >"$run_dir/cleanup.txt" 2>&1 || true

  local -a args=(
    -a "$ACTION" -p "$APP_ID" -n "$RECEIVER"
    --es user_prompt "$prompt"
    --es prompt_tail_variant raw_dialog_tail_variant_b
    --ei max_output_tokens 32
  )
  if [[ -n "$context" ]]; then
    args+=(--es context "$context")
  fi
  adb -s "$ENDPOINT" shell am broadcast "${args[@]}" \
    >"$run_dir/broadcast.txt" 2>&1 ||
    { printf 'FAIL broadcast failed for %s\n' "$label" >&2; return 1; }

  wait_for_result "$run_dir/result.txt" ||
    { printf 'FAIL timed out waiting for %s\n' "$label" >&2; return 1; }
  pull_app_file "$NATIVE_RESULT_FILE" "$run_dir/native_result.txt"
  pull_app_file "$NATIVE_DIAG_FILE" "$run_dir/native_diag.txt"
  adb -s "$ENDPOINT" logcat -d -t 1200 >"$run_dir/logcat.txt" 2>&1 || true

  {
    local check=0
    printf 'turn=%s\n' "$label"
    assert_common_policy "$run_dir/result.txt" || check=1
    assert_npu_evidence "$run_dir/result.txt" "$run_dir/native_result.txt" \
      "$run_dir/native_diag.txt" "$run_dir/logcat.txt" || check=1
    assert_sampler "$run_dir/native_result.txt" "$run_dir/native_diag.txt" || check=1
    assert_input_bound "$run_dir/native_result.txt" "$run_dir/native_diag.txt" || check=1
    ((check == 0))
  } | tee "$run_dir/assertions.txt"
  return "${PIPESTATUS[0]}"
}

main() {
  validate_endpoint
  validate_timeout
  cd "$ROOT_DIR"

  local timestamp
  timestamp="$(date +%Y%m%d_%H%M%S)"
  OUT_DIR="$ROOT_DIR/artifacts/npu_conversation_policy_device_validation/$timestamp"
  mkdir -p "$OUT_DIR"
  printf '%s\n' "$ENDPOINT" >"$OUT_DIR/endpoint.txt"

  adb connect "$ENDPOINT" >"$OUT_DIR/adb_connect.txt" 2>&1 || true
  adb devices -l >"$OUT_DIR/adb_devices.txt"
  awk -v serial="$ENDPOINT" '$1 == serial && $2 == "device" { found=1 }
    END { exit found ? 0 : 1 }' "$OUT_DIR/adb_devices.txt" ||
    fail "explicit ADB endpoint is not online: $ENDPOINT"

  if [[ "$VERIFY_ARTIFACT" == true ]]; then
    scripts/verify_npu_conversation_policy_artifacts.sh --skip-preflight \
      >"$OUT_DIR/artifact_verification.txt" 2>&1
  fi
  [[ -f "$APK" ]] || fail "APK not found: $APK"
  sha256sum "$APK" >"$OUT_DIR/apk_sha256.txt"
  if [[ "$INSTALL" == true ]]; then
    adb -s "$ENDPOINT" install -r "$APK" >"$OUT_DIR/install.txt" 2>&1
  fi

  adb -s "$ENDPOINT" shell dumpsys package "$APP_ID" \
    >"$OUT_DIR/package_dump.txt" 2>&1
  grep -E 'versionCode=|versionName=|lastUpdateTime=|DevOnlyNpuOneTurnConversationReceiver' \
    "$OUT_DIR/package_dump.txt" >"$OUT_DIR/package_summary.txt" || true
  grep -q 'DevOnlyNpuOneTurnConversationReceiver' "$OUT_DIR/package_dump.txt" ||
    fail "DEV-only receiver missing from installed package"

  adb -s "$ENDPOINT" shell pm path "$APP_ID" >"$OUT_DIR/package_path.txt"
  adb -s "$ENDPOINT" shell getprop ro.product.manufacturer >"$OUT_DIR/manufacturer.txt"
  adb -s "$ENDPOINT" shell getprop ro.product.model >"$OUT_DIR/model.txt"
  adb -s "$ENDPOINT" shell getprop ro.soc.model >"$OUT_DIR/soc_model.txt"

  local failure=0
  run_turn turn1 "日本の首都を一語で答えてください。" "" || failure=1
  run_turn turn2 "前の回答を踏まえ、国名を一語で答えてください。" \
    $'ユーザー: 日本の首都を一語で答えてください。\nアシスタント: 東京' || failure=1

  {
    printf 'endpoint=%s\n' "$ENDPOINT"
    printf 'apk=%s\n' "$APK"
    printf 'package=%s\n' "$APP_ID"
    printf 'turn_count=2\n'
    printf 'result=%s\n' "$([[ "$failure" -eq 0 ]] && printf PASS || printf FAIL)"
    printf 'artifact_dir=%s\n' "${OUT_DIR#$ROOT_DIR/}"
  } | tee "$OUT_DIR/summary.txt"

  [[ "$failure" -eq 0 ]] || exit 1
  printf 'npu_conversation_policy_device_validation=ok\n'
}

main "$@"
