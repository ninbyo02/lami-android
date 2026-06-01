#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
ACTION="io.github.ninbyo02.lami.action.LITERT_LM_GPU_BENCHMARK"
RECEIVER="io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkReceiver"
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

wait_for_state() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    pull_app_file "files/litert_lm_gpu_benchmark_state.txt" "$OUT_DIR/state.txt"
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

write_timeout_artifacts() {
  {
    printf '# LiteRT-LM GPU Benchmark\n\n'
    printf -- '- timestamp: `%s`\n' "$TIMESTAMP"
    printf -- '- route_type: `litert_lm_gpu_benchmark`\n'
    printf -- '- backend: `GPU`\n'
    printf -- '- status: `failure`\n'
    printf -- '- reason: `host_timeout_waiting_for_receiver`\n'
    printf -- '- timeout: `true`\n'
    printf -- '- fresh_crash: `%s`\n' "$1"
  } >"$ARTIFACT_MD"
  {
    printf '"timestamp","route_type","backend","prompt","max_output_tokens","model_path","model_exists","model_length","engine_create_ms","conversation_create_ms","first_token_ms","ttft_ms","decode_ms","total_ms","output_tokens","tokens_per_second","finish_reason","stop_reason","raw_output","sanitized_output","status","reason","fallback_used","timeout","fresh_crash"\n'
    printf '"%s","litert_lm_gpu_benchmark","GPU","","","","false","0","","","","","","","","","","","","","failure","host_timeout_waiting_for_receiver","false","true","%s"\n' "$TIMESTAMP" "$1"
  } >"$ARTIFACT_CSV"
}

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
  files/litert_lm_gpu_benchmark_state.txt \
  "files/litert_lm_gpu_benchmark_${TIMESTAMP}.md" \
  "files/litert_lm_gpu_benchmark_${TIMESTAMP}.csv" \
  >"$OUT_DIR/cleanup_app_files.txt" 2>&1 || true
adb_cmd logcat -c >"$OUT_DIR/logcat_clear.txt" 2>&1 || true

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
    >"$OUT_DIR/am_broadcast.txt" 2>&1 || true
else
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -n "$APP_ID/$RECEIVER" \
    -a "$ACTION" \
    --es timestamp "$TIMESTAMP" \
    --es prompts "$PROMPTS" \
    --es max_output_tokens_list "$MAX_OUTPUT_TOKENS_LIST" \
    --el timeout_ms "$CASE_TIMEOUT_MS" \
    >"$OUT_DIR/am_broadcast.txt" 2>&1 || true
fi

wait_status=success
if ! wait_for_state; then
  wait_status=timeout
fi

pull_app_file "files/litert_lm_gpu_benchmark_state.txt" "$OUT_DIR/state.txt"
pull_app_file "files/litert_lm_gpu_benchmark_${TIMESTAMP}.md" "$ARTIFACT_MD"
pull_app_file "files/litert_lm_gpu_benchmark_${TIMESTAMP}.csv" "$ARTIFACT_CSV"
adb_cmd logcat -d -t 2000 >"$OUT_DIR/logcat_tail.txt" 2>&1 || true
adb_cmd shell pidof "$APP_ID" >"$OUT_DIR/pid_after.txt" 2>&1 || true
adb_cmd shell dumpsys meminfo "$APP_ID" >"$OUT_DIR/meminfo_after.txt" 2>&1 || true

if [ "$wait_status" = timeout ] || [ ! -s "$ARTIFACT_MD" ] || [ ! -s "$ARTIFACT_CSV" ]; then
  process_alive=false
  if [ -s "$OUT_DIR/pid_after.txt" ] && grep -Eq '^[0-9]+' "$OUT_DIR/pid_after.txt"; then
    process_alive=true
  fi
  fresh_crash=false
  if [ "$process_alive" = false ]; then
    fresh_crash=true
  fi
  write_timeout_artifacts "$fresh_crash"
fi

{
  printf 'timestamp=%s\n' "$TIMESTAMP"
  printf 'device=%s\n' "$DEVICE_SERIAL"
  printf 'wait_status=%s\n' "$wait_status"
  printf 'markdown=%s\n' "$ARTIFACT_MD"
  printf 'csv=%s\n' "$ARTIFACT_CSV"
  if [ -s "$OUT_DIR/state.txt" ]; then
    printf '\n[state]\n'
    cat "$OUT_DIR/state.txt"
  fi
} >"$OUT_DIR/summary.txt"

log "markdown: ${ARTIFACT_MD#$ROOT_DIR/}"
log "csv: ${ARTIFACT_CSV#$ROOT_DIR/}"
log "details: ${OUT_DIR#$ROOT_DIR/}"
