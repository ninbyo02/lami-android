#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
ACTIVITY=".MainActivity"
OUT_DIR="$ROOT_DIR/artifacts/litert_lm_gpu_route_probe/$TIMESTAMP"
DEVICE_SERIAL=""
OBSERVE_SECONDS=45
START_APP=true
CLEAR_LOGCAT=false
LABEL="manual-gpu-route-observation"

while [ $# -gt 0 ]; do
  case "$1" in
    --device)
      DEVICE_SERIAL="${2:-}"
      shift 2
      ;;
    --seconds|--observe-seconds)
      OBSERVE_SECONDS="${2:-45}"
      shift 2
      ;;
    --app-id)
      APP_ID="${2:-}"
      shift 2
      ;;
    --activity)
      ACTIVITY="${2:-}"
      shift 2
      ;;
    --label)
      LABEL="${2:-}"
      shift 2
      ;;
    --no-start)
      START_APP=false
      shift
      ;;
    --clear-logcat)
      CLEAR_LOGCAT=true
      shift
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_litert_lm_gpu_route_probe.sh [--device <serial>] [--seconds <n>] [--label <name>]

Observes the standard LiteRT-LM GPU/local inference route without changing
Backend.NPU, QAIRT/QNN settings, fallback policy, or ChatScreen behavior.

Default behavior:
  - selects a connected non-emulator device when --device is omitted;
  - launches io.github.ninbyo02.lami/.MainActivity;
  - waits while the operator sends one local GPU prompt in the normal UI;
  - collects logcat, package dump, meminfo, and app debug traces;
  - writes a markdown summary under artifacts/litert_lm_gpu_route_probe/.

This is an observation probe. It does not broadcast the standard hidden NPU
receiver, does not run RunDecode, and does not modify preferred backend.
EOF
      exit 0
      ;;
    *)
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

if ! [[ "$OBSERVE_SECONDS" =~ ^[0-9]+$ ]] || [ "$OBSERVE_SECONDS" -le 0 ]; then
  printf 'ERROR: --seconds must be a positive integer\n' >&2
  exit 2
