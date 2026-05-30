#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
RECEIVER="io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver"
ACTION="io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_256_quality_compare/$TIMESTAMP"
PREFLIGHT_DIR="$ROOT_DIR/artifacts/qairt244_npu_max256_guard_preflight/$TIMESTAMP"
BASELINE_DIR="$ROOT_DIR/artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810"
DEVICE_SERIAL=""
TIMEOUT_SECONDS=30
TEMPLATE_MODE="gemma_it_like"
MAX_OUTPUT_TOKENS=256
MAX256_GUARD_MARKER="qairt244_editable_prompt_max256_v1"
NATIVE_ARTIFACT="${QAIRT244_MAX256_NATIVE_ARTIFACT:-}"
PREFLIGHT_ONLY=false
SINGLE_PROMPT_ONLY=false
SINGLE_PROMPT="こんにちは"

PROMPTS=(
  "こんにちは"
  "Pythonで簡単な電卓コードを書いて"
  "ラミィのNPU推論について短く説明して"
)

while [ $# -gt 0 ]; do
  case "$1" in
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --template) TEMPLATE_MODE="${2:-}"; shift 2 ;;
    --artifact|--native-artifact)
      NATIVE_ARTIFACT="${2:-}"
      shift 2
      ;;
    --preflight-only)
      PREFLIGHT_ONLY=true
      shift
      ;;
    --single-prompt-only)
      SINGLE_PROMPT_ONLY=true
      shift
      ;;
    --prompt)
      SINGLE_PROMPT="${2:-}"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_max_output_256_quality_compare.sh --preflight-only [--artifact <native-build-artifact>]
  scripts/run_qairt244_npu_max_output_256_quality_compare.sh --single-prompt-only --artifact <native-build-artifact> [--prompt <prompt>] [--device <serial>] [--timeout <seconds>] [--template <mode>]
  scripts/run_qairt244_npu_max_output_256_quality_compare.sh --artifact <native-build-artifact> [--device <serial>] [--timeout <seconds>] [--template <mode>]

Runs the standardDebug hidden QAIRT244 SM8750 NPU route once per prompt with
sanitizer_only and max_output_tokens=256. The existing
artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810 artifact is
used as the 128-token baseline reference; this runner does not rerun 128.

Default execution is refused until static native artifact evidence shows:
  - qairt244_editable_prompt_max256_v1
  - native_max_output_tokens_limit=256
  - SetMaxOutputTokens(256)
  - SM8750-only model/selection evidence

--preflight-only writes artifacts/qairt244_npu_max256_guard_preflight/<timestamp>/
and exits before device selection, app launch, NPU generation, or RunDecode.

--single-prompt-only writes artifacts/qairt244_npu_max_output_256_single_prompt/<timestamp>/
and runs exactly one prompt. The default prompt is こんにちは.

Safety constraints:
  - max_output_tokens is capped at 256 by this runner.
  - each prompt is executed once.
  - DB/TTS/Markdown/streaming and selectedPath persistence remain disconnected.
  - no retry, fallback, or normal ChatScreen assistant-list insertion is used.
EOF
      exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

if ! [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || [ "$TIMEOUT_SECONDS" -le 0 ]; then
  printf 'ERROR: --timeout must be a positive integer\n' >&2
  exit 2
fi
if [ "$MAX_OUTPUT_TOKENS" -gt 256 ]; then
  printf 'ERROR: max_output_tokens must be <=256\n' >&2
  exit 2
fi
if [ "$SINGLE_PROMPT_ONLY" = true ]; then
  if [ -z "$SINGLE_PROMPT" ]; then
    printf 'ERROR: --prompt must not be empty\n' >&2
    exit 2
  fi
  OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_256_single_prompt/$TIMESTAMP"
  PROMPTS=("$SINGLE_PROMPT")
fi

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() { printf '[qairt244-max-output-256-compare] %s\n' "$*"; }

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
    "Pythonで簡単な電卓コードを書いて") printf 'python_calculator' ;;
    "ラミィのNPU推論について短く説明して") printf 'lami_npu_short' ;;
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
  local file value
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

