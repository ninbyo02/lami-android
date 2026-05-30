#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
RECEIVER="io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver"
ACTION="io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_turn_stop_quality_compare/$TIMESTAMP"
DEVICE_SERIAL=""
TIMEOUT_SECONDS=30
TEMPLATE_MODE="gemma_it_like"

PROMPTS=("こんにちは" "はじめまして" "こんばんは")
CASE_IDS=("sanitizer_only")
CASE_LABELS=("enhanced sanitizer_only + fixed max_output_tokens_128")
CASE_MAX_TOKENS=("128")
ROLLBACK_CASE_IDS=("lower_max_tokens_64_sanitizer" "lower_max_tokens_32_sanitizer")
ROLLBACK_CASE_LABELS=("lower_max_tokens_64 + sanitizer" "lower_max_tokens_32 + sanitizer")
ROLLBACK_CASE_MAX_TOKENS=("64" "32")
ROLLBACK_CASE_REASONS=("rollback_empty_after_sanitize" "rollback_adapter_failure_or_timeout")
STOP_SEQUENCE_CASE_ID="stop_sequence_end_of_turn"
REPETITION_SUPPRESSION_MARKER="${QAIRT244_REPETITION_SUPPRESSION_MARKER:-not_requested_api_pending}"

while [ $# -gt 0 ]; do
  case "$1" in
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --template) TEMPLATE_MODE="${2:-}"; shift 2 ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_turn_stop_quality_compare.sh [--device <serial>] [--timeout <seconds>] [--template <mode>]

Runs the standardDebug hidden QAIRT244 SM8750 NPU route for a small Gemma
turn-stop quality comparison. The runner executes each supported case once per
prompt with a 30 second default timeout and writes artifacts under:

  artifacts/qairt244_npu_turn_stop_quality_compare/<timestamp>/

Cases:
  - sanitizer_only, fixed max_output_tokens=128
  - stop_sequence_end_of_turn is recorded as not_run/native_stop_not_exposed
  - lower_max_tokens_64 + sanitizer is recorded as rollback_not_adopted
  - lower_max_tokens_32 + sanitizer is recorded as rollback_not_adopted
  - repetition suppression is marker-only until the API is confirmed; set
    QAIRT244_REPETITION_SUPPRESSION_MARKER to annotate artifacts without
    changing native/runtime behavior.

This script targets package io.github.ninbyo02.lami and reuses the standard
hidden receiver flow. It does not use the customnpu package.
EOF
      exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

if ! [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || [ "$TIMEOUT_SECONDS" -le 0 ]; then
  printf 'ERROR: --timeout must be a positive integer\n' >&2
  exit 2
fi

for max_tokens in "${CASE_MAX_TOKENS[@]}"; do
  if ! [[ "$max_tokens" =~ ^[0-9]+$ ]] || [ "$max_tokens" -gt 128 ]; then
    printf 'ERROR: case max_output_tokens must be <=128, got: %s\n' "$max_tokens" >&2
    exit 2
  fi
done
for max_tokens in "${ROLLBACK_CASE_MAX_TOKENS[@]}"; do
  if ! [[ "$max_tokens" =~ ^[0-9]+$ ]] || [ "$max_tokens" -ge 128 ]; then
    printf 'ERROR: rollback case max_output_tokens must be below 128, got: %s\n' "$max_tokens" >&2
    exit 2
  fi
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() { printf '[qairt244-turn-stop-quality-compare] %s\n' "$*"; }

adb_cmd() {
  if [ -n "$DEVICE_SERIAL" ]; then
    adb -s "$DEVICE_SERIAL" "$@"
  else
    adb "$@"
  fi
}

choose_device() {
  adb devices >"$OUT_DIR/adb_devices.txt" 2>&1 || return 1
  if [ -n "$DEVICE_SERIAL" ]; then
    awk -v serial="$DEVICE_SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit found ? 0 : 1 }' "$OUT_DIR/adb_devices.txt"
    return $?
  fi
  DEVICE_SERIAL="$(awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }' "$OUT_DIR/adb_devices.txt")"
  [ -n "$DEVICE_SERIAL" ]
}

pull_app_file() {
  local app_path="$1"
  local dest="$2"
  adb_cmd exec-out run-as "$APP_ID" cat "$app_path" >"$dest" 2>"$dest.err" || true
  if [ ! -s "$dest" ]; then
    rm -f "$dest"
  fi
}

capture_screenshot() {
  local case_id="$1"
  local slug="$2"
  local run_dir="$3"
  local remote_png="/sdcard/qairt244_turn_stop_${case_id}_${slug}.png"
  adb_cmd shell screencap -p "$remote_png" >"$run_dir/screenshot_capture.txt" 2>&1 || true
  adb_cmd pull "$remote_png" "$run_dir/screenshot.png" >"$run_dir/screenshot_pull.txt" 2>&1 || true
  adb_cmd shell rm -f "$remote_png" >/dev/null 2>&1 || true
  cp "$run_dir/screenshot.png" "$OUT_DIR/screenshot_${case_id}_${slug}.png" 2>/dev/null || true
}

wait_for_state() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if adb_cmd shell run-as "$APP_ID" test -s "files/qairt244_standard_hidden_prompt_state.txt" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 124
}

