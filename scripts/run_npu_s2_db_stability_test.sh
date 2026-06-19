#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
RECEIVER="io.github.ninbyo02.lami.npu.NpuS2DbStabilityTestReceiver"
ACTION="io.github.ninbyo02.lami.action.NPU_S2_DB_STABILITY_TEST"
ARTIFACT_DIR="$ROOT_DIR/artifacts"
LOG_DIR="$ARTIFACT_DIR/npu_s2_db_stability_logs_$TIMESTAMP"
DEVICE_SERIAL=""
TIMEOUT_SECONDS=240
MAX_OUTPUT_TOKENS=128
PROMPT_TIMEOUT_MS=180000
PROMPT_INDEX=1
MODE="single"
PROMPT_COUNT=10
PROMPT_SLEEP_SECONDS=4
PROMPTS=(
  "こんにちは"
  "ああああ"
  "明日の天気は"
  "Pythonについて一言で教えて"
  "1+1は？"
  "自己紹介して"
  "日本語で短く返答してください"
  "箇条書きで3つ教えて"
  "今日の予定を確認したい"
  "ありがとう"
)

while [ $# -gt 0 ]; do
  case "$1" in
    --single) MODE="single"; shift ;;
    --batch) MODE="batch"; shift ;;
    --prompt-index) PROMPT_INDEX="${2:-}"; MODE="single"; shift 2 ;;
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --app-id) APP_ID="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --prompt-timeout-ms) PROMPT_TIMEOUT_MS="${2:-}"; shift 2 ;;
    --max-output-tokens) MAX_OUTPUT_TOKENS="${2:-}"; shift 2 ;;
    --prompt-sleep) PROMPT_SLEEP_SECONDS="${2:-}"; shift 2 ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_npu_s2_db_stability_test.sh [--single] [--prompt-index <1-10>] [--device <serial>]
  scripts/run_npu_s2_db_stability_test.sh --batch [--device <serial>]
      [--timeout <seconds>] [--prompt-timeout-ms <ms>] [--max-output-tokens <tokens>]
      [--prompt-sleep <seconds>]

Runs the standardDebug dev-only NPU S2_DB stability receiver on a connected
Qualcomm/sm8750 device.

Default mode is safe single-step: one broadcast executes one prompt only
(prompt index 1 unless --prompt-index is supplied). Batch mode must be requested
explicitly with --batch. Batch still dispatches one prompt per receiver call,
sleeps between prompts, checks device responsiveness, and stops immediately on
failure, timeout, or suspected ANR.

Reports are pulled as prompt fragments:

  artifacts/npu_s2_db_stability_YYYYMMDD_HHMMSS_prompt_N.md
  artifacts/npu_s2_db_stability_YYYYMMDD_HHMMSS_prompt_N.csv

Scope: S2 decoding and save-decision logic. It does not verify ChatScreen UI DB
insertion or conversation-history duplicate rows.
EOF
      exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$ARTIFACT_DIR" "$LOG_DIR"

log() { printf '[npu-s2-db-stability] %s\n' "$*"; }

print_generated_artifacts() {
  if [ -s "$LOG_DIR/generated_artifacts.txt" ]; then
    log "generated artifacts:"
    sed 's/^/[npu-s2-db-stability]   /' "$LOG_DIR/generated_artifacts.txt"
  fi
}

adb_cmd() {
  if [ -n "$DEVICE_SERIAL" ]; then
    adb -s "$DEVICE_SERIAL" "$@"
  else
    adb "$@"
  fi
}

choose_device() {
  adb devices >"$LOG_DIR/adb_devices.txt" 2>&1 || return 1
  if [ -n "$DEVICE_SERIAL" ]; then
    awk -v serial="$DEVICE_SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit found ? 0 : 1 }' "$LOG_DIR/adb_devices.txt"
    return $?
  fi
  DEVICE_SERIAL="$(awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }' "$LOG_DIR/adb_devices.txt")"
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

state_value() {
  local key="$1"
  local state_file="${2:-$LOG_DIR/state.txt}"
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$state_file" 2>/dev/null
}

state_value_or_default() {
  local key="$1"
  local state_file="$2"
  local default_value="$3"
  local value
  value="$(state_value "$key" "$state_file")"
  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf '%s' "$default_value"
  fi
}

csv_cell() {
  local value="$1"
  local escaped
  escaped="$(printf '%s' "$value" | sed 's/"/""/g')"
  printf '"%s"' "$escaped"
}

markdown_cell() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/|/\\|/g'
}

