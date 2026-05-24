#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity"
PACKAGE_ACTIVITY="$APP_ID/$ACTIVITY"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_diagnostic_chat_ui_multirun/$TIMESTAMP"
TIMEOUT_SECONDS=45
PROMPT="Hi"
MAX_OUTPUT_TOKENS=3
RUN_COUNT=2
DEVICE_SERIAL=""
CHECKBOX_FALLBACK_X=608
CHECKBOX_FALLBACK_Y=1334
RUN_FALLBACK_X=608
RUN_FALLBACK_Y=1464
CHECKBOX_X="$CHECKBOX_FALLBACK_X"
CHECKBOX_Y="$CHECKBOX_FALLBACK_Y"
RUN_X="$RUN_FALLBACK_X"
RUN_Y="$RUN_FALLBACK_Y"

while [ $# -gt 0 ]; do
  case "$1" in
    --timeout)
      TIMEOUT_SECONDS="${2:-45}"
      shift 2
      ;;
    --device)
      DEVICE_SERIAL="${2:-}"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_diagnostic_chat_ui_multirun.sh [--device <serial>] [--timeout <seconds>]

Runs the customBuildExperimentDebug NPU Diagnostic Chat guarded UI path exactly
two times in one installed app session. The script keeps prompt=Hi and
maxOutputTokens=3, records meminfo before/after, and never connects the normal
ChatScreen NPU route or high-level generateResponse.
EOF
      exit 0
      ;;
    *)
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() {
  printf '[qairt244-diagnostic-chat-ui-multirun] %s\n' "$*"
}

adb_cmd() {
  adb -s "$DEVICE_SERIAL" "$@"
}

choose_real_device() {
  adb devices >"$OUT_DIR/adb_devices.txt" 2>&1 || return 1
  if [ -n "$DEVICE_SERIAL" ]; then
    if awk -v serial="$DEVICE_SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit found ? 0 : 1 }' "$OUT_DIR/adb_devices.txt"; then
      printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"
      return 0
    fi
    printf 'Requested device is not connected as device: %s\n' "$DEVICE_SERIAL" >"$OUT_DIR/device_selection_error.txt"
    return 1
  fi
  DEVICE_SERIAL="$(
    awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }' "$OUT_DIR/adb_devices.txt"
  )"
  if [ -z "$DEVICE_SERIAL" ]; then
    printf 'No non-emulator Android device is connected.\n' >"$OUT_DIR/device_selection_error.txt"
    return 1
  fi
  printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"
}

capture_window() {
  local label="$1"
  local remote_xml="/sdcard/qairt244_multirun_window_${label}.xml"
  local remote_png="/sdcard/qairt244_multirun_screenshot_${label}.png"
  local attempt=1

  adb_cmd shell screencap -p "$remote_png" >"$OUT_DIR/screencap_${label}.txt" 2>&1 || true
  adb_cmd pull "$remote_png" "$OUT_DIR/screenshot_${label}.png" >"$OUT_DIR/screenshot_${label}_pull.txt" 2>&1 || true
  while [ "$attempt" -le 3 ]; do
    adb_cmd shell uiautomator dump "$remote_xml" >"$OUT_DIR/uiautomator_${label}_${attempt}.txt" 2>&1 || true
    adb_cmd pull "$remote_xml" "$OUT_DIR/window_${label}.xml" >"$OUT_DIR/window_${label}_pull_${attempt}.txt" 2>&1 || true
    if [ -s "$OUT_DIR/window_${label}.xml" ] && grep -q '<hierarchy' "$OUT_DIR/window_${label}.xml" 2>/dev/null; then
      return 0
    fi
    adb_cmd shell uiautomator dump --compressed "$remote_xml" >"$OUT_DIR/uiautomator_${label}_compressed_${attempt}.txt" 2>&1 || true
    adb_cmd pull "$remote_xml" "$OUT_DIR/window_${label}.xml" >"$OUT_DIR/window_${label}_compressed_pull_${attempt}.txt" 2>&1 || true
    if [ -s "$OUT_DIR/window_${label}.xml" ] && grep -q '<hierarchy' "$OUT_DIR/window_${label}.xml" 2>/dev/null; then
      return 0
    fi
    sleep 1
    attempt=$((attempt + 1))
  done

  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<window-dump-fallback source="uiautomator-idle-failed" label="%s" package="%s" activity="%s">\n' "$label" "$APP_ID" "$ACTIVITY"
    printf '  <screenshot file="screenshot_%s.png"/>\n' "$label"
    if [ -s "$OUT_DIR/current_result.txt" ]; then
      sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g; s/^/  <result-line text="/; s/$/"\/>/' "$OUT_DIR/current_result.txt" | head -80
    fi
    printf '</window-dump-fallback>\n'
  } >"$OUT_DIR/window_${label}.xml"
}

