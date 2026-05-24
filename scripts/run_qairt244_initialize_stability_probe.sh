#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACT_DIR="${1:-artifacts/qairt244_htp_log_callback_aligned_build/20260522_224734}"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity"
DEFAULT_MODEL_BASENAME="gemma-4-E2B-it_qualcomm_sm8750.litertlm"
MODEL_PATH="/data/user/0/$APP_ID/files/local_models/$DEFAULT_MODEL_BASENAME"
RUNS=2
SNAPSHOT_FILE="files/npu_experiment_probe.txt"
DRY_RUN_FILE="files/npu_engine_initialize_dry_run.txt"
CRASH_MARKER_FILE="files/npu_engine_initialize_crash_marker.txt"
LAST_STAGE_FILE="files/npu_engine_initialize_last_stage.txt"
NATIVE_DIAG_FILE="files/qairt244_native_diag.txt"
LINKER_DEBUG_PROP="debug.ld.app.$APP_ID"
LINKER_DEBUG_VALUE="dlerror,dlopen,dlsym"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_initialize_stability/$TIMESTAMP"
CUSTOM_APK="$ROOT_DIR/app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk"

shift $(( $# > 0 ? 1 : 0 ))
while [ "$#" -gt 0 ]; do
  case "$1" in
    --model-path)
      MODEL_PATH="${2:-}"
      shift 2
      ;;
    --runs)
      RUNS="${2:-}"
      shift 2
      ;;
    *)
      echo "[qairt244-init-stability] unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1

log() {
  printf '[qairt244-init-stability] %s\n' "$*"
}

clear_linker_debug_prop() {
  adb shell "setprop '$LINKER_DEBUG_PROP' ''" >/dev/null 2>&1 || true
}

pull_app_file() {
  local remote="$1"
  local local_path="$2"
  if adb exec-out run-as "$APP_ID" cat "$remote" >"$local_path" 2>/dev/null; then
    if [ -s "$local_path" ]; then
      return 0
    fi
  fi
  printf '<missing>\n' >"$local_path"
  return 1
}

if [ "$RUNS" -lt 1 ] || [ "$RUNS" -gt 2 ]; then
  log "ERROR: --runs must be 1 or 2 for this safety probe."
  exit 2
fi

if ! command -v adb >/dev/null 2>&1; then
  log "ERROR: adb not found."
  exit 3
fi

DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -lt 1 ]; then
  log "ERROR: no adb device connected."
  exit 4
fi

mkdir -p "$OUT_DIR"
trap clear_linker_debug_prop EXIT

log "staging custom native stack from $ARTIFACT_DIR"
bash scripts/stage_litert_custom_build_stack_for_experiment.sh "$ARTIFACT_DIR" >"$OUT_DIR/stage_stack_stdout.txt" 2>&1 || {
  cat "$OUT_DIR/stage_stack_stdout.txt"
  exit 5
}

log "assembling customBuildExperimentDebug"
./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/assemble_stdout.txt" 2>&1 || {
  cat "$OUT_DIR/assemble_stdout.txt"
  exit 6
}

log "capturing package metadata"
unzip -l "$CUSTOM_APK" >"$OUT_DIR/apk_zip_listing.txt" 2>/dev/null || true
if [ -x /home/sato/Android/Sdk/build-tools/36.1.0/aapt ]; then
  /home/sato/Android/Sdk/build-tools/36.1.0/aapt dump xmltree "$CUSTOM_APK" AndroidManifest.xml >"$OUT_DIR/apk_manifest_xmltree.txt" 2>/dev/null || true
  /home/sato/Android/Sdk/build-tools/36.1.0/aapt dump badging "$CUSTOM_APK" >"$OUT_DIR/apk_badging.txt" 2>/dev/null || true
fi

log "installing customBuildExperimentDebug once"
./gradlew :app:installCustomBuildExperimentDebug >"$OUT_DIR/install_stdout.txt" 2>&1 || {
  cat "$OUT_DIR/install_stdout.txt"
  exit 7
}
adb shell dumpsys package "$APP_ID" >"$OUT_DIR/dumpsys_package.txt" 2>/dev/null || true

printf 'run_index\trun_id\tinitialize_returned\tinitialize_success\tclose_returned\tcrash_suspected\tprocess_alive_after_close\tcheck_runtime_ok\tqnn_backend_ok\n' >"$OUT_DIR/runs.tsv"

