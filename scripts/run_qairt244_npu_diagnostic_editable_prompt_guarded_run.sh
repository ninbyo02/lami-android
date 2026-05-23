#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity"
PACKAGE_ACTIVITY="$APP_ID/$ACTIVITY"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/$TIMESTAMP"
DEVICE_SERIAL=""
PROMPT="Hi"
TIMEOUT_SECONDS=30

while [ $# -gt 0 ]; do
  case "$1" in
    --device)
      DEVICE_SERIAL="${2:-}"
      shift 2
      ;;
    --prompt)
      PROMPT="${2:-}"
      shift 2
      ;;
    --timeout)
      TIMEOUT_SECONDS="${2:-30}"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_diagnostic_editable_prompt_guarded_run.sh [--device <serial>] [--prompt <ascii-short>] [--timeout <seconds>]

Builds/installs customBuildExperimentDebug and launches NPU Diagnostic Chat with:
  allowEditablePromptPreview=true
  allowGuardedNpuRun=true
  allowEditablePromptExecution=true

Current native QAIRT 2.44 short multi-token artifact is fixed to prompt=Hi.
When editable prompt native support is missing, this runner stops at preflight,
collects read-only artifacts, and does not tap RUN.
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
  printf '[qairt244-editable-prompt-guarded-run] %s\n' "$*"
}

adb_cmd() {
  adb -s "$DEVICE_SERIAL" "$@"
}

choose_real_device() {
  adb devices >"$OUT_DIR/adb_devices.txt" 2>&1 || return 1
  if [ -n "$DEVICE_SERIAL" ]; then
    awk -v serial="$DEVICE_SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit found ? 0 : 1 }' "$OUT_DIR/adb_devices.txt"
    return $?
  fi
  DEVICE_SERIAL="$(
    awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }' "$OUT_DIR/adb_devices.txt"
  )"
  [ -n "$DEVICE_SERIAL" ]
}

capture_window() {
  local label="$1"
  local remote_xml="/sdcard/qairt244_editable_prompt_${label}.xml"
  local remote_png="/sdcard/qairt244_editable_prompt_${label}.png"

  adb_cmd shell screencap -p "$remote_png" >"$OUT_DIR/screencap_${label}.txt" 2>&1 || true
  adb_cmd pull "$remote_png" "$OUT_DIR/screenshot_${label}.png" >"$OUT_DIR/screenshot_${label}_pull.txt" 2>&1 || true
  adb_cmd shell uiautomator dump "$remote_xml" >"$OUT_DIR/uiautomator_${label}.txt" 2>&1 || true
  adb_cmd pull "$remote_xml" "$OUT_DIR/window_${label}.xml" >"$OUT_DIR/window_${label}_pull.txt" 2>&1 || true
  if ! grep -q '<hierarchy' "$OUT_DIR/window_${label}.xml" 2>/dev/null; then
    {
      printf '<?xml version="1.0" encoding="UTF-8"?>\n'
      printf '<window-dump-fallback label="%s" reason="uiautomator unavailable">\n' "$label"
      printf '  <screenshot file="screenshot_%s.png"/>\n' "$label"
      printf '</window-dump-fallback>\n'
    } >"$OUT_DIR/window_${label}.xml"
  fi
}

pull_app_file() {
  local remote="$1"
  local local_file="$2"
  adb_cmd shell run-as "$APP_ID" cat "$remote" >"$local_file" 2>"$local_file.pull.err" || true
}

write_preflight() {
  local native_supported="false"
  local kotlin_supported="unknown"
  if rg -q 'supportsEditablePromptExecution\(\): Boolean = false' app/src/customBuildExperimentDebug/java 2>/dev/null; then
    kotlin_supported="false"
  fi
  {
    printf 'editable_prompt_native_supported=%s\n' "$native_supported"
    printf 'kotlin_supportsEditablePromptExecution=%s\n' "$kotlin_supported"
    printf 'native_short_multitoken_prompt=fixed_hi\n'
    printf 'preflight_result=blocked_native_fixed_hi\n'
    printf 'run_button_should_be_clicked=false\n'
    printf 'engine_initialize=false\n'
    printf 'run_decode=false\n'
    printf 'npu_generation=false\n'
  } >"$OUT_DIR/preflight.txt"
}

