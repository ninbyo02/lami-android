#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity"
PACKAGE_ACTIVITY="$APP_ID/$ACTIVITY"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_diagnostic_chat_ui_smoke/$TIMESTAMP"
TIMEOUT_SECONDS=45
PROMPT="Hi"
MAX_OUTPUT_TOKENS=3
CHECKBOX_FALLBACK_X=608
CHECKBOX_FALLBACK_Y=1334
RUN_FALLBACK_X=608
RUN_FALLBACK_Y=1464
DEVICE_SERIAL=""
RUN_ID="unknown"
TIMEOUT_OCCURRED=false
UI_OPERATION_OK=false

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
  scripts/run_qairt244_npu_diagnostic_chat_ui_smoke.sh [--device <serial>] [--timeout <seconds>]

Builds and installs customBuildExperimentDebug, launches the custom-only
NPU Diagnostic Chat Activity, checks the DEV confirmation, taps
Run 3-token smoke exactly once, then collects result/native diag/logcat/
tombstone classification/screenshot artifacts.

This script does not connect the normal ChatScreen NPU route, does not set
selectedPath=npu in the normal route, and does not call high-level
generateResponse.
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
  printf '[qairt244-diagnostic-chat-ui-smoke] %s\n' "$*"
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
  local remote_xml="/sdcard/qairt244_window_${label}.xml"
  local remote_png="/sdcard/qairt244_screenshot_${label}.png"
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
    if [ -s "$OUT_DIR/result.txt" ]; then
      sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g; s/^/  <result-line text="/; s/$/"\/>/' "$OUT_DIR/result.txt" | head -80
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

collect_app_files() {
  adb_cmd shell run-as "$APP_ID" cat files/qairt244_short_multitoken_smoke_result.txt >"$OUT_DIR/result.txt" 2>"$OUT_DIR/result.pull.err" || true
  adb_cmd shell run-as "$APP_ID" cat files/qairt244_native_diag.txt >"$OUT_DIR/native_diag.txt" 2>"$OUT_DIR/native_diag.pull.err" || true
  adb_cmd shell run-as "$APP_ID" cat files/npu_engine_initialize_last_stage.txt >"$OUT_DIR/stage_file.txt" 2>"$OUT_DIR/stage_file.pull.err" || true
}

collect_package_dump_extract() {
  adb_cmd shell cmd package dump "$APP_ID" >"$OUT_DIR/package_dump_full.local.txt" 2>"$OUT_DIR/package_dump.err" || true
  {
    grep -A30 -B5 -i 'NpuDiagnosticChatActivity' "$OUT_DIR/package_dump_full.local.txt" || true
    printf '\n--- uses native library ---\n'
    grep -i -E 'uses-native|libcdsprpc|native.*library' "$OUT_DIR/package_dump_full.local.txt" || true
  } | sed 's/[[:space:]]\+$//' >"$OUT_DIR/package_dump_extract.txt"
}

collect_tombstone_diagnostics() {
  if [ -x scripts/collect_npu_tombstone_diagnostics_v2.sh ]; then
    bash scripts/collect_npu_tombstone_diagnostics_v2.sh \
      --app-id "$APP_ID" \
      --label customnpu-diagnostic-chat-ui-script \
      --run-id "$RUN_ID" \
      --output-dir "$OUT_DIR/diagnostics" \
      >"$OUT_DIR/diagnostics_collect.log" 2>&1 || true
  fi
}