wait_for_state() {
  local prompt_no="$1"
  local state_dest="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if adb_cmd shell run-as "$APP_ID" test -s "files/npu_s2_db_stability_state.txt" >/dev/null 2>&1; then
      pull_app_file "files/npu_s2_db_stability_state.txt" "$state_dest"
      local status
      local seen_prompt_no
      status="$(state_value status "$state_dest")"
      seen_prompt_no="$(state_value prompt_no "$state_dest")"
      if [ -z "$seen_prompt_no" ]; then
        seen_prompt_no="$(state_value prompt_index "$state_dest")"
      fi
      if [ "$seen_prompt_no" = "$prompt_no" ] && [ "$status" != "running" ]; then
        return 0
      fi
    fi
    sleep 2
  done
  return 124
}

write_fallback_state() {
  local state_dest="$1"
  local prompt_no="$2"
  local status="$3"
  local reason="$4"
  local prompt_text="${PROMPTS[$((prompt_no - 1))]:-}"
  {
    printf 'receiver=npu_s2_db_stability_test\n'
    printf 'status=%s\n' "$status"
    printf 'reason=%s\n' "$reason"
    printf 'automation_scope=s2_decoding_and_save_decision_logic\n'
    printf 'ui_db_integration=false\n'
    printf 'route_mode=S2_DB\n'
    printf 'timestamp=%s\n' "$TIMESTAMP"
    printf 'prompt_index=%s\n' "$prompt_no"
    printf 'prompt_no=%s\n' "$prompt_no"
    printf 'prompt_number=%s\n' "$prompt_no"
    printf 'prompt_count=%s\n' "$PROMPT_COUNT"
    printf 'prompt_text=%s\n' "$prompt_text"
    printf 'prompt_timeout_ms=%s\n' "$PROMPT_TIMEOUT_MS"
    printf 'judgement=fail\n'
    printf 'notes=automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false; source=script_fallback\n'
    printf 'markdown_file=npu_s2_db_stability_%s_prompt_%s.md\n' "$TIMESTAMP" "$prompt_no"
    printf 'csv_file=npu_s2_db_stability_%s_prompt_%s.csv\n' "$TIMESTAMP" "$prompt_no"
  } >"$state_dest"
}

generate_fallback_reports() {
  local state_file="$1"
  local prompt_no="$2"
  local markdown_dest="$3"
  local csv_dest="$4"
  local prompt_index
  local prompt_text
  local status
  local reason
  local judgement
  local notes
  prompt_index="$(state_value_or_default prompt_index "$state_file" "$prompt_no")"
  prompt_no="$(state_value_or_default prompt_no "$state_file" "$prompt_no")"
  prompt_text="$(state_value_or_default prompt_text "$state_file" "${PROMPTS[$((prompt_no - 1))]:-}")"
  status="$(state_value_or_default status "$state_file" "failure")"
  reason="$(state_value_or_default reason "$state_file" "fallback_state_report")"
  judgement="$(state_value_or_default judgement "$state_file" "fail")"
  notes="$(state_value_or_default notes "$state_file" "automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false; source=state_fallback")"

  {
    printf '# NPU S2_DB Stability Test\n\n'
    printf -- '- timestamp: `%s`\n' "$TIMESTAMP"
    printf -- '- execution_mode: `single_prompt`\n'
    printf -- '- prompt_index: `%s`\n' "$prompt_index"
    printf -- '- prompt_no: `%s`\n' "$prompt_no"
    printf -- '- prompt_text: `%s`\n' "$prompt_text"
    printf -- '- status: `%s`\n' "$status"
    printf -- '- reason: `%s`\n' "$reason"
    printf -- '- fallback_report: `true`\n\n'
    printf '| prompt_index | prompt_no | prompt_text | status | reason | judgement | notes |\n'
    printf '| --- | --- | --- | --- | --- | --- | --- |\n'
    printf '| %s | %s | %s | %s | %s | %s | %s |\n' \
      "$(markdown_cell "$prompt_index")" \
      "$(markdown_cell "$prompt_no")" \
      "$(markdown_cell "$prompt_text")" \
      "$(markdown_cell "$status")" \
      "$(markdown_cell "$reason")" \
      "$(markdown_cell "$judgement")" \
      "$(markdown_cell "$notes")"
  } >"$markdown_dest"

  {
    csv_cell "prompt_index"; printf ','
    csv_cell "prompt_no"; printf ','
    csv_cell "prompt_text"; printf ','
    csv_cell "status"; printf ','
    csv_cell "reason"; printf ','
    csv_cell "judgement"; printf ','
    csv_cell "notes"; printf '\n'
    csv_cell "$prompt_index"; printf ','
    csv_cell "$prompt_no"; printf ','
    csv_cell "$prompt_text"; printf ','
    csv_cell "$status"; printf ','
    csv_cell "$reason"; printf ','
    csv_cell "$judgement"; printf ','
    csv_cell "$notes"; printf '\n'
  } >"$csv_dest"
}

