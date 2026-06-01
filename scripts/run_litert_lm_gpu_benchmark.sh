#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
ACTION="io.github.ninbyo02.lami.action.LITERT_LM_GPU_BENCHMARK"
RECEIVER="io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkReceiver"
STATE_APP_FILE="files/litert_lm_gpu_benchmark_state.txt"
MARKER_APP_FILE="files/litert_lm_gpu_benchmark_marker.txt"
OUT_DIR="$ROOT_DIR/artifacts/litert_lm_gpu_benchmark/$TIMESTAMP"
ARTIFACT_MD="$ROOT_DIR/artifacts/litert_lm_gpu_benchmark_${TIMESTAMP}.md"
ARTIFACT_CSV="$ROOT_DIR/artifacts/litert_lm_gpu_benchmark_${TIMESTAMP}.csv"
DEVICE_SERIAL=""
TIMEOUT_SECONDS=90
CASE_TIMEOUT_MS=60000
MODEL_PATH=""
PROMPTS="こんにちは|||カレーの材料を箇条書きで教えて"
MAX_OUTPUT_TOKENS_LIST="32,64,128,256"
BUILD_AND_INSTALL=true
LOGCAT_PID=""
BROADCAST_EXIT_CODE="not-run"

while [ $# -gt 0 ]; do
  case "$1" in
    --device)
      DEVICE_SERIAL="${2:-}"
      shift 2
      ;;
    --timeout)
      TIMEOUT_SECONDS="${2:-90}"
      shift 2
      ;;
    --case-timeout-ms)
      CASE_TIMEOUT_MS="${2:-60000}"
      shift 2
      ;;
    --model-path)
      MODEL_PATH="${2:-}"
      shift 2
      ;;
    --prompts)
      PROMPTS="${2:-}"
      shift 2
      ;;
    --max-output-tokens)
      MAX_OUTPUT_TOKENS_LIST="${2:-}"
      shift 2
      ;;
    --skip-build-install)
      BUILD_AND_INSTALL=false
      shift
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_litert_lm_gpu_benchmark.sh [--device <serial>] [--timeout <seconds>] [--case-timeout-ms <ms>]

Runs the debug-only standard app LiteRT-LM GPU benchmark receiver and pulls:
  artifacts/litert_lm_gpu_benchmark_<timestamp>.md
  artifacts/litert_lm_gpu_benchmark_<timestamp>.csv

Defaults:
  prompts: こんにちは ||| カレーの材料を箇条書きで教えて
  max_output_tokens: 32,64,128,256
  backend: Backend.GPU fixed by the receiver's EngineConfig

Safety:
  - does not use the hidden NPU receiver;
  - does not touch Backend.NPU;
  - does not stage or modify QAIRT/QNN libraries;
  - does not change fallback settings;
  - does not call production ChatScreen.
EOF
      exit 0
      ;;
    *)
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

if ! [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || [ "$TIMEOUT_SECONDS" -le 0 ]; then
  printf 'ERROR: --timeout must be a positive integer\n' >&2
  exit 2
fi
if ! [[ "$CASE_TIMEOUT_MS" =~ ^[0-9]+$ ]] || [ "$CASE_TIMEOUT_MS" -le 0 ]; then
  printf 'ERROR: --case-timeout-ms must be a positive integer\n' >&2
  exit 2
fi

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR" "$ROOT_DIR/artifacts"

log() {
  printf '[litert-lm-gpu-benchmark] %s\n' "$*"
}

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

state_value() {
  local key="$1"
  local file="$2"
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; found=1 } END { exit found ? 0 : 1 }' "$file" 2>/dev/null
}

pull_marker() {
  pull_app_file "$MARKER_APP_FILE" "$OUT_DIR/marker.txt"
}

marker_value() {
  local key="$1"
  state_value "$key" "$OUT_DIR/marker.txt" 2>/dev/null || true
}

wait_for_state() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local last_marker_stage=""
  while [ "$SECONDS" -lt "$deadline" ]; do
    pull_app_file "$STATE_APP_FILE" "$OUT_DIR/state.txt"
    pull_marker
    if [ -s "$OUT_DIR/marker.txt" ]; then
      local marker_stage
      marker_stage="$(marker_value stage)"
      if [ "$marker_stage" != "$last_marker_stage" ]; then
        {
          printf 'host_poll_second=%s\n' "$SECONDS"
          cat "$OUT_DIR/marker.txt"
          printf '\n'
        } >>"$OUT_DIR/marker_history.txt"
        last_marker_stage="$marker_stage"
      fi
    fi
    if [ -s "$OUT_DIR/state.txt" ]; then
      local status
      status="$(state_value status "$OUT_DIR/state.txt" || true)"
      case "$status" in
        success|partial|failure|blocked)
          return 0
          ;;
      esac
    fi
    sleep 1
  done
  return 124
}