prompt_slug() {
  case "$1" in
    "こんにちは") printf 'konnichiwa' ;;
    "はじめまして") printf 'hajimemashite' ;;
    "こんばんは") printf 'konbanwa' ;;
    *) printf '%s' "$1" | LC_ALL=C tr -c 'A-Za-z0-9_' '_' | sed 's/_\{1,\}/_/g; s/^_//; s/_$//' ;;
  esac
}

kv_value_from_file() {
  local key="$1"
  local file="$2"
  [ -f "$file" ] || return 1
  awk -F= -v key="$key" '
    $1 == key {
      sub(/^[^=]*=/, "")
      value=$0
      found=1
    }
    END {
      if (found) {
        print value
      } else {
        exit 1
      }
    }
  ' "$file"
}

case_value() {
  local key="$1"
  local run_dir="$2"
  local file
  local value
  for file in \
    "$run_dir/display_diagnostics.txt" \
    "$run_dir/result.txt" \
    "$run_dir/receiver_state.txt" \
    "$run_dir/native_diag.txt"; do
    [ -f "$file" ] || continue
    value="$(kv_value_from_file "$key" "$file")"
    if [ -n "$value" ]; then
      printf '%s' "$value"
      return 0
    fi
  done
  printf 'unavailable'
}

write_unescaped_case_value() {
  local key="$1"
  local run_dir="$2"
  local dest="$3"
  local value
  value="$(case_value "$key" "$run_dir")"
  if [ "$value" = unavailable ]; then
    : >"$dest"
  else
    printf '%s' "$value" | perl -pe 's/\\n/\n/g; s/\\\\/\\/g' >"$dest"
  fi
}

has_npu_evidence() {
  local run_dir="$1"
  local evidence backend
  evidence="$(case_value npu_backend_evidence "$run_dir")"
  backend="$(case_value npu_backend "$run_dir")"
  if printf '%s\n%s\n' "$evidence" "$backend" | grep -Eiq 'QNN|HTP|FastRPC|NPU|selected_path_npu'; then
    return 0
  fi
  if rg -q "QNN|HTP|FastRPC|selected_path_npu|Backend\\.NPU|RunDecode" "$run_dir/native_diag.txt" "$run_dir/result.txt" "$run_dir/logcat_tail.txt" 2>/dev/null; then
    return 0
  fi
  return 1
}

classify_quality() {
  local prompt="$1"
  local run_dir="$2"
  local sanitized_file="$3"
  local wait_status="$4"
  local reason fresh

  reason="$(case_value reasonCode "$run_dir")"
  fresh="$(case_value fresh_crash "$run_dir")"

  if [ "$wait_status" = timeout ] || [ "$(case_value timeout "$run_dir")" = true ]; then
    printf 'timeout'
    return 0
  fi
  if [ "$fresh" = true ] || rg -q "FATAL EXCEPTION|SIGABRT|SIGSEGV|AndroidRuntime" "$run_dir/logcat_tail.txt" 2>/dev/null; then
    printf 'crash'
    return 0
  fi
  if [ "$reason" = empty_after_sanitize ] || [ ! -s "$sanitized_file" ]; then
    printf 'empty_after_sanitize'
    return 0
  fi
  if awk -v prompt="$prompt" '{ line=$0; gsub(/^>+/, "", line); gsub(/^[[:space:]]+|[[:space:]]+$/, "", line); if (line == prompt) found=1 } END { exit found ? 0 : 1 }' "$sanitized_file" 2>/dev/null; then
    printf 'prompt_echo'
    return 0
  fi
  if rg -qi '<\|im_start\|>|<\|im_end\|>|<start_of_turn>|<end_of_turn>|\[/?INST\]|### system|### user|### assistant' "$sanitized_file" 2>/dev/null; then
    printf 'template_artifact'
    return 0
  fi
  if awk '
    NF {
      line=$0
      gsub(/^>+/, "", line)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
      if (line != "" && seen[line]++) {
        found=1
        exit
      }
    }
    END { exit found ? 0 : 1 }
  ' "$sanitized_file" 2>/dev/null; then
    printf 'repeated_completion'
    return 0
  fi
  if rg -q '[A-Za-z]{3,}' "$sanitized_file" 2>/dev/null; then
    printf 'multilingual_drift'
    return 0
  fi
  if rg -q '[ぁ-んァ-ン一-龯]' "$sanitized_file" 2>/dev/null; then
    printf 'natural_japanese'
    return 0
  fi
  printf 'multilingual_drift'
}