ensure_reports() {
  local prompt_no="$1"
  local state_dest="$2"
  local markdown_file
  local csv_file
  markdown_file="$(state_value markdown_file "$state_dest")"
  csv_file="$(state_value csv_file "$state_dest")"
  if [ -z "$markdown_file" ]; then
    markdown_file="npu_s2_db_stability_${TIMESTAMP}_prompt_${prompt_no}.md"
  fi
  if [ -z "$csv_file" ]; then
    csv_file="npu_s2_db_stability_${TIMESTAMP}_prompt_${prompt_no}.csv"
  fi

  local markdown_dest="$ARTIFACT_DIR/$markdown_file"
  local csv_dest="$ARTIFACT_DIR/$csv_file"
  if [ -n "$(state_value markdown_file "$state_dest")" ]; then
    pull_app_file "files/$markdown_file" "$markdown_dest"
  fi
  if [ -n "$(state_value csv_file "$state_dest")" ]; then
    pull_app_file "files/$csv_file" "$csv_dest"
  fi

  if [ ! -s "$markdown_dest" ] || [ ! -s "$csv_dest" ]; then
    log "generating fallback reports for prompt $prompt_no from state"
    generate_fallback_reports "$state_dest" "$prompt_no" "$markdown_dest" "$csv_dest"
  fi

  if [ -s "$markdown_dest" ] && [ -s "$csv_dest" ]; then
    log "prompt $prompt_no markdown: ${markdown_dest#$ROOT_DIR/}"
    log "prompt $prompt_no csv: ${csv_dest#$ROOT_DIR/}"
    printf '%s\n' "$markdown_dest" >>"$LOG_DIR/generated_artifacts.txt"
    printf '%s\n' "$csv_dest" >>"$LOG_DIR/generated_artifacts.txt"
    return 0
  fi
  log "failed to produce reports for prompt $prompt_no"
  return 1
}

check_device_responsive() {
  local label="$1"
  local out_prefix="$2"
  if ! adb_cmd shell pidof "$APP_ID" >"$out_prefix.pidof.txt" 2>&1; then
    log "device/app unresponsive at $label: pidof failed"
    return 1
  fi
  if ! adb_cmd shell dumpsys activity top >"$out_prefix.dumpsys_activity_top.txt" 2>&1; then
    log "device unresponsive at $label: dumpsys activity top failed"
    return 1
  fi
  if grep -E "ANR|NOT RESPONDING|Application Not Responding" "$out_prefix.dumpsys_activity_top.txt" >/dev/null 2>&1; then
    log "ANR suspicion at $label"
    return 1
  fi
  return 0
}

cleanup_prompt_files() {
  adb_cmd shell run-as "$APP_ID" rm -f \
    files/npu_s2_db_stability_state.txt \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_1.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_1.csv \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_2.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_2.csv \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_3.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_3.csv \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_4.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_4.csv \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_5.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_5.csv \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_6.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_6.csv \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_7.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_7.csv \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_8.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_8.csv \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_9.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_9.csv \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_10.md \
    files/npu_s2_db_stability_"$TIMESTAMP"_prompt_10.csv >"$LOG_DIR/cleanup_app_files.txt" 2>&1 || true
}