classify_tombstone_freshness() {
  local result_file="$OUT_DIR/result.txt"
  local crash_summary="$OUT_DIR/diagnostics/crash_summary.md"
  local tombstone_latest="$OUT_DIR/diagnostics/tombstone_latest.txt"
  local tombstone_app_extract="$OUT_DIR/diagnostics/tombstone_app_extract.txt"
  local dropbox_app_extract="$OUT_DIR/diagnostics/dropbox_app_extract.txt"
  local stage_file="$OUT_DIR/diagnostics/stage_file.txt"
  local classification="no-fresh-tombstone"
  local result_status="missing"
  local signal_line="missing"
  local tombstone_path="missing"
  local tombstone_contains_run_id="false"
  local current_run_marker_present="false"
  local process_line=""
  local process_alive="false"

  if grep -q '^result=success$' "$result_file" 2>/dev/null; then
    result_status="success"
  elif [ -s "$result_file" ]; then
    result_status="present-non-success"
  fi
  if [ -s "$crash_summary" ]; then
    signal_line="$(grep -m1 '^- signal:' "$crash_summary" 2>/dev/null | sed 's/^- signal: //')"
  fi
  if [ -s "$OUT_DIR/diagnostics/tombstone_path.txt" ]; then
    tombstone_path="$(tr -d '\r' <"$OUT_DIR/diagnostics/tombstone_path.txt")"
  fi
  if [ "$RUN_ID" != "unknown" ] && grep -Fq "$RUN_ID" "$tombstone_latest" "$tombstone_app_extract" "$dropbox_app_extract" 2>/dev/null; then
    tombstone_contains_run_id="true"
  fi
  if [ "$RUN_ID" != "unknown" ] && grep -Fq "$RUN_ID" "$stage_file" "$OUT_DIR/stage_file.txt" "$OUT_DIR/native_diag.txt" "$OUT_DIR/result.txt" 2>/dev/null; then
    current_run_marker_present="true"
  fi
  process_line="$(adb_cmd shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$process_line" ]; then
    process_alive="true"
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
    printf '# Tombstone Freshness Classification\n\n'
    printf '%s\n' "- classification: \`$classification\`"
    printf '%s\n' "- diagnostic chat run id: \`$RUN_ID\`"
    printf '%s\n' "- result status: \`$result_status\`"
    printf '%s\n' "- selected tombstone path: \`$tombstone_path\`"
    printf '%s\n' "- signal line: \`$signal_line\`"
    printf '%s\n' "- tombstone/dropbox contains current run id: \`$tombstone_contains_run_id\`"
    printf '%s\n' "- current run marker present in app files: \`$current_run_marker_present\`"
    printf '%s\n' "- process alive after UI smoke: \`$process_alive\`"
    printf '%s\n\n' "- process pid: \`${process_line:-missing}\`"
    if [ "$classification" = "stale-tombstone-ignored" ]; then
      printf 'The collector selected an older tombstone/dropbox body that does not contain the current Diagnostic Chat run id. Because the UI smoke result is success and current-run markers are present in app-private files, this tombstone is ignored for the run outcome.\n'
    elif [ "$classification" = "fresh-crash" ]; then
      printf 'The selected tombstone/dropbox body contains the current Diagnostic Chat run id and is classified as a fresh crash.\n'
    else
      printf 'No fresh crash evidence was found for this Diagnostic Chat UI smoke run.\n'
    fi
  } >"$OUT_DIR/stale_tombstone_note.md"
  printf '%s\n' "$classification" >"$OUT_DIR/tombstone_classification.txt"
}

