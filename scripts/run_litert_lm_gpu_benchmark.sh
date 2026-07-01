#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
ACTION="io.github.ninbyo02.lami.action.LITERT_LM_GPU_BENCHMARK"
RECEIVER="io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkReceiver"
STATE_APP_FILE="files/litert_lm_gpu_benchmark_state.txt"
MARKER_APP_FILE="files/litert_lm_gpu_benchmark_marker.txt"
MARKER_HISTORY_APP_FILE="files/litert_lm_gpu_benchmark_marker_history.txt"
OUT_DIR="$ROOT_DIR/artifacts/litert_lm_gpu_benchmark/$TIMESTAMP"
ARTIFACT_MD="$ROOT_DIR/artifacts/litert_lm_gpu_benchmark_${TIMESTAMP}.md"
ARTIFACT_CSV="$ROOT_DIR/artifacts/litert_lm_gpu_benchmark_${TIMESTAMP}.csv"
DEVICE_SERIAL=""
TIMEOUT_SECONDS=90
CASE_TIMEOUT_MS=60000
MODEL_PATH=""
PROMPTS=$'こんにちは\nカレーの材料を箇条書きで教えて'
MAX_OUTPUT_TOKENS_LIST="32,64,128,256"
BACKEND_VARIANT="gpu"
CLOSE_POLICY="normal"
PHASE="send-message"
MODEL_PATH_SOURCE="auto"
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
    --max-output-tokens|--max-output-tokens-list)
      MAX_OUTPUT_TOKENS_LIST="${2:-}"
      shift 2
      ;;
    --backend)
      BACKEND_VARIANT="${2:-gpu}"
      shift 2
      ;;
    --close-policy)
      CLOSE_POLICY="${2:-normal}"
      shift 2
      ;;
    --phase)
      PHASE="${2:-send-message}"
      shift 2
      ;;
    --model-path-source)
      MODEL_PATH_SOURCE="${2:-auto}"
      shift 2
      ;;
    --skip-build-install)
      BUILD_AND_INSTALL=false
      shift
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_litert_lm_gpu_benchmark.sh [--device <serial>] [--timeout <seconds>] [--case-timeout-ms <ms>] [--backend <variant>] [--close-policy <normal|skip-conversation|skip-all>] [--phase <engine-only|conversation-only|send-message>] [--model-path-source <auto|generic_fallback>] [--max-output-tokens-list <csv>]

Runs the debug-only standard app LiteRT-LM GPU benchmark receiver and pulls:
  artifacts/litert_lm_gpu_benchmark_<timestamp>.md
  artifacts/litert_lm_gpu_benchmark_<timestamp>.csv

Defaults:
  prompts: こんにちは / カレーの材料を箇条書きで教えて
  max_output_tokens_list: 32,64,128,256
  backend: gpu
  close_policy: normal
  phase: send-message
  model_path_source: auto

Backend variants:
  automatic
  default (alias for automatic)
  gpu
  cpu
  gpu-null-modalities
  gpu-cpu-modalities
  gpu-cache-dir
  gpu-null-max
  gpu-all
  gallery-chat-parity

Close policies:
  normal             close Conversation and Engine
  skip-conversation  skip Conversation.close(), still close Engine
  skip-all           skip Conversation.close() and Engine.close()

Phases:
  engine-only        create and initialize Engine only
  conversation-only  create and initialize Engine, then create Conversation
  send-message       full benchmark path, including sendMessage

Model path sources:
  auto              existing benchmark behavior: explicit --model-path, base model setting, then local_models
  generic_fallback  SettingsPreferences.getValidLocalGenericModelPathOrNull only; missing fails with reason=generic_fallback_model_missing

Transport:
  prompts, model_path, and max_output_tokens are sent as base64 extras so
  spaces, Japanese text, symbols, and pipe characters are not interpreted by
  the Android shell. --prompts accepts newline-separated prompts; legacy |||
  input is normalized to newlines before transport.

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
case "$BACKEND_VARIANT" in
  automatic|default)
    BACKEND_VARIANT="automatic"
    ;;
  gpu|cpu|gpu-null-modalities|gpu-cpu-modalities|gpu-cache-dir|gpu-null-max|gpu-all|gallery-chat-parity)
    ;;
  *)
    printf 'ERROR: --backend must be one of: automatic, default, gpu, cpu, gpu-null-modalities, gpu-cpu-modalities, gpu-cache-dir, gpu-null-max, gpu-all, gallery-chat-parity\n' >&2
    exit 2
    ;;