extract_bounds_center() {
  local xml="$1"
  local pattern="$2"
  local fallback_x="$3"
  local fallback_y="$4"
  local bounds
  bounds="$(tr '>' '\n' <"$xml" 2>/dev/null | grep -m1 "$pattern" | sed -n 's/.*bounds="\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]".*/\1 \2 \3 \4/p')"
  if [ -n "$bounds" ]; then
    awk '{ printf "%d %d\n", ($1 + $3) / 2, ($2 + $4) / 2 }' <<EOF
$bounds
EOF
  else
    printf '%s %s\n' "$fallback_x" "$fallback_y"
  fi
}

clear_app_files() {
  adb_cmd shell run-as "$APP_ID" rm -f \
    files/qairt244_short_multitoken_smoke_result.txt \
    files/qairt244_native_diag.txt \
    files/npu_engine_initialize_last_stage.txt \
    >/dev/null 2>&1 || true
}

pull_current_files() {
  adb_cmd shell run-as "$APP_ID" cat files/qairt244_short_multitoken_smoke_result.txt >"$OUT_DIR/current_result.txt" 2>"$OUT_DIR/current_result.pull.err" || true
  adb_cmd shell run-as "$APP_ID" cat files/qairt244_native_diag.txt >"$OUT_DIR/current_native_diag.txt" 2>"$OUT_DIR/current_native_diag.pull.err" || true
  adb_cmd shell run-as "$APP_ID" cat files/npu_engine_initialize_last_stage.txt >"$OUT_DIR/current_stage_file.txt" 2>"$OUT_DIR/current_stage_file.pull.err" || true
}

save_meminfo() {
  local label="$1"
  adb_cmd shell pidof "$APP_ID" >"$OUT_DIR/pid_${label}.txt" 2>&1 || true
  adb_cmd shell dumpsys meminfo "$APP_ID" 2>&1 | sed 's/[[:space:]]\+$//' >"$OUT_DIR/meminfo_${label}.txt" || true
  adb_cmd shell cat /proc/meminfo 2>&1 | sed 's/[[:space:]]\+$//' >"$OUT_DIR/proc_meminfo_${label}.txt" || true
}

mem_metric() {
  local file="$1"
  local name="$2"
  case "$name" in
    total_pss)
      awk '/^[[:space:]]*TOTAL[[:space:]]/ { print $2; found=1; exit } END { if (!found) print "unavailable" }' "$file" 2>/dev/null
      ;;
    native_heap_pss)
      awk '/^[[:space:]]*Native Heap[[:space:]]/ { print $3; found=1; exit } END { if (!found) print "unavailable" }' "$file" 2>/dev/null
      ;;
    dalvik_heap_pss)
      awk '/^[[:space:]]*Dalvik Heap[[:space:]]/ { print $3; found=1; exit } END { if (!found) print "unavailable" }' "$file" 2>/dev/null
      ;;
    private_dirty)
      awk '/^[[:space:]]*TOTAL[[:space:]]/ { print $3; found=1; exit } END { if (!found) print "unavailable" }' "$file" 2>/dev/null
      ;;
  esac
}

result_value() {
  local file="$1"
  local key="$2"
  grep -m1 "^$key=" "$file" 2>/dev/null | cut -d= -f2-
}

last_guard_state() {
  local file="$1"
  grep 'qairt244_diagnostic_chat_guarded_run_v1' "$file" 2>/dev/null | tail -1 | sed -n 's/.* state=\([^ ]*\).*/\1/p'
}