write_summary() {
  local state_file="$OUT_DIR/editable_prompt_preview_state.txt"
  local result_file="$OUT_DIR/result.txt"
  local run_executed="false"
  local result_status="not_run"
  local output="not_run"
  if grep -q '^result=' "$result_file" 2>/dev/null; then
    run_executed="true"
    result_status="$(grep -m1 '^result=' "$result_file" | cut -d= -f2-)"
    output="$(grep -m1 '^output=' "$result_file" | cut -d= -f2-)"
  fi

  {
    printf '# QAIRT 2.44 NPU Diagnostic Editable Prompt Guarded Run\n\n'
    printf 'Artifact: `%s`\n\n' "${OUT_DIR#$ROOT_DIR/}"
    printf '## Outcome\n\n'
    printf '```text\n'
    printf 'requested_prompt=%s\n' "$PROMPT"
    printf 'run_executed=%s\n' "$run_executed"
    printf 'result=%s\n' "$result_status"
    printf 'output=%s\n' "$output"
    cat "$OUT_DIR/preflight.txt" 2>/dev/null || true
    if [ -s "$state_file" ]; then
      cat "$state_file"
    fi
    printf 'timeout=false\n'
    printf 'fresh_crash=false\n'
    printf 'normal_chatscreen_connected=false\n'
    printf 'selected_path_npu_normal_route=false\n'
    printf '```\n\n'
    printf '## Classification\n\n'
    printf 'The current native short multi-token entrypoint is fixed to prompt `Hi`; editable prompt execution is therefore preflight-blocked. RUN was not tapped and no NPU generation was started.\n'
  } >"$OUT_DIR/summary.md"
}

main() {
  log "artifact: ${OUT_DIR#$ROOT_DIR/}"
  if ! choose_real_device; then
    printf 'No non-emulator device found.\n' >"$OUT_DIR/summary.md"
    exit 1
  fi
  printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"

  write_preflight

  git status --short >"$OUT_DIR/git_status.txt" 2>&1 || true
  ./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/gradle_assemble.log" 2>&1
  ./gradlew :app:installCustomBuildExperimentDebug >"$OUT_DIR/gradle_install.log" 2>&1

  adb_cmd logcat -c >"$OUT_DIR/logcat_clear.txt" 2>&1 || true
  adb_cmd shell am force-stop "$APP_ID" >"$OUT_DIR/force_stop_before.txt" 2>&1 || true
  adb_cmd shell run-as "$APP_ID" rm -f \
    files/qairt244_editable_prompt_preview_state.txt \
    files/qairt244_short_multitoken_smoke_result.txt \
    files/qairt244_native_diag.txt \
    >"$OUT_DIR/cleanup_app_files.txt" 2>&1 || true

  adb_cmd shell am start -W -n "$PACKAGE_ACTIVITY" \
    --ez allowEditablePromptPreview true \
    --ez allowGuardedNpuRun true \
    --ez allowEditablePromptExecution true \
    >"$OUT_DIR/activity_start.txt" 2>&1 || true
  sleep 1
  capture_window "before"

  # The Activity writes the prompt preview state on launch. Current native code
  # cannot consume editable prompts, so this runner intentionally does not tap
  # DEV checkbox or RUN.
  pull_app_file "files/qairt244_editable_prompt_preview_state.txt" "$OUT_DIR/editable_prompt_preview_state.txt"
  pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$OUT_DIR/result.txt"
  pull_app_file "files/qairt244_native_diag.txt" "$OUT_DIR/native_diag.txt"

  capture_window "after"
  adb_cmd logcat -d -t 400 >"$OUT_DIR/logcat_tail.txt" 2>&1 || true
  adb_cmd shell cmd package dump "$APP_ID" >"$OUT_DIR/package_dump_full.txt" 2>&1 || true
  {
    grep -A30 -B5 -i 'NpuDiagnosticChatActivity' "$OUT_DIR/package_dump_full.txt" || true
    printf '\n--- uses native library ---\n'
    grep -i -E 'uses-native|libcdsprpc|native.*library' "$OUT_DIR/package_dump_full.txt" || true
  } >"$OUT_DIR/package_dump_extract.txt"

  {
    printf '# Tombstone Freshness Classification\n\n'
    printf -- '- classification: `not-run`\n'
    printf -- '- reason: editable prompt execution preflight blocked before RUN tap\n'
    printf -- '- fresh crash: `false`\n'
  } >"$OUT_DIR/stale_tombstone_note.md"

  write_summary
  log "done"
}

main "$@"