cleanup_app_files() {
  local dest="$1"
  adb_cmd shell run-as "$APP_ID" rm -f \
    files/qairt244_short_multitoken_smoke_result.txt \
    files/qairt244_native_diag.txt \
    files/qairt244_chat_screen_model_path_resolution.txt \
    files/qairt244_chat_screen_real_npu_once_guard.txt \
    files/qairt244_dev_npu_ui_cleanup_state.txt \
    files/qairt244_standard_hidden_display_diagnostics.txt \
    files/qairt244_standard_hidden_prompt_state.txt >"$dest" 2>&1 || true
}

run_case_prompt() {
  local case_id="$1"
  local case_label="$2"
  local requested_max_tokens="$3"
  local prompt="$4"
  local slug="$5"
  local run_dir="$OUT_DIR/run_${case_id}_${slug}"
  local wait_status status success actual_max fallback fresh npu_evidence quality

  mkdir -p "$run_dir"
  log "run case=$case_id prompt=$prompt requested_max_output_tokens=$requested_max_tokens timeout=${TIMEOUT_SECONDS}s"

  {
    printf 'case_id=%s\n' "$case_id"
    printf 'case_label=%s\n' "$case_label"
    printf 'prompt=%s\n' "$prompt"
    printf 'requested_max_output_tokens=%s\n' "$requested_max_tokens"
    printf 'template_mode=%s\n' "$TEMPLATE_MODE"
    printf 'timeout_seconds=%s\n' "$TIMEOUT_SECONDS"
    printf 'stop_sequence_control=native_api_not_exposed\n'
    printf 'repetition_suppression_marker=%s\n' "$REPETITION_SUPPRESSION_MARKER"
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
  } >"$run_dir/request.txt"

  cleanup_app_files "$run_dir/cleanup_app_files.txt"
  adb_cmd shell am start -W -n "$APP_ID/.MainActivity" >"$run_dir/am_start.txt" 2>&1 || true
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -a "$ACTION" \
    -n "$APP_ID/$RECEIVER" \
    --es prompt "$prompt" \
    --es template "$TEMPLATE_MODE" \
    --es template_mode "$TEMPLATE_MODE" \
    --ei max_output_tokens "$requested_max_tokens" \
    --ez enable_developer_access true \
    --ez enable_route true \
    --ez run true >"$run_dir/broadcast.txt" 2>&1 || true

  wait_status=success
  if ! wait_for_state; then
    wait_status=timeout
    adb_cmd shell am force-stop "$APP_ID" >"$run_dir/force_stop_timeout.txt" 2>&1 || true
  fi

  pull_app_file "files/qairt244_standard_hidden_prompt_state.txt" "$run_dir/receiver_state.txt"
  pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$run_dir/result.txt"
  pull_app_file "files/qairt244_native_diag.txt" "$run_dir/native_diag.txt"
  pull_app_file "files/qairt244_chat_screen_model_path_resolution.txt" "$run_dir/resolved_model_path.txt"
  pull_app_file "files/qairt244_dev_npu_ui_cleanup_state.txt" "$run_dir/ui_cleanup_state.txt"
  pull_app_file "files/qairt244_standard_hidden_display_diagnostics.txt" "$run_dir/display_diagnostics.txt"
  adb_cmd logcat -d -t 800 >"$run_dir/logcat_tail.txt" 2>&1 || true
  capture_screenshot "$case_id" "$slug" "$run_dir"

  write_unescaped_case_value raw_output "$run_dir" "$OUT_DIR/raw_output_${case_id}_${slug}.txt"
  write_unescaped_case_value sanitized_output "$run_dir" "$OUT_DIR/sanitized_output_${case_id}_${slug}.txt"
  cp "$run_dir/native_diag.txt" "$OUT_DIR/native_diag_${case_id}_${slug}.txt" 2>/dev/null || : >"$OUT_DIR/native_diag_${case_id}_${slug}.txt"

  success=false
  if [ "$wait_status" = success ] && grep -q '^success=true$' "$run_dir/receiver_state.txt" 2>/dev/null; then
    success=true
  fi
  status="$wait_status"
  if [ "$wait_status" = success ] && [ "$success" != true ]; then
    status=failure
  fi
  actual_max="$(case_value max_output_tokens "$run_dir")"
  fallback="$(case_value fallback_used "$run_dir")"
  fresh="$(case_value fresh_crash "$run_dir")"
  npu_evidence=false
  if has_npu_evidence "$run_dir"; then
    npu_evidence=true
  fi
  quality="$(classify_quality "$prompt" "$run_dir" "$OUT_DIR/sanitized_output_${case_id}_${slug}.txt" "$wait_status")"

  if [ "$success" = true ] && [ "$npu_evidence" != true ]; then
    status=failure_missing_npu_evidence
  fi
  if [ "$success" = true ] && [ "$fallback" != false ]; then
    status=failure_fallback_used
  fi
  if [ "$success" = true ] && [ "$fresh" != false ]; then
    status=failure_fresh_crash
  fi
  if [ "$success" = true ] && [ "$actual_max" != "$requested_max_tokens" ]; then
    status=max_tokens_not_honored
  fi

  {
    cat "$run_dir/request.txt"
    printf 'status=%s\n' "$status"
    printf 'receiver_success=%s\n' "$success"
    printf 'wait_status=%s\n' "$wait_status"
    printf 'actual_max_output_tokens=%s\n' "$actual_max"
    printf 'max_output_tokens_honored=%s\n' "$([ "$actual_max" = "$requested_max_tokens" ] && printf true || printf false)"
    printf 'npu_evidence=%s\n' "$npu_evidence"
    printf 'npu_backend=%s\n' "$(case_value npu_backend "$run_dir")"
    printf 'npu_backend_evidence=%s\n' "$(case_value npu_backend_evidence "$run_dir")"
    printf 'fallback_used=%s\n' "$fallback"
    printf 'fresh_crash=%s\n' "$fresh"
    printf 'timeout=%s\n' "$(case_value timeout "$run_dir")"
    printf 'reasonCode=%s\n' "$(case_value reasonCode "$run_dir")"
    printf 'sanitizer_applied=%s\n' "$(case_value sanitizer_applied "$run_dir")"
    printf 'removed_template_token_count=%s\n' "$(case_value removed_template_token_count "$run_dir")"
    printf 'removed_prompt_echo=%s\n' "$(case_value removed_prompt_echo "$run_dir")"
    printf 'raw_output_length=%s\n' "$(case_value raw_output_length "$run_dir")"
    printf 'sanitized_output_length=%s\n' "$(case_value sanitized_output_length "$run_dir")"
    printf 'decode_elapsed_ms=%s\n' "$(case_value decode_elapsed_ms "$run_dir")"
    printf 'finish_reason=%s\n' "$(case_value finish_reason "$run_dir")"
    printf 'stop_reason=%s\n' "$(case_value stop_reason "$run_dir")"
    printf 'selected_path_npu_saved=%s\n' "$(case_value selected_path_npu_saved "$run_dir")"
    printf 'quality_classification=%s\n' "$quality"
    printf 'native_quality_classification=%s\n' "$(case_value quality_classification "$run_dir")"
    printf 'stop_sequence_control=native_api_not_exposed\n'
    printf 'repetition_suppression_marker=%s\n' "$REPETITION_SUPPRESSION_MARKER"
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
  } >"$run_dir/case_summary.txt"

  cat "$run_dir/case_summary.txt" >>"$OUT_DIR/case_summaries.txt"
  printf '\n' >>"$OUT_DIR/case_summaries.txt"
  cat "$run_dir/logcat_tail.txt" >>"$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
}