write_summary() {
  local result_status output_value elapsed_ms decode_elapsed_ms max_tokens backend backend_evidence tombstone_classification timeout_value
  result_status="$(grep -m1 '^result=' "$OUT_DIR/result.txt" 2>/dev/null | cut -d= -f2-)"
  output_value="$(grep -m1 '^output=' "$OUT_DIR/result.txt" 2>/dev/null | cut -d= -f2-)"
  elapsed_ms="$(grep -m1 '^elapsed_ms=' "$OUT_DIR/result.txt" 2>/dev/null | cut -d= -f2-)"
  decode_elapsed_ms="$(grep -m1 '^decode_elapsed_ms=' "$OUT_DIR/result.txt" 2>/dev/null | cut -d= -f2-)"
  max_tokens="$(grep -m1 '^max_output_tokens=' "$OUT_DIR/result.txt" 2>/dev/null | cut -d= -f2-)"
  backend="$(grep -m1 '^npu_backend=' "$OUT_DIR/result.txt" 2>/dev/null | cut -d= -f2-)"
  backend_evidence="$(grep -m1 '^npu_backend_evidence=' "$OUT_DIR/result.txt" 2>/dev/null | cut -d= -f2-)"
  tombstone_classification="$(cat "$OUT_DIR/tombstone_classification.txt" 2>/dev/null || true)"
  timeout_value="$(cat "$OUT_DIR/timeout_state.txt" 2>/dev/null | grep -m1 '^timeout=' | cut -d= -f2-)"

  {
    printf '# QAIRT 2.44 NPU Diagnostic Chat UI Smoke\n\n'
    printf 'Artifact: `%s`\n\n' "${OUT_DIR#$ROOT_DIR/}"
    printf '## Outcome\n\n'
    printf '```text\n'
    printf 'result=%s\n' "${result_status:-none}"
    printf 'output=%s\n' "${output_value:-none}"
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=%s\n' "${max_tokens:-none}"
    printf 'elapsed_ms=%s\n' "${elapsed_ms:-none}"
    printf 'decode_elapsed_ms=%s\n' "${decode_elapsed_ms:-none}"
    printf 'npu_backend=%s\n' "${backend:-none}"
    printf 'npu_backend_evidence=%s\n' "${backend_evidence:-none}"
    printf 'timeout=%s\n' "${timeout_value:-none}"
    printf 'tombstone_classification=%s\n' "${tombstone_classification:-none}"
    printf 'run_id=%s\n' "$RUN_ID"
    printf 'ui_dev_checkbox_taps=1\n'
    printf 'ui_run_button_taps=1\n'
    printf 'ui_operation_ok=%s\n' "$UI_OPERATION_OK"
    printf 'normal_chat_screen_connected=false\n'
    printf 'selectedPath_npu_normal_route=false\n'
    printf 'high_level_generateResponse=false\n'
    printf 'streaming=false\n'
    printf '```\n\n'
    printf '## Artifacts\n\n'
    printf -- '- `screenshot_before.png`\n'
    printf -- '- `screenshot_after.png`\n'
    printf -- '- `window_before.xml`\n'
    printf -- '- `window_after.xml`\n'
    printf -- '- `result.txt`\n'
    printf -- '- `native_diag.txt`\n'
    printf -- '- `logcat_tail.txt`\n'
    printf -- '- `stale_tombstone_note.md`\n'
    printf -- '- `package_dump_extract.txt`\n\n'
    printf 'The script launches only `%s`, checks the DEV confirmation once, taps the guarded run button once, and never connects the normal ChatScreen NPU route.\n' "$ACTIVITY"
  } >"$OUT_DIR/summary.md"
}