start_probe_logcat() {
  adb_cmd logcat -b all -v threadtime >"$OUT_DIR/logcat_probe_threadtime.txt" 2>&1 &
  LOGCAT_PID="$!"
}

stop_probe_logcat() {
  if [ -n "$LOGCAT_PID" ]; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LOGCAT_PID" >/dev/null 2>&1 || true
    LOGCAT_PID=""
  fi
}

collect_crash_artifacts() {
  adb_cmd shell pidof "$APP_ID" >"$OUT_DIR/pid_after.txt" 2>&1 || true
  adb_cmd shell dumpsys meminfo "$APP_ID" >"$OUT_DIR/meminfo_after.txt" 2>&1 || true
  adb_cmd shell dumpsys activity crashes >"$OUT_DIR/dumpsys_activity_crashes.txt" 2>&1 || true
  adb_cmd shell dumpsys activity processes >"$OUT_DIR/dumpsys_activity_processes.txt" 2>&1 || true
  adb_cmd shell dumpsys dropbox --print >"$OUT_DIR/dropbox_full.txt" 2>&1 || true
  adb_cmd shell ls -lt /data/tombstones >"$OUT_DIR/tombstone_listing.txt" 2>&1 || true
  adb_cmd shell 'ls -t /data/tombstones/tombstone_* 2>/dev/null | head -n 1' >"$OUT_DIR/tombstone_latest_path.txt" 2>&1 || true
  local tombstone_path
  tombstone_path="$(tr -d '\r' <"$OUT_DIR/tombstone_latest_path.txt" 2>/dev/null || true)"
  if [ -n "$tombstone_path" ]; then
    adb_cmd shell cat "$tombstone_path" >"$OUT_DIR/tombstone_latest.txt" 2>&1 || true
  fi
  {
    printf '# Crash Probe Summary\n\n'
    printf -- '- app_id: `%s`\n' "$APP_ID"
    printf -- '- pid_after: `%s`\n' "$(tr -d '\r' <"$OUT_DIR/pid_after.txt" 2>/dev/null || true)"
    printf -- '- latest_marker_stage: `%s`\n' "$(marker_value stage)"
    printf -- '- latest_marker_detail: `%s`\n' "$(marker_value detail)"
    printf -- '- tombstone_latest_path: `%s`\n' "$tombstone_path"
    printf '\n## activity crashes extract\n\n```text\n'
    grep -Ei "$APP_ID|crash|exception|fatal|native|SIG|ANR" "$OUT_DIR/dumpsys_activity_crashes.txt" 2>/dev/null | tail -120 || true
    printf '\n```\n\n## dropbox extract\n\n```text\n'
    grep -Ei "$APP_ID|data_app_crash|data_app_native_crash|SYSTEM_TOMBSTONE|FATAL|SIG|Exception" "$OUT_DIR/dropbox_full.txt" 2>/dev/null | tail -160 || true
    printf '\n```\n\n## tombstone extract\n\n```text\n'
    grep -Ei "$APP_ID|signal|Abort message|backtrace|liblitert|LiteRT|GPU|OpenCL|QNN|HTP" "$OUT_DIR/tombstone_latest.txt" 2>/dev/null | head -180 || true
    printf '\n```\n'
  } >"$OUT_DIR/crash_summary.md"
}

append_host_timeout_state() {
  local fresh_crash="$1"
  local process_alive="$2"
  local latest_stage="$3"
  local latest_detail="$4"
  {
    printf 'host_wait_status=timeout\n'
    printf 'host_reason=host_timeout_waiting_for_receiver\n'
    printf 'host_timeout=true\n'
    printf 'host_fresh_crash=%s\n' "$fresh_crash"
    printf 'host_process_alive=%s\n' "$process_alive"
    printf 'host_latest_stage=%s\n' "${latest_stage:-unknown}"
    printf 'host_latest_detail=%s\n' "${latest_detail:-unknown}"
    printf 'host_am_broadcast_exit_code=%s\n' "$BROADCAST_EXIT_CODE"
    printf 'host_logcat_probe_threadtime=logcat_probe_threadtime.txt\n'
    printf 'host_crash_summary=crash_summary.md\n'
  } >>"$OUT_DIR/state.txt"
}

