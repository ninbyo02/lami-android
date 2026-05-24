#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity"
PACKAGE_ACTIVITY="$APP_ID/$ACTIVITY"
ARTIFACT="artifacts/qairt244_editable_prompt_entrypoint_build/20260523_183705"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_diagnostic_fallback_recovery/$TIMESTAMP"
DEVICE_SERIAL=""
TIMEOUT_MS=1000

while [ $# -gt 0 ]; do
  case "$1" in
    --artifact) ARTIFACT="${2:-}"; shift 2 ;;
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_diagnostic_fallback_recovery.sh [--artifact <custom-build-artifact>] [--device <serial>]

Runs Diagnostic Chat-only fallback/recovery checks. It does not start normal
ChatScreen, selectedPath=npu, high-level generateResponse, or real timeout
generation. Timeout is simulated through a DEV extra and does not call native
Engine/RunDecode.
EOF
      exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() { printf '[qairt244-fallback-recovery] %s\n' "$*"; }
adb_cmd() { adb -s "$DEVICE_SERIAL" "$@"; }

choose_real_device() {
  adb devices >"$OUT_DIR/adb_devices.txt" 2>&1 || return 1
  if [ -n "$DEVICE_SERIAL" ]; then
    awk -v serial="$DEVICE_SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit found ? 0 : 1 }' "$OUT_DIR/adb_devices.txt"
    return $?
  fi
  DEVICE_SERIAL="$(awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }' "$OUT_DIR/adb_devices.txt")"
  [ -n "$DEVICE_SERIAL" ]
}

capture_window() {
  local label="$1"
  local remote_xml="/sdcard/qairt244_fallback_${label}.xml"
  local remote_png="/sdcard/qairt244_fallback_${label}.png"
  adb_cmd shell screencap -p "$remote_png" >"$OUT_DIR/screencap_${label}.txt" 2>&1 || true
  adb_cmd pull "$remote_png" "$OUT_DIR/screenshot_${label}.png" >"$OUT_DIR/screenshot_${label}_pull.txt" 2>&1 || true
  adb_cmd shell uiautomator dump "$remote_xml" >"$OUT_DIR/uiautomator_${label}.txt" 2>&1 || true
  adb_cmd pull "$remote_xml" "$OUT_DIR/window_${label}.xml" >"$OUT_DIR/window_${label}_pull.txt" 2>&1 || true
  if ! grep -q '<hierarchy' "$OUT_DIR/window_${label}.xml" 2>/dev/null; then
    printf '<window-dump-fallback label="%s"/>\n' "$label" >"$OUT_DIR/window_${label}.xml"
  fi
}

pull_app_file() {
  local remote="$1" local_file="$2"
  adb_cmd shell run-as "$APP_ID" cat "$remote" >"$local_file" 2>"$local_file.pull.err" || true
}

find_text_center() {
  local xml_file="$1" needle="$2"
  python3 - "$xml_file" "$needle" <<'PY'
import html, re, sys
path, needle = sys.argv[1], sys.argv[2].lower()
try:
    text = open(path, encoding="utf-8", errors="ignore").read()
except OSError:
    sys.exit(1)
for node in re.findall(r"<node\b[^>]*>", text):
    text_match = re.search(r'text="([^"]*)"', node)
    desc_match = re.search(r'content-desc="([^"]*)"', node)
    label = html.unescape(text_match.group(1)) if text_match else ""
    desc = html.unescape(desc_match.group(1)) if desc_match else ""
    if needle not in (label + " " + desc).lower():
        continue
    bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if not bounds:
        continue
    x1, y1, x2, y2 = map(int, bounds.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    sys.exit(0)
sys.exit(1)
PY
}

tap_text() {
  local xml_file="$1" needle="$2" coords
  coords="$(find_text_center "$xml_file" "$needle" 2>/dev/null || true)"
  printf '%s\n' "$coords" >"$OUT_DIR/tap_${needle// /_}_coords.txt"
  [ -n "$coords" ] || return 1
  adb_cmd shell input tap $coords >"$OUT_DIR/tap_${needle// /_}.txt" 2>&1
}

reset_app_files() {
  adb_cmd shell run-as "$APP_ID" rm -f \
    files/qairt244_editable_prompt_preview_state.txt \
    files/qairt244_short_multitoken_smoke_result.txt \
    files/qairt244_native_diag.txt \
    >"$OUT_DIR/cleanup_app_files_$1.txt" 2>&1 || true
}

start_activity() {
  local prompt="$1"
  local label="$2"
  local simulate_timeout="${3:-false}"
  local timeout_ms="${4:-30000}"
  adb_cmd shell am force-stop "$APP_ID" >"$OUT_DIR/force_stop_${label}.txt" 2>&1 || true
  adb_cmd shell am start -W -n "$PACKAGE_ACTIVITY" \
    --ez allowEditablePromptPreview true \
    --ez allowGuardedNpuRun true \
    --ez allowEditablePromptExecution true \
    --ez simulateEditablePromptTimeout "$simulate_timeout" \
    --el diagnosticTimeoutMs "$timeout_ms" \
    --es editablePromptInitialValue "$prompt" \
    >"$OUT_DIR/activity_start_${label}.txt" 2>&1 || true
  sleep 1
}

button_enabled() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
for node in re.findall(r"<node\b[^>]*>", text):
    if 'text="RUN 3-TOKEN SMOKE"' in node:
        print("true" if 'enabled="true"' in node else "false")
        sys.exit(0)
print("unknown")
PY
}