write_not_run_rows_for_executable_cases() {
  local reason="$1"
  local case_index prompt slug case_id requested_max_tokens
  for case_index in "${!CASE_IDS[@]}"; do
    case_id="${CASE_IDS[$case_index]}"
    requested_max_tokens="${CASE_MAX_TOKENS[$case_index]}"
    for prompt in "${PROMPTS[@]}"; do
      slug="$(prompt_slug "$prompt")"
      {
        printf "case_id=%s\n" "$case_id"
        printf "case_label=%s\n" "${CASE_LABELS[$case_index]}"
        printf "prompt=%s\n" "$prompt"
        printf "requested_max_output_tokens=%s\n" "$requested_max_tokens"
        printf "status=not_run/%s\n" "$reason"
        printf "receiver_success=not_run\n"
        printf "wait_status=not_run\n"
        printf "actual_max_output_tokens=not_run\n"
        printf "max_output_tokens_honored=not_run\n"
        printf "npu_evidence=not_run\n"
        printf "npu_backend=not_run\n"
        printf "npu_backend_evidence=not_run\n"
        printf "fallback_used=not_run\n"
        printf "fresh_crash=not_run\n"
        printf "timeout=not_run\n"
        printf "reasonCode=%s\n" "$reason"
        printf "sanitizer_applied=not_run\n"
        printf "removed_template_token_count=not_run\n"
        printf "removed_prompt_echo=not_run\n"
        printf "raw_output_length=not_run\n"
        printf "sanitized_output_length=not_run\n"
        printf "decode_elapsed_ms=not_run\n"
        printf "finish_reason=not_run\n"
        printf "stop_reason=not_run\n"
        printf "selected_path_npu_saved=not_run\n"
        printf "quality_classification=not_run\n"
        printf "native_quality_classification=not_run\n"
        printf "stop_sequence_control=native_api_not_exposed\n"
        printf "repetition_suppression_marker=%s\n" "$REPETITION_SUPPRESSION_MARKER"
        printf "db=false\n"
        printf "tts=false\n"
        printf "markdown=false\n"
        printf "streaming=false\n"
        printf "\n"
      } >>"$OUT_DIR/case_summaries.txt"
      : >"$OUT_DIR/raw_output_${case_id}_${slug}.txt"
      : >"$OUT_DIR/sanitized_output_${case_id}_${slug}.txt"
      printf "status=not_run\nreasonCode=%s\n" "$reason" >"$OUT_DIR/native_diag_${case_id}_${slug}.txt"
    done
  done
}