write_timeout_artifacts() {
  local fresh_crash="$1"
  local process_alive="$2"
  local latest_stage="$3"
  local latest_detail="$4"
  {
    printf '# LiteRT-LM GPU Benchmark\n\n'
    printf -- '- timestamp: `%s`\n' "$TIMESTAMP"
    printf -- '- route_type: `litert_lm_gpu_benchmark`\n'
    printf -- '- backend: `GPU`\n'
    printf -- '- status: `failure`\n'
    printf -- '- reason: `host_timeout_waiting_for_receiver`\n'
    printf -- '- timeout: `true`\n'
    printf -- '- fresh_crash: `%s`\n' "$fresh_crash"
    printf -- '- process_alive: `%s`\n' "$process_alive"
    printf -- '- latest_stage: `%s`\n' "${latest_stage:-unknown}"
    printf -- '- latest_detail: `%s`\n' "${latest_detail:-unknown}"
    printf -- '- am_broadcast_exit_code: `%s`\n' "$BROADCAST_EXIT_CODE"
    printf '\n## am broadcast\n\n```text\n'
    cat "$OUT_DIR/am_broadcast.txt" 2>/dev/null || true
    printf '\n```\n\n## latest marker\n\n```text\n'
    cat "$OUT_DIR/marker.txt" 2>/dev/null || true
    printf '\n```\n\n## crash summary\n\n'
    if [ -s "$OUT_DIR/crash_summary.md" ]; then
      cat "$OUT_DIR/crash_summary.md"
    else
      printf 'No crash summary collected.\n'
    fi
  } >"$ARTIFACT_MD"
  {
    printf '"timestamp","route_type","backend","prompt","max_output_tokens","model_path","model_exists","model_length","engine_create_ms","conversation_create_ms","first_token_ms","ttft_ms","decode_ms","total_ms","output_tokens","tokens_per_second","finish_reason","stop_reason","raw_output","sanitized_output","status","reason","fallback_used","timeout","fresh_crash","process_alive","latest_stage","latest_detail","am_broadcast_exit_code"\n'
    printf '"%s","litert_lm_gpu_benchmark","GPU","","","","false","0","","","","","","","","","","","","","failure","host_timeout_waiting_for_receiver","false","true","%s","%s","%s","%s","%s"\n' "$TIMESTAMP" "$fresh_crash" "$process_alive" "$latest_stage" "$latest_detail" "$BROADCAST_EXIT_CODE"
  } >"$ARTIFACT_CSV"
}

trap stop_probe_logcat EXIT

if ! command -v adb >/dev/null 2>&1; then
  log "adb not found"
  exit 1
fi

if ! choose_device; then
  log "no connected non-emulator device"
  exit 1
fi
printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"

if [ "$BUILD_AND_INSTALL" = true ]; then
  log "building standardDebug"
  ./gradlew :app:assembleStandardDebug >"$OUT_DIR/gradle_assemble_standard_debug.log" 2>&1 || {
    log "standardDebug build failed"
    exit 1
  }
  log "installing standardDebug"
  adb_cmd install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk >"$OUT_DIR/adb_install.txt" 2>&1 || {
    log "install failed"
    exit 1
  }
fi

adb_cmd shell run-as "$APP_ID" rm -f \
  "$STATE_APP_FILE" \
  "$MARKER_APP_FILE" \
  "files/litert_lm_gpu_benchmark_${TIMESTAMP}.md" \
  "files/litert_lm_gpu_benchmark_${TIMESTAMP}.csv" \
  >"$OUT_DIR/cleanup_app_files.txt" 2>&1 || true
adb_cmd shell run-as "$APP_ID" sh -c 'for f in files/local_models/*.litertlm; do [ -f "$f" ] && ls -l "$f"; done' \
  >"$OUT_DIR/app_local_models.txt" 2>&1 || true
adb_cmd logcat -c >"$OUT_DIR/logcat_clear.txt" 2>&1 || true
start_probe_logcat

log "broadcasting GPU benchmark receiver"
if [ -n "$MODEL_PATH" ]; then
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -n "$APP_ID/$RECEIVER" \
    -a "$ACTION" \
    --es timestamp "$TIMESTAMP" \
    --es model_path "$MODEL_PATH" \
    --es prompts "$PROMPTS" \
    --es max_output_tokens_list "$MAX_OUTPUT_TOKENS_LIST" \
    --el timeout_ms "$CASE_TIMEOUT_MS" \
    >"$OUT_DIR/am_broadcast.txt" 2>&1
  BROADCAST_EXIT_CODE="$?"
else
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -n "$APP_ID/$RECEIVER" \
    -a "$ACTION" \
    --es timestamp "$TIMESTAMP" \
    --es prompts "$PROMPTS" \
    --es max_output_tokens_list "$MAX_OUTPUT_TOKENS_LIST" \
    --el timeout_ms "$CASE_TIMEOUT_MS" \
    >"$OUT_DIR/am_broadcast.txt" 2>&1
  BROADCAST_EXIT_CODE="$?"