for run_index in $(seq 1 "$RUNS"); do
  RUN_ID="$(date +%s%3N 2>/dev/null || date +%s)_$run_index"
  RUN_DIR="$OUT_DIR/run_$run_index"
  mkdir -p "$RUN_DIR"

  log "run $run_index/$RUNS initialize-only dry-run runId=$RUN_ID"
  adb shell run-as "$APP_ID" rm -f "$SNAPSHOT_FILE" "$DRY_RUN_FILE" "$CRASH_MARKER_FILE" "$LAST_STAGE_FILE" "$NATIVE_DIAG_FILE" >/dev/null 2>&1 || true
  adb logcat -c >/dev/null 2>&1 || true
  adb shell setprop "$LINKER_DEBUG_PROP" "$LINKER_DEBUG_VALUE" >/dev/null 2>&1 || true
  adb shell getprop "$LINKER_DEBUG_PROP" >"$RUN_DIR/linker_debug_property_before.txt" 2>/dev/null || true

  adb shell am start -W -n "$APP_ID/$ACTIVITY" \
    --ez run_engine_initialize_dry_run true \
    --ez diagnostic_files_cleared_before_run true \
    --es run_id "$RUN_ID" \
    --es model_path "$MODEL_PATH" >"$RUN_DIR/am_start.txt" 2>&1 || true

  sleep 3

  pull_app_file "$SNAPSHOT_FILE" "$RUN_DIR/probe_snapshot.txt" || true
  pull_app_file "$DRY_RUN_FILE" "$RUN_DIR/stage_file.txt" || true
  pull_app_file "$NATIVE_DIAG_FILE" "$RUN_DIR/qairt244_native_diag.txt" || true
  pull_app_file "$CRASH_MARKER_FILE" "$RUN_DIR/crash_marker.txt" || true
  pull_app_file "$LAST_STAGE_FILE" "$RUN_DIR/last_stage.txt" || true

  adb logcat -b all -d -t 5000 >"$RUN_DIR/logcat_tail.txt" 2>/dev/null || true
  PID="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
  printf '%s\n' "${PID:-<not-running>}" >"$RUN_DIR/pid_after_close.txt"
  if [ -n "$PID" ]; then
    adb shell cat "/proc/$PID/maps" >"$RUN_DIR/proc_maps_after_close.txt" 2>/dev/null || true
  else
    printf '<not-running>\n' >"$RUN_DIR/proc_maps_after_close.txt"
  fi

  initialize_returned=false
  initialize_success=false
  close_returned=false
  crash_marker=false
  process_alive=false
  check_runtime_ok=false
  qnn_backend_ok=false

  grep -q 'Engine.initialize returned' "$RUN_DIR/stage_file.txt" && initialize_returned=true
  grep -q 'initialize result=success' "$RUN_DIR/probe_snapshot.txt" && initialize_success=true
  grep -q 'Engine.close returned' "$RUN_DIR/stage_file.txt" && close_returned=true
  if grep -q 'completed=false' "$RUN_DIR/crash_marker.txt" && ! grep -q 'completed=true' "$RUN_DIR/crash_marker.txt"; then
    crash_marker=true
  fi
  [ -n "$PID" ] && process_alive=true
  grep -q 'LiteRtDispatchCheckRuntimeCompatibility status=kLiteRtStatusOk(0)' "$RUN_DIR/qairt244_native_diag.txt" && check_runtime_ok=true
  if grep -Eq 'QnnDevice_create done\. device = .*status 0x0' "$RUN_DIR/qairt244_native_diag.txt" || [ "$check_runtime_ok" = "true" ]; then
    qnn_backend_ok=true
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$run_index" \
    "$RUN_ID" \
    "$initialize_returned" \
    "$initialize_success" \
    "$close_returned" \
    "$crash_marker" \
    "$process_alive" \
    "$check_runtime_ok" \
    "$qnn_backend_ok" >>"$OUT_DIR/runs.tsv"
done

clear_linker_debug_prop
adb shell getprop "$LINKER_DEBUG_PROP" >"$OUT_DIR/linker_debug_property_after.txt" 2>/dev/null || true

SUCCESS_COUNT="$(awk -F '\t' 'NR > 1 && $3 == "true" && $4 == "true" { count++ } END { print count + 0 }' "$OUT_DIR/runs.tsv")"
CLOSE_COUNT="$(awk -F '\t' 'NR > 1 && $5 == "true" { count++ } END { print count + 0 }' "$OUT_DIR/runs.tsv")"
CRASH_COUNT="$(awk -F '\t' 'NR > 1 && $6 == "true" { count++ } END { print count + 0 }' "$OUT_DIR/runs.tsv")"

{
  printf '# QAIRT 2.44 Initialize Stability Probe\n\n'
  printf '%s\n' "- Artifact dir: \`$OUT_DIR\`"
  printf '%s\n' "- Stack artifact: \`$ARTIFACT_DIR\`"
  printf '%s\n' "- Runs requested: \`$RUNS\`"
  printf '%s\n' "- Initialize successes: \`$SUCCESS_COUNT/$RUNS\`"
  printf '%s\n' "- Close successes: \`$CLOSE_COUNT/$RUNS\`"
  printf '%s\n' "- Crash markers: \`$CRASH_COUNT\`"
  printf '%s\n' "- Install count: \`1\`"
  printf '%s\n' "- Conversation/Session/generateResponse: \`not run\`"
  printf '\n## Runs\n\n'
  printf '| Run | Run ID | Initialize returned | Initialize success | Close returned | Crash suspected | Process alive after close | Compatibility OK | QNN backend OK |\n'
  printf '| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n'
  awk -F '\t' 'NR > 1 {printf "| `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9}' "$OUT_DIR/runs.tsv"
} >"$OUT_DIR/summary.md"

cat "$OUT_DIR/summary.md"

if [ "$SUCCESS_COUNT" -ne "$RUNS" ] || [ "$CLOSE_COUNT" -ne "$RUNS" ] || [ "$CRASH_COUNT" -ne 0 ]; then
  log "ERROR: stability probe did not meet success criteria."
  exit 8
fi

log "done; initialize-only stability probe passed"