write_stop_sequence_not_run_rows() {
  local prompt slug
  for prompt in "${PROMPTS[@]}"; do
    slug="$(prompt_slug "$prompt")"
    {
      printf 'case_id=%s\n' "$STOP_SEQUENCE_CASE_ID"
      printf 'case_label=%s\n' "$STOP_SEQUENCE_CASE_ID"
      printf 'prompt=%s\n' "$prompt"
      printf 'requested_max_output_tokens=128\n'
      printf 'status=not_run/native_stop_not_exposed\n'
      printf 'receiver_success=not_run\n'
      printf 'wait_status=not_run\n'
      printf 'actual_max_output_tokens=not_run\n'
      printf 'max_output_tokens_honored=not_run\n'
      printf 'npu_evidence=not_run\n'
      printf 'npu_backend=not_run\n'
      printf 'npu_backend_evidence=not_run\n'
      printf 'fallback_used=not_run\n'
      printf 'fresh_crash=not_run\n'
      printf 'timeout=not_run\n'
      printf 'reasonCode=native_stop_not_exposed\n'
      printf 'sanitizer_applied=not_run\n'
      printf 'removed_template_token_count=not_run\n'
      printf 'removed_prompt_echo=not_run\n'
      printf 'raw_output_length=not_run\n'
      printf 'sanitized_output_length=not_run\n'
      printf 'decode_elapsed_ms=not_run\n'
      printf 'finish_reason=not_run\n'
      printf 'stop_reason=native_stop_not_exposed\n'
      printf 'selected_path_npu_saved=not_run\n'
      printf 'quality_classification=not_run\n'
      printf 'native_quality_classification=not_run\n'
      printf 'stop_sequence_control=native_api_not_exposed\n'
      printf 'repetition_suppression_marker=%s\n' "$REPETITION_SUPPRESSION_MARKER"
      printf 'db=false\n'
      printf 'tts=false\n'
      printf 'markdown=false\n'
      printf 'streaming=false\n'
      printf '\n'
    } >>"$OUT_DIR/case_summaries.txt"
    : >"$OUT_DIR/raw_output_${STOP_SEQUENCE_CASE_ID}_${slug}.txt"
    : >"$OUT_DIR/sanitized_output_${STOP_SEQUENCE_CASE_ID}_${slug}.txt"
    printf 'status=not_run\nreasonCode=native_stop_not_exposed\n' >"$OUT_DIR/native_diag_${STOP_SEQUENCE_CASE_ID}_${slug}.txt"
  done
}