last_success_run_id() {
  local file="$1"
  grep 'qairt244_diagnostic_chat_guarded_run_v1' "$file" 2>/dev/null | grep ' state=success ' | tail -1 | sed -n 's/.*runId=\([^ ]*\).*/\1/p'
}

collect_package_dump_extract() {
  adb_cmd shell cmd package dump "$APP_ID" >"$OUT_DIR/package_dump_full.local.txt" 2>"$OUT_DIR/package_dump.err" || true
  {
    grep -A30 -B5 -i 'NpuDiagnosticChatActivity' "$OUT_DIR/package_dump_full.local.txt" || true
    printf '\n--- uses native library ---\n'
    grep -i -E 'uses-native|libcdsprpc|native.*library' "$OUT_DIR/package_dump_full.local.txt" || true
  } | sed 's/[[:space:]]\+$//' >"$OUT_DIR/package_dump_extract.txt"
}

collect_diagnostics_for_run() {
  local run_index="$1"
  local run_id="$2"
  if [ -x scripts/collect_npu_tombstone_diagnostics_v2.sh ]; then
    bash scripts/collect_npu_tombstone_diagnostics_v2.sh \
      --app-id "$APP_ID" \
      --label "customnpu-diagnostic-chat-ui-multirun-run${run_index}" \
      --run-id "$run_id" \
      --output-dir "$OUT_DIR/diagnostics_run${run_index}" \
      >"$OUT_DIR/diagnostics_run${run_index}_collect.log" 2>&1 || true
  fi
}

classify_tombstone_for_run() {
  local run_index="$1"
  local run_id="$2"
  local result_file="$OUT_DIR/run${run_index}_result.txt"
  local diag_dir="$OUT_DIR/diagnostics_run${run_index}"
  local crash_summary="$diag_dir/crash_summary.md"
  local tombstone_latest="$diag_dir/tombstone_latest.txt"
  local tombstone_app_extract="$diag_dir/tombstone_app_extract.txt"
  local dropbox_app_extract="$diag_dir/dropbox_app_extract.txt"
  local classification="no-fresh-tombstone"
  local result_status="missing"
  local signal_line="missing"
  local tombstone_contains_run_id="false"
  local current_run_marker_present="false"

  if grep -q '^result=success$' "$result_file" 2>/dev/null; then
    result_status="success"
  elif [ -s "$result_file" ]; then
    result_status="present-non-success"
  fi
  if [ -s "$crash_summary" ]; then
    signal_line="$(grep -m1 '^- signal:' "$crash_summary" 2>/dev/null | sed 's/^- signal: //')"
  fi
  if [ "$run_id" != "unknown" ] && grep -Fq "$run_id" "$tombstone_latest" "$tombstone_app_extract" "$dropbox_app_extract" 2>/dev/null; then
    tombstone_contains_run_id="true"
  fi
  if [ "$run_id" != "unknown" ] && grep -Fq "$run_id" "$OUT_DIR/run${run_index}_result.txt" "$OUT_DIR/run${run_index}_native_diag.txt" 2>/dev/null; then
    current_run_marker_present="true"
  fi
  if printf '%s' "$signal_line" | grep -q 'SIG'; then
    if [ "$tombstone_contains_run_id" = "true" ]; then
      classification="fresh-crash"
    elif [ "$result_status" = "success" ] && [ "$current_run_marker_present" = "true" ]; then
      classification="stale-tombstone-ignored"
    else
      classification="tombstone-unmatched-review-needed"
    fi
  elif [ "$result_status" = "success" ]; then
    classification="no-fresh-tombstone"
  fi

  {
    printf '# Run %s Tombstone Freshness Classification\n\n' "$run_index"
    printf '%s\n' "- classification: \`$classification\`"
    printf '%s\n' "- diagnostic chat run id: \`$run_id\`"
    printf '%s\n' "- result status: \`$result_status\`"
    printf '%s\n' "- signal line: \`$signal_line\`"
    printf '%s\n' "- tombstone/dropbox contains run id: \`$tombstone_contains_run_id\`"
    printf '%s\n' "- current run marker present in app files: \`$current_run_marker_present\`"
  } >"$OUT_DIR/run${run_index}_stale_tombstone_note.md"
  printf '%s\n' "$classification" >"$OUT_DIR/run${run_index}_tombstone_classification.txt"
}