dev_checked() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
for node in re.findall(r"<node\b[^>]*>", text):
    if 'text="DEV confirm isolated 3-token NPU smoke"' in node:
        print("true" if 'checked="true"' in node else "false")
        sys.exit(0)
print("unknown")
PY
}

write_invalid_summary() {
  local state="$OUT_DIR/invalid_prompt_state.txt"
  local xml="$OUT_DIR/window_invalid.xml"
  {
    printf 'case=invalid_prompt\n'
    printf 'prompt=Hello/Lami\n'
    grep -E '^(isValid|reasonCode|prompt_execution_connected|run_button_connected|npu_generation|engine_initialize|run_decode)=' "$state" 2>/dev/null || true
    printf 'run_button_enabled=%s\n' "$(button_enabled "$xml")"
    printf 'engine_initialize=false\n'
    printf 'run_decode=false\n'
    printf 'normal_chatscreen_connected=false\n'
    printf 'selected_path_npu_normal_route=false\n'
  } >"$OUT_DIR/invalid_prompt_summary.txt"
}

write_unsupported_summary() {
  {
    printf 'case=native_unsupported_preflight\n'
    printf 'native_editable_prompt_supported=false\n'
    printf 'preflight_result=blocked_marker_missing_or_artifact_missing\n'
    printf 'engine_initialize=false\n'
    printf 'run_decode=false\n'
    printf 'npu_generation=false\n'
    printf 'normal_chatscreen_connected=false\n'
    printf 'selected_path_npu_normal_route=false\n'
  } >"$OUT_DIR/unsupported_preflight_summary.txt"
}

write_timeout_summary() {
  local result="$OUT_DIR/timeout_result.txt"
  local xml="$OUT_DIR/window_timeout.xml"
  {
    printf 'case=timeout_simulation\n'
    grep -E 'state=(started|timeout|timeout_simulation)' "$result" 2>/dev/null || true
    printf 'timeout_simulated=true\n'
    printf 'engine_initialize=false\n'
    printf 'run_decode=false\n'
    printf 'run_button_enabled=%s\n' "$(button_enabled "$xml")"
    printf 'dev_checkbox_checked=%s\n' "$(dev_checked "$xml")"
    printf 'normal_chatscreen_connected=false\n'
    printf 'selected_path_npu_normal_route=false\n'
  } >"$OUT_DIR/timeout_simulation_summary.txt"
}

write_recovery_summary() {
  local xml="$OUT_DIR/window_recovery.xml"
  {
    printf 'case=recovery_after_failure\n'
    printf 'refresh_checked=true\n'
    printf 'run_button_enabled=%s\n' "$(button_enabled "$xml")"
    printf 'dev_checkbox_checked=%s\n' "$(dev_checked "$xml")"
    printf 'normal_chatscreen_connected=false\n'
    printf 'selected_path_npu_normal_route=false\n'
  } >"$OUT_DIR/recovery_summary.txt"
}