run_prompt() {
  local prompt_no="$1"
  local state_dest="$LOG_DIR/state_prompt_${prompt_no}.txt"
  local prefix="$LOG_DIR/prompt_${prompt_no}"

  log "prompt $prompt_no/$PROMPT_COUNT: dispatch"
  adb_cmd shell run-as "$APP_ID" rm -f files/npu_s2_db_stability_state.txt >"$prefix.cleanup_state.txt" 2>&1 || true
  check_device_responsive "before prompt $prompt_no" "$prefix.before" || return 1

  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -a "$ACTION" \
    -n "$APP_ID/$RECEIVER" \
    --es timestamp "$TIMESTAMP" \
    --ei max_output_tokens "$MAX_OUTPUT_TOKENS" \
    --ei prompt_index "$prompt_no" \
    --ei prompt_timeout_ms "$PROMPT_TIMEOUT_MS" >"$prefix.broadcast.txt" 2>&1 || true

  if ! wait_for_state "$prompt_no" "$state_dest"; then
    log "timeout waiting for receiver state at prompt $prompt_no"
    adb_cmd logcat -d -t 1000 >"$prefix.logcat_tail.txt" 2>&1 || true
    write_fallback_state "$state_dest" "$prompt_no" "failure" "script_wait_timeout"
    ensure_reports "$prompt_no" "$state_dest" || true
    return 1
  fi

  local status
  local reason
  status="$(state_value status "$state_dest")"
  reason="$(state_value reason "$state_dest")"

  adb_cmd logcat -d -t 1000 >"$prefix.logcat_tail.txt" 2>&1 || true
  local responsive_status=0
  check_device_responsive "after prompt $prompt_no" "$prefix.after" || responsive_status=1

  ensure_reports "$prompt_no" "$state_dest" || return 1

  if [ "$status" != "success" ]; then
    log "receiver failed at prompt $prompt_no: status=${status:-unknown} reason=${reason:-unknown}"
    return 1
  fi
  if [ "$responsive_status" -ne 0 ]; then
    log "stopping after prompt $prompt_no because device responsiveness check failed"
    return 1
  fi
  return 0
}

main() {
  log "artifact timestamp: $TIMESTAMP"
  log "mode: $MODE"
  git status --short >"$LOG_DIR/git_status.txt" 2>&1 || true
  choose_device || { log "no non-emulator device"; exit 1; }
  printf '%s\n' "$DEVICE_SERIAL" >"$LOG_DIR/selected_device.txt"

  case "$PROMPT_INDEX" in
    ''|*[!0-9]*) log "invalid prompt index: $PROMPT_INDEX"; exit 2 ;;
  esac
  case "$PROMPT_SLEEP_SECONDS" in
    ''|*[!0-9]*) log "invalid prompt sleep seconds: $PROMPT_SLEEP_SECONDS"; exit 2 ;;
  esac
  if [ "$PROMPT_SLEEP_SECONDS" -lt 3 ] || [ "$PROMPT_SLEEP_SECONDS" -gt 5 ]; then
    log "prompt sleep must be 3..5 seconds"
    exit 2
  fi
  if [ "$PROMPT_INDEX" -lt 1 ] || [ "$PROMPT_INDEX" -gt "$PROMPT_COUNT" ]; then
    log "prompt index must be 1..$PROMPT_COUNT"
    exit 2
  fi

  cleanup_prompt_files

  adb_cmd shell am start -W -n "$APP_ID/.MainActivity" >"$LOG_DIR/am_start.txt" 2>&1 || true
  check_device_responsive "after app start" "$LOG_DIR/app_start" || exit 1

  if [ "$MODE" = "single" ]; then
    if ! run_prompt "$PROMPT_INDEX"; then
      print_generated_artifacts
      exit 1
    fi
  elif [ "$MODE" = "batch" ]; then
    local index
    index=1
    while [ "$index" -le "$PROMPT_COUNT" ]; do
      if ! run_prompt "$index"; then
        print_generated_artifacts
        exit 1
      fi
      index=$((index + 1))
      if [ "$index" -le "$PROMPT_COUNT" ]; then
        log "sleep ${PROMPT_SLEEP_SECONDS}s before next prompt"
        sleep "$PROMPT_SLEEP_SECONDS"
      fi
    done
  else
    log "invalid mode: $MODE"
    exit 2
  fi

  print_generated_artifacts
  log "logs: ${LOG_DIR#$ROOT_DIR/}"
}

main "$@"