esac
case "$CLOSE_POLICY" in
  normal|skip-conversation|skip-all)
    ;;
  *)
    printf 'ERROR: --close-policy must be one of: normal, skip-conversation, skip-all\n' >&2
    exit 2
    ;;
esac
case "$PHASE" in
  engine-only|conversation-only|send-message)
    ;;
  *)
    printf 'ERROR: --phase must be one of: engine-only, conversation-only, send-message\n' >&2
    exit 2
    ;;
esac
case "$MODEL_PATH_SOURCE" in
  auto|generic_fallback)
    ;;
  *)
    printf 'ERROR: --model-path-source must be one of: auto, generic_fallback\n' >&2
    exit 2
    ;;
esac
BACKEND_LABEL="GPU"
if [ "$BACKEND_VARIANT" = "cpu" ]; then
  BACKEND_LABEL="CPU"
elif [ "$BACKEND_VARIANT" = "automatic" ]; then
  BACKEND_LABEL="Automatic"
fi
INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC=false
if [ "$CLOSE_POLICY" != "normal" ]; then
  INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC=true
fi

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR" "$ROOT_DIR/artifacts"

log() {
  printf '[litert-lm-gpu-benchmark] %s\n' "$*"
}

base64_no_wrap() {
  printf '%s' "$1" | base64 | tr -d '\n'
}