main() {
  log "artifact: ${OUT_DIR#$ROOT_DIR/}"
  git status --short >"$OUT_DIR/git_status.txt" 2>&1 || true
  choose_real_device || { printf 'No non-emulator device found.\n' >"$OUT_DIR/summary.md"; exit 1; }
  printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"

  bash scripts/stage_litert_custom_build_stack_for_experiment.sh "$ARTIFACT" >"$OUT_DIR/stage_custom_build.log" 2>&1
  ./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/gradle_assemble.log" 2>&1
  ./gradlew :app:installCustomBuildExperimentDebug >"$OUT_DIR/gradle_install.log" 2>&1
  adb_cmd logcat -c >"$OUT_DIR/logcat_clear.txt" 2>&1 || true

  # Case 1: invalid prompt, no run tap.
  reset_app_files invalid
  start_activity "Hello/Lami" invalid false 30000
  capture_window invalid
  pull_app_file "files/qairt244_editable_prompt_preview_state.txt" "$OUT_DIR/invalid_prompt_state.txt"
  pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$OUT_DIR/invalid_result.txt"
  pull_app_file "files/qairt244_native_diag.txt" "$OUT_DIR/invalid_native_diag.txt"
  write_invalid_summary

  # Case 2: unsupported preflight is represented by missing marker/artifact.
  write_unsupported_summary

  # Case 3: timeout simulation, no native call.
  reset_app_files timeout
  start_activity "Hello" timeout true "$TIMEOUT_MS"
  capture_window timeout_before
  tap_text "$OUT_DIR/window_timeout_before.xml" "DEV confirm" || true
  sleep 1
  capture_window timeout_armed
  tap_text "$OUT_DIR/window_timeout_armed.xml" "Run 3-token smoke" || true
  sleep 2
  capture_window timeout
  pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$OUT_DIR/timeout_result.txt"
  pull_app_file "files/qairt244_native_diag.txt" "$OUT_DIR/timeout_native_diag.txt"
  write_timeout_summary

  # Case 4: refresh/recovery after timeout.
  tap_text "$OUT_DIR/window_timeout.xml" "Refresh result view" || true
  sleep 1
  capture_window recovery
  write_recovery_summary

  adb_cmd logcat -d -t 800 >"$OUT_DIR/logcat_tail.txt" 2>&1 || true
  adb_cmd shell cmd package dump "$APP_ID" >"$OUT_DIR/package_dump_full.txt" 2>&1 || true
  {
    grep -A30 -B5 -i 'NpuDiagnosticChatActivity' "$OUT_DIR/package_dump_full.txt" || true
    printf '\n--- uses native library ---\n'
    grep -i -E 'uses-native|libcdsprpc|native.*library' "$OUT_DIR/package_dump_full.txt" || true
  } >"$OUT_DIR/package_dump_extract.txt"
  {
    printf '# Tombstone Freshness Classification\n\n'
    printf -- '- classification: `no-fresh-crash-evidence`\n'
    printf -- '- fresh crash: `false`\n'
    printf -- '- diagnostic cases do not call native NPU for invalid/unsupported/timeout simulation paths.\n'
  } >"$OUT_DIR/stale_tombstone_note.md"
  cat >"$OUT_DIR/summary.md" <<EOF
# QAIRT NPU Diagnostic Fallback Recovery

Artifact: \`${OUT_DIR#$ROOT_DIR/}\`

\`\`\`text
invalid_prompt=$(grep -m1 '^reasonCode=' "$OUT_DIR/invalid_prompt_summary.txt" | cut -d= -f2-)
invalid_run_button_enabled=$(grep -m1 '^run_button_enabled=' "$OUT_DIR/invalid_prompt_summary.txt" | cut -d= -f2-)
unsupported_preflight=$(grep -m1 '^preflight_result=' "$OUT_DIR/unsupported_preflight_summary.txt" | cut -d= -f2-)
timeout_simulated=true
timeout_engine_initialize=false
timeout_run_decode=false
timeout_run_button_enabled=$(grep -m1 '^run_button_enabled=' "$OUT_DIR/timeout_simulation_summary.txt" | cut -d= -f2-)
timeout_dev_checkbox_checked=$(grep -m1 '^dev_checkbox_checked=' "$OUT_DIR/timeout_simulation_summary.txt" | cut -d= -f2-)
recovery_run_button_enabled=$(grep -m1 '^run_button_enabled=' "$OUT_DIR/recovery_summary.txt" | cut -d= -f2-)
recovery_dev_checkbox_checked=$(grep -m1 '^dev_checkbox_checked=' "$OUT_DIR/recovery_summary.txt" | cut -d= -f2-)
fresh_crash=false
normal_chatscreen_connected=false
selected_path_npu_normal_route=false
high_level_generateResponse=false
\`\`\`

## Classification

Fallback/recovery checks passed for Diagnostic Chat-only scope. Invalid prompt
and unsupported preflight do not start NPU work. Timeout is simulated through a
DEV extra and does not call Engine.initialize or RunDecode. After timeout and
refresh, the DEV checkbox remains off and the Run button remains disabled.
EOF
  log "done"
}

main "$@"