write_rollback_not_adopted_rows() {
  local case_index prompt slug case_id requested_max_tokens reason
  for case_index in "${!ROLLBACK_CASE_IDS[@]}"; do
    case_id="${ROLLBACK_CASE_IDS[$case_index]}"
    requested_max_tokens="${ROLLBACK_CASE_MAX_TOKENS[$case_index]}"
    reason="${ROLLBACK_CASE_REASONS[$case_index]}"
    for prompt in "${PROMPTS[@]}"; do
      slug="$(prompt_slug "$prompt")"
      {
        printf 'case_id=%s\n' "$case_id"
        printf 'case_label=%s\n' "${ROLLBACK_CASE_LABELS[$case_index]}"
        printf 'prompt=%s\n' "$prompt"
        printf 'requested_max_output_tokens=%s\n' "$requested_max_tokens"
        printf 'status=not_run/rollback_not_adopted\n'
        printf 'receiver_success=not_run\n'
        printf 'wait_status=not_run\n'
        printf 'actual_max_output_tokens=not_run\n'
        printf 'max_output_tokens_honored=not_run\n'
        printf 'npu_evidence=not_run\n'
        printf 'npu_backend=not_run\n'
        printf 'npu_backend_evidence=not_run\n'
        printf 'fallback_used=not_run\n'
        printf 'fresh_crash=not_run\n'
        printf 'timeout=not_run\n'
        printf 'reasonCode=%s\n' "$reason"
        printf 'sanitizer_applied=not_run\n'
        printf 'removed_template_token_count=not_run\n'
        printf 'removed_prompt_echo=not_run\n'
        printf 'raw_output_length=not_run\n'
        printf 'sanitized_output_length=not_run\n'
        printf 'decode_elapsed_ms=not_run\n'
        printf 'finish_reason=not_run\n'
        printf 'stop_reason=%s\n' "$reason"
        printf 'selected_path_npu_saved=not_run\n'
        printf 'quality_classification=rollback_not_adopted\n'
        printf 'native_quality_classification=not_run\n'
        printf 'stop_sequence_control=native_api_not_exposed\n'
        printf 'repetition_suppression_marker=%s\n' "$REPETITION_SUPPRESSION_MARKER"
        printf 'db=false\n'
        printf 'tts=false\n'
        printf 'markdown=false\n'
        printf 'streaming=false\n'
        printf '\n'
      } >>"$OUT_DIR/case_summaries.txt"
      : >"$OUT_DIR/raw_output_${case_id}_${slug}.txt"
      : >"$OUT_DIR/sanitized_output_${case_id}_${slug}.txt"
      printf 'status=not_run\nreasonCode=%s\n' "$reason" >"$OUT_DIR/native_diag_${case_id}_${slug}.txt"
    done
  done
}

summary_field() {
  local field="$1"
  local case_id="$2"
  local prompt="$3"
  awk -v field="$field" -v case_id="$case_id" -v prompt="$prompt" '
    BEGIN { RS=""; FS="\n" }
    {
      cid=""; p="";
      for (i = 1; i <= NF; i++) {
        if ($i ~ /^case_id=/) { cid=substr($i, 9) }
        if ($i ~ /^prompt=/) { p=substr($i, 8) }
      }
      if (cid == case_id && p == prompt) {
        for (i = 1; i <= NF; i++) {
          if (index($i, field "=") == 1) {
            print substr($i, length(field) + 2);
            exit;
          }
        }
      }
    }
  ' "$OUT_DIR/case_summaries.txt"
}