line_repetition_detected() {
  local sanitized_file="$1"
  awk '
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
  ' "$sanitized_file" 2>/dev/null
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
  if rg -qi '<\|im_start\|>|<\|im_end\|>|<start_of_turn>|<end_of_turn>|\[/?INST\]|### system|### user|### assistant' "$sanitized_file" 2>/dev/null; then
    printf 'template_artifact_after_sanitize'
    return 0
  fi
  if line_repetition_detected "$sanitized_file"; then
    printf 'repeated_completion'
    return 0
  fi
  if [ "$prompt" = "Pythonで簡単な電卓コードを書いて" ] &&
    rg -q 'def |print\(|input\(|class |import |while |if __name__|計算|電卓|Python|python' "$sanitized_file" 2>/dev/null; then
    printf 'useful_code'
    return 0
  fi
  if rg -q '[A-Za-z]{18,}|^[[:space:]]*[A-Za-z][A-Za-z ,.;:!?'\''"()/-]{20,}$' "$sanitized_file" 2>/dev/null; then
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

write_meminfo() {
  local label="$1"
  local dest="$2"
  {
    printf 'label=%s\n' "$label"
    printf 'device=%s\n' "$DEVICE_SERIAL"
    adb_cmd shell dumpsys meminfo "$APP_ID"
  } >"$dest" 2>&1 || true
}

append_meminfo_after_each_run() {
  local label="$1"
  {
    printf '\n===== %s =====\n' "$label"
    adb_cmd shell dumpsys meminfo "$APP_ID"
  } >>"$OUT_DIR/meminfo_after_each_run.txt" 2>&1 || true
}

capture_screenshot() {
  local slug="$1"
  local run_dir="$2"
  local remote_png="/sdcard/qairt244_max_output_256_${slug}.png"
  adb_cmd shell screencap -p "$remote_png" >"$run_dir/screenshot_capture.txt" 2>&1 || true
  adb_cmd pull "$remote_png" "$run_dir/screenshot.png" >"$run_dir/screenshot_pull.txt" 2>&1 || true
  adb_cmd shell rm -f "$remote_png" >/dev/null 2>&1 || true
  cp "$run_dir/screenshot.png" "$OUT_DIR/screenshot_256_${slug}.png" 2>/dev/null || true
}

run_prompt_256() {
  local prompt="$1"
  local slug="$2"
  local run_dir="$OUT_DIR/run_256_${slug}"
  local wait_status success status actual_max fallback fresh npu_evidence quality start_ms end_ms elapsed_ms

  mkdir -p "$run_dir"
  log "run prompt=$prompt max_output_tokens=$MAX_OUTPUT_TOKENS timeout=${TIMEOUT_SECONDS}s"

  {
    printf 'case_id=max_output_tokens_256\n'
    printf 'prompt=%s\n' "$prompt"
    printf 'requested_max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
    printf 'template_mode=%s\n' "$TEMPLATE_MODE"
    printf 'timeout_seconds=%s\n' "$TIMEOUT_SECONDS"
    printf 'sanitizer_mode=sanitizer_only\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
  } >"$run_dir/request.txt"

  cleanup_app_files "$run_dir/cleanup_app_files.txt"
  adb_cmd shell am start -W -n "$APP_ID/.MainActivity" >"$run_dir/am_start.txt" 2>&1 || true
  start_ms="$(date +%s000)"
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -a "$ACTION" \
    -n "$APP_ID/$RECEIVER" \
    --es prompt "$prompt" \
    --es template "$TEMPLATE_MODE" \
    --es template_mode "$TEMPLATE_MODE" \
    --ei max_output_tokens "$MAX_OUTPUT_TOKENS" \
    --ez allow_max_output_tokens_compare true \
    --ez enable_developer_access true \
    --ez enable_route true \
    --ez run true >"$run_dir/broadcast.txt" 2>&1 || true

  wait_status=success
  if ! wait_for_state; then
    wait_status=timeout
    adb_cmd shell am force-stop "$APP_ID" >"$run_dir/force_stop_timeout.txt" 2>&1 || true
  fi
  end_ms="$(date +%s000)"
  elapsed_ms=$((end_ms - start_ms))

  pull_app_file "files/qairt244_standard_hidden_prompt_state.txt" "$run_dir/receiver_state.txt"
  pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$run_dir/result.txt"
  pull_app_file "files/qairt244_native_diag.txt" "$run_dir/native_diag.txt"
  pull_app_file "files/qairt244_chat_screen_model_path_resolution.txt" "$run_dir/resolved_model_path.txt"
  pull_app_file "files/qairt244_dev_npu_ui_cleanup_state.txt" "$run_dir/ui_cleanup_state.txt"
  pull_app_file "files/qairt244_standard_hidden_display_diagnostics.txt" "$run_dir/display_diagnostics.txt"
  adb_cmd logcat -d -t 900 >"$run_dir/logcat_tail.txt" 2>&1 || true
  capture_screenshot "$slug" "$run_dir"
  append_meminfo_after_each_run "after_${slug}"

  cp "$run_dir/result.txt" "$OUT_DIR/result_256_${slug}.txt" 2>/dev/null || : >"$OUT_DIR/result_256_${slug}.txt"
  cp "$run_dir/native_diag.txt" "$OUT_DIR/native_diag_256_${slug}.txt" 2>/dev/null || : >"$OUT_DIR/native_diag_256_${slug}.txt"
  write_unescaped_case_value raw_output "$run_dir" "$OUT_DIR/raw_output_256_${slug}.txt"
  write_unescaped_case_value sanitized_output "$run_dir" "$OUT_DIR/sanitized_output_256_${slug}.txt"

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
  quality="$(classify_quality "$prompt" "$run_dir" "$OUT_DIR/sanitized_output_256_${slug}.txt" "$wait_status")"

  if [ "$success" = true ] && [ "$npu_evidence" != true ]; then
    status=failure_missing_npu_evidence
  fi
  if [ "$success" = true ] && [ "$fallback" != false ]; then
    status=failure_fallback_used
  fi
  if [ "$success" = true ] && [ "$fresh" != false ]; then
    status=failure_fresh_crash
  fi
  if [ "$success" = true ] && [ "$actual_max" != "$MAX_OUTPUT_TOKENS" ]; then
    status=max_tokens_not_honored
  fi
  if [ "$success" = true ] && [ "$quality" = template_artifact_after_sanitize ]; then
    status=failure_template_artifact_after_sanitize
  fi
  if [ "$success" = true ] && [ "$quality" = empty_after_sanitize ]; then
    status=failure_empty_after_sanitize
  fi

  {
    cat "$run_dir/request.txt"
    printf 'status=%s\n' "$status"
    printf 'receiver_success=%s\n' "$success"
    printf 'wait_status=%s\n' "$wait_status"
    printf 'elapsed_ms=%s\n' "$elapsed_ms"
    printf 'actual_max_output_tokens=%s\n' "$actual_max"
    printf 'max_output_tokens_honored=%s\n' "$([ "$actual_max" = "$MAX_OUTPUT_TOKENS" ] && printf true || printf false)"
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
    printf 'decode_ms=%s\n' "$(case_value decode_elapsed_ms "$run_dir")"
    printf 'decode_elapsed_ms=%s\n' "$(case_value decode_elapsed_ms "$run_dir")"
    printf 'finish_reason=%s\n' "$(case_value finish_reason "$run_dir")"
    printf 'stop_reason=%s\n' "$(case_value stop_reason "$run_dir")"
    printf 'selected_path_npu_saved=%s\n' "$(case_value selected_path_npu_saved "$run_dir")"
    printf 'quality_classification=%s\n' "$quality"
    printf 'native_quality_classification=%s\n' "$(case_value quality_classification "$run_dir")"
    printf 'standard_route_connected=false\n'
    printf 'normal_ui_route_connected=false\n'
    printf 'assistant_message_list_inserted=false\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
  } >"$run_dir/case_summary.txt"

  cat "$run_dir/case_summary.txt" >>"$OUT_DIR/case_summaries.txt"
  printf '\n' >>"$OUT_DIR/case_summaries.txt"
  cat "$run_dir/logcat_tail.txt" >>"$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
}

summary_field() {
  local field="$1"
  local prompt="$2"
  awk -F= -v field="$field" -v prompt="$prompt" '
    /^case_id=/ {
      if (in_record && p == prompt && found) {
        print value
        exit
      }
      in_record=1
      p=""
      found=0
      value=""
      next
    }
    in_record && $1 == "prompt" { sub(/^[^=]*=/, ""); p=$0; next }
    in_record && $1 == field { sub(/^[^=]*=/, ""); value=$0; found=1; next }
    END {
      if (in_record && p == prompt && found) {
        print value
      }
    }
  ' "$OUT_DIR/case_summaries.txt"
}

baseline_field() {
  local prompt="$1"
  local field="$2"
  [ -f "$BASELINE_DIR/case_summaries.txt" ] || {
    printf 'baseline_missing'
    return 0
  }
  awk -F= -v field="$field" -v prompt="$prompt" '
    /^case_id=/ {
      if (in_record && cid == "sanitizer_only" && p == prompt && found) {
        print value
        exit
      }
      in_record=1
      sub(/^[^=]*=/, "")
      cid=$0
      p=""
      found=0
      value=""
      next
    }
    in_record && $1 == "prompt" { sub(/^[^=]*=/, ""); p=$0; next }
    in_record && $1 == field { sub(/^[^=]*=/, ""); value=$0; found=1; next }
    END {
      if (in_record && cid == "sanitizer_only" && p == prompt && found) {
        print value
      }
    }
  ' "$BASELINE_DIR/case_summaries.txt"
}

write_comparison_table() {
  local prompt status quality decode elapsed len npu fallback fresh timeout selected baseline_quality baseline_decode baseline_status
  {
    printf '# QAIRT244 NPU max_output_tokens 256 quality/safety comparison\n\n'
    printf '128 baseline reference: `%s`\n\n' "${BASELINE_DIR#$ROOT_DIR/}"
    printf '| prompt | 128_ref_status | 128_ref_quality | 128_ref_decode_ms | 256_status | 256_quality | 256_decode_ms | 256_elapsed_ms | 256_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |\n'
    printf '| --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |\n'
    for prompt in "${PROMPTS[@]}"; do
      baseline_status="$(baseline_field "$prompt" status)"
      baseline_quality="$(baseline_field "$prompt" quality_classification)"
      baseline_decode="$(baseline_field "$prompt" decode_elapsed_ms)"
      if [ -z "$baseline_status" ]; then baseline_status="not_in_128_reference"; fi
      if [ -z "$baseline_quality" ]; then baseline_quality="not_in_128_reference"; fi
      if [ -z "$baseline_decode" ]; then baseline_decode=""; fi
      status="$(summary_field status "$prompt")"
      quality="$(summary_field quality_classification "$prompt")"
      decode="$(summary_field decode_ms "$prompt")"
      elapsed="$(summary_field elapsed_ms "$prompt")"
      len="$(summary_field sanitized_output_length "$prompt")"
      npu="$(summary_field npu_evidence "$prompt")"
      fallback="$(summary_field fallback_used "$prompt")"
      timeout="$(summary_field timeout "$prompt")"
      fresh="$(summary_field fresh_crash "$prompt")"
      selected="$(summary_field selected_path_npu_saved "$prompt")"
      printf '| `%s` | `%s` | `%s` | %s | `%s` | `%s` | %s | %s | %s | `%s` | `%s` | `%s` | `%s` | `%s` |\n' \
        "$prompt" "$baseline_status" "$baseline_quality" "${baseline_decode:-}" \
        "${status:-missing}" "${quality:-missing}" "${decode:-}" "${elapsed:-}" "${len:-}" \
        "${npu:-missing}" "${fallback:-missing}" "${timeout:-missing}" "${fresh:-missing}" "${selected:-missing}"
    done
  } >"$OUT_DIR/comparison_table.md"
}

write_runtime_marker_scan() {
  {
    for file in "$OUT_DIR"/run_*/*.txt "$OUT_DIR"/native_diag_*.txt "$OUT_DIR/logcat_tail.txt" "$OUT_DIR/comparison_table.md"; do
      [ -f "$file" ] || continue
      rg -n "QNN|HTP|FastRPC|RunDecode|EngineFactory|native_prompt|sanitizer|selected_path_npu|fallback_used|timeout|fresh_crash|max_output_tokens|quality_classification|db=false|tts=false|markdown=false|streaming=false|Engine\\.initialize|RunDecode" "$file" | sed "s#^#${file#$OUT_DIR/}:#" || true
    done
  } >"$OUT_DIR/runtime_marker_scan.txt"
}

write_grep_safety() {
  {
    printf '# grep safety scan\n'
    printf 'package_target=%s\n' "$APP_ID"
    printf 'receiver=%s\n' "$RECEIVER"
    printf 'max_output_tokens=256\n'
    printf 'max_output_tokens_compare_enabled=true\n\n'
    rg -n "allow_max_output_tokens_compare|QAIRT244_MAX_OUTPUT_TOKENS_COMPARE_LIMIT|max_output_tokens|selectedPath.*npu|selected_path_npu|tts=true|markdown=true|streaming=true|db=true|generateResponse|assistant_message_list|standard_route_connected" \
      scripts/run_qairt244_npu_max_output_256_quality_compare.sh app/src/debug/java app/src/main/java app/src/customBuildExperimentDebug 2>&1 || true
    printf '\n# safety assertions recorded by runner\n'
    printf 'standard_hidden_receiver_only=true\n'
    printf 'normal_chat_screen_connected=false\n'
    printf 'assistant_message_list_inserted=false\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
    printf 'selected_path_npu_saved_by_runner=false\n'
    printf 'max_output_tokens_over_256=false\n'
  } >"$OUT_DIR/grep_safety.txt"
}

preflight_log() { printf '[qairt244-max256-guard-preflight] %s\n' "$*"; }

native_artifact_binary() {
  if [ -f "$NATIVE_ARTIFACT" ]; then
    printf '%s' "$NATIVE_ARTIFACT"
    return 0
  fi
  if [ -n "$NATIVE_ARTIFACT" ] && [ -f "$NATIVE_ARTIFACT/built_libs/liblitertlm_jni.so" ]; then
    printf '%s' "$NATIVE_ARTIFACT/built_libs/liblitertlm_jni.so"
    return 0
  fi
  return 1
}

collect_max256_preflight_evidence() {
  local text_sources="$PREFLIGHT_DIR/evidence_sources.txt"
  local evidence="$PREFLIGHT_DIR/evidence.txt"
  local binary

  mkdir -p "$PREFLIGHT_DIR"
  git status --short >"$PREFLIGHT_DIR/git_status.txt" 2>&1 || true
  {
    printf 'mode=%s\n' "$([ "$PREFLIGHT_ONLY" = true ] && printf preflight-only || printf execution-guard)"
    printf 'native_artifact=%s\n' "${NATIVE_ARTIFACT:-none}"
    printf 'required_marker=%s\n' "$MAX256_GUARD_MARKER"
    printf 'required_native_limit=native_max_output_tokens_limit=256\n'
    printf 'required_decode_setter=SetMaxOutputTokens(256)\n'
    printf 'required_sm8750_selection=true\n'
    printf 'npu_run_executed=false\n'
    printf 'run_decode_executed=false\n'
    printf 'chat_screen_connected=false\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
  } >"$PREFLIGHT_DIR/preflight_config.txt"

  {
    printf '# static max256 guard evidence\n'
    printf 'artifact=%s\n\n' "${NATIVE_ARTIFACT:-none}"
  } >"$evidence"

  : >"$text_sources"
  if [ -n "$NATIVE_ARTIFACT" ] && [ -d "$NATIVE_ARTIFACT" ]; then
    find "$NATIVE_ARTIFACT" -maxdepth 5 -type f \
      \( -name '*.txt' -o -name '*.md' -o -name '*.patch' -o -name '*.tsv' -o -name '*.json' \) \
      | sort >"$text_sources" 2>/dev/null || true
  elif [ -n "$NATIVE_ARTIFACT" ] && [ -f "$NATIVE_ARTIFACT" ]; then
    printf '%s\n' "$NATIVE_ARTIFACT" >"$text_sources"
  fi

  while IFS= read -r source_file; do
    [ -f "$source_file" ] || continue
    rg -n "$MAX256_GUARD_MARKER|native_max_output_tokens_limit=256|SetMaxOutputTokens\\(256\\)|SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750" \
      "$source_file" | sed "s#^#${source_file}:#" >>"$evidence" 2>/dev/null || true
  done <"$text_sources"

  {
    printf '# staged binary check\n'
    printf 'native_artifact=%s\n' "${NATIVE_ARTIFACT:-none}"
    if binary="$(native_artifact_binary)"; then
      printf 'binary=%s\n' "$binary"
      file "$binary" || true
      sha256sum "$binary" || true
      printf '\n# strings evidence\n'
      strings "$binary" 2>/dev/null |
        grep -E "$MAX256_GUARD_MARKER|native_max_output_tokens_limit=256|SetMaxOutputTokens\\(256\\)|SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750" || true
    else
      printf 'binary=missing\n'
    fi
  } >"$PREFLIGHT_DIR/staged_binary_check.txt" 2>&1

  cat "$PREFLIGHT_DIR/staged_binary_check.txt" >>"$evidence"

  {
    printf '# grep safety scan\n'
    printf 'script=%s\n' "scripts/run_qairt244_npu_max_output_256_quality_compare.sh"
    printf 'preflight_only=%s\n' "$PREFLIGHT_ONLY"
    printf 'npu_run_executed=false\n'
    printf 'run_decode_executed=false\n\n'
    rg -n "choose_device|adb_cmd|RunDecode|am start|am broadcast|--preflight-only|collect_max256_preflight_evidence|require_max256_guard_evidence|db=false|tts=false|markdown=false|streaming=false|gemma-4-E2B-it_qualcomm_sm8750|SM8750|sm8750" \
      scripts/run_qairt244_npu_max_output_256_quality_compare.sh scripts/check_qairt244_native_patch.sh docs 2>&1 || true
  } >"$PREFLIGHT_DIR/grep_safety.txt"
}

write_max256_preflight_summary() {
  local marker_present="$1"
  local native_limit_present="$2"
  local setter_present="$3"
  local sm8750_present="$4"
  local guard_status="$5"
  {
    printf '# QAIRT244 max_output_tokens=256 guard preflight\n\n'
    printf -- '- artifact: `%s`\n' "${PREFLIGHT_DIR#$ROOT_DIR/}"
    printf -- '- native_artifact: `%s`\n' "${NATIVE_ARTIFACT:-none}"
    printf -- '- requested_max_output_tokens: `%s`\n' "$MAX_OUTPUT_TOKENS"
    printf -- '- guard_status: `%s`\n' "$guard_status"
    printf -- '- npu_run_executed: `false`\n'
    printf -- '- run_decode_executed: `false`\n'
    printf -- '- chat_screen_connected: `false`\n'
    printf -- '- db_tts_markdown_streaming: `false,false,false,false`\n\n'
    printf '## Required Static Evidence\n\n'
    printf '| check | status |\n'
    printf '| --- | --- |\n'
    printf '| `%s` | `%s` |\n' "$MAX256_GUARD_MARKER" "$marker_present"
    printf '| `native_max_output_tokens_limit=256` | `%s` |\n' "$native_limit_present"
    printf '| `SetMaxOutputTokens(256)` | `%s` |\n' "$setter_present"
    printf '| `SM8750` selection | `%s` |\n' "$sm8750_present"
    printf '\n## Result\n\n'
    if [ "$guard_status" = pass ]; then
      printf '256 guard-only patch built; run not executed. The 256 runner may proceed only outside `--preflight-only` and only with this guard evidence still present.\n'
    else
      printf '256 guard-only patch evidence incomplete; run refused before device selection, NPU, or RunDecode.\n'
    fi
  } >"$PREFLIGHT_DIR/summary.md"

  {
    printf 'marker_present=%s\n' "$marker_present"
    printf 'native_limit_present=%s\n' "$native_limit_present"
    printf 'setter_present=%s\n' "$setter_present"
    printf 'sm8750_present=%s\n' "$sm8750_present"
    printf 'guard_status=%s\n' "$guard_status"
    printf 'npu_run_executed=false\n'
    printf 'run_decode_executed=false\n'
  } >"$PREFLIGHT_DIR/marker.txt"
}

require_max256_guard_evidence() {
  local marker_present=false
  local native_limit_present=false
  local setter_present=false
  local sm8750_present=false
  local guard_status=blocked

  collect_max256_preflight_evidence
  grep -q "$MAX256_GUARD_MARKER" "$PREFLIGHT_DIR/evidence.txt" && marker_present=true
  grep -q 'native_max_output_tokens_limit=256' "$PREFLIGHT_DIR/evidence.txt" && native_limit_present=true
  grep -q 'SetMaxOutputTokens(256)' "$PREFLIGHT_DIR/evidence.txt" && setter_present=true
  grep -Eiq 'SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750' "$PREFLIGHT_DIR/evidence.txt" && sm8750_present=true

  if [ "$marker_present" = true ] &&
    [ "$native_limit_present" = true ] &&
    [ "$setter_present" = true ] &&
    [ "$sm8750_present" = true ]; then
    guard_status=pass
  fi

  write_max256_preflight_summary "$marker_present" "$native_limit_present" "$setter_present" "$sm8750_present" "$guard_status"

  if [ "$guard_status" = pass ]; then
    preflight_log "summary: ${PREFLIGHT_DIR#$ROOT_DIR/}/summary.md"
    return 0
  fi

  preflight_log "blocked: missing required static max256 guard evidence"
  preflight_log "summary: ${PREFLIGHT_DIR#$ROOT_DIR/}/summary.md"
  return 1
}

write_stale_tombstone_note() {
  {
    printf '# stale tombstone note\n\n'
    printf 'This runner does not use stale tombstones as crash evidence. Fresh crash status is taken from the hidden receiver result, native diagnostics, and current logcat tail for each single prompt run.\n\n'
    printf -- '- fresh_crash must remain false for adoption.\n'
    printf -- '- timeout must remain false for adoption.\n'
    printf -- '- no retry or fallback run is performed by this script.\n'
  } >"$OUT_DIR/stale_tombstone_note.md"
}

write_summary() {
  local overall_status="$1"
  {
    if [ "$SINGLE_PROMPT_ONLY" = true ]; then
      printf '# QAIRT244 NPU max_output_tokens 256 single prompt verification\n\n'
    else
      printf '# QAIRT244 NPU max_output_tokens 256 quality/safety compare\n\n'
    fi
    printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
    printf -- '- baseline_reference: `%s`\n' "${BASELINE_DIR#$ROOT_DIR/}"
    printf -- '- device: `%s`\n' "${DEVICE_SERIAL:-unselected}"
    printf -- '- package: `%s`\n' "$APP_ID"
    printf -- '- receiver: `%s`\n' "$RECEIVER"
    printf -- '- timeout_seconds_per_run: `%s`\n' "$TIMEOUT_SECONDS"
    printf -- '- template_mode: `%s`\n' "$TEMPLATE_MODE"
    printf -- '- executable_case: `sanitizer_only + max_output_tokens=256`\n'
    if [ "$SINGLE_PROMPT_ONLY" = true ]; then
      printf -- '- run_count_policy: `single prompt, one run only`\n'
    else
      printf -- '- run_count_policy: `one run per prompt only`\n'
    fi
    printf -- '- overall_status: `%s`\n' "$overall_status"
    printf '\n## Prompts\n\n'
    for prompt in "${PROMPTS[@]}"; do
      printf -- '- `%s`\n' "$prompt"
    done
    printf '\n## Comparison\n\n'
    cat "$OUT_DIR/comparison_table.md" 2>/dev/null || true
    printf '\n## Safety Notes\n\n'
    printf -- '- 128 remains the adopted hidden experimental H1 display baseline unless 256 is separately accepted after this artifact review.\n'
    printf -- '- The 256 run is hidden experimental compare-only and requires explicit `allow_max_output_tokens_compare=true`.\n'
    printf -- '- The runner does not connect standard route, normal ChatScreen assistant list, DB, TTS, Markdown, or streaming.\n'
    printf -- '- The runner does not perform retry, fallback, or multiple unbounded generations.\n'
    printf -- '- Adoption requires QNN/HTP/FastRPC evidence, `fallback_used=false`, `timeout=false`, `fresh_crash=false`, artifact-free sanitized output, and no retained memory anomaly after 10 seconds.\n'
  } >"$OUT_DIR/summary.md"
}

main() {
  local prompt slug overall_status status quality

  if [ "$PREFLIGHT_ONLY" = true ]; then
    require_max256_guard_evidence
    exit $?
  fi
  require_max256_guard_evidence || exit 1

  log "artifact: ${OUT_DIR#$ROOT_DIR/}"
  : >"$OUT_DIR/case_summaries.txt"
  : >"$OUT_DIR/logcat_tail.txt"
  : >"$OUT_DIR/meminfo_after_each_run.txt"
  git status --short >"$OUT_DIR/git_status.txt" 2>&1 || true
  write_stale_tombstone_note
  choose_device || {
    log "no non-emulator device"
    write_comparison_table
    write_grep_safety
    write_runtime_marker_scan
    write_summary no_device
    exit 1
  }
  printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"
  write_meminfo before "$OUT_DIR/meminfo_before.txt"

  for prompt in "${PROMPTS[@]}"; do
    slug="$(prompt_slug "$prompt")"
    run_prompt_256 "$prompt" "$slug"
  done

  sleep 10
  write_meminfo after_10s "$OUT_DIR/meminfo_after_10s.txt"
  write_comparison_table
  write_grep_safety
  write_runtime_marker_scan

  overall_status=success
  while IFS= read -r status; do
    case "$status" in
      success) ;;
      *) overall_status=failure ;;
    esac
  done < <(awk -F= '$1 == "status" { print $2 }' "$OUT_DIR/case_summaries.txt")
  while IFS= read -r quality; do
    case "$quality" in
      natural_japanese|useful_code) ;;
      *) overall_status=failure ;;
    esac
  done < <(awk -F= '$1 == "quality_classification" { print $2 }' "$OUT_DIR/case_summaries.txt")

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