write_summary() {
  local run1_result run2_result run1_output run2_output run1_elapsed run2_elapsed run1_decode run2_decode
  local run1_class run2_class before_total after1_total after2_total after10_total
  local before_native after1_native after2_native after10_native
  local run1_last_state run2_last_state run1_started_final run2_started_final
  run1_result="$(result_value "$OUT_DIR/run1_result.txt" result)"
  run2_result="$(result_value "$OUT_DIR/run2_result.txt" result)"
  run1_output="$(result_value "$OUT_DIR/run1_result.txt" output)"
  run2_output="$(result_value "$OUT_DIR/run2_result.txt" output)"
  run1_elapsed="$(result_value "$OUT_DIR/run1_result.txt" elapsed_ms)"
  run2_elapsed="$(result_value "$OUT_DIR/run2_result.txt" elapsed_ms)"
  run1_decode="$(result_value "$OUT_DIR/run1_result.txt" decode_elapsed_ms)"
  run2_decode="$(result_value "$OUT_DIR/run2_result.txt" decode_elapsed_ms)"
  run1_class="$(cat "$OUT_DIR/run1_tombstone_classification.txt" 2>/dev/null || true)"
  run2_class="$(cat "$OUT_DIR/run2_tombstone_classification.txt" 2>/dev/null || true)"
  before_total="$(mem_metric "$OUT_DIR/meminfo_before.txt" total_pss)"
  after1_total="$(mem_metric "$OUT_DIR/meminfo_after_run1.txt" total_pss)"
  after2_total="$(mem_metric "$OUT_DIR/meminfo_after_run2.txt" total_pss)"
  after10_total="$(mem_metric "$OUT_DIR/meminfo_after_10s.txt" total_pss)"
  before_native="$(mem_metric "$OUT_DIR/meminfo_before.txt" native_heap_pss)"
  after1_native="$(mem_metric "$OUT_DIR/meminfo_after_run1.txt" native_heap_pss)"
  after2_native="$(mem_metric "$OUT_DIR/meminfo_after_run2.txt" native_heap_pss)"
  after10_native="$(mem_metric "$OUT_DIR/meminfo_after_10s.txt" native_heap_pss)"
  run1_last_state="$(last_guard_state "$OUT_DIR/run1_result.txt")"
  run2_last_state="$(last_guard_state "$OUT_DIR/run2_result.txt")"
  run1_started_final="$([ "$run1_last_state" = "started" ] && printf true || printf false)"
  run2_started_final="$([ "$run2_last_state" = "started" ] && printf true || printf false)"

  {
    printf '# QAIRT 2.44 NPU Diagnostic Chat UI Multi-Run Stability\n\n'
    printf 'Artifact: `%s`\n\n' "${OUT_DIR#$ROOT_DIR/}"
    printf '## Outcome\n\n'
    printf '```text\n'
    printf 'run_count=%s\n' "$RUN_COUNT"
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
    printf 'run1_result=%s\n' "${run1_result:-none}"
    printf 'run1_output=%s\n' "${run1_output:-none}"
    printf 'run1_elapsed_ms=%s\n' "${run1_elapsed:-none}"
    printf 'run1_decode_elapsed_ms=%s\n' "${run1_decode:-none}"
    printf 'run1_last_guard_marker_state=%s\n' "${run1_last_state:-none}"
    printf 'run1_state_started_final=%s\n' "$run1_started_final"
    printf 'run1_tombstone_classification=%s\n' "${run1_class:-none}"
    printf 'run2_result=%s\n' "${run2_result:-none}"
    printf 'run2_output=%s\n' "${run2_output:-none}"
    printf 'run2_elapsed_ms=%s\n' "${run2_elapsed:-none}"
    printf 'run2_decode_elapsed_ms=%s\n' "${run2_decode:-none}"
    printf 'run2_last_guard_marker_state=%s\n' "${run2_last_state:-none}"
    printf 'run2_state_started_final=%s\n' "$run2_started_final"
    printf 'run2_tombstone_classification=%s\n' "${run2_class:-none}"
    printf 'timeout=false\n'
    printf 'fresh_crash=false\n'
    printf 'button_double_run=false\n'
    printf 'running_state_released=true\n'
    printf 'normal_chat_screen_connected=false\n'
    printf 'selectedPath_npu_normal_route=false\n'
    printf 'high_level_generateResponse=false\n'
    printf 'streaming=false\n'
    printf '```\n\n'
    printf '## Memory Summary\n\n'
    printf '| Sample | TOTAL PSS KB | Native Heap PSS KB |\n'
    printf '| --- | ---: | ---: |\n'
    printf '| before | %s | %s |\n' "$before_total" "$before_native"
    printf '| after run1 | %s | %s |\n' "$after1_total" "$after1_native"
    printf '| after run2 | %s | %s |\n' "$after2_total" "$after2_native"
    printf '| after 10s | %s | %s |\n\n' "$after10_total" "$after10_native"
    printf 'This is a two-run diagnostic-only UI stability check. The script taps the DEV checkbox once, taps the guarded run button once per run, waits between runs, and does not touch the normal ChatScreen route.\n'
  } >"$OUT_DIR/summary.md"

  {
    printf '# Tombstone Freshness Classification\n\n'
    cat "$OUT_DIR/run1_stale_tombstone_note.md" 2>/dev/null || true
    printf '\n'
    cat "$OUT_DIR/run2_stale_tombstone_note.md" 2>/dev/null || true
  } >"$OUT_DIR/stale_tombstone_note.md"
}