normalize_prompts() {
  printf '%s' "${PROMPTS//|||/$'\n'}"
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

dry_run_command_if_requested() {
  if [ "${LAMI_BENCHMARK_DRY_RUN_COMMAND:-false}" != "true" ]; then
    return 0
  fi
  local prompts_payload prompts_count max_output_tokens_count requested_run_count model_path_arg_present
  prompts_payload="$(normalize_prompts)"
  prompts_count="$(printf '%s\n' "$prompts_payload" | awk 'NF { count++ } END { print count + 0 }')"
  max_output_tokens_count="$(printf '%s' "$MAX_OUTPUT_TOKENS_LIST" | awk -F, '{ count = 0; for (i = 1; i <= NF; i++) if ($i ~ /^[[:space:]]*[0-9]+[[:space:]]*$/) count++; print count }')"
  requested_run_count=$((prompts_count * max_output_tokens_count))
  if [ -n "$MODEL_PATH" ]; then
    model_path_arg_present=true
  else
    model_path_arg_present=false
  fi
  printf 'dry_run=true\n'
  printf 'backend=%s\n' "$BACKEND_LABEL"
  printf 'backend_variant=%s\n' "$BACKEND_VARIANT"
  printf 'close_policy=%s\n' "$CLOSE_POLICY"
  printf 'phase=%s\n' "$PHASE"
  printf 'model_path_source=%s\n' "$MODEL_PATH_SOURCE"
  printf 'model_path_arg_present=%s\n' "$model_path_arg_present"
  printf 'prompts_count=%s\n' "$prompts_count"
  printf 'max_output_tokens_list=%s\n' "$MAX_OUTPUT_TOKENS_LIST"
  printf 'requested_run_count=%s\n' "$requested_run_count"
  printf 'receiver_extra=--es backend_variant %s\n' "$BACKEND_VARIANT"
  exit 0
}

first_matching_line() {
  local pattern="$1"
  shift
  local file
  for file in "$@"; do
    if [ -s "$file" ]; then
      local match
      match="$(grep -Eim 1 "$pattern" "$file" 2>/dev/null | sed 's/^[[:space:]]*//' || true)"
      if [ -n "$match" ]; then
        printf '%s\n' "$match"
        return 0
      fi
    fi
  done
  return 0
}

extract_signal() {
  first_matching_line 'Fatal signal|signal [0-9]+|SIGABRT|SIGSEGV|SIGBUS|SIGILL' \
    "$OUT_DIR/tombstone_latest.txt" \
    "$OUT_DIR/logcat_postrun_threadtime.txt" \
    "$OUT_DIR/logcat_probe_threadtime.txt" \
    "$OUT_DIR/dropbox_full.txt"
}

extract_abort_message() {
  first_matching_line 'Abort message|abort message|abort\(|Check failed|Fatal error|terminating with uncaught exception' \
    "$OUT_DIR/tombstone_latest.txt" \
    "$OUT_DIR/logcat_postrun_threadtime.txt" \
    "$OUT_DIR/logcat_probe_threadtime.txt" \
    "$OUT_DIR/dropbox_full.txt"
}

extract_crash_process() {
  first_matching_line "Cmdline:|pid:|process name|$APP_ID" \
    "$OUT_DIR/tombstone_latest.txt" \
    "$OUT_DIR/logcat_postrun_threadtime.txt" \
    "$OUT_DIR/logcat_probe_threadtime.txt" \
    "$OUT_DIR/dropbox_full.txt"
}

extract_build_ids() {
  grep -Eih 'BuildId:|Build ID|BuildId=' \
    "$OUT_DIR/tombstone_latest.txt" \
    "$OUT_DIR/logcat_postrun_threadtime.txt" \
    "$OUT_DIR/logcat_probe_threadtime.txt" \
    "$OUT_DIR/dropbox_full.txt" 2>/dev/null |
    sed 's/^[[:space:]]*//' |
    awk '!seen[$0]++ { print }' |
    head -20 |
    tr '\n' '|' |
    sed 's/|$//' || true
}

extract_backtrace_head() {
  awk '
    BEGIN { capture = 0; count = 0 }
    /backtrace:/ { capture = 1; next }
    capture == 1 && count < 12 {
      if ($0 ~ /#[0-9]+| pc /) {
        gsub(/^[[:space:]]+/, "", $0)
        print
        count++
      } else if (count > 0) {
        exit
      }
    }
  ' "$OUT_DIR/tombstone_latest.txt" 2>/dev/null |
    tr '\n' '|' |
    sed 's/|$//' || true
}

write_crash_fields() {
  local pid_after signal_line abort_line crash_process build_ids backtrace_head native_crash_suspected
  pid_after="$(tr -d '\r\n' <"$OUT_DIR/pid_after.txt" 2>/dev/null || true)"
  signal_line="$(extract_signal)"
  abort_line="$(extract_abort_message)"
  crash_process="$(extract_crash_process)"
  build_ids="$(extract_build_ids)"
  backtrace_head="$(extract_backtrace_head)"
  native_crash_suspected=false
  if [ -z "$pid_after" ] || [ -n "$signal_line" ] || [ -n "$abort_line" ] || [ -n "$backtrace_head" ]; then
    native_crash_suspected=true
  fi
  {
    printf 'native_crash_suspected=%s\n' "$native_crash_suspected"
    printf 'crash_process=%s\n' "${crash_process:-missing}"
    printf 'signal=%s\n' "${signal_line:-missing}"
    printf 'abort_message=%s\n' "${abort_line:-missing}"
    printf 'backtrace_head=%s\n' "${backtrace_head:-missing}"
    printf 'build_ids=%s\n' "${build_ids:-missing}"
    printf 'background_logcat=%s\n' "$OUT_DIR/logcat_probe_threadtime.txt"
    printf 'adb_logcat_d=%s\n' "$OUT_DIR/logcat_postrun_threadtime.txt"
    printf 'dropbox=%s\n' "$OUT_DIR/dropbox_full.txt"
    printf 'tombstone_listing=%s\n' "$OUT_DIR/tombstone_listing.txt"
    printf 'latest_tombstone=%s\n' "$OUT_DIR/tombstone_latest.txt"
  } >"$OUT_DIR/crash_fields.txt"
}

crash_field_value() {
  local key="$1"
  state_value "$key" "$OUT_DIR/crash_fields.txt" 2>/dev/null || true
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
  tombstone_path="$(grep -Eom 1 '/data/tombstones/tombstone_[^[:space:]]+' "$OUT_DIR/tombstone_latest_path.txt" 2>/dev/null || true)"
  if [ -n "$tombstone_path" ]; then
    adb_cmd shell cat "$tombstone_path" >"$OUT_DIR/tombstone_latest.txt" 2>&1 || true
  fi
  write_crash_fields
  {
    printf '# Crash Probe Summary\n\n'
    printf -- '- app_id: `%s`\n' "$APP_ID"
    printf -- '- backend_variant: `%s`\n' "$BACKEND_VARIANT"
    printf -- '- close_policy: `%s`\n' "$CLOSE_POLICY"
    printf -- '- phase: `%s`\n' "$PHASE"
    printf -- '- max_output_tokens_list: `%s`\n' "$MAX_OUTPUT_TOKENS_LIST"
    printf -- '- intentionally_leaked_for_diagnostic: `%s`\n' "$INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC"
    printf -- '- native_crash_suspected: `%s`\n' "$(crash_field_value native_crash_suspected)"
    printf -- '- crash_process: `%s`\n' "$(crash_field_value crash_process)"
    printf -- '- signal: `%s`\n' "$(crash_field_value signal)"
    printf -- '- abort_message: `%s`\n' "$(crash_field_value abort_message)"
    printf -- '- backtrace_head: `%s`\n' "$(crash_field_value backtrace_head)"
    printf -- '- build_ids: `%s`\n' "$(crash_field_value build_ids)"
    printf -- '- pid_after: `%s`\n' "$(tr -d '\r' <"$OUT_DIR/pid_after.txt" 2>/dev/null || true)"
    printf -- '- latest_marker_stage: `%s`\n' "$(marker_value stage)"
    printf -- '- latest_marker_detail: `%s`\n' "$(marker_value detail)"
    printf -- '- tombstone_latest_path: `%s`\n' "$tombstone_path"
    printf -- '- background_logcat: `%s`\n' "$OUT_DIR/logcat_probe_threadtime.txt"
    printf -- '- adb_logcat_d: `%s`\n' "$OUT_DIR/logcat_postrun_threadtime.txt"
    printf -- '- dropbox: `%s`\n' "$OUT_DIR/dropbox_full.txt"
    printf -- '- tombstone_listing: `%s`\n' "$OUT_DIR/tombstone_listing.txt"
    printf -- '- latest_tombstone: `%s`\n' "$OUT_DIR/tombstone_latest.txt"
    printf '\n## activity crashes extract\n\n```text\n'
    grep -Ei "$APP_ID|crash|exception|fatal|native|SIG|ANR" "$OUT_DIR/dumpsys_activity_crashes.txt" 2>/dev/null | tail -120 || true
    printf '\n```\n\n## logcat crash extract\n\n```text\n'
    grep -Ei "$APP_ID|AndroidRuntime|DEBUG|DEBUGGERD|libc|crash_dump64|tombstoned|LiteRT|litert|GPU|OpenCL|Vulkan|FATAL|SIGABRT|SIGSEGV|SIGBUS|abort|tombstone|BuildId" \
      "$OUT_DIR/logcat_probe_threadtime.txt" "$OUT_DIR/logcat_postrun_threadtime.txt" 2>/dev/null | tail -220 || true
    printf '\n```\n\n## dropbox extract\n\n```text\n'
    grep -Ei "$APP_ID|data_app_crash|data_app_native_crash|SYSTEM_TOMBSTONE|FATAL|SIG|Exception" "$OUT_DIR/dropbox_full.txt" 2>/dev/null | tail -160 || true
    printf '\n```\n\n## tombstone extract\n\n```text\n'
    grep -Ei "$APP_ID|Cmdline|pid:|signal|Abort message|backtrace|BuildId|Build ID|liblitert|LiteRT|GPU|OpenCL|Vulkan|QNN|HTP" "$OUT_DIR/tombstone_latest.txt" 2>/dev/null | head -220 || true
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
    printf 'requested_run_count=%s\n' "$REQUESTED_RUN_COUNT"
    printf 'completed_run_count=0\n'
    printf 'success_count=0\n'
    printf 'failure_count=0\n'
    printf 'timeout_count=1\n'
    printf 'fallback_count=0\n'
    printf 'host_fresh_crash=%s\n' "$fresh_crash"
    printf 'host_process_alive=%s\n' "$process_alive"
    printf 'host_latest_stage=%s\n' "${latest_stage:-unknown}"
    printf 'host_latest_detail=%s\n' "${latest_detail:-unknown}"
    printf 'host_am_broadcast_exit_code=%s\n' "$BROADCAST_EXIT_CODE"
    printf 'close_policy=%s\n' "$CLOSE_POLICY"
    printf 'phase=%s\n' "$PHASE"
    printf 'model_path_source=%s\n' "$MODEL_PATH_SOURCE"
    printf 'intentionally_leaked_for_diagnostic=%s\n' "$INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC"
    printf 'native_crash_suspected=%s\n' "$(crash_field_value native_crash_suspected)"
    printf 'crash_process=%s\n' "$(crash_field_value crash_process)"
    printf 'signal=%s\n' "$(crash_field_value signal)"
    printf 'abort_message=%s\n' "$(crash_field_value abort_message)"
    printf 'backtrace_head=%s\n' "$(crash_field_value backtrace_head)"
    printf 'build_ids=%s\n' "$(crash_field_value build_ids)"
    printf 'host_logcat_probe_threadtime=logcat_probe_threadtime.txt\n'
    printf 'host_logcat_postrun_threadtime=logcat_postrun_threadtime.txt\n'
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
    printf -- '- backend: `%s`\n' "$BACKEND_LABEL"
    printf -- '- backend_variant: `%s`\n' "$BACKEND_VARIANT"
    printf -- '- close_policy: `%s`\n' "$CLOSE_POLICY"
    printf -- '- phase: `%s`\n' "$PHASE"
    printf -- '- model_path_source: `%s`\n' "$MODEL_PATH_SOURCE"
    printf -- '- intentionally_leaked_for_diagnostic: `%s`\n' "$INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC"
    printf -- '- status: `failure`\n'
    printf -- '- reason: `host_timeout_waiting_for_receiver`\n'
    printf -- '- timeout: `true`\n'
    printf -- '- requested_run_count: `%s`\n' "$REQUESTED_RUN_COUNT"
    printf -- '- completed_run_count: `0`\n'
    printf -- '- success_count: `0`\n'
    printf -- '- failure_count: `0`\n'
    printf -- '- timeout_count: `1`\n'
    printf -- '- fallback_count: `0`\n'
    printf -- '- fresh_crash: `%s`\n' "$fresh_crash"
    printf -- '- process_alive: `%s`\n' "$process_alive"
    printf -- '- latest_stage: `%s`\n' "${latest_stage:-unknown}"
    printf -- '- latest_detail: `%s`\n' "${latest_detail:-unknown}"
    printf -- '- am_broadcast_exit_code: `%s`\n' "$BROADCAST_EXIT_CODE"
    printf -- '- native_crash_suspected: `%s`\n' "$(crash_field_value native_crash_suspected)"
    printf -- '- crash_process: `%s`\n' "$(crash_field_value crash_process)"
    printf -- '- signal: `%s`\n' "$(crash_field_value signal)"
    printf -- '- abort_message: `%s`\n' "$(crash_field_value abort_message)"
    printf -- '- backtrace_head: `%s`\n' "$(crash_field_value backtrace_head)"
    printf -- '- build_ids: `%s`\n' "$(crash_field_value build_ids)"
    printf '\n## am broadcast\n\n```text\n'
    cat "$OUT_DIR/am_broadcast.txt" 2>/dev/null || true
    printf '\n```\n\n## latest marker\n\n```text\n'
    cat "$OUT_DIR/marker.txt" 2>/dev/null || true
    printf '\n```\n\n## marker history\n\n```text\n'
    cat "$OUT_DIR/app_marker_history.txt" 2>/dev/null || cat "$OUT_DIR/marker_history.txt" 2>/dev/null || true
    printf '\n```\n\n## crash summary\n\n'
    if [ -s "$OUT_DIR/crash_summary.md" ]; then
      cat "$OUT_DIR/crash_summary.md"
    else
      printf 'No crash summary collected.\n'
    fi
  } >"$ARTIFACT_MD"
  send_api_variant="flow_string_with_blocking_fallback"
  sampler_top_k=""
  sampler_top_p=""
  sampler_temperature=""
  conversation_config_used="false"
  contents_api_used="false"
  if [ "$BACKEND_VARIANT" = "gallery-chat-parity" ]; then
    send_api_variant="gallery_contents_callback"
    sampler_top_k="64"
    sampler_top_p="0.95"
    sampler_temperature="1.0"
    conversation_config_used="true"
    contents_api_used="true"
  fi
  {
    printf '"timestamp","route_type","backend","backend_variant","close_policy","phase","prompt","max_output_tokens","max_output_tokens_list","model_path","model_exists","model_length","engine_create_ms","conversation_create_ms","first_token_ms","ttft_ms","decode_ms","total_ms","output_tokens","tokens_per_second","finish_reason","stop_reason","raw_output","sanitized_output","status","reason","send_exception_class","send_exception_message","send_exception_cause_chain","intentionally_leaked_for_diagnostic","fallback_used","timeout","fresh_crash","send_api_variant","sampler_top_k","sampler_top_p","sampler_temperature","conversation_config_used","contents_api_used","process_alive","latest_stage","latest_detail","am_broadcast_exit_code"\n'
    printf '"%s","litert_lm_gpu_benchmark","%s","%s","%s","%s","","","%s","","false","0","","","","","","","","","","","","","failure","host_timeout_waiting_for_receiver","","","","%s","false","true","%s","%s","%s","%s","%s","%s","%s","%s","%s"\n' "$TIMESTAMP" "$BACKEND_LABEL" "$BACKEND_VARIANT" "$CLOSE_POLICY" "$PHASE" "$MAX_OUTPUT_TOKENS_LIST" "$INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC" "$fresh_crash" "$send_api_variant" "$sampler_top_k" "$sampler_top_p" "$sampler_temperature" "$conversation_config_used" "$contents_api_used" "$process_alive" "$latest_stage" "$latest_detail" "$BROADCAST_EXIT_CODE"
  } >"$ARTIFACT_CSV"
}

trap stop_probe_logcat EXIT

dry_run_command_if_requested

if ! command -v adb >/dev/null 2>&1; then
  log "adb not found"
  exit 1
fi
if ! command -v base64 >/dev/null 2>&1; then
  log "base64 not found"
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
  "$MARKER_HISTORY_APP_FILE" \
  "files/litert_lm_gpu_benchmark_${TIMESTAMP}.md" \
  "files/litert_lm_gpu_benchmark_${TIMESTAMP}.csv" \
  >"$OUT_DIR/cleanup_app_files.txt" 2>&1 || true
adb_cmd shell run-as "$APP_ID" sh -c 'for f in files/local_models/*.litertlm; do [ -f "$f" ] && ls -l "$f"; done' \
  >"$OUT_DIR/app_local_models.txt" 2>&1 || true
adb_cmd logcat -c >"$OUT_DIR/logcat_clear.txt" 2>&1 || true
start_probe_logcat

PROMPTS_PAYLOAD="$(normalize_prompts)"
PROMPTS_COUNT="$(printf '%s\n' "$PROMPTS_PAYLOAD" | awk 'NF { count++ } END { print count + 0 }')"
MAX_OUTPUT_TOKENS_COUNT="$(printf '%s' "$MAX_OUTPUT_TOKENS_LIST" | awk -F, '{ count = 0; for (i = 1; i <= NF; i++) if ($i ~ /^[[:space:]]*[0-9]+[[:space:]]*$/) count++; print count }')"
REQUESTED_RUN_COUNT=$((PROMPTS_COUNT * MAX_OUTPUT_TOKENS_COUNT))
PROMPTS_BASE64="$(base64_no_wrap "$PROMPTS_PAYLOAD")"
MAX_OUTPUT_TOKENS_LIST_BASE64="$(base64_no_wrap "$MAX_OUTPUT_TOKENS_LIST")"
MODEL_PATH_BASE64=""
if [ -n "$MODEL_PATH" ]; then
  MODEL_PATH_BASE64="$(base64_no_wrap "$MODEL_PATH")"
fi

log "broadcasting GPU benchmark receiver backend=$BACKEND_VARIANT close_policy=$CLOSE_POLICY phase=$PHASE model_path_source=$MODEL_PATH_SOURCE"
if [ -n "$MODEL_PATH" ]; then
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -n "$APP_ID/$RECEIVER" \
    -a "$ACTION" \
    --es timestamp "$TIMESTAMP" \
    --es model_path_base64 "$MODEL_PATH_BASE64" \
    --es prompts_base64 "$PROMPTS_BASE64" \
    --es max_output_tokens_list_base64 "$MAX_OUTPUT_TOKENS_LIST_BASE64" \
    --es backend_variant "$BACKEND_VARIANT" \
    --es close_policy "$CLOSE_POLICY" \
    --es phase "$PHASE" \
    --es model_path_source "$MODEL_PATH_SOURCE" \
    --el timeout_ms "$CASE_TIMEOUT_MS" \
    >"$OUT_DIR/am_broadcast_raw.txt" 2>&1
  BROADCAST_EXIT_CODE="$?"
else
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -n "$APP_ID/$RECEIVER" \
    -a "$ACTION" \
    --es timestamp "$TIMESTAMP" \
    --es prompts_base64 "$PROMPTS_BASE64" \
    --es max_output_tokens_list_base64 "$MAX_OUTPUT_TOKENS_LIST_BASE64" \
    --es backend_variant "$BACKEND_VARIANT" \
    --es close_policy "$CLOSE_POLICY" \
    --es phase "$PHASE" \
    --es model_path_source "$MODEL_PATH_SOURCE" \
    --el timeout_ms "$CASE_TIMEOUT_MS" \
    >"$OUT_DIR/am_broadcast_raw.txt" 2>&1
  BROADCAST_EXIT_CODE="$?"
fi
{
  printf 'timestamp=%s\n' "$TIMESTAMP"
  printf 'action=%s\n' "$ACTION"
  printf 'receiver=%s\n' "$RECEIVER"
  printf 'broadcast_exit_code=%s\n' "$BROADCAST_EXIT_CODE"
  printf 'transport=base64_safe_extras\n'
  printf 'backend_variant=%s\n' "$BACKEND_VARIANT"
  printf 'close_policy=%s\n' "$CLOSE_POLICY"
  printf 'phase=%s\n' "$PHASE"
  printf 'model_path_source=%s\n' "$MODEL_PATH_SOURCE"
  printf 'max_output_tokens_list=%s\n' "$MAX_OUTPUT_TOKENS_LIST"
  printf 'intentionally_leaked_for_diagnostic=%s\n' "$INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC"
  printf 'model_path_arg_present=%s\n' "$(if [ -n "$MODEL_PATH" ]; then printf true; else printf false; fi)"
  printf 'prompts_count=%s\n' "$PROMPTS_COUNT"
  printf 'requested_run_count=%s\n' "$REQUESTED_RUN_COUNT"
  printf 'max_output_tokens_list=%s\n' "$MAX_OUTPUT_TOKENS_LIST"
  printf 'case_timeout_ms=%s\n' "$CASE_TIMEOUT_MS"
  printf 'raw_broadcast_result=am_broadcast_raw.txt\n'
  printf '\n[raw_broadcast_result]\n'
  cat "$OUT_DIR/am_broadcast_raw.txt" 2>/dev/null || true
} >"$OUT_DIR/am_broadcast.txt"
{
  printf 'timestamp=%s\n' "$TIMESTAMP"
  printf 'exit_code=%s\n' "$BROADCAST_EXIT_CODE"
  printf 'broadcast_exit_code=%s\n' "$BROADCAST_EXIT_CODE"
  printf 'action=%s\n' "$ACTION"
  printf 'receiver=%s\n' "$RECEIVER"
  printf 'transport=base64_safe_extras\n'
  printf 'backend_variant=%s\n' "$BACKEND_VARIANT"
  printf 'close_policy=%s\n' "$CLOSE_POLICY"
  printf 'phase=%s\n' "$PHASE"
  printf 'model_path_source=%s\n' "$MODEL_PATH_SOURCE"
  printf 'intentionally_leaked_for_diagnostic=%s\n' "$INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC"
  printf 'model_path_arg_present=%s\n' "$(if [ -n "$MODEL_PATH" ]; then printf true; else printf false; fi)"
  printf 'prompts_count=%s\n' "$PROMPTS_COUNT"
  printf 'requested_run_count=%s\n' "$REQUESTED_RUN_COUNT"
  printf 'max_output_tokens_list=%s\n' "$MAX_OUTPUT_TOKENS_LIST"
  printf 'case_timeout_ms=%s\n' "$CASE_TIMEOUT_MS"
  printf 'raw_broadcast_result=am_broadcast_raw.txt\n'
} >"$OUT_DIR/am_broadcast_status.txt"

wait_status=success
if ! wait_for_state; then
  wait_status=timeout
fi
stop_probe_logcat

pull_app_file "$STATE_APP_FILE" "$OUT_DIR/state.txt"
pull_marker
pull_app_file "$MARKER_HISTORY_APP_FILE" "$OUT_DIR/app_marker_history.txt"
pull_app_file "files/litert_lm_gpu_benchmark_${TIMESTAMP}.md" "$ARTIFACT_MD"
pull_app_file "files/litert_lm_gpu_benchmark_${TIMESTAMP}.csv" "$ARTIFACT_CSV"
adb_cmd logcat -b all -d -v threadtime >"$OUT_DIR/logcat_postrun_threadtime.txt" 2>&1 || true
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
      printf 'backend=%s\n' "$BACKEND_LABEL"
      printf 'backend_variant=%s\n' "$BACKEND_VARIANT"
      printf 'close_policy=%s\n' "$CLOSE_POLICY"
      printf 'phase=%s\n' "$PHASE"
      printf 'model_path_source=%s\n' "$MODEL_PATH_SOURCE"
      printf 'intentionally_leaked_for_diagnostic=%s\n' "$INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC"
      printf 'status=failure\n'
      printf 'reason=host_timeout_waiting_for_receiver\n'
      printf 'app_state_present=false\n'
      printf 'timeout=true\n'
      printf 'requested_run_count=%s\n' "$REQUESTED_RUN_COUNT"
      printf 'completed_run_count=0\n'
      printf 'success_count=0\n'
      printf 'failure_count=0\n'
      printf 'timeout_count=1\n'
      printf 'fallback_count=0\n'
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
  receiver_started_marker_seen=false
  if grep -q '^stage=receiver_started$' "$OUT_DIR/app_marker_history.txt" 2>/dev/null ||
    grep -q '^stage=receiver_started$' "$OUT_DIR/marker_history.txt" 2>/dev/null; then
    receiver_started_marker_seen=true
  fi
  printf 'timestamp=%s\n' "$TIMESTAMP"
  printf 'device=%s\n' "$DEVICE_SERIAL"
  printf 'app_id=%s\n' "$APP_ID"
  printf 'action=%s\n' "$ACTION"
  printf 'receiver=%s\n' "$RECEIVER"
  printf 'broadcast_exit_code=%s\n' "$BROADCAST_EXIT_CODE"
  printf 'backend_variant=%s\n' "$BACKEND_VARIANT"
  printf 'close_policy=%s\n' "$CLOSE_POLICY"
  printf 'phase=%s\n' "$PHASE"
  printf 'model_path_source=%s\n' "$MODEL_PATH_SOURCE"
  printf 'intentionally_leaked_for_diagnostic=%s\n' "$INTENTIONALLY_LEAKED_FOR_DIAGNOSTIC"
  printf 'wait_status=%s\n' "$wait_status"
  printf 'receiver_started_marker_seen=%s\n' "$receiver_started_marker_seen"
  printf 'process_alive=%s\n' "$(if grep -Eq '^[0-9]+' "$OUT_DIR/pid_after.txt" 2>/dev/null; then printf true; else printf false; fi)"
  printf 'latest_stage=%s\n' "$(marker_value stage)"
  printf 'latest_detail=%s\n' "$(marker_value detail)"
  printf 'native_crash_suspected=%s\n' "$(crash_field_value native_crash_suspected)"
  printf 'crash_process=%s\n' "$(crash_field_value crash_process)"
  printf 'signal=%s\n' "$(crash_field_value signal)"
  printf 'abort_message=%s\n' "$(crash_field_value abort_message)"
  printf 'backtrace_head=%s\n' "$(crash_field_value backtrace_head)"
  printf 'build_ids=%s\n' "$(crash_field_value build_ids)"
  printf 'background_logcat=%s\n' "$OUT_DIR/logcat_probe_threadtime.txt"
  printf 'adb_logcat_d=%s\n' "$OUT_DIR/logcat_postrun_threadtime.txt"
  printf 'dropbox=%s\n' "$OUT_DIR/dropbox_full.txt"
  printf 'tombstone_listing=%s\n' "$OUT_DIR/tombstone_listing.txt"
  printf 'latest_tombstone=%s\n' "$OUT_DIR/tombstone_latest.txt"
  printf 'crash_summary=%s\n' "$OUT_DIR/crash_summary.md"
  printf 'markdown=%s\n' "$ARTIFACT_MD"
  printf 'csv=%s\n' "$ARTIFACT_CSV"
  printf '\n[am_broadcast]\n'
  cat "$OUT_DIR/am_broadcast.txt" 2>/dev/null || true
  printf '\n[marker]\n'
  cat "$OUT_DIR/marker.txt" 2>/dev/null || true
  printf '\n[marker_history]\n'
  cat "$OUT_DIR/app_marker_history.txt" 2>/dev/null || cat "$OUT_DIR/marker_history.txt" 2>/dev/null || true
  if [ -s "$OUT_DIR/state.txt" ]; then
    printf '\n[state]\n'
    cat "$OUT_DIR/state.txt"
  fi
} >"$OUT_DIR/summary.txt"

log "markdown: ${ARTIFACT_MD#$ROOT_DIR/}"
log "csv: ${ARTIFACT_CSV#$ROOT_DIR/}"
log "details: ${OUT_DIR#$ROOT_DIR/}"
