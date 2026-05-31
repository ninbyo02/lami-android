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
PROMPT_INDEX=0
MODE="single"
PROMPT_COUNT=10
PROMPT_SLEEP_SECONDS=4

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
  scripts/run_npu_s2_db_stability_test.sh [--single] [--prompt-index <0-9>] [--device <serial>]
  scripts/run_npu_s2_db_stability_test.sh --batch [--device <serial>]
      [--timeout <seconds>] [--prompt-timeout-ms <ms>] [--max-output-tokens <tokens>]
      [--prompt-sleep <seconds>]

Runs the standardDebug dev-only NPU S2_DB stability receiver on a connected
Qualcomm/sm8750 device.

Default mode is safe single-step: one broadcast executes one prompt only
(prompt index 0 unless --prompt-index is supplied). Batch mode must be requested
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

wait_for_state() {
  local prompt_index="$1"
  local state_dest="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if adb_cmd shell run-as "$APP_ID" test -s "files/npu_s2_db_stability_state.txt" >/dev/null 2>&1; then
      pull_app_file "files/npu_s2_db_stability_state.txt" "$state_dest"
      local status
      local seen_prompt_index
      status="$(state_value status "$state_dest")"
      seen_prompt_index="$(state_value prompt_index "$state_dest")"
      if [ "$seen_prompt_index" = "$prompt_index" ] && [ "$status" != "running" ]; then
        return 0
      fi
    fi
    sleep 2
  done
  return 124
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
  local prompt_index="$1"
  local prompt_number=$((prompt_index + 1))
  local state_dest="$LOG_DIR/state_prompt_${prompt_number}.txt"
  local prefix="$LOG_DIR/prompt_${prompt_number}"

  log "prompt $prompt_number/$PROMPT_COUNT: dispatch"
  adb_cmd shell run-as "$APP_ID" rm -f files/npu_s2_db_stability_state.txt >"$prefix.cleanup_state.txt" 2>&1 || true
  check_device_responsive "before prompt $prompt_number" "$prefix.before" || return 1

  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -a "$ACTION" \
    -n "$APP_ID/$RECEIVER" \
    --es timestamp "$TIMESTAMP" \
    --ei max_output_tokens "$MAX_OUTPUT_TOKENS" \
    --ei prompt_index "$prompt_index" \
    --ei prompt_timeout_ms "$PROMPT_TIMEOUT_MS" >"$prefix.broadcast.txt" 2>&1 || true

  if ! wait_for_state "$prompt_index" "$state_dest"; then
    log "timeout waiting for receiver state at prompt $prompt_number"
    adb_cmd logcat -d -t 1000 >"$prefix.logcat_tail.txt" 2>&1 || true
    return 1
  fi

  local markdown_file
  local csv_file
  local status
  local reason
  markdown_file="$(state_value markdown_file "$state_dest")"
  csv_file="$(state_value csv_file "$state_dest")"
  status="$(state_value status "$state_dest")"
  reason="$(state_value reason "$state_dest")"

  adb_cmd logcat -d -t 1000 >"$prefix.logcat_tail.txt" 2>&1 || true
  check_device_responsive "after prompt $prompt_number" "$prefix.after" || return 1

  if [ "$status" != "success" ] || [ -z "$markdown_file" ] || [ -z "$csv_file" ]; then
    log "receiver failed at prompt $prompt_number: status=${status:-unknown} reason=${reason:-unknown}"
    return 1
  fi

  local markdown_dest="$ARTIFACT_DIR/$markdown_file"
  local csv_dest="$ARTIFACT_DIR/$csv_file"
  pull_app_file "files/$markdown_file" "$markdown_dest"
  pull_app_file "files/$csv_file" "$csv_dest"

  if [ ! -s "$markdown_dest" ] || [ ! -s "$csv_dest" ]; then
    log "failed to pull generated reports for prompt $prompt_number"
    return 1
  fi

  log "prompt $prompt_number markdown: ${markdown_dest#$ROOT_DIR/}"
  log "prompt $prompt_number csv: ${csv_dest#$ROOT_DIR/}"
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
  if [ "$PROMPT_INDEX" -lt 0 ] || [ "$PROMPT_INDEX" -ge "$PROMPT_COUNT" ]; then
    log "prompt index must be 0..$((PROMPT_COUNT - 1))"
    exit 2
  fi

  cleanup_prompt_files

  adb_cmd shell am start -W -n "$APP_ID/.MainActivity" >"$LOG_DIR/am_start.txt" 2>&1 || true
  check_device_responsive "after app start" "$LOG_DIR/app_start" || exit 1

  if [ "$MODE" = "single" ]; then
    run_prompt "$PROMPT_INDEX" || exit 1
  elif [ "$MODE" = "batch" ]; then
    local index
    index=0
    while [ "$index" -lt "$PROMPT_COUNT" ]; do
      run_prompt "$index" || exit 1
      index=$((index + 1))
      if [ "$index" -lt "$PROMPT_COUNT" ]; then
        log "sleep ${PROMPT_SLEEP_SECONDS}s before next prompt"
        sleep "$PROMPT_SLEEP_SECONDS"
      fi
    done
  else
    log "invalid mode: $MODE"
    exit 2
  fi

  log "logs: ${LOG_DIR#$ROOT_DIR/}"
}

main "$@"