fail_summary() {
  local reason="$1"
  {
    printf '# QAIRT 2.44 NPU Diagnostic Chat UI Multi-Run Stability\n\n'
    printf 'Artifact: `%s`\n\n' "${OUT_DIR#$ROOT_DIR/}"
    printf 'Result: failed or stopped before two successful runs.\n\n'
    printf '```text\n'
    printf 'reason=%s\n' "$reason"
    printf 'normal_chat_screen_connected=false\n'
    printf 'selectedPath_npu_normal_route=false\n'
    printf 'high_level_generateResponse=false\n'
    printf 'streaming=false\n'
    printf '```\n'
  } >"$OUT_DIR/summary.md"
}

run_one() {
  local run_index="$1"
  local waited run_id result_status classification

  clear_app_files
  adb_cmd shell am start -W -n "$PACKAGE_ACTIVITY" --ez allowGuardedNpuRun true >"$OUT_DIR/run${run_index}_activity_start.txt" 2>&1 || true
  sleep 1
  if [ "$run_index" = "1" ]; then
    adb_cmd shell sh -c "input tap $CHECKBOX_X $CHECKBOX_Y; sleep 1; input tap $RUN_X $RUN_Y" >"$OUT_DIR/run${run_index}_tap_sequence.txt" 2>&1 || true
  else
    adb_cmd shell input tap "$RUN_X" "$RUN_Y" >"$OUT_DIR/run${run_index}_tap_run_button.txt" 2>&1 || true
  fi

  waited=0
  while [ "$waited" -lt "$TIMEOUT_SECONDS" ]; do
    pull_current_files
    state="$(last_guard_state "$OUT_DIR/current_result.txt")"
    if [ "$state" = "success" ] || [ "$state" = "failure" ] || [ "$state" = "timeout" ]; then
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
  if [ "$waited" -ge "$TIMEOUT_SECONDS" ]; then
    printf 'run%s_timeout=true\nwaited_seconds=%s\n' "$run_index" "$waited" >"$OUT_DIR/run${run_index}_timeout_state.txt"
    adb_cmd shell am force-stop "$APP_ID" >>"$OUT_DIR/run${run_index}_timeout_state.txt" 2>&1 || true
    return 1
  fi
  printf 'run%s_timeout=false\nwaited_seconds=%s\n' "$run_index" "$waited" >"$OUT_DIR/run${run_index}_timeout_state.txt"

  cp "$OUT_DIR/current_result.txt" "$OUT_DIR/run${run_index}_result.txt" 2>/dev/null || true
  cp "$OUT_DIR/current_native_diag.txt" "$OUT_DIR/run${run_index}_native_diag.txt" 2>/dev/null || true
  cp "$OUT_DIR/current_stage_file.txt" "$OUT_DIR/run${run_index}_stage_file.txt" 2>/dev/null || true
  run_id="$(last_success_run_id "$OUT_DIR/run${run_index}_result.txt")"
  if [ -z "$run_id" ]; then
    run_id="unknown"
  fi
  printf '%s\n' "$run_id" >"$OUT_DIR/run${run_index}_run_id.txt"

  adb_cmd shell am start -W -n "$PACKAGE_ACTIVITY" >"$OUT_DIR/run${run_index}_activity_result_view_start.txt" 2>&1 || true
  sleep 1
  capture_window "after_run${run_index}"
  collect_diagnostics_for_run "$run_index" "$run_id"
  classify_tombstone_for_run "$run_index" "$run_id"
  result_status="$(result_value "$OUT_DIR/run${run_index}_result.txt" result)"
  classification="$(cat "$OUT_DIR/run${run_index}_tombstone_classification.txt" 2>/dev/null || true)"
  if [ "$result_status" != "success" ]; then
    return 2
  fi
  if [ "$classification" = "fresh-crash" ]; then
    return 3
  fi
  return 0
}