fi

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() {
  printf '[litert-lm-gpu-route-probe] %s\n' "$*"
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

collect_meminfo() {
  local label="$1"
  adb_cmd shell dumpsys meminfo "$APP_ID" >"$OUT_DIR/meminfo_${label}.txt" 2>&1 || true
  adb_cmd shell pidof "$APP_ID" >"$OUT_DIR/pid_${label}.txt" 2>&1 || true
  adb_cmd shell cat /proc/meminfo >"$OUT_DIR/proc_meminfo_${label}.txt" 2>&1 || true
}

collect_static_source_inventory() {
  {
    printf '# LiteRT-LM GPU Route Source Inventory\n\n'
    printf 'label=%s\n' "$LABEL"
    printf 'timestamp=%s\n' "$TIMESTAMP"
    printf '\n## Backend configuration evidence\n\n'
    rg -n "buildLiteRtEngineConfig|Backend\\.GPU\\(|Backend\\.CPU\\(|fallback-gpu|preferred-backend|NPU_DISABLED|EngineConfig\\(" \
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt \
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt \
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings/PreferredBackendDryRunSetting.kt \
      2>/dev/null || true
    printf '\n## Sampling and token limit evidence\n\n'
    rg -n "maxOutputTokens|maxNumTokens|maxTokens|setMaxOutputTokens|setTopK|setTemperature|SamplerConfig|topP|topK|temperature" \
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt \
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt \
      app/src/main/java/io/github/ninbyo02/lami/ui/screens/settings \
      2>/dev/null || true
  } >"$OUT_DIR/source_inventory.md"
}

write_matrix_template() {
  {
    printf 'case_id\tbackend\tmax_output_tokens\ttemperature\ttop_k\ttop_p\tprompt_template\tstatus\n'
    printf 'gpu_32_default\tGPU\t32\tcurrent\tcurrent\tcurrent\tcurrent-chat-template\tplanned-needs-code-hook\n'
    printf 'gpu_64_default\tGPU\t64\tcurrent\tcurrent\tcurrent\tcurrent-chat-template\tplanned-needs-code-hook\n'
    printf 'gpu_128_default\tGPU\t128\tcurrent\tcurrent\tcurrent\tcurrent-chat-template\tplanned-needs-code-hook\n'
    printf 'gpu_256_default\tGPU\t256\tcurrent\tcurrent\tcurrent\tcurrent-chat-template\tplanned-needs-code-hook\n'
    printf 'gpu_sampling_t0\tGPU\t128\t0.0\t1\tcurrent\tcurrent-chat-template\tplanned-needs-code-hook\n'
    printf 'gpu_sampling_t08\tGPU\t128\t0.8\t10\t0.95\tcurrent-chat-template\tplanned-needs-code-hook\n'
    printf 'gpu_template_raw\tGPU\t128\tcurrent\tcurrent\tcurrent\traw-user-prompt\tplanned-needs-code-hook\n'
    printf 'gpu_template_gemma\tGPU\t128\tcurrent\tcurrent\tcurrent\tgemma-it-like\tplanned-needs-code-hook\n'
  } >"$OUT_DIR/probe_matrix.tsv"
}

summarize_runtime() {
  local logcat_file="$OUT_DIR/logcat_tail.txt"
  local reflection_file="$OUT_DIR/local_reflection_trace.log"
  local summary_file="$OUT_DIR/summary.md"
  {
    printf '# LiteRT-LM GPU Route Probe Summary\n\n'
    printf -- '- label: `%s`\n' "$LABEL"
    printf -- '- app id: `%s`\n' "$APP_ID"
    printf -- '- device: `%s`\n' "$DEVICE_SERIAL"
    printf -- '- observe seconds: `%s`\n' "$OBSERVE_SECONDS"
    printf -- '- safety: standard app observation only; no Backend.NPU, QAIRT/QNN, fallback, hidden receiver, or ChatScreen behavior changes\n\n'
    printf '## Runtime Signals\n\n'
    if [ -s "$reflection_file" ]; then
      printf '### local_reflection_trace excerpts\n\n```text\n'
      rg -n "UPSTREAM|LOCAL_ROUTE_DIAG|preferred-backend|official-direct|held-create|engine-initialize|conversation-create|tokenizer-recount|MeasuredToken|tokensPerSecond|timeToFirstToken|modelInit|fallback|timeout" "$reflection_file" 2>/dev/null | tail -120 || true
      printf '\n```\n\n'
    else
      printf 'No `files/debug/local_reflection_trace.log` was readable. Run one local GPU prompt during the observation window.\n\n'
    fi
    if [ -s "$logcat_file" ]; then
      printf '### logcat excerpts\n\n```text\n'
      rg -n "qairt244_gpu_prefill_preinvoke_v1|ChatScreen|LocalWsTrace|LiteRT|LiteRt|litert|GPU|OpenCL|WebGPU|tokensPerSecond|timeToFirstTokenMs|modelInitMs|prefill|decode|lowmemorykiller|ActivityManager|am_kill|FATAL|SIGABRT|SIGSEGV|QNN|HTP|NPU|RunDecode" "$logcat_file" 2>/dev/null | tail -160 || true
      printf '\n```\n\n'
    fi
    printf '## Artifacts\n\n'
    printf -- '- `source_inventory.md`: static source evidence for backend, token, and sampling paths\n'
    printf -- '- `probe_matrix.tsv`: planned matrix for max_output_tokens, prompt template, and sampling experiments\n'
    printf -- '- `meminfo_before.txt` / `meminfo_after.txt`: process memory snapshots\n'
    printf -- '- `package_dump.txt`: package/native library visibility\n'
    printf -- '- `logcat_tail.txt`: runtime log tail\n'
    printf -- '- `local_reflection_trace.log`: app-side route trace when readable\n'
  } >"$summary_file"
}

if ! command -v adb >/dev/null 2>&1; then
  log "adb not found"
  printf 'adb not found\n' >"$OUT_DIR/error.txt"
  exit 1
fi

if ! choose_device; then
  log "no connected non-emulator device"
  printf 'no connected non-emulator device\n' >"$OUT_DIR/error.txt"
  exit 1
fi

printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"
collect_static_source_inventory
write_matrix_template

adb_cmd shell getprop >"$OUT_DIR/getprop.txt" 2>&1 || true
adb_cmd shell dumpsys package "$APP_ID" >"$OUT_DIR/package_dump.txt" 2>&1 || true
collect_meminfo before

if [ "$CLEAR_LOGCAT" = true ]; then
  adb_cmd logcat -c >"$OUT_DIR/logcat_clear.txt" 2>&1 || true
fi

if [ "$START_APP" = true ]; then
  log "launching $APP_ID/$ACTIVITY"
  adb_cmd shell am start -W -n "$APP_ID/$ACTIVITY" >"$OUT_DIR/am_start.txt" 2>&1 || true
fi

log "observing for $OBSERVE_SECONDS seconds; send a normal local GPU prompt in the app now"
sleep "$OBSERVE_SECONDS"

collect_meminfo after
adb_cmd logcat -d -t 3000 >"$OUT_DIR/logcat_tail.txt" 2>&1 || true
pull_app_file "files/debug/local_reflection_trace.log" "$OUT_DIR/local_reflection_trace.log"
pull_app_file "files/local_reflection_trace.log" "$OUT_DIR/local_reflection_trace_legacy.log"

summarize_runtime

log "wrote $OUT_DIR"
printf '%s\n' "$OUT_DIR"