write_failure_summary() {
  local reason="$1"
  printf 'timeout=true\nreason=%s\n' "$reason" >"$OUT_DIR/timeout_state.txt"
  cat >"$OUT_DIR/summary.md" <<EOF
# QAIRT 2.44 NPU Diagnostic Chat UI Smoke

Artifact: \`${OUT_DIR#$ROOT_DIR/}\`

Result: not completed.

Reason:

\`\`\`text
$reason
\`\`\`

Safety:

\`\`\`text
normal_chat_screen_connected=false
selectedPath_npu_normal_route=false
high_level_generateResponse=false
streaming=false
\`\`\`
EOF
}

log "artifact: ${OUT_DIR#$ROOT_DIR/}"
if ! choose_real_device; then
  write_failure_summary "No non-emulator Nubia/real Android device was available."
  log "blocked: no real device"
  exit 1
fi
log "device: $DEVICE_SERIAL"

./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/assemble.log" 2>&1 || {
  write_failure_summary "assembleCustomBuildExperimentDebug failed; see assemble.log."
  exit 1
}

adb_cmd install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk >"$OUT_DIR/install.log" 2>&1 || {
  write_failure_summary "adb install failed; see install.log."
  exit 1
}

adb_cmd shell run-as "$APP_ID" rm -f \
  files/qairt244_short_multitoken_smoke_result.txt \
  files/qairt244_native_diag.txt \
  files/npu_engine_initialize_last_stage.txt \
  >"$OUT_DIR/cleanup_before.txt" 2>&1 || true
adb_cmd logcat -c >/dev/null 2>&1 || true

collect_package_dump_extract
adb_cmd shell am start -W -n "$PACKAGE_ACTIVITY" --ez allowGuardedNpuRun true >"$OUT_DIR/activity_start.txt" 2>&1 || {
  write_failure_summary "Activity launch failed; see activity_start.txt."
  exit 1
}
sleep 1
capture_window before

checkbox_xy="$(extract_bounds_center "$OUT_DIR/window_before.xml" 'DEV confirm isolated 3-token NPU smoke' "$CHECKBOX_FALLBACK_X" "$CHECKBOX_FALLBACK_Y")"
checkbox_x="$(printf '%s\n' "$checkbox_xy" | awk '{ print $1 }')"
checkbox_y="$(printf '%s\n' "$checkbox_xy" | awk '{ print $2 }')"
adb_cmd shell input tap "$checkbox_x" "$checkbox_y" >"$OUT_DIR/tap_checkbox.txt" 2>&1 || true
sleep 1

capture_window armed
run_xy="$(extract_bounds_center "$OUT_DIR/window_armed.xml" 'Run 3-token smoke' "$RUN_FALLBACK_X" "$RUN_FALLBACK_Y")"
run_x="$(printf '%s\n' "$run_xy" | awk '{ print $1 }')"
run_y="$(printf '%s\n' "$run_xy" | awk '{ print $2 }')"
adb_cmd shell input tap "$run_x" "$run_y" >"$OUT_DIR/tap_run_button.txt" 2>&1 || true
UI_OPERATION_OK=true

waited=0
while [ "$waited" -lt "$TIMEOUT_SECONDS" ]; do
  collect_app_files
  if grep -Eq 'qairt244_diagnostic_chat_guarded_run_v1 .* state=(success|failure|timeout)' "$OUT_DIR/result.txt" 2>/dev/null; then
    break
  fi
  sleep 1
  waited=$((waited + 1))
done

if [ "$waited" -ge "$TIMEOUT_SECONDS" ]; then
  TIMEOUT_OCCURRED=true
  printf 'timeout=true\nwaited_seconds=%s\n' "$waited" >"$OUT_DIR/timeout_state.txt"
  adb_cmd shell am force-stop "$APP_ID" >>"$OUT_DIR/timeout_state.txt" 2>&1 || true
else
  printf 'timeout=false\nwaited_seconds=%s\n' "$waited" >"$OUT_DIR/timeout_state.txt"
fi

collect_app_files
RUN_ID="$(sed -n 's/.*runId=\([^ ]*\).*/\1/p' "$OUT_DIR/result.txt" 2>/dev/null | tail -1)"
if [ -z "$RUN_ID" ]; then
  RUN_ID="unknown"
fi
printf '%s\n' "$RUN_ID" >"$OUT_DIR/run_id.txt"

adb_cmd shell am start -W -n "$PACKAGE_ACTIVITY" >"$OUT_DIR/activity_result_view_start.txt" 2>&1 || true
sleep 1
capture_window after
adb_cmd logcat -d -t 500 >"$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
collect_tombstone_diagnostics
classify_tombstone_freshness
write_summary

if [ "$TIMEOUT_OCCURRED" = true ]; then
  log "timeout after ${TIMEOUT_SECONDS}s"
else
  log "completed after ${waited}s"
fi
log "result: $(grep -m1 '^result=' "$OUT_DIR/result.txt" 2>/dev/null | cut -d= -f2- || true)"