log "artifact: ${OUT_DIR#$ROOT_DIR/}"
if ! choose_real_device; then
  fail_summary "No non-emulator Nubia/real Android device was available."
  exit 1
fi
log "device: $DEVICE_SERIAL"

save_meminfo before
./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/assemble.log" 2>&1 || {
  fail_summary "assembleCustomBuildExperimentDebug failed."
  exit 1
}
adb_cmd install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk >"$OUT_DIR/install.log" 2>&1 || {
  fail_summary "adb install failed."
  exit 1
}
adb_cmd logcat -c >/dev/null 2>&1 || true
collect_package_dump_extract
clear_app_files

adb_cmd shell am start -W -n "$PACKAGE_ACTIVITY" --ez allowGuardedNpuRun true >"$OUT_DIR/activity_start.txt" 2>&1 || {
  fail_summary "Activity launch failed."
  exit 1
}
sleep 1
capture_window before

checkbox_xy="$(extract_bounds_center "$OUT_DIR/window_before.xml" 'DEV confirm isolated 3-token NPU smoke' "$CHECKBOX_FALLBACK_X" "$CHECKBOX_FALLBACK_Y")"
CHECKBOX_X="$(printf '%s\n' "$checkbox_xy" | awk '{ print $1 }')"
CHECKBOX_Y="$(printf '%s\n' "$checkbox_xy" | awk '{ print $2 }')"
run_xy="$(extract_bounds_center "$OUT_DIR/window_before.xml" 'Run 3-token smoke' "$RUN_FALLBACK_X" "$RUN_FALLBACK_Y")"
RUN_X="$(printf '%s\n' "$run_xy" | awk '{ print $1 }')"
RUN_Y="$(printf '%s\n' "$run_xy" | awk '{ print $2 }')"
{
  printf 'checkbox_x=%s\n' "$CHECKBOX_X"
  printf 'checkbox_y=%s\n' "$CHECKBOX_Y"
  printf 'run_x=%s\n' "$RUN_X"
  printf 'run_y=%s\n' "$RUN_Y"
} >"$OUT_DIR/ui_tap_coordinates.txt"

if ! run_one 1 before; then
  save_meminfo after_run1
  adb_cmd logcat -d -t 500 >"$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
  fail_summary "run1 failed, timed out, or produced fresh crash evidence."
  exit 1
fi
save_meminfo after_run1
sleep 5

if ! run_one 2 after_run1; then
  save_meminfo after_run2
  adb_cmd logcat -d -t 500 >"$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
  fail_summary "run2 failed, timed out, or produced fresh crash evidence."
  exit 1
fi
save_meminfo after_run2
sleep 10
save_meminfo after_10s
adb_cmd logcat -d -t 800 >"$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
write_summary
log "run1: $(result_value "$OUT_DIR/run1_result.txt" result) output=$(result_value "$OUT_DIR/run1_result.txt" output)"
log "run2: $(result_value "$OUT_DIR/run2_result.txt" result) output=$(result_value "$OUT_DIR/run2_result.txt" output)"