write_comparison_table() {
  local case_id prompt status requested actual npu fallback fresh quality sanitized_len reason stop_reason
  {
    printf '# QAIRT244 NPU turn-stop quality comparison\n\n'
    printf '| case | prompt | requested_max_output_tokens | actual_max_output_tokens | status | npu_evidence | fallback_used | fresh_crash | quality_classification | sanitized_output_length | reasonCode | stop_reason |\n'
    printf '| --- | --- | ---: | ---: | --- | --- | --- | --- | --- | ---: | --- | --- |\n'
    for case_id in "${CASE_IDS[@]}" "${ROLLBACK_CASE_IDS[@]}" "$STOP_SEQUENCE_CASE_ID"; do
      for prompt in "${PROMPTS[@]}"; do
        status="$(summary_field status "$case_id" "$prompt")"
        requested="$(summary_field requested_max_output_tokens "$case_id" "$prompt")"
        actual="$(summary_field actual_max_output_tokens "$case_id" "$prompt")"
        npu="$(summary_field npu_evidence "$case_id" "$prompt")"
        fallback="$(summary_field fallback_used "$case_id" "$prompt")"
        fresh="$(summary_field fresh_crash "$case_id" "$prompt")"
        quality="$(summary_field quality_classification "$case_id" "$prompt")"
        sanitized_len="$(summary_field sanitized_output_length "$case_id" "$prompt")"
        reason="$(summary_field reasonCode "$case_id" "$prompt")"
        stop_reason="$(summary_field stop_reason "$case_id" "$prompt")"
        printf '| `%s` | `%s` | %s | %s | `%s` | `%s` | `%s` | `%s` | `%s` | %s | `%s` | `%s` |\n' \
          "$case_id" "$prompt" "${requested:-unknown}" "${actual:-unknown}" "${status:-missing}" \
          "${npu:-missing}" "${fallback:-missing}" "${fresh:-missing}" "${quality:-missing}" \
          "${sanitized_len:-unknown}" "${reason:-missing}" "${stop_reason:-missing}"
      done
    done
  } >"$OUT_DIR/comparison_table.md"
}