fi
{
  printf 'timestamp=%s\n' "$TIMESTAMP"
  printf 'broadcast_exit_code=%s\n' "$BROADCAST_EXIT_CODE"
  printf 'action=%s\n' "$ACTION"
  printf 'receiver=%s\n' "$RECEIVER"
  printf 'model_path_arg=%s\n' "${MODEL_PATH:-none}"
  printf 'prompts=%s\n' "$PROMPTS"
  printf 'max_output_tokens_list=%s\n' "$MAX_OUTPUT_TOKENS_LIST"
  printf 'case_timeout_ms=%s\n' "$CASE_TIMEOUT_MS"
  printf '\n[am_broadcast]\n'
  cat "$OUT_DIR/am_broadcast.txt"
} >"$OUT_DIR/am_broadcast_status.txt"

wait_status=success
if ! wait_for_state; then
  wait_status=timeout
fi
stop_probe_logcat

pull_app_file "$STATE_APP_FILE" "$OUT_DIR/state.txt"
pull_marker
pull_app_file "files/litert_lm_gpu_benchmark_${TIMESTAMP}.md" "$ARTIFACT_MD"
pull_app_file "files/litert_lm_gpu_benchmark_${TIMESTAMP}.csv" "$ARTIFACT_CSV"
adb_cmd logcat -d -t 2000 >"$OUT_DIR/logcat_tail.txt" 2>&1 || true
collect_crash_artifacts

if [ "$wait_status" = timeout ] || [ ! -s "$ARTIFACT_MD" ] || [ ! -s "$ARTIFACT_CSV" ]; then
  process_alive=false
  if [ -s "$OUT_DIR/pid_after.txt" ] && grep -Eq '^[0-9]+' "$OUT_DIR/pid_after.txt"; then
    process_alive=true
  fi
  fresh_crash=false
  if [ "$process_alive" = false ]; then
    fresh_crash=true
  fi
  latest_stage="$(marker_value stage)"
  latest_detail="$(marker_value detail)"
  if [ ! -s "$OUT_DIR/state.txt" ]; then
    {
      printf 'timestamp=%s\n' "$TIMESTAMP"
      printf 'route_type=litert_lm_gpu_benchmark\n'
      printf 'backend=GPU\n'
      printf 'status=failure\n'
      printf 'reason=host_timeout_waiting_for_receiver\n'
      printf 'app_state_present=false\n'
      printf 'timeout=true\n'
      printf 'fresh_crash=%s\n' "$fresh_crash"
      printf 'process_alive=%s\n' "$process_alive"
      printf 'latest_stage=%s\n' "${latest_stage:-unknown}"
      printf 'latest_detail=%s\n' "${latest_detail:-unknown}"
      printf 'am_broadcast_exit_code=%s\n' "$BROADCAST_EXIT_CODE"
    } >"$OUT_DIR/state.txt"
  fi
  append_host_timeout_state "$fresh_crash" "$process_alive" "${latest_stage:-unknown}" "${latest_detail:-unknown}"
  write_timeout_artifacts "$fresh_crash" "$process_alive" "${latest_stage:-unknown}" "${latest_detail:-unknown}"
fi

{
  printf 'timestamp=%s\n' "$TIMESTAMP"
  printf 'device=%s\n' "$DEVICE_SERIAL"
  printf 'app_id=%s\n' "$APP_ID"
  printf 'action=%s\n' "$ACTION"
  printf 'receiver=%s\n' "$RECEIVER"
  printf 'broadcast_exit_code=%s\n' "$BROADCAST_EXIT_CODE"
  printf 'wait_status=%s\n' "$wait_status"
  printf 'process_alive=%s\n' "$(if grep -Eq '^[0-9]+' "$OUT_DIR/pid_after.txt" 2>/dev/null; then printf true; else printf false; fi)"
  printf 'latest_stage=%s\n' "$(marker_value stage)"
  printf 'latest_detail=%s\n' "$(marker_value detail)"
  printf 'markdown=%s\n' "$ARTIFACT_MD"
  printf 'csv=%s\n' "$ARTIFACT_CSV"
  printf '\n[am_broadcast]\n'
  cat "$OUT_DIR/am_broadcast.txt" 2>/dev/null || true
  printf '\n[marker]\n'
  cat "$OUT_DIR/marker.txt" 2>/dev/null || true
  if [ -s "$OUT_DIR/state.txt" ]; then
    printf '\n[state]\n'
    cat "$OUT_DIR/state.txt"
  fi
} >"$OUT_DIR/summary.txt"

log "markdown: ${ARTIFACT_MD#$ROOT_DIR/}"
log "csv: ${ARTIFACT_CSV#$ROOT_DIR/}"
log "details: ${OUT_DIR#$ROOT_DIR/}"
