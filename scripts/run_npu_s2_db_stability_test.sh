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
TIMEOUT_SECONDS=900
MAX_OUTPUT_TOKENS=128

while [ $# -gt 0 ]; do
  case "$1" in
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --app-id) APP_ID="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --max-output-tokens) MAX_OUTPUT_TOKENS="${2:-}"; shift 2 ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_npu_s2_db_stability_test.sh [--device <serial>] [--timeout <seconds>] [--max-output-tokens <tokens>]

Runs the standardDebug dev-only NPU S2_DB stability receiver on a connected
Qualcomm/sm8750 device. The app-side runner executes the 10 stability prompts
through NpuStandardRouteS1Bridge + NpuStandardRouteS2DbMapper and writes reports
that this script pulls to:

  artifacts/npu_s2_db_stability_YYYYMMDD_HHMMSS.md
  artifacts/npu_s2_db_stability_YYYYMMDD_HHMMSS.csv

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
  local state_file="$LOG_DIR/state.txt"
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$state_file" 2>/dev/null
}

wait_for_state() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if adb_cmd shell run-as "$APP_ID" test -s "files/npu_s2_db_stability_state.txt" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 124
}

main() {
  log "artifact timestamp: $TIMESTAMP"
  git status --short >"$LOG_DIR/git_status.txt" 2>&1 || true
  choose_device || { log "no non-emulator device"; exit 1; }
  printf '%s\n' "$DEVICE_SERIAL" >"$LOG_DIR/selected_device.txt"

  adb_cmd shell run-as "$APP_ID" rm -f \
    files/npu_s2_db_stability_state.txt \
    files/npu_s2_db_stability_"$TIMESTAMP".md \
    files/npu_s2_db_stability_"$TIMESTAMP".csv >"$LOG_DIR/cleanup_app_files.txt" 2>&1 || true

  adb_cmd shell am start -W -n "$APP_ID/.MainActivity" >"$LOG_DIR/am_start.txt" 2>&1 || true
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -a "$ACTION" \
    -n "$APP_ID/$RECEIVER" \
    --es timestamp "$TIMESTAMP" \
    --ei max_output_tokens "$MAX_OUTPUT_TOKENS" >"$LOG_DIR/broadcast.txt" 2>&1 || true

  if ! wait_for_state; then
    log "timeout waiting for receiver state"
    adb_cmd logcat -d -t 1000 >"$LOG_DIR/logcat_tail.txt" 2>&1 || true
    exit 1
  fi

  pull_app_file "files/npu_s2_db_stability_state.txt" "$LOG_DIR/state.txt"
  markdown_file="$(state_value markdown_file)"
  csv_file="$(state_value csv_file)"
  status="$(state_value status)"

  if [ "$status" != "success" ] || [ -z "$markdown_file" ] || [ -z "$csv_file" ]; then
    log "receiver failed: status=${status:-unknown}"
    adb_cmd logcat -d -t 1000 >"$LOG_DIR/logcat_tail.txt" 2>&1 || true
    exit 1
  fi

  markdown_dest="$ARTIFACT_DIR/$markdown_file"
  csv_dest="$ARTIFACT_DIR/$csv_file"
  pull_app_file "files/$markdown_file" "$markdown_dest"
  pull_app_file "files/$csv_file" "$csv_dest"
  adb_cmd logcat -d -t 1000 >"$LOG_DIR/logcat_tail.txt" 2>&1 || true

  if [ ! -s "$markdown_dest" ] || [ ! -s "$csv_dest" ]; then
    log "failed to pull generated reports"
    exit 1
  fi

  log "markdown: ${markdown_dest#$ROOT_DIR/}"
  log "csv: ${csv_dest#$ROOT_DIR/}"
  log "logs: ${LOG_DIR#$ROOT_DIR/}"
}

main "$@"