write_runtime_marker_scan() {
  {
    for file in "$OUT_DIR"/run_*/*.txt "$OUT_DIR"/native_diag_*.txt "$OUT_DIR/logcat_tail.txt" "$OUT_DIR/comparison_table.md"; do
      [ -f "$file" ] || continue
      rg -n "QNN|HTP|FastRPC|RunDecode|EngineFactory|native_prompt|sanitizer|selected_path_npu|fallback_used|timeout|fresh_crash|max_output_tokens|stop_reason|end_of_turn|quality_classification|repetition_suppression|db=false|tts=false|markdown=false|streaming=false" "$file" | sed "s#^#${file#$OUT_DIR/}:#" || true
    done
  } >"$OUT_DIR/runtime_marker_scan.txt"
}

write_grep_safety() {
  {
    printf '# grep safety scan\n'
    printf 'package_target=%s\n' "$APP_ID"
    printf 'receiver=%s\n\n' "$RECEIVER"
    rg -n "customnpu|io\\.github\\.ninbyo02\\.lami|StandardHiddenQairt244PromptReceiver|STANDARD_HIDDEN_QAIRT244_PROMPT|max_output_tokens|stop_sequence|Backend\\.NPU|selectedPath.*npu|selected_path_npu|tts=true|markdown=true|streaming=true|db=true|generateResponse|repetition" \
      scripts/run_qairt244_npu_turn_stop_quality_compare.sh app/src/debug/java app/src/main/java app/src/customBuildExperimentDebug 2>&1 || true
    printf '\n# safety assertions recorded by runner\n'
    printf 'standard_hidden_receiver_only=true\n'
    printf 'normal_chat_screen_connected=false\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
    printf 'selected_path_npu_saved_by_runner=false\n'
    printf 'native_route_code_changed_by_runner=false\n'
  } >"$OUT_DIR/grep_safety.txt"
}

write_external_native_files() {
  {
    printf 'status=not_modified\n'
    printf 'native_checkout_edited=false\n'
    printf 'reason=runner-only task; standard hidden native stop-sequence control is not exposed in the inspected receiver flow.\n'
  } >"$OUT_DIR/external_native_status.txt"
  {
    printf '# No external native diff produced by this runner.\n'
    printf '# stop_sequence_end_of_turn is recorded as not_run/native_stop_not_exposed.\n'
  } >"$OUT_DIR/external_native_diff.patch"
}

write_summary() {
  local overall_status="$1"
  {
    printf '# QAIRT244 NPU Gemma turn-stop quality compare\n\n'
    printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
    printf -- '- device: `%s`\n' "${DEVICE_SERIAL:-unselected}"
    printf -- '- package: `%s`\n' "$APP_ID"
    printf -- '- receiver: `%s`\n' "$RECEIVER"
    printf -- '- action: `%s`\n' "$ACTION"
    printf -- '- timeout_seconds_per_run: `%s`\n' "$TIMEOUT_SECONDS"
    printf -- '- template_mode: `%s`\n' "$TEMPLATE_MODE"
    printf -- '- maxOutputTokens_policy: `128 or lower only`\n'
    printf -- '- repetition_suppression_marker: `%s`\n' "$REPETITION_SUPPRESSION_MARKER"
    printf -- '- overall_status: `%s`\n' "$overall_status"
    printf -- '- stop_sequence_end_of_turn: `not_run/native_stop_not_exposed`\n'
    printf '\n## Prompts\n\n'
    printf -- '- `こんにちは`\n- `はじめまして`\n- `こんばんは`\n'
    printf '\n## Cases\n\n'
    printf -- '- `sanitizer_only`: standard hidden run, fixed max_output_tokens=128\n'
    printf -- '- `lower_max_tokens_64_sanitizer`: not run; rollback_not_adopted after empty_after_sanitize evidence\n'
    printf -- '- `lower_max_tokens_32_sanitizer`: not run; rollback_not_adopted after adapter failure / timeout evidence\n'
    printf -- '- `stop_sequence_end_of_turn`: not run until native stop-sequence control is exposed\n'
    printf '\n## Comparison\n\n'
    cat "$OUT_DIR/comparison_table.md" 2>/dev/null || true
    printf '\n## Notes\n\n'
    printf -- '- The standard hidden receiver flow is reused and package `io.github.ninbyo02.lami` is targeted.\n'
    printf -- '- `max_output_tokens` is fixed at 128 for the hidden safety baseline; lower token caps are recorded only as rollback rows.\n'
    printf -- '- Stop sequence and repetition suppression are artifact markers only here; no unconfirmed native/API controls are invoked.\n'
    printf -- '- NPU evidence, `fallback_used`, and `fresh_crash` are recorded per run.\n'
    printf -- '- The runner does not edit route code, native code, DB, TTS, Markdown, or streaming paths.\n'
  } >"$OUT_DIR/summary.md"
}

main() {
  local case_index prompt slug overall_status status

  log "artifact: ${OUT_DIR#$ROOT_DIR/}"
  : >"$OUT_DIR/case_summaries.txt"
  : >"$OUT_DIR/logcat_tail.txt"
  git status --short >"$OUT_DIR/git_status.txt" 2>&1 || true
  write_external_native_files
  choose_device || {
    log "no non-emulator device"
    write_not_run_rows_for_executable_cases no_device
    write_rollback_not_adopted_rows
    write_stop_sequence_not_run_rows
    write_comparison_table
    write_grep_safety
    write_runtime_marker_scan
    write_summary no_device
    exit 1
  }
  printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"

  for case_index in "${!CASE_IDS[@]}"; do
    for prompt in "${PROMPTS[@]}"; do
      slug="$(prompt_slug "$prompt")"
      run_case_prompt "${CASE_IDS[$case_index]}" "${CASE_LABELS[$case_index]}" "${CASE_MAX_TOKENS[$case_index]}" "$prompt" "$slug"
    done
  done

  write_rollback_not_adopted_rows
  write_stop_sequence_not_run_rows
  write_comparison_table
  write_grep_safety
  write_runtime_marker_scan

  overall_status=success
  while IFS= read -r status; do
    case "$status" in
      success|not_run/native_stop_not_exposed|not_run/rollback_not_adopted) ;;
      *) overall_status=failure ;;
    esac
  done < <(awk -F= '$1 == "status" { print $2 }' "$OUT_DIR/case_summaries.txt")

  write_summary "$overall_status"
  log "summary: ${OUT_DIR#$ROOT_DIR/}/summary.md"
  if [ "$overall_status" = success ]; then
    log "success"
    exit 0
  fi
  log "failure"
  exit 1
}

main "$@"
